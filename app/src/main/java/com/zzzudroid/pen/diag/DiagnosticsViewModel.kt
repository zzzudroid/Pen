package com.zzzudroid.pen.diag

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.ViewModel

const val TAG = "PenDiag"

/** Последнее известное состояние одного указателя — для панели живых значений. */
data class PointerLive(
    val pointerId: Int,
    val tool: Tool,
    val action: String,
    val hover: Boolean,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float,
    val tiltRad: Float,
    val orientationRad: Float,
    val distance: Float,
    val buttonState: Int,
    val historySize: Int,
    val deviceName: String,
    val source: String,
)

/** Точка тестового штриха: координаты в системе тестовой зоны + давление. */
class StrokePoint(val x: Float, val y: Float, val pressure: Float)

/** Снимок состояния для отрисовки UI (собирается не чаще ~10 раз в секунду). */
data class DiagSnapshot(
    val pointers: List<PointerLive>,
    val tools: List<ToolSnapshot>,
    val buttonStates: List<Int>,
    val keyCodes: List<String>,
    val log: List<String>,
    val hoverSeen: Boolean,
    val unbuffered: Boolean,
    val totalEvents: Long,
)

/** Неизменяемая копия статистики инструмента для UI и отчёта. */
data class ToolSnapshot(
    val tool: Tool,
    val contactSamples: Long,
    val hoverSamples: Long,
    val pressureMin: Float,
    val pressureMax: Float,
    val pressureLevels: Int,
    val tiltMinRad: Float,
    val tiltMaxRad: Float,
    val orientationMinRad: Float,
    val orientationMaxRad: Float,
    val contactRateHz: Double,
    val hoverRateHz: Double,
    val avgHistoricalPerEvent: Double,
)

class DiagnosticsViewModel : ViewModel() {

    /** Экран диагностики открыт — только тогда собираем события. */
    var active = false

    /** Режим unbuffered dispatch: события приходят сразу, без группировки по кадрам. */
    var unbuffered = false

    private val acc = PenStatsAccumulator()
    private val pointers = LinkedHashMap<Int, PointerLive>()
    private val log = ArrayDeque<String>()
    private var hoverSeen = false
    private var totalEvents = 0L
    private val startUptime = SystemClock.uptimeMillis()

    // Штрихи тестовой зоны хранятся здесь, чтобы пережить поворот экрана
    val strokes = mutableListOf<MutableList<StrokePoint>>()

    /** Сырое событие касания/hover/кнопки с уровня Activity. */
    fun onRawMotion(ev: MotionEvent) {
        if (!active) return
        totalEvents++
        val action = ev.actionMasked
        val hover = action == MotionEvent.ACTION_HOVER_ENTER ||
            action == MotionEvent.ACTION_HOVER_MOVE ||
            action == MotionEvent.ACTION_HOVER_EXIT
        if (hover) hoverSeen = true
        if (ev.buttonState != 0) acc.buttonStates += ev.buttonState

        val deviceName = ev.device?.name ?: "?"
        val source = sourceNames(ev.source).joinToString("|")
        val actionName = actionName(ev)

        for (i in 0 until ev.pointerCount) {
            val id = ev.getPointerId(i)
            val tool = toolOf(ev.getToolType(i))

            // Нажатие/отпускание кнопки — не точка движения, в статистику не идёт
            if (action != MotionEvent.ACTION_BUTTON_PRESS && action != MotionEvent.ACTION_BUTTON_RELEASE) {
                acc.addEvent(tool, id, samplesOf(ev, i), hover)
            }

            pointers[id] = PointerLive(
                pointerId = id,
                tool = tool,
                action = actionName,
                hover = hover,
                x = ev.getX(i),
                y = ev.getY(i),
                pressure = ev.getPressure(i),
                size = ev.getSize(i),
                tiltRad = ev.getAxisValue(MotionEvent.AXIS_TILT, i),
                orientationRad = ev.getOrientation(i),
                distance = ev.getAxisValue(MotionEvent.AXIS_DISTANCE, i),
                buttonState = ev.buttonState,
                historySize = ev.historySize,
                deviceName = deviceName,
                source = source,
            )

            // Конец потока точек — следующий интервал не считаем частотой
            val ended = when (action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_HOVER_EXIT -> true
                MotionEvent.ACTION_POINTER_UP -> i == ev.actionIndex
                else -> false
            }
            if (ended) acc.endStream(id, hover)
        }

        val line = "${relTime()} $actionName ${pointerSummary(ev)}"
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_HOVER_MOVE) {
            Log.v(TAG, line)
        } else {
            Log.d(TAG, line)
            addLog(line)
        }
    }

    /** Клавиши: часть перьев отдаёт свои кнопки как KeyEvent. */
    fun onKeyEvent(ev: KeyEvent) {
        if (!active) return
        val code = KeyEvent.keyCodeToString(ev.keyCode)
        acc.keyCodes += code
        val act = when (ev.action) {
            KeyEvent.ACTION_DOWN -> "KEY_DOWN"
            KeyEvent.ACTION_UP -> "KEY_UP"
            else -> "KEY_${ev.action}"
        }
        val line = "${relTime()} $act $code dev=${ev.device?.name ?: "?"} scan=${ev.scanCode}"
        Log.d(TAG, line)
        addLog(line)
    }

    fun reset() {
        acc.reset()
        pointers.clear()
        log.clear()
        hoverSeen = false
        totalEvents = 0
    }

    fun clearStrokes() = strokes.clear()

    fun snapshot() = DiagSnapshot(
        pointers = pointers.values.toList(),
        tools = acc.tools.map { it.toSnapshot() },
        buttonStates = acc.buttonStates.toList(),
        keyCodes = acc.keyCodes.toList(),
        log = log.toList(),
        hoverSeen = hoverSeen,
        unbuffered = unbuffered,
        totalEvents = totalEvents,
    )

    private fun addLog(line: String) {
        log.addFirst(line)
        while (log.size > LOG_SIZE) log.removeLast()
    }

    private fun relTime(): String {
        val ms = SystemClock.uptimeMillis() - startUptime
        return "%d.%03d".format(ms / 1000, ms % 1000)
    }

    companion object {
        private const val LOG_SIZE = 40
    }
}

