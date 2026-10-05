package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.PageSpec

/** Пересчёт единиц PDF и выбор разрешения подложки (чистый Kotlin, покрыт JVM-тестами). */
object PdfUnits {
    private const val MM_PER_INCH = 25.4f
    private const val PT_PER_INCH = 72f

    /** Мировых единиц на пункт PDF. */
    const val UNITS_PER_PT = MM_PER_INCH / PT_PER_INCH * PageSpec.UNITS_PER_MM

    /** Ступени ширины растра подложки, px. */
    val CACHE_STEPS = intArrayOf(512, 1024, 2048, 4096)

    fun ptToUnits(pt: Float) = pt * UNITS_PER_PT

    fun unitsToPt(units: Float) = units / UNITS_PER_PT

    /** Ширина растра: ближайшая ступень не меньше [widthPx], но не больше максимальной. */
    fun cacheStep(widthPx: Int): Int = CACHE_STEPS.firstOrNull { it >= widthPx } ?: CACHE_STEPS.last()
}
