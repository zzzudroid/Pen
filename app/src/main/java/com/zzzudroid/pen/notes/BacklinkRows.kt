package com.zzzudroid.pen.notes

/** Строка панели «Откуда ссылаются»: подпись и куда перейти по нажатию. */
data class BacklinkRow(
    val text: String,
    val fileName: String,
    /** Страница источника с нуля (для перехода). */
    val page: Int,
    /** Центр области ссылки в мировых координатах страницы — туда прокручиваем при переходе. */
    val focusX: Float,
    val focusY: Float,
)

/** Подписи и данные строк обратных ссылок — чистая логика, покрыта JVM-тестами. */
object BacklinkRows {
    /** «Название · стр. N», у доски «Название · холст», если страница неизвестна — только название. */
    fun format(title: String, kind: DocKind, pageIndex: Int): String = when {
        kind == DocKind.BOARD -> "$title · холст"
        pageIndex >= 0 -> "$title · стр. ${pageIndex + 1}"
        else -> title
    }

    fun rows(backlinks: List<Backlink>): List<BacklinkRow> = backlinks.map { b ->
        BacklinkRow(
            text = format(b.source.title, b.source.kind, b.pageIndex),
            fileName = b.source.stamp.name,
            page = maxOf(b.pageIndex, 0),
            focusX = (b.link.left + b.link.right) / 2,
            focusY = (b.link.top + b.link.bottom) / 2,
        )
    }
}
