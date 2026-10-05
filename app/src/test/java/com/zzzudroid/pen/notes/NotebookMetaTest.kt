package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.Bookmark
import com.zzzudroid.pen.canvas.DocLink
import com.zzzudroid.pen.canvas.LinkTarget
import com.zzzudroid.pen.canvas.PageTemplate
import com.zzzudroid.pen.canvas.ViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookMetaTest {

    private fun meta(links: List<DocLink> = emptyList(), bookmarks: List<Bookmark> = emptyList()) = NotebookMeta(
        title = "Лекции", template = PageTemplate.LINED, pageWidth = 1050f, pageHeight = 1485f,
        created = 1, modified = 2, pages = listOf(PageMeta("p1", 1050f, 1485f, PageTemplate.LINED)),
        docId = "doc-1", links = links, bookmarks = bookmarks,
    )

    /** meta.json формата v2 — без docId, links и bookmarks. */
    private val v2Json = """
        {"formatVersion":2,"title":"Старая","template":"lined","pageWidth":1050.0,"pageHeight":1485.0,
         "created":1,"modified":2,"pages":["a","b"],
         "pageInfo":[{"id":"a","width":1050.0,"height":1485.0,"template":"lined"},{"id":"b","width":1050.0,"height":1485.0,"template":"grid"}]}
    """.trimIndent()

    @Test
    fun `запись и чтение v3 сохраняют идентификатор, ссылки и закладки`() {
        val link = DocLink("l1", "p1", 10f, 20f, 110f, 60f, LinkTarget("doc-2", bookmarkId = "b1", view = ViewState(0.5f, 12f, -7f)))
        val mark = Bookmark("b1", "Склад", ViewState(0.1f, 100f, 200f))
        val text = MetaJson.text(meta(listOf(link), listOf(mark)))
        val back = MetaJson.parse(text, "Лекции.pen", 0, hasPdfSource = false)
        assertEquals("doc-1", back.docId)
        assertEquals(listOf(link), back.links)
        assertEquals(listOf(mark), back.bookmarks)
        assertTrue(text.contains("\"formatVersion\": 3"))
    }

    @Test
    fun `ссылка без закладки и вида читается с пустыми необязательными полями`() {
        val link = DocLink("l1", "p1", 0f, 0f, 5f, 5f, LinkTarget("doc-2"))
        val back = MetaJson.parse(MetaJson.text(meta(listOf(link))), "x.pen", 0, false)
        assertNull(back.links.single().target.bookmarkId)
        assertNull(back.links.single().target.view)
    }

    @Test
    fun `файл v2 читается, ссылок и закладок нет, идентификатор выведен из имени файла`() {
        val m = MetaJson.parse(v2Json, "Старая.pen", 5, hasPdfSource = false)
        assertEquals("Старая", m.title)
        assertEquals(2, m.pages.size)
        assertTrue(m.links.isEmpty())
        assertTrue(m.bookmarks.isEmpty())
        assertEquals(MetaJson.legacyDocId("Старая.pen"), m.docId)
    }

    @Test
    fun `идентификатор старого файла одинаков при каждом чтении и различается у разных файлов`() {
        val a1 = MetaJson.parse(v2Json, "A.pen", 0, false).docId
        val a2 = MetaJson.parse(v2Json, "A.pen", 0, false).docId
        val b = MetaJson.parse(v2Json, "B.pen", 0, false).docId
        assertEquals(a1, a2)
        assertNotEquals(a1, b)
    }

    @Test
    fun `после сохранения идентификатор остаётся и при переименовании`() {
        val old = MetaJson.parse(v2Json, "Старая.pen", 5, false)
        // Сохранение пишет docId в файл; чтение «Новая.pen» с тем же текстом берёт docId из файла, а не из нового имени
        val saved = MetaJson.text(old)
        val renamed = MetaJson.parse(saved, "Новая.pen", 6, false)
        assertEquals(old.docId, renamed.docId)
    }

    @Test(expected = java.io.IOException::class)
    fun `файл более новой версии отвергается`() {
        MetaJson.parse(v2Json.replace("\"formatVersion\":2", "\"formatVersion\":9"), "x.pen", 0, false)
    }
}
