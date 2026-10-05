package com.zzzudroid.pen.canvas

/** Прямоугольник в мировых координатах. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    /** Расширить во все стороны на [d]. */
    fun inflate(d: Float) = Bounds(left - d, top - d, right + d, bottom + d)

    fun union(o: Bounds) = Bounds(
        minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom),
    )
}

/** Замкнутый многоугольник петли (мировые координаты). Петля замыкается автоматически. */
class LassoPolygon(private val xs: FloatArray, private val ys: FloatArray) {
    init {
        require(xs.size == ys.size) { "Разное число координат x и y" }
    }

    val bounds: Bounds = if (xs.isEmpty()) Bounds(0f, 0f, 0f, 0f)
    else Bounds(xs.min(), ys.min(), xs.max(), ys.max())

    /** Точка внутри петли: луч вправо, правило чёт-нечет. */
    fun contains(x: Float, y: Float): Boolean {
        val n = xs.size
        if (n < 3 || !bounds.contains(x, y)) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val yi = ys[i]
            val yj = ys[j]
            if ((yi > y) != (yj > y)) {
                val xCross = xs[i] + (y - yi) * (xs[j] - xs[i]) / (yj - yi)
                if (x < xCross) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Доля точек внутри петли не меньше [threshold]. Пустой набор не выделяется. */
    fun selects(xs: FloatArray, ys: FloatArray, threshold: Float = 0.5f): Boolean {
        if (xs.isEmpty()) return false
        var inside = 0
        for (i in xs.indices) if (contains(xs[i], ys[i])) inside++
        return inside >= threshold * xs.size
    }
}

/** Преобразование выделения: масштаб вокруг якоря + сдвиг. x' = ax + (x-ax)*scale + dx */
data class SelectionTransform(
    val scale: Float = 1f,
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val dx: Float = 0f,
    val dy: Float = 0f,
) {
    fun mapX(x: Float) = anchorX + (x - anchorX) * scale + dx
    fun mapY(y: Float) = anchorY + (y - anchorY) * scale + dy

    /** Масштаб положителен, поэтому углы остаются на своих местах. */
    fun map(b: Bounds) = Bounds(mapX(b.left), mapY(b.top), mapX(b.right), mapY(b.bottom))

    val isIdentity: Boolean get() = scale == 1f && dx == 0f && dy == 0f
}
