package com.zzzudroid.pen.notes

/**
 * Текстовые теги документов («планерка», «идеи»…). Чистая логика без Android, покрыта JVM-тестами.
 * Регистр тега сохраняется, но «Идеи» и «идеи» считаются одним тегом.
 */
object Tags {
    const val MAX_LENGTH = 24
    const val MAX_PER_DOC = 12

    /** Тег из введённого текста: без пробелов по краям и ведущих «#», лишние пробелы схлопнуты, не длиннее [MAX_LENGTH]; null — пусто. */
    fun normalize(raw: String): String? {
        val t = raw.trim().trimStart('#').trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).trim()
        return t.ifEmpty { null }
    }

    private fun key(tag: String) = tag.lowercase()

    /** [tags] с добавленным [raw]: нормализован, без дублей (без учёта регистра), не больше [MAX_PER_DOC]. */
    fun add(tags: List<String>, raw: String): List<String> {
        val tag = normalize(raw) ?: return tags
        if (tags.any { key(it) == key(tag) } || tags.size >= MAX_PER_DOC) return tags
        return tags + tag
    }

    /** [tags] без [tag] (без учёта регистра). */
    fun remove(tags: List<String>, tag: String): List<String> = tags.filterNot { key(it) == key(tag) }

    /** Очистка списка из файла или ввода: нормализация, дубли и лишнее сверх лимита отбрасываются. */
    fun clean(tags: List<String>): List<String> = tags.fold(emptyList()) { acc, t -> add(acc, t) }

    /** Теги всех документов: сначала самые частые, при равенстве — по алфавиту. Возвращает пары (тег, сколько документов). */
    fun counts(documents: List<List<String>>): List<Pair<String, Int>> {
        val byKey = LinkedHashMap<String, Pair<String, Int>>()
        for (doc in documents) {
            for (tag in doc.distinctBy(::key)) {
                val old = byKey[key(tag)]
                byKey[key(tag)] = if (old == null) tag to 1 else old.first to (old.second + 1)
            }
        }
        return byKey.values.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { key(it.first) })
    }

    /** Подходит ли документ с [docTags] под выбранный тег [selected] (null — фильтра нет). */
    fun matches(docTags: List<String>, selected: String?): Boolean =
        selected == null || docTags.any { key(it) == key(selected) }

    /** Подсказки при вводе: известные теги, которых ещё нет у документа и которые начинаются с введённого (пусто — самые частые). */
    fun suggestions(known: List<Pair<String, Int>>, current: List<String>, typed: String, limit: Int = 8): List<String> {
        val prefix = key(typed.trim().trimStart('#'))
        return known.map { it.first }
            .filter { t -> current.none { key(it) == key(t) } && key(t).startsWith(prefix) }
            .take(limit)
    }
}
