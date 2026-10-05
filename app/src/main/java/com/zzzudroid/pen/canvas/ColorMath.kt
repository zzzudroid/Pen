package com.zzzudroid.pen.canvas

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Чистая цветовая математика для выбора цвета пера (без android.graphics, покрыта JVM-тестами).
 * Цвета — ARGB в виде Int, как в Android.
 */
object ColorMath {
    /** Сетка цветов: 12 тонов по горизонтали + столбец серых, [GRID_ROWS] рядов от светлых к тёмным. */
    const val GRID_HUES = 12
    const val GRID_COLUMNS = GRID_HUES + 1
    const val GRID_ROWS = 9

    private val HUES = floatArrayOf(355f, 22f, 40f, 50f, 58f, 82f, 125f, 175f, 218f, 250f, 283f, 328f)
    private val SATURATION = floatArrayOf(0.18f, 0.30f, 0.50f, 0.70f, 0.85f, 0.85f, 0.85f, 0.85f, 0.85f)
    private val VALUE = floatArrayOf(1.00f, 1.00f, 1.00f, 0.98f, 0.95f, 0.82f, 0.65f, 0.48f, 0.32f)

    /** Цвет ячейки сетки: [column] 0 until [GRID_COLUMNS] (последний — серые), [row] 0 until [GRID_ROWS]. Непрозрачный. */
    fun gridColor(column: Int, row: Int): Int {
        if (column == GRID_HUES) {
            // Серый столбец: от белого к чёрному
            val g = (255 * (1f - row / (GRID_ROWS - 1f))).roundToInt()
            return argb(255, g, g, g)
        }
        return hsvToArgb(HUES[column], SATURATION[row], VALUE[row])
    }

    /** HSV → непрозрачный ARGB. [h] 0…360, [s] и [v] 0…1. */
    fun hsvToArgb(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = v * s
        val x = c * (1f - abs(hh % 2f - 1f))
        val m = v - c
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt())
    }

    /** ARGB → [h 0…360, s 0…1, v 0…1] (прозрачность игнорируется). */
    fun argbToHsv(argb: Int): FloatArray {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Прозрачность цвета в процентах: 0 — невидим, 100 — непрозрачный. */
    fun opacityPercent(argb: Int): Int = ((argb ushr 24) * 100f / 255f).roundToInt()

    /** Тот же цвет с прозрачностью [percent] (0…100). */
    fun withOpacity(argb: Int, percent: Int): Int =
        (argb and 0x00FFFFFF) or ((percent.coerceIn(0, 100) * 255f / 100f).roundToInt() shl 24)

    /** Тот же тон (RGB) с прозрачностью исходного цвета. */
    fun withRgb(source: Int, rgb: Int): Int = (source and 0xFF000000.toInt()) or (rgb and 0x00FFFFFF)
}
