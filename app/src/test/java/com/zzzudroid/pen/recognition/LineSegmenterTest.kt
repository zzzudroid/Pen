package com.zzzudroid.pen.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class LineSegmenterTest {

    private fun box(i: Int, left: Float, top: Float, w: Float = 30f, h: Float = 40f) =
        StrokeBox(i, left, top, left + w, top + h)

    @Test
    fun `две строки сверху вниз, слева направо`() {
        val boxes = listOf(
            box(0, 100f, 0f), box(1, 0f, 0f), box(2, 50f, 5f),
            box(3, 0f, 100f), box(4, 60f, 102f),
        )
        assertEquals(listOf(listOf(1, 2, 0), listOf(3, 4)), LineSegmenter.segment(boxes))
    }

    @Test
    fun `наклонная строка остаётся одной`() {
        // Каждое следующее слово ниже на половину высоты — к концу строка ушла на две высоты вниз
        val boxes = (0 until 5).map { box(it, it * 40f, it * 20f) }
        assertEquals(listOf(listOf(0, 1, 2, 3, 4)), LineSegmenter.segment(boxes))
    }

    @Test
    fun `точка над буквой не делает отдельную строку`() {
        val boxes = listOf(
            box(0, 0f, 100f), box(1, 40f, 100f), box(2, 80f, 100f),
            // «кратка» над третьей буквой: маленькая и выше строки
            box(3, 85f, 80f, w = 10f, h = 6f),
            // следующая строка
            box(4, 0f, 200f), box(5, 40f, 200f),
        )
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4, 5)), LineSegmenter.segment(boxes))
    }

    @Test
    fun `пусто`() {
        assertEquals(emptyList<List<Int>>(), LineSegmenter.segment(emptyList()))
    }
}
