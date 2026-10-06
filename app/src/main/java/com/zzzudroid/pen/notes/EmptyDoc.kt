package com.zzzudroid.pen.notes

/** Когда документ считается пустым и удаляется при выходе (чистый Kotlin, покрыт JVM-тестами). */
object EmptyDoc {
    /**
     * Пустой — это новая нетронутая заметка или холст: одна страница без штрихов, без ссылок, закладок и тегов.
     * Копию PDF не удаляем никогда: даже без заметок в ней есть сам PDF, который человек открыл читать.
     * Если страниц несколько, их добавляли намеренно — тоже не трогаем.
     */
    fun isDiscardable(hasPdfSource: Boolean, pageCount: Int, strokeCount: Int, linkCount: Int, bookmarkCount: Int, tagCount: Int): Boolean =
        !hasPdfSource && pageCount == 1 && strokeCount == 0 && linkCount == 0 && bookmarkCount == 0 && tagCount == 0
}
