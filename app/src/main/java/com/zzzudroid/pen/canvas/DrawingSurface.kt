package com.zzzudroid.pen.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.ImmutableParallelogram
import androidx.ink.geometry.ImmutableSegment
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import kotlin.math.hypot

/**
 * Поверхность рисования: снизу — слой готовых штрихов, сверху — InProgressStrokesView
 * (активный штрих во фронт-буфере). Все указатели разбираются здесь, в одном месте.
 */
@SuppressLint("ViewConstructor")
class DrawingSurface(context: Context, private val vm: CanvasViewModel) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val renderer = CanvasStrokeRenderer.create()
    private val viewMatrix = Matrix()
    private val worldMatrix = Matrix()

    private val strokesLayer = StrokesLayer(context)
    private val inProgress = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)

    // Рисующие указатели: id указателя → id штриха в InProgressStrokesView
    private val drawing = HashMap<Int, InProgressStrokeId>()

    // Стирающие указатели: id → последняя точка в мировых координатах
    private val erasing = HashMap<Int, FloatArray>()
    private val erasedNow = mutableListOf<Stroke>()

    // Указатели пера, касающиеся экрана (для защиты от ладони)
    private val stylusDown = HashSet<Int>()

    // Пальцы жеста: id → последняя позиция на экране; игнорируемые касания (ладонь)
    private val fingers = LinkedHashMap<Int, FloatArray>()
    private val ignored = HashSet<Int>()
    private var gestureStart = 0L
    private var gestureMaxFingers = 0
    private var gestureTravel = 0f

    init {
        addView(strokesLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(inProgress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        inProgress.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                // Сначала штрих попадает в нижний слой, и только потом убирается из верхнего — без мигания
                strokes.values.forEach { vm.addStroke(it) }
                strokesLayer.invalidate()
                inProgress.removeFinishedStrokes(strokes.keys)
            }
        })
    }

    /** Модель изменилась извне (отмена, повтор, сброс вида). */
    fun refresh() = strokesLayer.invalidate()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) vm.onViewSize(w, h, density)
    }

    // Все касания обрабатываем сами, дочерние View их не получают
    override fun onInterceptTouchEvent(ev: MotionEvent) = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        predictor.record(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(ev, ev.actionIndex)
            MotionEvent.ACTION_MOVE -> onMove(ev)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(ev, ev.actionIndex)
            MotionEvent.ACTION_UP -> {
                onPointerUp(ev, ev.actionIndex)
                resetAll()
            }
            MotionEvent.ACTION_CANCEL -> {
                // Система отменила весь жест (обычно распознала ладонь)
                drawing.values.forEach { inProgress.cancelStroke(it, ev) }
                finishErasing()
                resetAll()
            }
        }
        return true
    }

    private fun onPointerDown(ev: MotionEvent, i: Int) {
        val id = ev.getPointerId(i)
        val tool = ev.getToolType(i)
        val isPen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER

        if (isPen) {
            requestUnbufferedDispatch(ev)
            stylusDown += id
            // Перо коснулось — все пальцы на экране считаем ладонью
            ignored += fingers.keys
            fingers.clear()
            startPen(ev, i, id)
            return
        }

        if (stylusDown.isNotEmpty() || StylusPresence.isHovering()) {
            ignored += id
            return
        }

        if (vm.drawWithFinger && fingers.isEmpty() && drawing.isEmpty() && ev.pointerCount == 1) {
            startPen(ev, i, id)
            return
        }

        // Второй палец во время рисования пальцем: превращаем рисование в жест
        if (drawing.isNotEmpty() && stylusDown.isEmpty()) {
            drawing.values.forEach { inProgress.cancelStroke(it, ev) }
            for (k in 0 until ev.pointerCount) {
                val pid = ev.getPointerId(k)
                if (drawing.containsKey(pid)) fingers[pid] = floatArrayOf(ev.getX(k), ev.getY(k))
            }
            drawing.clear()
        }

        if (fingers.isEmpty()) {
            gestureStart = ev.eventTime
            gestureMaxFingers = 0
            gestureTravel = 0f
        }
        fingers[id] = floatArrayOf(ev.getX(i), ev.getY(i))
        gestureMaxFingers = maxOf(gestureMaxFingers, fingers.size)
    }

    private fun startPen(ev: MotionEvent, i: Int, id: Int) {
        // На странице с границами штрих начинается только на листе
        val spec = vm.pageSpec
        if (spec != null) {
            val w = worldPoint(ev.getX(i), ev.getY(i))
            if (!spec.contains(w[0], w[1])) return
        }
        val eraser = ev.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER ||
            (ev.buttonState and STYLUS_ERASER_BUTTONS) != 0
        val brush = vm.brush(density, eraserOverride = eraser)
        if (brush == null) {
            erasing[id] = worldPoint(ev.getX(i), ev.getY(i))
            eraseAlong(erasing[id]!!, erasing[id]!!)
        } else {
            vm.viewMatrix(viewMatrix).invert(worldMatrix)
            drawing[id] = inProgress.startStroke(ev, id, brush, worldMatrix)
        }
    }

    private fun onMove(ev: MotionEvent) {
        if (drawing.isNotEmpty()) {
            val predicted = predictor.predict()
            for ((pid, strokeId) in drawing) {
                if (ev.findPointerIndex(pid) >= 0) inProgress.addToStroke(ev, pid, strokeId, predicted)
            }
            predicted?.recycle()
        }

        for ((pid, last) in erasing) {
            val i = ev.findPointerIndex(pid)
            if (i < 0) continue
            val p = worldPoint(ev.getX(i), ev.getY(i))
            eraseAlong(last, p)
            last[0] = p[0]
            last[1] = p[1]
        }

        if (fingers.isNotEmpty()) onGestureMove(ev)
    }

    private fun onPointerUp(ev: MotionEvent, i: Int) {
        val id = ev.getPointerId(i)
        stylusDown -= id
        ignored -= id

        drawing.remove(id)?.let { strokeId ->
            // Отпускание с FLAG_CANCELED — система решила, что касание случайное
            if (ev.flags and MotionEvent.FLAG_CANCELED != 0) inProgress.cancelStroke(strokeId, ev)
            else inProgress.finishStroke(ev, id, strokeId)
        }

        if (erasing.remove(id) != null && erasing.isEmpty()) finishErasing()

        if (fingers.remove(id) != null && fingers.isEmpty()) onGestureEnd(ev.eventTime)
    }

    private fun resetAll() {
        drawing.clear()
        erasing.clear()
        stylusDown.clear()
        fingers.clear()
        ignored.clear()
    }

    // --- Жесты пальцами ---

    private fun onGestureMove(ev: MotionEvent) {
        val ids = fingers.keys.filter { ev.findPointerIndex(it) >= 0 }
        if (ids.isEmpty()) return
        val vp = vm.viewport

        if (ids.size == 1) {
            val i = ev.findPointerIndex(ids[0])
            val last = fingers.getValue(ids[0])
            val dx = ev.getX(i) - last[0]
            val dy = ev.getY(i) - last[1]
            vp.panBy(dx, dy)
            gestureTravel += hypot(dx, dy)
        } else {
            // Два первых пальца: сдвиг центра и изменение расстояния
            val a = fingers.getValue(ids[0])
            val b = fingers.getValue(ids[1])
            val ia = ev.findPointerIndex(ids[0])
            val ib = ev.findPointerIndex(ids[1])
            val ax = ev.getX(ia); val ay = ev.getY(ia)
            val bx = ev.getX(ib); val by = ev.getY(ib)
            val oldCx = (a[0] + b[0]) / 2; val oldCy = (a[1] + b[1]) / 2
            val newCx = (ax + bx) / 2; val newCy = (ay + by) / 2
            val oldDist = hypot(a[0] - b[0], a[1] - b[1])
            val newDist = hypot(ax - bx, ay - by)
            vp.panBy(newCx - oldCx, newCy - oldCy)
            if (oldDist > MIN_PINCH_PX && newDist > MIN_PINCH_PX) vp.zoomAround(newCx, newCy, newDist / oldDist)
            gestureTravel += hypot(newCx - oldCx, newCy - oldCy) + kotlin.math.abs(newDist - oldDist)
        }

        for (pid in ids) {
            val i = ev.findPointerIndex(pid)
            fingers.getValue(pid).let { it[0] = ev.getX(i); it[1] = ev.getY(i) }
        }
        vm.viewportChanged()
        strokesLayer.invalidate()
    }

    private fun onGestureEnd(time: Long) {
        // Короткий тап без движения: два пальца — отмена, три — повтор
        val tap = time - gestureStart < TAP_MAX_MS && gestureTravel < TAP_MAX_TRAVEL_DP * density
        if (tap && gestureMaxFingers == 2) vm.undo()
        if (tap && gestureMaxFingers == 3) vm.redo()
        if (tap && gestureMaxFingers >= 2) strokesLayer.invalidate()
    }

    // --- Ластик ---

    private fun worldPoint(x: Float, y: Float) =
        floatArrayOf(vm.viewport.screenToWorldX(x), vm.viewport.screenToWorldY(y))

    private fun eraseAlong(from: FloatArray, to: FloatArray) {
        val radius = ERASER_RADIUS_DP * density / vm.viewport.scale
        val area = ImmutableParallelogram.fromSegmentAndPadding(
            ImmutableSegment(ImmutableVec(from[0], from[1]), ImmutableVec(to[0], to[1])),
            radius,
        )
        val hit = vm.strokes.filter { it.shape.intersects(area, AffineTransform.IDENTITY) }
        if (hit.isEmpty()) return
        hit.forEach { vm.removeLive(it) }
        erasedNow += hit
        strokesLayer.invalidate()
    }

    private fun finishErasing() {
        vm.commitErase(erasedNow.toList())
        erasedNow.clear()
    }

    /** Слой готовых штрихов: фон (стол, лист, шаблон) и все штрихи текущей страницы. */
    private inner class StrokesLayer(context: Context) : View(context) {
        private val matrix = Matrix()

        override fun onDraw(canvas: Canvas) {
            vm.viewMatrix(matrix)
            val spec = vm.pageSpec
            if (spec == null) {
                canvas.drawColor(BACKGROUND)
            } else {
                canvas.drawColor(DESK)
                canvas.save()
                canvas.concat(matrix)
                drawPage(canvas, spec, vm.viewport.scale)
                canvas.restore()
            }
            for (s in vm.strokes) renderer.draw(canvas, s, matrix)
        }
    }

    companion object {
        /** Кнопки пера, при удержании которых перо стирает. Уточнить по отчёту диагностики. */
        const val STYLUS_ERASER_BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY

        const val ERASER_RADIUS_DP = 10f
        const val TAP_MAX_MS = 250L
        const val TAP_MAX_TRAVEL_DP = 24f
        const val MIN_PINCH_PX = 10f
        val BACKGROUND = Color.rgb(0xFB, 0xFA, 0xF7)
        val DESK = Color.rgb(0xE4, 0xE1, 0xDA)
    }
}
