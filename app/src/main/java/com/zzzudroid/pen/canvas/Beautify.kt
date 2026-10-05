package com.zzzudroid.pen.canvas

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * «Красивый почерк»: сглаживание готового штриха после отрыва пера без отставания и без усадки.
 *
 * Путь перевыбирается с равным шагом по длине дуги, затем к нему применяется фильтр Таубина (шаг сглаживания λ и обратный шаг μ):
 * мелкое дрожание гасится, а размер петель и букв сохраняется (обычное сглаживание «съедало» бы петли). Концы штриха не двигаются.
 * Чистый Kotlin, покрыт тестами.
 */
object Beautify {
    /** Шаг перевыборки, ед. мира (0,3 мм). */
    const val STEP = 1.5f

    private const val LAMBDA = 0.5f

    /**
     * Полоса пропускания фильтра по силе 1…3 (лёгкое, среднее, сильное): чем меньше, тем крупнее неровности, которые сглаживаются
     * (примерно 4, 7 и 12 мм длины волны при шаге 0,3 мм). Обратный шаг μ = 1 / (kPb − 1 / λ).
     */
    private fun passband(strength: Int): Float = when (strength) { 1 -> 0.05f; 2 -> 0.018f; else -> 0.007f }

    /** Число пар проходов: больше — круче срез. */
    fun passes(strength: Int): Int = when (strength) { 1 -> 70; 2 -> 150; else -> 300 }

    /** Результат: новые координаты и положение каждой новой точки в исходных (дробный индекс) — для переноса давления, времени и наклона. */
    class Path(val xs: FloatArray, val ys: FloatArray, val source: FloatArray)

    /** Сглаженный путь для точек ([xs], [ys]) или null, если штрих слишком короткий, чтобы его трогать. */
    fun smooth(xs: FloatArray, ys: FloatArray, strength: Int): Path? {
        val n = xs.size
        if (n < 4 || strength <= 0) return null

        // Длина дуги до каждой исходной точки
        val s = FloatArray(n)
        for (i in 1 until n) s[i] = s[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val total = s[n - 1]
        if (total < STEP * 6) return null // совсем короткий штрих (точка, чёрточка) оставляем как есть

        // Равномерная перевыборка (не больше 4000 точек)
        val m = ((total / STEP).toInt() + 1).coerceIn(4, 4000)
        val px = FloatArray(m)
        val py = FloatArray(m)
        val src = FloatArray(m)
        var j = 0
        for (k in 0 until m) {
            val target = total * k / (m - 1)
            while (j < n - 2 && s[j + 1] < target) j++
            val seg = s[j + 1] - s[j]
            val t = if (seg > 1e-6f) ((target - s[j]) / seg).coerceIn(0f, 1f) else 0f
            px[k] = xs[j] + (xs[j + 1] - xs[j]) * t
            py[k] = ys[j] + (ys[j + 1] - ys[j]) * t
            src[k] = j + t
        }

        // Фильтр Таубина: сглаживание и обратный шаг; концы закреплены
        val tx = FloatArray(m)
        val ty = FloatArray(m)
        val mu = 1f / (passband(strength) - 1f / LAMBDA)
        repeat(passes(strength)) {
            laplace(px, py, tx, ty, LAMBDA)
            laplace(tx, ty, px, py, mu)
        }
        return Path(px, py, src)
    }

    /** Один шаг: каждая внутренняя точка сдвигается к среднему соседей на долю [k]; концы остаются. */
    private fun laplace(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray, k: Float) {
        val m = ax.size
        bx[0] = ax[0]; by[0] = ay[0]
        bx[m - 1] = ax[m - 1]; by[m - 1] = ay[m - 1]
        for (i in 1 until m - 1) {
            bx[i] = ax[i] + k * ((ax[i - 1] + ax[i + 1]) / 2f - ax[i])
            by[i] = ay[i] + k * ((ay[i - 1] + ay[i + 1]) / 2f - ay[i])
        }
    }

    /**
     * «Перьевая» толщина: коэффициент 0,33…1 для каждой точки по скорости письма — медленные участки толще, быстрые тоньше,
     * как у чернильной ручки. Скорость берётся по окну в ±[WINDOW] точек, чтобы шум времени не давал рывков толщины.
     * [tMs] — время каждой точки (мс). Если время не растёт (нет данных) — везде 1.
     */
    fun widthFactors(xs: FloatArray, ys: FloatArray, tMs: FloatArray): FloatArray {
        val n = xs.size
        val w = FloatArray(n) { 1f }
        if (n < 2 * WINDOW + 1) return w
        val s = FloatArray(n)
        for (i in 1 until n) s[i] = s[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        for (i in 0 until n) {
            val a = (i - WINDOW).coerceAtLeast(0)
            val b = (i + WINDOW).coerceAtMost(n - 1)
            val dt = tMs[b] - tMs[a]
            if (dt <= 0f) continue
            w[i] = widthForSpeed((s[b] - s[a]) / 5f / dt * 1000f) // 5 ед. мира = 1 мм
        }
        return w
    }

    /**
     * Коэффициент толщины 0,33…1 по скорости письма, мм/с. Кривая снята с INKredible (тот же планшет, имитация пера):
     * ~100 мм/с и медленнее — полная толщина, ~190 — около 0,75, ~410 — около 0,33.
     */
    fun widthForSpeed(mmPerSec: Float): Float {
        val t = ((mmPerSec - SLOW_MM_S) / (FAST_MM_S - SLOW_MM_S)).coerceIn(0f, 1f)
        return 1f - (1f - MIN_WIDTH) * t * t * (3 - 2 * t)
    }

    private const val WINDOW = 4
    private const val SLOW_MM_S = 90f
    private const val FAST_MM_S = 420f
    private const val MIN_WIDTH = 0.33f

    /** Длина дуги ломаной — для тестов и оценки. */
    fun length(xs: FloatArray, ys: FloatArray): Float {
        var l = 0f
        for (i in 1 until xs.size) l += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        return l
    }

    /** Среднеквадратичное расстояние между соответствующими точками двух путей одинаковой длины — для тестов. */
    fun rms(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray): Float {
        var sum = 0f
        for (i in ax.indices) sum += (ax[i] - bx[i]).let { it * it } + (ay[i] - by[i]).let { it * it }
        return sqrt(sum / ax.size)
    }
}