private fun ToolStats.toSnapshot() = ToolSnapshot(
    tool = tool,
    contactSamples = contactSamples,
    hoverSamples = hoverSamples,
    pressureMin = pressure.min,
    pressureMax = pressure.max,
    pressureLevels = pressureLevelCount,
    tiltMinRad = tilt.min,
    tiltMaxRad = tilt.max,
    orientationMinRad = orientation.min,
    orientationMaxRad = orientation.max,
    contactRateHz = contactRateHz,
    hoverRateHz = hoverRateHz,
    avgHistoricalPerEvent = avgHistoricalPerEvent,
)

fun toolOf(toolType: Int) = when (toolType) {
    MotionEvent.TOOL_TYPE_STYLUS -> Tool.STYLUS
    MotionEvent.TOOL_TYPE_ERASER -> Tool.ERASER
    MotionEvent.TOOL_TYPE_FINGER -> Tool.FINGER
    MotionEvent.TOOL_TYPE_MOUSE -> Tool.MOUSE
    else -> Tool.UNKNOWN
}

/** Все точки указателя из события: исторические, затем текущая. */
private fun samplesOf(ev: MotionEvent, i: Int): List<PenSample> {
    val n = ev.historySize
    val out = ArrayList<PenSample>(n + 1)
    for (h in 0 until n) {
        out += PenSample(
            x = ev.getHistoricalX(i, h),
            y = ev.getHistoricalY(i, h),
            pressure = ev.getHistoricalPressure(i, h),
            tiltRad = ev.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i, h),
            orientationRad = ev.getHistoricalOrientation(i, h),
            timeNanos = ev.getHistoricalEventTimeNanos(h),
        )
    }
    out += PenSample(
        x = ev.getX(i),
        y = ev.getY(i),
        pressure = ev.getPressure(i),
        tiltRad = ev.getAxisValue(MotionEvent.AXIS_TILT, i),
        orientationRad = ev.getOrientation(i),
        timeNanos = ev.eventTimeNanos,
    )
    return out
}

private fun actionName(ev: MotionEvent): String {
    var name = MotionEvent.actionToString(ev.actionMasked)
    if (ev.actionMasked == MotionEvent.ACTION_BUTTON_PRESS || ev.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) {
        name += "(" + buttonNames(ev.actionButton) + ")"
    }
    // FLAG_CANCELED — система отменила касание (обычно распознала ладонь)
    if (ev.flags and MotionEvent.FLAG_CANCELED != 0) name += "[CANCELED]"
    return name
}

private fun pointerSummary(ev: MotionEvent): String = buildString {
    for (i in 0 until ev.pointerCount) {
        append("#").append(ev.getPointerId(i)).append(' ')
        append(toolOf(ev.getToolType(i)).name)
        append(" p=").append("%.3f".format(ev.getPressure(i)))
        append(" tilt=").append("%.2f".format(ev.getAxisValue(MotionEvent.AXIS_TILT, i)))
        append(' ')
    }
    if (ev.buttonState != 0) append("btn=").append(buttonNames(ev.buttonState)).append(' ')
    append("hist=").append(ev.historySize)
}

/** Расшифровка битовой маски кнопок MotionEvent. */
fun buttonNames(state: Int): String {
    if (state == 0) return "нет"
    val names = listOf(
        MotionEvent.BUTTON_PRIMARY to "PRIMARY",
        MotionEvent.BUTTON_SECONDARY to "SECONDARY",
        MotionEvent.BUTTON_TERTIARY to "TERTIARY",
        MotionEvent.BUTTON_BACK to "BACK",
        MotionEvent.BUTTON_FORWARD to "FORWARD",
        MotionEvent.BUTTON_STYLUS_PRIMARY to "STYLUS_PRIMARY",
        MotionEvent.BUTTON_STYLUS_SECONDARY to "STYLUS_SECONDARY",
    ).filter { state and it.first != 0 }.map { it.second }
    return names.joinToString("+") + " (0x" + Integer.toHexString(state) + ")"
}
