package com.zzzudroid.pen.canvas

import kotlin.math.PI

/**
 * Приведение значений пера к диапазонам, которых требует Ink (иначе нативный код бросает исключение).
 * Android отдаёт азимут пера в диапазоне −π…π, а Ink принимает 0…2π; нажим — 0…1, наклон — 0…π/2.
 * Недопустимое значение заменяется на «нет данных» (−1).
 */
object InkInputRanges {
    private const val TWO_PI = (2 * PI).toFloat()
    private const val NONE = -1f

    fun orientation(radians: Float): Float {
        if (radians.isNaN() || radians.isInfinite()) return NONE
        var r = radians % TWO_PI
        if (r < 0f) r += TWO_PI
        return if (r in 0f..<TWO_PI) r else 0f
    }

    fun pressure(p: Float): Float = if (p.isNaN() || p.isInfinite()) NONE else p.coerceIn(0f, 1f)

    fun tilt(t: Float): Float = if (t.isNaN() || t.isInfinite()) NONE else t.coerceIn(0f, (PI / 2).toFloat())
}
