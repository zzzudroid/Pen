package com.zzzudroid.pen.notes

/** Чистая логика главного экрана: сколько показывать «последних» и какие именно (покрыта JVM-тестами). */
object HomeLogic {
    /** Зазор между карточками и высота подписи под миниатюрой (значок, дата, страницы), dp. */
    const val GAP_DP = 12f
    const val CAPTION_DP = 56f

    /** Высота подписи при масштабе шрифта [fontScale]: заголовок и строка даты растут вместе со шрифтом. */
    fun captionDp(fontScale: Float): Float = 16f + 40f * fontScale.coerceAtLeast(1f)

    /** Самая узкая карточка, при которой подпись ещё читается целиком, dp. */
    const val MIN_CARD_DP = 130f

    /** Колонок в половине: три в альбомной ориентации, две в портретной. */
    fun columnsFor(landscape: Boolean): Int = if (landscape) 3 else 2

    /** Размер карточек половины: заданное число колонок и целое число рядов (карточки не режутся). */
    data class GridLayout(val cardWidthDp: Float, val rows: Int, val columns: Int, val captionDp: Float = CAPTION_DP) {
        val cardHeightDp get() = cardHeightFor(cardWidthDp, captionDp)
        val cells get() = columns * rows
        val widthDp get() = columns * cardWidthDp + (columns - 1) * GAP_DP
        val heightDp get() = rows * cardHeightDp + (rows - 1) * GAP_DP
    }

    private const val MAX_ROWS = 4

    /** Высота карточки шириной [cardWidthDp]: миниатюра формата A4 и подпись. */
    fun cardHeightFor(cardWidthDp: Float, captionDp: Float = CAPTION_DP): Float = cardWidthDp * (297f / 210f) + captionDp

    /**
     * Раскладка в области [widthDp] × [heightDp]: [columns] колонок на всю ширину и столько рядов, сколько целиком влезает.
     * Если по высоте не влезает и двух рядов (альбомная ориентация), карточки уменьшаются, чтобы поместились два ряда;
     * если и так слишком мелко — один ряд.
     */
    fun layout(widthDp: Float, heightDp: Float, captionDp: Float = CAPTION_DP, columns: Int = 2): GridLayout {
        val full = ((widthDp - (columns - 1) * GAP_DP) / columns).coerceAtLeast(MIN_CARD_DP)
        val rowsFit = ((heightDp + GAP_DP) / (cardHeightFor(full, captionDp) + GAP_DP)).toInt()
        if (rowsFit >= 2) return GridLayout(full, rowsFit.coerceAtMost(MAX_ROWS), columns, captionDp)
        val two = ((heightDp - GAP_DP) / 2 - captionDp) * (210f / 297f)
        if (two >= MIN_CARD_DP) return GridLayout(minOf(two, full), 2, columns, captionDp)
        val one = ((heightDp - captionDp) * (210f / 297f)).coerceIn(MIN_CARD_DP, full)
        return GridLayout(one, 1, columns, captionDp)
    }

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
