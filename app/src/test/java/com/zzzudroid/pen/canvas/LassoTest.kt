package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoTest {

    private val square = LassoPolygon(floatArrayOf(0f, 10f, 10f, 0f), floatArrayOf(0f, 0f, 10f, 10f))

    // П-образная петля: вырез сверху между x=4 и x=6 до y=6
    private val uShape = LassoPolygon(
        floatArrayOf(0f, 4f, 4f, 6f, 6f, 10f, 10f, 0f),
        floatArrayOf(0f, 0f, 6f, 6f, 0f, 0f, 10f, 10f),
    )

    @Test
    fun `точка внутри и снаружи квадрата`() {
        assertTrue(square.contains(5f, 5f))
        assertFalse(square.contains(15f, 5f))
        assertFalse(square.contains(-1f, 5f))
    }

    @Test
    fun `вогнутая петля не включает вырез`() {
        assertTrue(uShape.contains(2f, 3f))
        assertTrue(uShape.contains(8f, 3f))
        assertFalse(uShape.contains(5f, 3f))
        assertTrue(uShape.contains(5f, 8f))
    }

    @Test
    fun `выделение по доле точек`() {
        val xs = floatArrayOf(1f, 2f, 3f, 20f)
        val ys = floatArrayOf(1f, 2f, 3f, 20f)
        assertTrue(square.selects(xs, ys, 0.5f))
        assertTrue(square.selects(xs, ys, 0.75f))
        assertFalse(square.selects(xs, ys, 0.9f))
        assertFalse(square.selects(FloatArray(0), FloatArray(0)))
    }

    @Test
    fun `вырожденная петля ничего не содержит`() {
        assertFalse(LassoPolygon(floatArrayOf(0f, 10f), floatArrayOf(0f, 10f)).contains(5f, 5f))
    }

    @Test
    fun `объединение и расширение границ`() {
        val a = Bounds(0f, 0f, 10f, 10f)
        val b = Bounds(5f, -5f, 20f, 8f)
        assertEquals(Bounds(0f, -5f, 20f, 10f), a.union(b))
        assertEquals(Bounds(-2f, -2f, 12f, 12f), a.inflate(2f))
        assertEquals(10f, a.width, 0f)
        assertEquals(5f, a.centerY, 0f)
    }

    @Test
    fun `масштаб вокруг якоря и сдвиг`() {
        val t = SelectionTransform(scale = 2f, anchorX = 10f, anchorY = 10f, dx = 3f, dy = -1f)
        assertEquals(13f, t.mapX(10f), 1e-6f)
        assertEquals(33f, t.mapX(20f), 1e-6f)
        assertEquals(Bounds(13f, 9f, 33f, 29f), t.map(Bounds(10f, 10f, 20f, 20f)))
    }

    @Test
    fun `тождественное преобразование`() {
        assertTrue(SelectionTransform().isIdentity)
        assertTrue(SelectionTransform(anchorX = 5f, anchorY = 7f).isIdentity)
        assertFalse(SelectionTransform(dx = 1f).isIdentity)
        assertFalse(SelectionTransform(scale = 1.5f).isIdentity)
    }
}
