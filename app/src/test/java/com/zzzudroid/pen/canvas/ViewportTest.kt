package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTest {

    @Test
    fun `мир и экран взаимно обратны`() {
        val v = Viewport()
        v.zoomAround(100f, 50f, 3f)
        v.panBy(17f, -4f)
        val sx = v.worldToScreenX(12f)
        val sy = v.worldToScreenY(-7f)
        assertEquals(12f, v.screenToWorldX(sx), 1e-4f)
        assertEquals(-7f, v.screenToWorldY(sy), 1e-4f)
    }

    @Test
    fun `зум оставляет точку под пальцами на месте`() {
        val v = Viewport()
        v.panBy(30f, 40f)
        val wx = v.screenToWorldX(200f)
        val wy = v.screenToWorldY(300f)
        v.zoomAround(200f, 300f, 2.5f)
        assertEquals(200f, v.worldToScreenX(wx), 1e-3f)
        assertEquals(300f, v.worldToScreenY(wy), 1e-3f)
        assertEquals(2.5f, v.scale, 1e-6f)
    }

    @Test
    fun `масштаб ограничен`() {
        val v = Viewport()
        v.zoomAround(0f, 0f, 1000f)
        assertEquals(Viewport.MAX_SCALE, v.scale, 0f)
        v.zoomAround(0f, 0f, 1e-6f)
        assertEquals(Viewport.MIN_SCALE, v.scale, 0f)
    }

    @Test
    fun `минимальный масштаб можно понизить для доски`() {
        val v = Viewport()
        v.minScale = 0.02f
        v.zoomAround(0f, 0f, 1e-6f)
        assertEquals(0.02f, v.scale, 0f)
        v.set(0.001f, 0f, 0f)
        assertEquals(0.02f, v.scale, 0f)
    }

    @Test
    fun `центр вида удерживается в пределах поля`() {
        val v = Viewport()
        // Экран 1000×600, масштаб 1: центр мира сначала в (500, 300); уводим сильно вправо и вверх
        v.panBy(-900_000f, 900_000f)
        v.clampCenter(100_000f, 1000f, 600f)
        assertEquals(100_000f, v.screenToWorldX(500f), 1f)
        assertEquals(-100_000f, v.screenToWorldY(300f), 1f)
        // Внутри поля — вид не меняется
        val w = Viewport()
        w.panBy(-5_000f, 2_000f)
        val tx = w.tx
        w.clampCenter(100_000f, 1000f, 600f)
        assertEquals(tx, w.tx, 0f)
    }

    @Test
    fun `сброс возвращает исходный вид`() {
        val v = Viewport()
        v.zoomAround(10f, 10f, 4f)
        v.panBy(5f, 5f)
        v.reset()
        assertEquals(1f, v.scale, 0f)
        assertEquals(0f, v.tx, 0f)
        assertEquals(0f, v.ty, 0f)
    }
}
