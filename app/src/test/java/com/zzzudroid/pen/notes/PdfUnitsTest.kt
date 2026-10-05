package com.zzzudroid.pen.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfUnitsTest {

    @Test
    fun `A4 в пунктах переводится в мировые единицы`() {
        // A4 = 595.28 × 841.89 пт = 210 × 297 мм = 1050 × 1485 ед.
        assertEquals(1050f, PdfUnits.ptToUnits(595.28f), 0.1f)
        assertEquals(1485f, PdfUnits.ptToUnits(841.89f), 0.1f)
    }

    @Test
    fun `пересчёт обратим`() {
        assertEquals(612f, PdfUnits.unitsToPt(PdfUnits.ptToUnits(612f)), 1e-3f)
    }

    @Test
    fun `ступени ширины растра`() {
        assertEquals(512, PdfUnits.cacheStep(1))
        assertEquals(512, PdfUnits.cacheStep(512))
        assertEquals(1024, PdfUnits.cacheStep(513))
        assertEquals(2048, PdfUnits.cacheStep(2000))
        assertEquals(4096, PdfUnits.cacheStep(4096))
        assertEquals(4096, PdfUnits.cacheStep(10000))
    }
}
