package com.zzzudroid.pen.recognition

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Границы штриха для разбиения на строки (мировые координаты). */
data class StrokeBox(val index: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2
}

/**
 * Разбиение штрихов страницы на строки текста (чистый Kotlin, покрыт JVM-тестами).
 * ML Kit распознаёт по одной строке, поэтому страницу нужно резать на строки заранее.
 */
object LineSegmenter {

    /** Штрих ниже этой доли медианной высоты — «мелочь» (точки, черточки): привязывается к строке позже. */
    private const val SMALL_FRACTION = 0.4f

    /** Допуск по центру относительно высоты строки. */
    private const val CENTER_TOLERANCE = 0.6f

    /** Минимальное вертикальное перекрытие с соседом по строке (доля меньшей высоты). */
    private const val MIN_OVERLAP = 0.3f

    private class Line {
        val boxes = mutableListOf<StrokeBox>()
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        val last get() = boxes.last()
        val height get() = bottom - top

        fun add(b: StrokeBox) {
            boxes += b
            top = min(top, b.top)
            bottom = max(bottom, b.bottom)
        }
    }

    /** Строки сверху вниз; внутри строки — индексы штрихов слева направо. */
    fun segment(boxes: List<StrokeBox>): List<List<Int>> {
        if (boxes.isEmpty()) return emptyList()
        val heights = boxes.map { it.height }.sorted()
        val median = heights[heights.size / 2].coerceAtLeast(1e-3f)
        val (small, big) = boxes.partition { it.height < median * SMALL_FRACTION }

        // Крупные штрихи идут слева направо и цепляются к строке, чей последний штрих рядом по вертикали:
        // так строка «следует» за наклоном почерка
        val lines = mutableListOf<Line>()
        for (b in big.sortedBy { it.left }) {
            val best = lines
                .filter { fits(it, b) }
                .minByOrNull { abs(it.last.centerY - b.centerY) }
            (best ?: Line().also { lines += it }).add(b)
        }
        if (lines.isEmpty()) {
            // Только мелкие штрихи — одна строка
            return listOf(boxes.sortedBy { it.left }.map { it.index })
        }

        // Мелкие штрихи (точки над «й», запятые) — к ближайшей по вертикали строке, при равенстве — пересекающейся по X
        for (s in small) {
            val best = lines.minWith(compareBy<Line>({ verticalGap(it, s) }, { -horizontalOverlap(it, s) }))
            best.add(s)
        }

        return lines
            .sortedBy { it.top }
            .map { line -> line.boxes.sortedBy { it.left }.map { it.index } }
    }

    private fun fits(line: Line, b: StrokeBox): Boolean {
        val prev = line.last
        val overlap = min(prev.bottom, b.bottom) - max(prev.top, b.top)
        if (overlap >= MIN_OVERLAP * min(prev.height, b.height)) return true
        return abs(prev.centerY - b.centerY) <= CENTER_TOLERANCE * max(line.height, b.height)
    }

    private fun verticalGap(line: Line, b: StrokeBox): Float = when {
        b.centerY < line.top -> line.top - b.centerY
        b.centerY > line.bottom -> b.centerY - line.bottom
        else -> 0f
    }

    private fun horizontalOverlap(line: Line, b: StrokeBox): Float {
        val l = line.boxes.minOf { it.left }
        val r = line.boxes.maxOf { it.right }
        return min(r, b.right) - max(l, b.left)
    }
}
