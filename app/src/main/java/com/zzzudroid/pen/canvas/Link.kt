package com.zzzudroid.pen.canvas

import java.util.UUID

/** Вид на холст: масштаб и сдвиг (экран = мир * scale + (tx, ty)) — для закладок и ссылок на место доски. */
data class ViewState(val scale: Float, val tx: Float, val ty: Float)

/**
 * Куда ведёт ссылка: документ [docId] и, необязательно, закладка [bookmarkId] доски или прямой вид [view].
 * Идентификатор документа постоянный (не имя файла), поэтому ссылка переживает переименование.
 */
data class LinkTarget(val docId: String, val bookmarkId: String? = null, val view: ViewState? = null)

/** Ссылка на выделенную область страницы: прямоугольник в мировых координатах страницы [pageId] и цель. */
data class DocLink(
    val id: String,
    val pageId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val target: LinkTarget,
) {
    val bounds get() = Bounds(left, top, right, bottom)

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    /** Ссылка после переноса/масштаба выделения (те же правила, что у штрихов); `id` и цель прежние. */
    fun transformed(t: SelectionTransform) = copy(
        left = t.mapX(left), right = t.mapX(right), top = t.mapY(top), bottom = t.mapY(bottom),
    )

    /** Прямоугольник целиком внутри [area] — такая ссылка едет вместе с выделением. */
    fun insideOf(area: Bounds) = left >= area.left && right <= area.right && top >= area.top && bottom <= area.bottom

    companion object {
        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}

/** Закладка доски: имя и запомненное место. */
data class Bookmark(val id: String, val name: String, val view: ViewState) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}
