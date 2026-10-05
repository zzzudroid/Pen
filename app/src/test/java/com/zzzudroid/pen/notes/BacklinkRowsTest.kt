package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.DocLink
import com.zzzudroid.pen.canvas.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class BacklinkRowsTest {

    @Test
    fun `страница нумеруется с единицы`() {
        assertEquals("Лекции · стр. 3", BacklinkRows.format("Лекции", DocKind.NOTEBOOK, 2))
    }

    @Test
    fun `доска подписывается словом холст`() {
        assertEquals("Схема · холст", BacklinkRows.format("Схема", DocKind.BOARD, 0))
    }

    @Test
    fun `неизвестная страница — только название`() {
        assertEquals("Лекции", BacklinkRows.format("Лекции", DocKind.NOTEBOOK, -1))
    }

    @Test
    fun `строка ведёт к центру области ссылки, неизвестная страница — первая`() {
        val src = IndexedDoc("s", FileStamp("Лекции.pen", 1, 1), "Лекции", DocKind.NOTEBOOK, listOf("p1", "p2"), emptyList())
        val link = DocLink("l", "p2", 10f, 20f, 30f, 60f, LinkTarget("t"))
        val row = BacklinkRows.rows(listOf(Backlink(src, link, 1), Backlink(src, link, -1)))
        assertEquals(BacklinkRow("Лекции · стр. 2", "Лекции.pen", 1, 20f, 40f), row[0])
        assertEquals(0, row[1].page)
    }
}
