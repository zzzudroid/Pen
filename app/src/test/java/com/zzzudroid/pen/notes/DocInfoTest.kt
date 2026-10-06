package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocInfoTest {
    private fun meta(pdf: Boolean, ink: Boolean) = NotebookMeta(
        "Книга", PageTemplate.BLANK, 100f, 100f, 0, 0, emptyList(), hasPdfSource = pdf, hasInk = ink,
    )

    @Test
    fun `путь во внутренней памяти читается из uri`() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3ADownload%2FКнига.pdf"
        assertEquals("Внутренняя память/Download/Книга.pdf", DocInfo.originText(uri, "Книга.pdf"))
    }

    @Test
    fun `путь на карте памяти`() {
        val uri = "content://com.android.externalstorage.documents/document/1A2B-3C4D%3ADocs%2Fa.pdf"
        assertEquals("Карта памяти 1A2B-3C4D/Docs/a.pdf", DocInfo.originText(uri, "a.pdf"))
    }

    @Test
    fun `у загрузок пути нет — имя и источник`() {
        val uri = "content://com.android.providers.downloads.documents/document/msf%3A1234"
        val t = DocInfo.originText(uri, "a.pdf")
        assertTrue(t, t.startsWith("a.pdf, источник «com.android.providers.downloads.documents»"))
    }

    @Test
    fun `источник неизвестен у старой копии`() {
        assertTrue(DocInfo.originText(null, null).startsWith("неизвестно"))
        assertTrue(DocInfo.originText("  ", null).startsWith("неизвестно"))
    }

    @Test
    fun `адрес не content — не падаем`() {
        assertTrue(DocInfo.originText("file:///sdcard/a.pdf", "a.pdf").contains("путь система не сообщает"))
    }

    @Test
    fun `предупреждение об удалении по видам документа`() {
        assertEquals("«Книга» будет удалён без возможности восстановления.", DocInfo.deleteWarning("Книга", meta(pdf = false, ink = false)))
        assertEquals("«Книга» будет удалён без возможности восстановления.", DocInfo.deleteWarning("Книга", null))
        assertTrue(DocInfo.deleteWarning("Книга", meta(pdf = true, ink = false)).contains("без заметок"))
        val withInk = DocInfo.deleteWarning("Книга", meta(pdf = true, ink = true))
        assertTrue(withInk, withInk.contains("с вашими заметками") && withInk.contains("исходный PDF-файл не пострадает"))
    }
}
