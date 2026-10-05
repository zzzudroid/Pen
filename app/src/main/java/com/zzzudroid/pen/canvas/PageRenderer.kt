package com.zzzudroid.pen.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

private val sheetPaint = Paint().apply { color = Color.WHITE }
private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(0xC8, 0xC4, 0xBC)
    style = Paint.Style.STROKE
}
private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB9, 0xD3, 0xEE) }
private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xA9, 0xB4, 0xC2) }

/**
 * Нарисовать лист и шаблон в мировых координатах (canvas уже умножен на матрицу вида).
 * [scale] — текущий масштаб, чтобы линии оставались тонкими на экране при любом зуме.
 */
fun drawPage(canvas: Canvas, spec: PageSpec, scale: Float) {
    val w = spec.width
    val h = spec.height
    canvas.drawRect(0f, 0f, w, h, sheetPaint)

    // Толщина линий шаблона — около 1 px на экране, но не толще 0,3 мм на бумаге
    val hairline = (1f / scale).coerceAtMost(0.3f * PageSpec.UNITS_PER_MM)
    linePaint.strokeWidth = hairline

    val mm = PageSpec.UNITS_PER_MM
    when (spec.template) {
        PageTemplate.BLANK -> Unit
        PageTemplate.LINED -> {
            val step = 8 * mm
            var y = 20 * mm
            while (y < h - 10 * mm) {
                canvas.drawLine(10 * mm, y, w - 10 * mm, y, linePaint)
                y += step
            }
        }
        PageTemplate.GRID -> {
            val step = 5 * mm
            var x = step
            while (x < w) { canvas.drawLine(x, 0f, x, h, linePaint); x += step }
            var y = step
            while (y < h) { canvas.drawLine(0f, y, w, y, linePaint); y += step }
        }
        PageTemplate.DOTS -> {
            val step = 5 * mm
            val r = (1.5f / scale).coerceIn(0.15f * mm, 0.35f * mm)
            var y = step
            while (y < h) {
                var x = step
                while (x < w) { canvas.drawCircle(x, y, r, dotPaint); x += step }
                y += step
            }
        }
    }

    borderPaint.strokeWidth = 1f / scale
    canvas.drawRect(0f, 0f, w, h, borderPaint)
}
