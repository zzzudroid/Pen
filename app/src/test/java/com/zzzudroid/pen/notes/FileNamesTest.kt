package com.zzzudroid.pen.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {

    @Test
    fun `запрещённые символы заменяются`() {
        assertEquals("Лекции_ матан_ 1_2", FileNames.sanitize("Лекции: матан? 1/2"))
    }

    @Test
    fun `пустое и точечное название`() {
        assertEquals("Без названия", FileNames.sanitize("   "))
        assertEquals("Без названия", FileNames.sanitize("..."))
    }

    @Test
    fun `длинное название обрезается`() {
        assertEquals(80, FileNames.sanitize("а".repeat(200)).length)
    }

    @Test
    fun `уникальное имя с суффиксом`() {
        val taken = setOf("лекции.pen", "лекции (2).pen")
        val name = FileNames.unique("Лекции") { it.lowercase() in taken }
        assertEquals("Лекции (3).pen", name)
    }

    @Test
    fun `свободное имя без суффикса`() {
        assertEquals("Дневник.pen", FileNames.unique("Дневник") { false })
    }
}
