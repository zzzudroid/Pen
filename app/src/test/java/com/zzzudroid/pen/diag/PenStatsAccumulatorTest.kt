package com.zzzudroid.pen.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenStatsAccumulatorTest {

    private fun s(p: Float, tMs: Long, tilt: Float = 0f, orient: Float = 0f) =
        PenSample(0f, 0f, p, tilt, orient, tMs * 1_000_000)

    @Test
    fun `диапазон и уровни давления считаются только по касаниям`() {
        val acc = PenStatsAccumulator()
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.1f, 0), s(0.5f, 4), s(0.5f, 8)), hover = false)
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.0f, 12)), hover = true)

        val st = acc.stats(Tool.STYLUS)!!
        assertEquals(0.1f, st.pressure.min, 1e-6f)
        assertEquals(0.5f, st.pressure.max, 1e-6f)
        assertEquals(2, st.pressureLevelCount)
        assertEquals(3L, st.contactSamples)
        assertEquals(1L, st.hoverSamples)
    }

    @Test
    fun `частота по интервалам внутри потока`() {
        val acc = PenStatsAccumulator()
        // Точки каждые 4 мс → 250 Гц
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.3f, 0), s(0.3f, 4)), hover = false)
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.3f, 8), s(0.3f, 12)), hover = false)
        assertEquals(250.0, acc.stats(Tool.STYLUS)!!.contactRateHz, 0.01)
    }

    @Test
    fun `пауза и новый штрих не портят частоту`() {
        val acc = PenStatsAccumulator()
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.3f, 0), s(0.3f, 5)), hover = false)
        acc.endStream(0, hover = false)
        // Новый штрих через 2 секунды — интервал между штрихами не учитывается
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.3f, 2000), s(0.3f, 2005)), hover = false)
        assertEquals(200.0, acc.stats(Tool.STYLUS)!!.contactRateHz, 0.01)
    }

    @Test
    fun `исторические точки на событие`() {
        val acc = PenStatsAccumulator()
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.2f, 0), s(0.2f, 4), s(0.2f, 8)), hover = false)
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.2f, 12)), hover = false)
        assertEquals(1.0, acc.stats(Tool.STYLUS)!!.avgHistoricalPerEvent, 1e-9)
    }

    @Test
    fun `инструменты считаются раздельно, наклон берётся и из hover`() {
        val acc = PenStatsAccumulator()
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0f, 0, tilt = 0.7f)), hover = true)
        acc.addEvent(Tool.FINGER, 1, listOf(s(1f, 0)), hover = false)
        assertEquals(0.7f, acc.stats(Tool.STYLUS)!!.tilt.max, 1e-6f)
        assertTrue(acc.stats(Tool.STYLUS)!!.pressure.isEmpty)
        assertEquals(1L, acc.stats(Tool.FINGER)!!.contactSamples)
    }

    @Test
    fun `сброс обнуляет всё`() {
        val acc = PenStatsAccumulator()
        acc.addEvent(Tool.STYLUS, 0, listOf(s(0.4f, 0)), hover = false)
        acc.buttonStates += 0x20
        acc.keyCodes += "KEYCODE_STYLUS_BUTTON_PRIMARY"
        acc.reset()
        assertNull(acc.stats(Tool.STYLUS))
        assertTrue(acc.buttonStates.isEmpty())
        assertTrue(acc.keyCodes.isEmpty())
    }
}
