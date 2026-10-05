package com.zzzudroid.pen.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {

    @Test
    fun `нормализация убирает решётку и лишние пробелы`() {
        assertEquals("планерка", Tags.normalize("  #планерка "))
        assertEquals("идеи на лето", Tags.normalize("идеи    на   лето"))
        assertNull(Tags.normalize("   "))
        assertNull(Tags.normalize("#"))
    }

    @Test
    fun `длинный тег обрезается`() {
        val t = Tags.normalize("а".repeat(100))!!
        assertEquals(Tags.MAX_LENGTH, t.length)
    }

    @Test
    fun `дубли без учёта регистра не добавляются, регистр первого сохраняется`() {
        var tags = Tags.add(emptyList(), "Идеи")
        tags = Tags.add(tags, "идеи")
        tags = Tags.add(tags, "#ИДЕИ ")
        assertEquals(listOf("Идеи"), tags)
    }

    @Test
    fun `больше лимита не добавляется`() {
        var tags = emptyList<String>()
        repeat(Tags.MAX_PER_DOC + 5) { tags = Tags.add(tags, "тег$it") }
        assertEquals(Tags.MAX_PER_DOC, tags.size)
    }

    @Test
    fun `удаление без учёта регистра`() {
        assertEquals(listOf("планерка"), Tags.remove(listOf("Идеи", "планерка"), "идеи"))
    }

    @Test
    fun `очистка списка из файла`() {
        assertEquals(listOf("а", "б"), Tags.clean(listOf(" а ", "А", "", "#б")))
    }

    @Test
    fun `частые теги сначала, затем по алфавиту`() {
        val docs = listOf(
            listOf("идеи", "планерка"),
            listOf("Идеи"),
            listOf("работа", "идеи", "идеи"), // повтор внутри документа считается один раз
            listOf("планерка"),
        )
        val c = Tags.counts(docs)
        assertEquals(listOf("идеи" to 3, "планерка" to 2, "работа" to 1), c.map { it.first.lowercase() to it.second })
    }

    @Test
    fun `фильтр по тегу`() {
        assertTrue(Tags.matches(listOf("Идеи"), null))
        assertTrue(Tags.matches(listOf("Идеи"), "идеи"))
        assertFalse(Tags.matches(listOf("Идеи"), "планерка"))
        assertFalse(Tags.matches(emptyList(), "идеи"))
    }

    @Test
    fun `подсказки не повторяют уже выбранные и учитывают ввод`() {
        val known = listOf("идеи" to 5, "планерка" to 3, "проект" to 2)
        assertEquals(listOf("планерка", "проект"), Tags.suggestions(known, listOf("идеи"), ""))
        assertEquals(listOf("планерка", "проект"), Tags.suggestions(known, emptyList(), "п"))
        assertEquals(listOf("проект"), Tags.suggestions(known, emptyList(), "#про"))
        assertTrue(Tags.suggestions(known, emptyList(), "я").isEmpty())
    }
}
