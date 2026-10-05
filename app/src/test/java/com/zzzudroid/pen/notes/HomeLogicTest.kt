package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeLogicTest {

    private fun meta(kind: DocKind, modified: Long) = NotebookMeta(
        title = "t", template = PageTemplate.LINED, pageWidth = 1050f, pageHeight = 1485f,
        created = 0, modified = modified, pages = listOf(PageMeta("p", 1050f, 1485f, PageTemplate.LINED)), kind = kind,
    )

    private fun entry(name: String, kind: DocKind, modified: Long) =
        NotebookEntry(File(name), meta(kind, modified))

    @Test
    fun `колонки по ширине половины`() {
        assertEquals(3, HomeLogic.columns(557f)) // Pad 7, альбомная
        assertEquals(2, HomeLogic.columns(364f)) // Pad 7, портретная
        assertEquals(1, HomeLogic.columns(100f)) // минимум одна
        assertEquals(4, HomeLogic.columns(5000f)) // максимум четыре
    }

    @Test
    fun `ячеек вдвое больше колонок`() {
        assertEquals(6, HomeLogic.cells(3))
        assertEquals(2, HomeLogic.cells(1))
    }

    @Test
    fun `последние — по виду и убыванию времени с обрезкой`() {
        val list = listOf(
            entry("a.pen", DocKind.NOTEBOOK, 10),
            entry("b.pen", DocKind.BOARD, 50),
            entry("c.pen", DocKind.NOTEBOOK, 30),
            entry("d.pen", DocKind.NOTEBOOK, 20),
        )
        val notes = HomeLogic.recent(list, DocKind.NOTEBOOK, 2)
        assertEquals(listOf("c.pen", "d.pen"), notes.map { it.file.name })
        assertEquals(listOf("b.pen"), HomeLogic.recent(list, DocKind.BOARD, 5).map { it.file.name })
    }

    @Test
    fun `нулевой и отрицательный лимит дают пустой список`() {
        val list = listOf(entry("a.pen", DocKind.NOTEBOOK, 1))
        assertTrue(HomeLogic.recent(list, DocKind.NOTEBOOK, 0).isEmpty())
        assertTrue(HomeLogic.recent(list, DocKind.NOTEBOOK, -3).isEmpty())
    }

    @Test
    fun `повреждённый файл считается тетрадью`() {
        val broken = NotebookEntry(File("x.pen"), null, "ошибка")
        val list = listOf(broken, entry("b.pen", DocKind.BOARD, 5))
        assertEquals(listOf("x.pen"), HomeLogic.recent(list, DocKind.NOTEBOOK, 5).map { it.file.name })
        assertTrue(HomeLogic.recent(list, DocKind.BOARD, 5).none { it.meta == null })
    }

    @Test
    fun `все документы вида`() {
        val list = listOf(entry("a.pen", DocKind.BOARD, 1), entry("b.pen", DocKind.BOARD, 2), entry("c.pen", DocKind.NOTEBOOK, 3))
        assertEquals(listOf("b.pen", "a.pen"), HomeLogic.all(list, DocKind.BOARD).map { it.file.name })
    }

    @Test
    fun `имя нового документа — дата`() {
        // 2026-10-05 в любом часовом поясе ±12 ч от полудня UTC — день не меняется
        assertEquals("05.10.2026", HomeLogic.todayTitle(1_791_201_600_000L)) // 12:00 UTC
    }
}
