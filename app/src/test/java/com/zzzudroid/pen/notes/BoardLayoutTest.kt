package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.Bounds
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardLayoutTest {

    @Test
    fun `страница экспорта — содержимое плюс поля`() {
        val p = BoardLayout.exportPage(Bounds(100f, -50f, 300f, 150f), marginMm = 10f)
        // 10 мм = 50 единиц
        assertEquals(300f, p.width, 1e-4f)
        assertEquals(300f, p.height, 1e-4f)
        // Левый верхний угол содержимого попадает в (50, 50)
        assertEquals(50f, 100f + p.offsetX, 1e-4f)
        assertEquals(50f, -50f + p.offsetY, 1e-4f)
    }

    @Test
    fun `пустая доска — лист A4`() {
        val p = BoardLayout.exportPage(null)
        assertEquals(1050f, p.width, 1e-4f)
        assertEquals(1485f, p.height, 1e-4f)
        assertEquals(0f, p.offsetX, 0f)
    }

    @Test
    fun `вписывание центрирует и сохраняет пропорции`() {
        val content = Bounds(0f, 0f, 100f, 50f)
        val f = BoardLayout.fit(content, 300f, 300f, marginFraction = 0f)
        assertEquals(3f, f.scale, 1e-4f)
        // По высоте 150 из 300 — сверху отступ 75
        assertEquals(0f, f.dx, 1e-4f)
        assertEquals(75f, f.dy, 1e-4f)
    }

    @Test
    fun `вырожденное содержимое не даёт бесконечности`() {
        val f = BoardLayout.fit(Bounds(10f, 10f, 10f, 10f), 300f, 400f)
        assertEquals(true, f.scale.isFinite())
    }

    @Test
    fun `вид документа по id`() {
        assertEquals(DocKind.BOARD, DocKind.byId("board"))
        assertEquals(DocKind.NOTEBOOK, DocKind.byId(null))
        assertEquals(DocKind.NOTEBOOK, DocKind.byId("что-то"))
    }
}
