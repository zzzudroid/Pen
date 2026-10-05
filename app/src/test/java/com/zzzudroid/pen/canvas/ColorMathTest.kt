package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun `чистые тона`() {
        assertEquals(0xFFFF0000.toInt(), ColorMath.hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), ColorMath.hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ColorMath.hsvToArgb(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.hsvToArgb(77f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), ColorMath.hsvToArgb(77f, 1f, 0f))
    }

    @Test
    fun `hsv туда и обратно`() {
        for (c in listOf(0xFF1F4EC9.toInt(), 0xFFD32F2F.toInt(), 0xFF2E7D32.toInt(), 0xFFEF8A00.toInt())) {
            val (h, s, v) = ColorMath.argbToHsv(c).let { Triple(it[0], it[1], it[2]) }
            val back = ColorMath.hsvToArgb(h, s, v)
            for (shift in listOf(16, 8, 0)) assertEquals((c shr shift and 0xFF).toDouble(), (back shr shift and 0xFF).toDouble(), 1.0)
        }
    }

    @Test
    fun `серые не имеют насыщенности`() {
        val hsv = ColorMath.argbToHsv(0xFF808080.toInt())
        assertEquals(0f, hsv[1], 1e-6f)
    }

    @Test
    fun `сетка — 13 столбцов, 9 рядов, серый столбец от белого к чёрному`() {
        assertEquals(13, ColorMath.GRID_COLUMNS)
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.gridColor(12, 0))
        assertEquals(0xFF000000.toInt(), ColorMath.gridColor(12, ColorMath.GRID_ROWS - 1))
        // все цвета сетки непрозрачные, а нижние ряды темнее верхних
        for (c in 0 until ColorMath.GRID_COLUMNS) {
            for (r in 0 until ColorMath.GRID_ROWS) assertEquals(0xFF, ColorMath.gridColor(c, r) ushr 24)
            val top = ColorMath.argbToHsv(ColorMath.gridColor(c, 0))[2]
            val bottom = ColorMath.argbToHsv(ColorMath.gridColor(c, ColorMath.GRID_ROWS - 1))[2]
            assertTrue(bottom <= top)
        }
    }

    @Test
    fun `прозрачность в процентах и обратно`() {
        val c = 0xFF1F4EC9.toInt()
        assertEquals(100, ColorMath.opacityPercent(c))
        val half = ColorMath.withOpacity(c, 47)
        assertEquals(47, ColorMath.opacityPercent(half))
        assertEquals(c and 0x00FFFFFF, half and 0x00FFFFFF) // цвет не меняется
        assertEquals(0, ColorMath.opacityPercent(ColorMath.withOpacity(c, 0)))
    }

    @Test
    fun `смена тона сохраняет прозрачность`() {
        val src = ColorMath.withOpacity(0xFFFF0000.toInt(), 47)
        val out = ColorMath.withRgb(src, 0x00FF00)
        assertEquals(ColorMath.opacityPercent(src), ColorMath.opacityPercent(out))
        assertEquals(0x00FF00, out and 0xFFFFFF)
    }
}
