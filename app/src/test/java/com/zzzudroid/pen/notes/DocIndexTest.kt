package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.DocLink
import com.zzzudroid.pen.canvas.LinkTarget
import com.zzzudroid.pen.canvas.ViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocIndexTest {

    private fun stamp(name: String, lm: Long = 1, len: Long = 10) = FileStamp(name, lm, len)

    private fun doc(docId: String, name: String, title: String = name, lm: Long = 1, links: List<DocLink> = emptyList(), pages: List<String> = listOf("p1")) =
        IndexedDoc(docId, stamp(name, lm), title, DocKind.NOTEBOOK, pages, links)

    private fun link(id: String, page: String, target: String, bm: String? = null) =
        DocLink(id, page, 0f, 0f, 10f, 10f, LinkTarget(target, bookmarkId = bm))

    @Test
    fun `неизменённые файлы не перечитываются, изменённые и новые — да`() {
        val old = listOf(doc("a", "a.pen"), doc("b", "b.pen", lm = 1))
        val read = mutableListOf<String>()
        val files = listOf(stamp("a.pen"), stamp("b.pen", lm = 2), stamp("c.pen"))
        val res = DocIndexLogic.update(old, files) { read += it.name; doc("id-${it.name}", it.name, lm = it.lastModified) }
        assertEquals(listOf("b.pen", "c.pen"), read)
        assertEquals(listOf("a", "id-b.pen", "id-c.pen"), res.map { it.docId })
    }

    @Test
    fun `исчезнувшие файлы удаляются из индекса`() {
        val old = listOf(doc("a", "a.pen"), doc("b", "b.pen"))
        val res = DocIndexLogic.update(old, listOf(stamp("a.pen"))) { null }
        assertEquals(listOf("a"), res.map { it.docId })
    }

    @Test
    fun `сбой чтения одного файла не мешает остальным`() {
        val files = listOf(stamp("bad.pen"), stamp("ok.pen"))
        val res = DocIndexLogic.update(emptyList(), files) { if (it.name == "bad.pen") null else doc("ok", it.name) }
        assertEquals(listOf("ok"), res.map { it.docId })
    }

    @Test
    fun `поиск по идентификатору, дубликат — самый свежий`() {
        val docs = listOf(doc("x", "old.pen", lm = 1), doc("x", "new.pen", lm = 9), doc("y", "y.pen"))
        assertEquals("new.pen", DocIndexLogic.find(docs, "x")?.stamp?.name)
        assertNull(DocIndexLogic.find(docs, "нет такого"))
    }

    @Test
    fun `обратные ссылки — только на цель, с номером страницы источника`() {
        val board = doc("board", "board.pen", "Схема")
        val lect = doc("lect", "l.pen", "Лекции", pages = listOf("p1", "p2", "p3"), links = listOf(link("1", "p3", "board", "склад"), link("2", "p1", "другой")))
        val back = DocIndexLogic.backlinks(listOf(board, lect), "board")
        assertEquals(1, back.size)
        assertEquals("Лекции", back.single().source.title)
        assertEquals(2, back.single().pageIndex) // третья страница, индекс с нуля
        assertEquals("склад", back.single().link.target.bookmarkId)
    }

    @Test
    fun `ссылки документа на самого себя в обратные не попадают`() {
        val d = doc("self", "s.pen", links = listOf(link("1", "p1", "self")))
        assertTrue(DocIndexLogic.backlinks(listOf(d), "self").isEmpty())
    }

    @Test
    fun `после удаления ссылки документ исчезает из обратных`() {
        val before = doc("a", "a.pen", links = listOf(link("1", "p1", "t")))
        val after = doc("a", "a.pen", lm = 2, links = emptyList())
        assertEquals(1, DocIndexLogic.backlinks(listOf(before), "t").size)
        assertTrue(DocIndexLogic.backlinks(listOf(after), "t").isEmpty())
    }

    @Test
    fun `индекс переживает запись в JSON и чтение`() {
        val l = DocLink("l", "p1", 1f, 2f, 3f, 4f, LinkTarget("t", "b", ViewState(0.5f, 1f, 2f)))
        val docs = listOf(doc("a", "a.pen", links = listOf(l)))
        assertEquals(docs, DocIndexLogic.fromJson(DocIndexLogic.toJson(docs)))
    }

    @Test
    fun `ссылка на удалённый документ — недоступна без падения`() {
        val t = LinkTarget("нет-такого", bookmarkId = "b")
        val res = LinkRouting.resolve(t, DocIndexLogic.find(emptyList(), t.docId))
        assertEquals(LinkResolution.Missing(LinkRouting.DOC_MISSING), res)
    }

    @Test
    fun `ссылка на найденный документ открывает его по текущему имени файла`() {
        val d = doc("t", "Новое имя.pen")
        val res = LinkRouting.resolve(LinkTarget("t", bookmarkId = "склад"), DocIndexLogic.find(listOf(d), "t"))
        assertEquals(LinkResolution.Open(NoteDestination("Новое имя.pen", "склад", null)), res)
    }
}
