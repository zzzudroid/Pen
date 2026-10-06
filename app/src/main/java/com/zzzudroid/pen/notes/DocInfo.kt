package com.zzzudroid.pen.notes

import java.net.URLDecoder

/** Тексты для свойств документа и подтверждения удаления (чистый Kotlin, покрыт JVM-тестами). */
object DocInfo {
    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    private const val DOCUMENT_PREFIX = "/document/"

    /**
     * Откуда скопирован PDF, по-человечески. Путь известен, только если файл выбирали из памяти устройства
     * (внутренней или карты): тогда это «Внутренняя память/Download/файл.pdf». У остальных источников
     * («Загрузки», облака, мессенджеры) система путь не сообщает — показываем имя файла и источник.
     */
    fun originText(uri: String?, name: String?): String {
        if (uri.isNullOrBlank()) return "неизвестно (копия создана до того, как программа стала это запоминать)"
        val parsed = parse(uri)
        val path = parsed?.let { (authority, docId) -> if (authority == EXTERNAL_STORAGE) storagePath(docId) else null }
        if (path != null) return path
        val source = parsed?.first ?: "неизвестный источник"
        return (name?.let { "$it, " } ?: "") + "источник «$source», путь система не сообщает"
    }

    /** Текст подтверждения удаления: у копии PDF — что исходный файл останется нетронутым. */
    fun deleteWarning(title: String, meta: NotebookMeta?): String = when {
        meta == null || !meta.hasPdfSource ->
            "«$title» будет удалён без возможности восстановления."
        meta.hasInk ->
            "«$title» — копия PDF с вашими заметками. Копия и заметки будут удалены без возможности восстановления; " +
                "исходный PDF-файл не пострадает."
        else ->
            "«$title» — копия PDF без заметок. Удалится только копия; исходный PDF-файл не пострадает."
    }

    /** Заголовок подтверждения удаления нескольких документов: «Удалить 3 документа?». */
    fun deleteManyTitle(n: Int): String = "Удалить $n ${plural(n, "документ", "документа", "документов")}?"

    /** Что сказать при удалении нескольких: копии PDF с заметками выделяем отдельно — это единственное, чего может быть жалко. */
    fun deleteManyWarning(metas: List<NotebookMeta?>): String {
        val withInk = metas.count { it?.hasPdfSource == true && it.hasInk }
        val pdfCopies = metas.count { it?.hasPdfSource == true }
        return buildString {
            append("Документы будут удалены без возможности восстановления.")
            if (pdfCopies > 0) append(" Исходные PDF-файлы копий не пострадают.")
            if (withInk > 0) append(" Копий PDF с вашими заметками среди них: $withInk.")
        }
    }

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }

    /** («авторитет», id документа) из content://авторитет/document/id; null — адрес другого вида. */
    private fun parse(uri: String): Pair<String, String>? {
        val rest = uri.removePrefix("content://").takeIf { it != uri } ?: return null
        val slash = rest.indexOf('/')
        if (slash < 0) return null
        val authority = rest.substring(0, slash)
        val tail = rest.substring(slash)
        val idStart = tail.indexOf(DOCUMENT_PREFIX).takeIf { it >= 0 } ?: return authority to ""
        val id = tail.substring(idStart + DOCUMENT_PREFIX.length).substringBefore('/')
        return authority to runCatching { URLDecoder.decode(id, "UTF-8") }.getOrDefault(id)
    }

    /** «primary:Download/a.pdf» → «Внутренняя память/Download/a.pdf»; «1A2B-3C4D:Docs/a.pdf» → «Карта памяти 1A2B-3C4D/Docs/a.pdf». */
    private fun storagePath(docId: String): String? {
        val colon = docId.indexOf(':')
        if (colon <= 0) return null
        val volume = docId.substring(0, colon)
        val path = docId.substring(colon + 1)
        val root = if (volume == "primary") "Внутренняя память" else "Карта памяти $volume"
        return if (path.isEmpty()) root else "$root/$path"
    }
}
