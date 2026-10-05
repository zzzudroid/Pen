package com.zzzudroid.pen.notes

/** Имена файлов тетрадей (чистый Kotlin, покрыт JVM-тестами). */
object FileNames {
    const val EXTENSION = ".pen"
    private const val MAX_LENGTH = 80
    private val FORBIDDEN = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /** Название → безопасная основа имени файла (без расширения). */
    fun sanitize(title: String): String {
        val cleaned = title.replace(FORBIDDEN, "_").trim().trim('.').trim()
        return cleaned.take(MAX_LENGTH).ifEmpty { "Без названия" }
    }

    /**
     * Уникальное имя файла: «Название.pen», «Название (2).pen», …
     * [exists] проверяет, занято ли имя (без учёта регистра — как на файловых системах Windows и FAT).
     */
    fun unique(title: String, exists: (String) -> Boolean): String {
        val base = sanitize(title)
        var name = base + EXTENSION
        var n = 2
        while (exists(name)) {
            name = "$base ($n)$EXTENSION"
            n++
        }
        return name
    }
}
