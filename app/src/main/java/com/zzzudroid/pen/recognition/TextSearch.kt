package com.zzzudroid.pen.recognition

/** Совпадение в тексте страницы: сниппет и диапазоны подсветки внутри него. */
data class TextMatch(val snippet: String, val highlights: List<IntRange>)

/** Нормализация и поиск по распознанному тексту (чистый Kotlin, покрыт JVM-тестами). */
object TextSearch {
    const val SNIPPET_RADIUS = 40

    /** Посимвольная нормализация: нижний регистр, «ё» → «е». Длина строки не меняется — индексы совпадают с исходником. */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            val l = c.lowercaseChar()
            append(if (l == 'ё') 'е' else l)
        }
    }

    /** Слова запроса (буквы и цифры), нормализованные. */
    fun words(query: String): List<String> =
        normalize(query).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.distinct()

    /**
     * Найти запрос в тексте: все слова должны встретиться (как подстроки).
     * Возвращает сниппет вокруг первого вхождения первого слова (±[SNIPPET_RADIUS]) и подсветку всех слов в нём, либо null.
     */
    fun match(text: String, query: String): TextMatch? {
        val ws = words(query)
        if (ws.isEmpty()) return null
        val norm = normalize(text)
        if (ws.any { !norm.contains(it) }) return null

        val first = norm.indexOf(ws[0])
        val start = (first - SNIPPET_RADIUS).coerceAtLeast(0)
        val end = (first + ws[0].length + SNIPPET_RADIUS).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        val body = text.substring(start, end).replace('\n', ' ')
        val normBody = norm.substring(start, end)

        val highlights = mutableListOf<IntRange>()
        for (w in ws) {
            var i = normBody.indexOf(w)
            while (i >= 0) {
                highlights += (prefix.length + i) until (prefix.length + i + w.length)
                i = normBody.indexOf(w, i + w.length)
            }
        }
        return TextMatch(prefix + body + suffix, highlights.sortedBy { it.first })
    }
}
