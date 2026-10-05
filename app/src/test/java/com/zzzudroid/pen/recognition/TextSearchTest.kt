package com.zzzudroid.pen.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun `регистр и ё не важны`() {
        val m = TextSearch.match("Ёлка в Лесу", "елка лес")
        assertNotNull(m)
        assertEquals("Ёлка в Лесу", m!!.snippet)
        assertEquals(listOf(0..3, 7..9), m.highlights)
    }

    @Test
    fun `все слова должны встретиться`() {
        assertNull(TextSearch.match("интеграл по частям", "интеграл ряд"))
        assertNotNull(TextSearch.match("интеграл по частям", "частям интеграл"))
    }

    @Test
    fun `сниппет обрезается с многоточиями`() {
        val text = "а".repeat(100) + " ключ " + "б".repeat(100)
        val m = TextSearch.match(text, "ключ")!!
        assertTrue(m.snippet.startsWith("…"))
        assertTrue(m.snippet.endsWith("…"))
        val h = m.highlights.single()
        assertEquals("ключ", m.snippet.substring(h.first, h.last + 1))
    }

    @Test
    fun `пустой запрос ничего не находит`() {
        assertNull(TextSearch.match("текст", "  ,. "))
    }

    @Test
    fun `переводы строк в сниппете заменяются пробелами`() {
        assertEquals("первая вторая", TextSearch.match("первая\nвторая", "вторая")!!.snippet)
    }
}
