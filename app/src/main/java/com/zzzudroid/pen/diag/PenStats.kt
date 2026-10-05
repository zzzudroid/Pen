package com.zzzudroid.pen.diag

import kotlin.math.roundToLong

/** Тип инструмента ввода (независимо от констант Android, чтобы логику можно было тестировать на JVM). */
enum class Tool(val title: String) {
    STYLUS("Перо"),
    ERASER("Ластик пера"),
    FINGER("Палец"),
    MOUSE("Мышь"),
    UNKNOWN("Неизвестно"),
}

/** Одна точка ввода: текущая или историческая (накопленная между кадрами). */
data class PenSample(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val tiltRad: Float,
    val orientationRad: Float,
    val timeNanos: Long,
)

/** Диапазон значений; пустой, пока не пришло ни одного значения. */
class Range {
    var min = Float.NaN; private set
    var max = Float.NaN; private set
    val isEmpty get() = min.isNaN()

    fun add(v: Float) {
        if (v.isNaN()) return
        if (isEmpty) {
            min = v
            max = v
            return
        }
        if (v < min) min = v
        if (v > max) max = v
    }
}

/** Накопленная статистика по одному типу инструмента. */
class ToolStats(val tool: Tool) {
    var contactSamples = 0L; private set
    var hoverSamples = 0L; private set
    var events = 0L; private set
    var historicalPoints = 0L; private set

    val pressure = Range()
    val tilt = Range()
    val orientation = Range()

    // Различимые уровни давления: уникальные значения с точностью до 1e-5
    private val pressureLevels = HashSet<Long>()
    val pressureLevelCount get() = pressureLevels.size

    // Интервалы между соседними точками одного потока — для частоты опроса
    private var contactIntervalsNanos = 0L
    private var contactIntervals = 0L
    private var hoverIntervalsNanos = 0L
    private var hoverIntervals = 0L

    /** Частота точек касания, Гц (0 — нет данных). */
    val contactRateHz get() = rate(contactIntervals, contactIntervalsNanos)

    /** Частота точек hover, Гц (0 — нет данных). */
    val hoverRateHz get() = rate(hoverIntervals, hoverIntervalsNanos)

    /** Среднее число исторических точек на одно событие касания. */
    val avgHistoricalPerEvent get() = if (events == 0L) 0.0 else historicalPoints.toDouble() / events

    internal fun addEvent(samples: List<PenSample>, hover: Boolean, prevTimeNanos: Long?) {
        var last = prevTimeNanos
        for (s in samples) {
            if (hover) hoverSamples++ else contactSamples++
            tilt.add(s.tiltRad)
            orientation.add(s.orientationRad)
            if (!hover) {
                pressure.add(s.pressure)
                pressureLevels.add((s.pressure * 1e5f).roundToLong())
            }
            if (last != null) {
                val dt = s.timeNanos - last
                // Паузы длиннее 100 мс — это не частота опроса, а перерыв в движении
                if (dt in 1..MAX_INTERVAL_NANOS) {
                    if (hover) { hoverIntervals++; hoverIntervalsNanos += dt }
                    else { contactIntervals++; contactIntervalsNanos += dt }
                }
            }
            last = s.timeNanos
        }
        if (!hover) {
            events++
            historicalPoints += (samples.size - 1).coerceAtLeast(0)
        }
    }

    private fun rate(count: Long, nanos: Long) = if (nanos == 0L) 0.0 else count * 1e9 / nanos

    companion object {
        const val MAX_INTERVAL_NANOS = 100_000_000L
    }
}

/**
 * Накопитель сводки диагностики за сессию.
 * Поток точек ведётся отдельно для каждого указателя и режима (касание/hover).
 */
class PenStatsAccumulator {
    private val byTool = LinkedHashMap<Tool, ToolStats>()
    private val lastTimeByStream = HashMap<Long, Long>()

    /** Все наблюдавшиеся ненулевые состояния кнопок (битовые маски MotionEvent.buttonState). */
    val buttonStates = sortedSetOf<Int>()

    /** Все наблюдавшиеся коды клавиш (кнопки пера, приходящие как KeyEvent). */
    val keyCodes = sortedSetOf<String>()

    val tools: List<ToolStats> get() = byTool.values.toList()

    fun stats(tool: Tool): ToolStats? = byTool[tool]

    /** Добавить точки одного указателя из одного события (исторические + текущая, по времени). */
    fun addEvent(tool: Tool, pointerId: Int, samples: List<PenSample>, hover: Boolean) {
        if (samples.isEmpty()) return
        val key = streamKey(pointerId, hover)
        val stats = byTool.getOrPut(tool) { ToolStats(tool) }
        stats.addEvent(samples, hover, lastTimeByStream[key])
        lastTimeByStream[key] = samples.last().timeNanos
    }

    /** Указатель поднят / ушёл из hover — следующий интервал не считать частотой. */
    fun endStream(pointerId: Int, hover: Boolean) {
        lastTimeByStream.remove(streamKey(pointerId, hover))
    }

    fun reset() {
        byTool.clear()
        lastTimeByStream.clear()
        buttonStates.clear()
        keyCodes.clear()
    }

    private fun streamKey(pointerId: Int, hover: Boolean) = pointerId.toLong() * 2 + if (hover) 1 else 0
}
