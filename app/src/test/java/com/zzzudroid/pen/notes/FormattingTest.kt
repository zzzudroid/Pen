package com.zzzudroid.pen.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormattingTest {

    @Test
    fun `размер в байтах до килобайта`() {
        assertEquals("0 Б", Formatting.formatFileSize(0))
        assertEquals("1012 Б", Formatting.formatFileSize(1012))
        assertEquals("1023 Б", Formatting.formatFileSize(1023))
    }

    @Test
    fun `размер в килобайтах с запятой`() {
        assertEquals("1,0 КБ", Formatting.formatFileSize(1024))
        assertEquals("38,0 КБ", Formatting.formatFileSize(38 * 1024L))
        assertEquals("493,5 КБ", Formatting.formatFileSize(505_344)) // 493,5 КБ
    }

    @Test
    fun `размер в мегабайтах`() {
        assertEquals("1,0 МБ", Formatting.formatFileSize(1_048_576))
        assertEquals("1,5 МБ", Formatting.formatFileSize(1_590_790))
        assertEquals("7,0 МБ", Formatting.formatFileSize(7_385_992))
    }

    @Test
    fun `граница килобайты-мегабайты не даёт 1024,0 КБ`() {
        assertEquals("1,0 МБ", Formatting.formatFileSize(1_048_575))
    }

    @Test
    fun `дата с ведущими нулями без времени`() {
        // 2026-10-05 19:20 UTC
        val millis = 1_791_228_000_000L
        assertEquals("05.10.2026", Formatting.formatDate(millis, ZoneId.of("UTC")))
    }

    @Test
    fun `дата зависит от часового пояса`() {
        val millis = 1_791_228_000_000L // 19:20 UTC → в Токио уже следующее утро
        assertEquals("06.10.2026", Formatting.formatDate(millis, ZoneId.of("Asia/Tokyo")))
    }
}
