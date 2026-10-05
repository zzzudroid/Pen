package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class InkInputRangesTest {
    @Test
    fun `отрицательный азимут переводится в 0…2π`() {
        val r = InkInputRanges.orientation(-0.300913f * PI.toFloat()) // значение из падения на планшете
        assertTrue(r >= 0f && r < (2 * PI).toFloat())
        assertEquals((2 - 0.300913) * PI, r.toDouble(), 1e-3)
    }

    @Test
    fun `азимут в допустимом диапазоне не меняется`() {
        assertEquals(1.5f, InkInputRanges.orientation(1.5f), 1e-6f)
        assertEquals(0f, InkInputRanges.orientation(0f), 1e-6f)
    }

    @Test
    fun `большие и нечисловые значения безопасны`() {
        assertTrue(InkInputRanges.orientation(10f * PI.toFloat()) in 0f..(2 * PI).toFloat())
        assertEquals(-1f, InkInputRanges.orientation(Float.NaN), 0f)
        assertEquals(-1f, InkInputRanges.pressure(Float.NaN), 0f)
        assertEquals(-1f, InkInputRanges.tilt(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun `нажим и наклон ограничиваются`() {
        assertEquals(1f, InkInputRanges.pressure(1.7f), 0f)
        assertEquals(0f, InkInputRanges.pressure(-0.2f), 0f)
        assertEquals((PI / 2).toFloat(), InkInputRanges.tilt(3f), 1e-6f)
    }
}
