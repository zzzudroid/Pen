package com.zzzudroid.pen.canvas

/**
 * Вид на холст: масштаб и сдвиг. Экран = мир * scale + (tx, ty).
 * Чистый Kotlin — матрицу Android строит вызывающий код.
 */
class Viewport {
    /** Минимальный масштаб: для страницы — [MIN_SCALE], для безграничной доски можно отдалять сильнее. */
    var minScale = MIN_SCALE

    var scale = 1f; private set
    var tx = 0f; private set
    var ty = 0f; private set

    fun worldToScreenX(x: Float) = x * scale + tx
    fun worldToScreenY(y: Float) = y * scale + ty
    fun screenToWorldX(x: Float) = (x - tx) / scale
    fun screenToWorldY(y: Float) = (y - ty) / scale

    fun panBy(dx: Float, dy: Float) {
        tx += dx
        ty += dy
    }

    /** Масштабировать вокруг точки экрана: точка мира под ней остаётся на месте. */
    fun zoomAround(focusX: Float, focusY: Float, factor: Float) {
        val newScale = (scale * factor).coerceIn(minScale, MAX_SCALE)
        val wx = screenToWorldX(focusX)
        val wy = screenToWorldY(focusY)
        scale = newScale
        tx = focusX - wx * newScale
        ty = focusY - wy * newScale
    }

    /**
     * Удержать центр вида внутри ±[limit] по каждой оси мира (экран шириной [viewW]×[viewH] пикселей):
     * безграничная доска всё же ограничена записью штрихов в графические списки (±100 000 ед. ≈ ±20 м).
     */
    fun clampCenter(limit: Float, viewW: Float, viewH: Float) {
        val cx = screenToWorldX(viewW / 2)
        val cy = screenToWorldY(viewH / 2)
        val nx = cx.coerceIn(-limit, limit)
        val ny = cy.coerceIn(-limit, limit)
        if (nx != cx) tx = viewW / 2 - nx * scale
        if (ny != cy) ty = viewH / 2 - ny * scale
    }

    fun reset() {
        scale = 1f
        tx = 0f
        ty = 0f
    }

    fun set(scale: Float, tx: Float, ty: Float) {
        this.scale = scale.coerceIn(minScale, MAX_SCALE)
        this.tx = tx
        this.ty = ty
    }

    companion object {
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 32f
    }
}
