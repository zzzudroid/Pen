package com.zzzudroid.pen.canvas

/**
 * Вид на холст: масштаб и сдвиг. Экран = мир * scale + (tx, ty).
 * Чистый Kotlin — матрицу Android строит вызывающий код.
 */
class Viewport {
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
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val wx = screenToWorldX(focusX)
        val wy = screenToWorldY(focusY)
        scale = newScale
        tx = focusX - wx * newScale
        ty = focusY - wy * newScale
    }

    fun reset() {
        scale = 1f
        tx = 0f
        ty = 0f
    }

    fun set(scale: Float, tx: Float, ty: Float) {
        this.scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        this.tx = tx
        this.ty = ty
    }

    companion object {
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 32f
    }
}
