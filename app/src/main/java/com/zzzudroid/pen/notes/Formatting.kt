package com.zzzudroid.pen.notes

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Форматирование для карточек документов (чистый Kotlin, покрыт JVM-тестами). */
object Formatting {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val RU = Locale("ru")

    /** Дата цифрами через точку, без времени и названия месяца: `05.10.2026`. */
    fun formatDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Размер файла: `1012 Б`, `38,0 КБ`, `1,5 МБ` (один знак после запятой, десятичная запятая). */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val kb = bytes / 1024.0
        // Округление до одного знака не должно давать «1024,0 КБ»: такое значение показываем в мегабайтах
        if (round1(kb) < 1024.0) return String.format(RU, "%.1f КБ", kb)
        return String.format(RU, "%.1f МБ", kb / 1024.0)
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
