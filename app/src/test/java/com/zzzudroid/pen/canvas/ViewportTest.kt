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
