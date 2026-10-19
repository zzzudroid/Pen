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
    fun `большой лист не уходит за края дальше поля`() {
        val v = Viewport()
        // Лист 1000×3000 при масштабе 2 (2000×6000 px) на экране 1000×600, поле 16
        v.set(2f, 0f, 0f)
        v.panBy(500f, 500f)
        v.clampToRect(0f, 0f, 1000f, 3000f, 1000f, 600f, 16f)
        assertEquals(16f, v.tx, 0f)
        assertEquals(16f, v.ty, 0f)
        v.panBy(-10_000f, -10_000f)
        v.clampToRect(0f, 0f, 1000f, 3000f, 1000f, 600f, 16f)
        assertEquals(1000f - 16f - 2000f, v.tx, 0f)
        assertEquals(600f - 16f - 6000f, v.ty, 0f)
        // Внутри допустимого — вид не меняется
        v.panBy(100f, 100f)
        val tx = v.tx
        val ty = v.ty
        v.clampToRect(0f, 0f, 1000f, 3000f, 1000f, 600f, 16f)
        assertEquals(tx, v.tx, 0f)
        assertEquals(ty, v.ty, 0f)
    }

    @Test
    fun `маленький лист стоит по центру`() {
        val v = Viewport()
        // Лист 100×200 при масштабе 1 на экране 1000×600 — по обеим осям помещается
        v.panBy(-300f, 400f)
        v.clampToRect(0f, 0f, 100f, 200f, 1000f, 600f, 16f)
        assertEquals(450f, v.tx, 0f)
        assertEquals(200f, v.ty, 0f)
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
