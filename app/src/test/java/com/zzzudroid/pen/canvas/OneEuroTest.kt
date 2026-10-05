package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OneEuroTest {

    @Test
    fun `неподвижная точка остаётся на месте`() {
        val f = OneEuro(minCutoff = 2f, beta = 0.01f)
        f.reset(100f, 0)
        repeat(50) { assertEquals(100f, f.filter(100f, (it + 1) * 3L), 1e-3f) }
    }

    @Test
    fun `дрожание вокруг линии гасится`() {
        val f = OneEuro(minCutoff = 1f, beta = 0.006f)
        f.reset(0f, 0)
        var prevRaw = 0f
        var prevOut = 0f
        var rawStep = 0f
        var outStep = 0f
        for (k in 1..200) {
            val raw = k * 0.2f + if (k % 2 == 0) 3f else -3f // дрожание ±3 ед. при медленном дрейфе
            val out = f.filter(raw, k * 3L)
            if (k > 40) {
                rawStep = maxOf(rawStep, abs(raw - prevRaw))
                outStep = maxOf(outStep, abs(out - prevOut))
            }
            prevRaw = raw
            prevOut = out
        }
        assertTrue("скачки между соседними точками должны уменьшиться: $outStep < $rawStep", outStep < rawStep / 4)
    }

    @Test
    fun `быстрое движение почти не отстаёт`() {
        val f = OneEuro(minCutoff = 2f, beta = 0.01f)
        f.reset(0f, 0)
        var out = 0f
        for (k in 1..100) out = f.filter(k * 20f, k * 3L) // 20 ед. за 3 мс — быстрый штрих
        // отставание заметно меньше пройденного за одну секунду пути
        assertTrue("отставание ${100 * 20f - out}", 100 * 20f - out < 150f)
    }

    @Test
    fun `события в одну миллисекунду не ломают фильтр`() {
        val f = OneEuro(minCutoff = 2f, beta = 0.01f)
        f.reset(0f, 10)
        assertEquals(0f, f.filter(5f, 10), 1e-6f)
    }
}
