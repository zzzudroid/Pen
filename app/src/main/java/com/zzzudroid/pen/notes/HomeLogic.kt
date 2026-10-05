package com.zzzudroid.pen.notes

/** Чистая логика главного экрана: сколько показывать «последних» и какие именно (покрыта JVM-тестами). */
object HomeLogic {
    /** Ширина карточки в сетке главного экрана и зазор между карточками, dp. */
    const val CARD_DP = 150f
    const val GAP_DP = 12f

    /** Колонок в половине экрана шириной [halfWidthDp]: сколько карточек помещается по ширине, от 1 до 4. */
    fun columns(halfWidthDp: Float): Int =
        ((halfWidthDp + GAP_DP) / (CARD_DP + GAP_DP)).toInt().coerceIn(1, 4)

    /** Всего ячеек в половине (включая плитку «создать»): два ряда. */
    fun cells(columns: Int): Int = columns * 2

    /**
     * Последние документы вида [kind]: по убыванию времени изменения, не больше [limit].
     * Повреждённый файл (метаданные не читаются) считается тетрадью, а время берётся с самого файла.
     */
    fun recent(entries: List<NotebookEntry>, kind: DocKind, limit: Int): List<NotebookEntry> {
        if (limit <= 0) return emptyList()
        return entries
            .filter { (it.meta?.kind ?: DocKind.NOTEBOOK) == kind }
            .sortedByDescending { it.meta?.modified ?: it.file.lastModified() }
            .take(limit)
    }

    /** Все документы вида [kind] по убыванию времени изменения (для полного списка). */
    fun all(entries: List<NotebookEntry>, kind: DocKind): List<NotebookEntry> = recent(entries, kind, Int.MAX_VALUE)

    /** Имя нового документа, созданного без диалога: сегодняшняя дата `дд.мм.гггг`. */
    fun todayTitle(nowMillis: Long = System.currentTimeMillis()): String = Formatting.formatDate(nowMillis)
}
