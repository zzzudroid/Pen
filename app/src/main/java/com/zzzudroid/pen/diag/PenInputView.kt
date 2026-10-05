package com.zzzudroid.pen.diag

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * Тестовая зона: перо рисует линию с толщиной по давлению, пальцы не рисуют.
 * Намеренно простая отрисовка через Bitmap — низкая задержка будет в canvas-core.
 */
@SuppressLint("ViewConstructor")
class PenInputView(context: Context, private val vm: DiagnosticsViewModel) : View(context) {

    private val density = resources.displayMetrics.density
    private val minWidth = 0.5f * density
    private val maxWidth = 8f * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x1F, 0x2A, 0x44)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bgColor = Color.rgb(0xFB, 0xFA, 0xF7)

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    // Текущий штрих по id указателя
    private val active = HashMap<Int, MutableList<StrokePoint>>()

    init {
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmapCanvas = Canvas(bitmap!!)
        redrawAll()
    }

    /** Перерисовать все сохранённые штрихи (после поворота или очистки). */
    fun redrawAll() {
        val c = bitmapCanvas ?: return
        c.drawColor(bgColor)
        for (stroke in vm.strokes) {
            for (k in 1 until stroke.size) drawSegment(c, stroke[k - 1], stroke[k])
        }
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && vm.unbuffered) {
            requestUnbufferedDispatch(ev)
        }
        val c = bitmapCanvas ?: return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                if (ev.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS) {
                    val stroke = mutableListOf(StrokePoint(ev.getX(i), ev.getY(i), ev.getPressure(i)))
                    active[ev.getPointerId(i)] = stroke
                    vm.strokes += stroke
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val stroke = active[ev.getPointerId(i)] ?: continue
                    for (h in 0 until ev.historySize) {
                        append(c, stroke, StrokePoint(ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), ev.getHistoricalPressure(i, h)))
                    }
                    append(c, stroke, StrokePoint(ev.getX(i), ev.getY(i), ev.getPressure(i)))
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> active.remove(ev.getPointerId(ev.actionIndex))
            MotionEvent.ACTION_UP -> active.clear()
            MotionEvent.ACTION_CANCEL -> {
                // Система отменила касание (например, ладонь) — убираем незавершённые штрихи
                vm.strokes.removeAll { s -> active.values.any { it === s } }
                active.clear()
                redrawAll()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    private fun append(c: Canvas, stroke: MutableList<StrokePoint>, p: StrokePoint) {
        drawSegment(c, stroke.last(), p)
        stroke += p
    }

    private fun drawSegment(c: Canvas, a: StrokePoint, b: StrokePoint) {
        val p = ((a.pressure + b.pressure) / 2f).coerceIn(0f, 1f)
        paint.strokeWidth = minWidth + (maxWidth - minWidth) * p
        c.drawLine(a.x, a.y, b.x, b.y, paint)
    }
}
