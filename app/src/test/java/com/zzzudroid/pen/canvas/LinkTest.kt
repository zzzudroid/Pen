package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {

    private fun link(l: Float, t: Float, r: Float, b: Float) = DocLink("id", "p", l, t, r, b, LinkTarget("doc"))

    @Test
    fun `точка внутри и вне области`() {
        val x = link(10f, 20f, 110f, 60f)
        assertTrue(x.contains(10f, 20f))
        assertTrue(x.contains(60f, 40f))
        assertFalse(x.contains(111f, 40f))
        assertFalse(x.contains(60f, 19f))
    }

    @Test
    fun `целиком внутри рамки выделения — едет, задетая частично — нет`() {
        val area = Bounds(0f, 0f, 200f, 100f)
        assertTrue(link(10f, 10f, 190f, 90f).insideOf(area))
        assertFalse(link(150f, 10f, 250f, 90f).insideOf(area)) // выходит за правую границу
        assertFalse(link(300f, 300f, 400f, 400f).insideOf(area))
    }

    @Test
    fun `перенос и масштаб ссылки совпадают с правилами штрихов`() {
        val l = link(10f, 20f, 110f, 60f)
        // сдвиг на (5, -5)
        val moved = l.transformed(SelectionTransform(dx = 5f, dy = -5f))
        assertEquals(15f, moved.left, 1e-4f)
        assertEquals(115f, moved.right, 1e-4f)
        assertEquals(15f, moved.top, 1e-4f)
        assertEquals(55f, moved.bottom, 1e-4f)
        // масштаб ×2 вокруг левого верхнего угла рамки (0,0)
        val scaled = l.transformed(SelectionTransform(scale = 2f, anchorX = 0f, anchorY = 0f))
        assertEquals(20f, scaled.left, 1e-4f)
        assertEquals(220f, scaled.right, 1e-4f)
        assertEquals(40f, scaled.top, 1e-4f)
        assertEquals(120f, scaled.bottom, 1e-4f)
        // идентификатор и цель сохраняются
        assertEquals("id", scaled.id)
        assertEquals("doc", scaled.target.docId)
    }
}
