package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class BeautifyTest {

    /** Окружность радиусом [r] (ед. мира), по [n] точек, с гауссовым шумом [noise]. */
    private fun circle(r: Float, n: Int, noise: Float, seed: Long = 1): Pair<FloatArray, FloatArray> {
        val rnd = Random(seed)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            val a = 2 * PI * i / (n - 1) * 0.9 // не замыкаем: у штриха есть начало и конец
            xs[i] = (r * cos(a)).toFloat() + rnd.nextGaussian().toFloat() * noise
            ys[i] = (r * sin(a)).toFloat() + rnd.nextGaussian().toFloat() * noise
        }
        return xs to ys
    }

    /** Мелкое дрожание: средняя длина второй разности (отклонение точки от середины соседей) без 15% у концов. */
    private fun wiggle(xs: FloatArray, ys: FloatArray): Float {
        val from = xs.size * 15 / 100
        val to = xs.size - from
        return (from until to).map { i ->
            hypot(xs[i - 1] - 2 * xs[i] + xs[i + 1], ys[i - 1] - 2 * ys[i] + ys[i + 1])
        }.average().toFloat()
    }

    @Test
    fun `дрожание гасится, радиус сохраняется`() {
        val r = 40f // 8 мм — размер крупной буквы
        val (xs, ys) = circle(r, 300, noise = 0.8f)
        val before = wiggle(xs, ys)
        val strong = Beautify.smooth(xs, ys, strength = 3)!!
        val after = wiggle(strong.xs, strong.ys)
        assertTrue("дрожание $before → $after должно упасть минимум в 10 раз", after < before / 10)
        // Чем сильнее уровень, тем глаже
        val light = Beautify.smooth(xs, ys, strength = 1)!!
        assertTrue(wiggle(light.xs, light.ys) > after)
        // Усадки нет: средний радиус остался прежним (допуск 3%)
        val meanR = strong.xs.indices.map { hypot(strong.xs[it], strong.ys[it]) }.average().toFloat()
        assertEquals(r, meanR, r * 0.03f)
    }

    @Test
    fun `концы штриха остаются на месте`() {
        val (xs, ys) = circle(30f, 200, noise = 0.5f)
        val p = Beautify.smooth(xs, ys, 3)!!
        assertEquals(xs.first(), p.xs.first(), 1e-4f)
        assertEquals(ys.first(), p.ys.first(), 1e-4f)
        assertEquals(xs.last(), p.xs.last(), 1e-4f)
        assertEquals(ys.last(), p.ys.last(), 1e-4f)
    }

    @Test
    fun `прямая остаётся прямой`() {
        val xs = FloatArray(100) { it * 3f }
        val ys = FloatArray(100) { it * 1.5f }
        val p = Beautify.smooth(xs, ys, 3)!!
        for (i in p.xs.indices) assertEquals(p.xs[i] / 2f, p.ys[i], 0.05f)
    }

    @Test
    fun `короткие штрихи не трогаем`() {
        assertNull(Beautify.smooth(floatArrayOf(0f, 1f, 2f), floatArrayOf(0f, 0f, 0f), 2)) // меньше 4 точек
        assertNull(Beautify.smooth(FloatArray(10) { it * 0.1f }, FloatArray(10), 2)) // точка/чёрточка
        assertNull(Beautify.smooth(FloatArray(10) { it * 5f }, FloatArray(10), 0)) // выключено
    }

    @Test
    fun `исходные индексы идут от начала к концу`() {
        val (xs, ys) = circle(40f, 150, noise = 0.3f)
        val p = Beautify.smooth(xs, ys, 1)!!
        assertEquals(0f, p.source.first(), 1e-4f)
        assertEquals((xs.size - 1).toFloat(), p.source.last(), 0.01f)
        for (i in 1 until p.source.size) assertTrue(p.source[i] >= p.source[i - 1])
        assertNotNull(p)
    }

    @Test
    fun `быстрые участки тоньше медленных`() {
        // Первые 60 точек — медленно (20 мм/с), следующие 60 — быстро (500 мм/с); шаг по времени 10 мс
        val n = 120
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val t = FloatArray(n) { it * 10f }
        var x = 0f
        for (i in 1 until n) {
            x += if (i < 60) 1f else 25f // 1 ед. за 10 мс = 20 мм/с; 25 ед. за 10 мс = 500 мм/с
            xs[i] = x
        }
        val w = Beautify.widthFactors(xs, ys, t)
        assertTrue("медленно: ${w[20]}", w[20] > 0.95f)
        assertTrue("быстро: ${w[100]}", w[100] < 0.45f)
        assertTrue(w.all { it in 0.39f..1.001f })
    }

    @Test
    fun `без времени толщина не меняется`() {
        val xs = FloatArray(30) { it * 2f }
        val w = Beautify.widthFactors(xs, FloatArray(30), FloatArray(30)) // время везде 0
        assertTrue(w.all { it == 1f })
    }
}
