package com.zzzudroid.pen.notes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyDocTest {
    private fun check(
        pdf: Boolean = false, pages: Int = 1, strokes: Int = 0, links: Int = 0, bookmarks: Int = 0, tags: Int = 0,
    ) = EmptyDoc.isDiscardable(pdf, pages, strokes, links, bookmarks, tags)

    @Test
    fun `нетронутая заметка или холст пусты`() = assertTrue(check())

    @Test
    fun `любой след работы спасает документ`() {
        assertFalse(check(strokes = 1))
        assertFalse(check(links = 1))
        assertFalse(check(bookmarks = 1))
        assertFalse(check(tags = 1))
    }

    @Test
    fun `добавленные страницы — намеренная работа`() = assertFalse(check(pages = 2))

    @Test
    fun `копию PDF не удаляем`() = assertFalse(check(pdf = true))
}
