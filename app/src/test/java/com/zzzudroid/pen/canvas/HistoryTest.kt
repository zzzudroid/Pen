package com.zzzudroid.pen.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    @Test
    fun `отмена и повтор в обратном порядке`() {
        val h = History<String>()
        h.push("a")
        h.push("b")
        assertEquals("b", h.undo())
        assertEquals("a", h.undo())
        assertNull(h.undo())
        assertEquals("a", h.redo())
        assertEquals("b", h.redo())
        assertNull(h.redo())
    }

    @Test
    fun `новое действие очищает ветку повтора`() {
        val h = History<String>()
        h.push("a")
        h.push("b")
        h.undo()
        assertTrue(h.canRedo)
        h.push("c")
        assertFalse(h.canRedo)
        assertEquals("c", h.undo())
        assertEquals("a", h.undo())
    }

    @Test
    fun `очистка`() {
        val h = History<Int>()
        h.push(1)
        h.undo()
        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }
}
