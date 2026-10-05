package com.zzzudroid.pen.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
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
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.floor
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
    private val overlay = SelectionOverlay(context)
    private val minimap = MinimapLayer(context)
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

    // --- Лассо ---
    private enum class LassoMode { LOOP, MOVE, SCALE }

    private var lassoPointer: Int? = null
    private var lassoMode = LassoMode.LOOP

    // Петля в мировых координатах
    private val loopXs = ArrayList<Float>()
    private val loopYs = ArrayList<Float>()

    // Перетаскивание/масштаб: исходные рамка и точка, текущее превью-преобразование
    private var dragBounds: Bounds? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var preview: SelectionTransform? = null
    private var previewSet: MutableSet<Stroke> = Collections.newSetFromMap(IdentityHashMap())

    init {
        addView(strokesLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(inProgress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(minimap, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        inProgress.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                // Сначала штрих попадает в нижний слой, и только потом убирается из верхнего — без мигания
                strokes.values.forEach { vm.addStroke(it) }
                strokesLayer.invalidate()
                inProgress.removeFinishedStrokes(strokes.keys)
            }
        })
    }

    /** Модель изменилась извне (отмена, повтор, сброс вида, выделение). */
    fun refresh() = invalidateLayers()

    /**
     * Подложка листа вместо шаблона: рисует в мировых координатах (canvas уже умножен на матрицу вида).
     * Возвращает true, если нарисовала; invalidate — перерисовать слой, когда подложка дорендерится.
     * Не задана — рисуется обычный шаблон страницы.
     */
    var pageBackground: ((canvas: Canvas, spec: PageSpec, scale: Float, invalidate: () -> Unit) -> Boolean)? = null

    /** Точечная сетка на безграничном холсте (доска). */
    var showGrid = false
        set(value) { field = value; strokesLayer.invalidate() }

    /** Мини-карта в правом нижнем углу (доска). */
    var showMinimap = false
        set(value) { field = value; minimap.invalidate() }

    private fun invalidateLayers() {
        strokesLayer.invalidate()
        overlay.invalidate()
        minimap.invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) vm.onViewSize(w, h, density)
    }

    // Все касания обрабатываем сами, дочерние View их не получают
    override fun onInterceptTouchEvent(ev: MotionEvent) = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // Касание мини-карты разбирается раньше пера и пальцев и забирает весь жест
        if (minimap.handleTouch(ev)) return true
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
                cancelLasso()
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

        // «Стирать пальцем»: один палец работает как штриховой ластик
        if (vm.eraseWithFinger && fingers.isEmpty() && erasing.isEmpty() && ev.pointerCount == 1) {
            fingerErasePointer = id
            erasing[id] = worldPoint(ev.getX(i), ev.getY(i))
            eraseAlong(erasing.getValue(id), erasing.getValue(id))
            return
        }

        // Второй палец во время стирания пальцем: завершаем стирание и превращаем жест в пан/зум
        fingerErasePointer?.let { pid ->
            finishErasing()
            erasing.remove(pid)
            fingerErasePointer = null
            val k = ev.findPointerIndex(pid)
            if (k >= 0) fingers[pid] = floatArrayOf(ev.getX(k), ev.getY(k))
        }

        if (fingers.isEmpty()) {
            gestureStart = ev.eventTime
            gestureMaxFingers = 0
            gestureTravel = 0f
        }
        fingers[id] = floatArrayOf(ev.getX(i), ev.getY(i))
        gestureMaxFingers = maxOf(gestureMaxFingers, fingers.size)
    }

    // Палец, который сейчас стирает (режим «стирать пальцем»)
    private var fingerErasePointer: Int? = null

    private fun startPen(ev: MotionEvent, i: Int, id: Int) {
        // Кнопки пера приложением не используются вообще — только инструмент на панели и ластик-инструмент пера
        val eraser = ev.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER
        // Лассо работает и за пределами листа; ластик пера стирает и в режиме лассо
        if (!eraser && vm.tool == CanvasTool.LASSO) {
            if (lassoPointer == null) startLasso(ev.getX(i), ev.getY(i), id)
            return
        }
        // На странице с границами штрих начинается только на листе
        val spec = vm.pageSpec
        if (spec != null) {
            val w = worldPoint(ev.getX(i), ev.getY(i))
            if (!spec.contains(w[0], w[1])) return
        }
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

        lassoPointer?.let { pid ->
            val i = ev.findPointerIndex(pid)
            if (i >= 0) moveLasso(ev, i)
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
        if (fingerErasePointer == id) fingerErasePointer = null

        if (lassoPointer == id) {
            if (ev.flags and MotionEvent.FLAG_CANCELED != 0) cancelLasso() else finishLasso()
        }

        if (fingers.remove(id) != null && fingers.isEmpty()) onGestureEnd(ev.eventTime)
    }

    private fun resetAll() {
        drawing.clear()
        erasing.clear()
        fingerErasePointer = null
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
        invalidateLayers()
    }

    private fun onGestureEnd(time: Long) {
        // Короткий тап без движения: два пальца — отмена, три — повтор
        val tap = time - gestureStart < TAP_MAX_MS && gestureTravel < TAP_MAX_TRAVEL_DP * density
        if (tap && gestureMaxFingers == 2) vm.undo()
        if (tap && gestureMaxFingers == 3) vm.redo()
        if (tap && gestureMaxFingers >= 2) strokesLayer.invalidate()
    }

    // --- Лассо ---

    /** Рамка выделения на экране (с учётом превью) или null. */
    private fun selectionScreenRect(): Bounds? {
        val b = StrokeOps.bounds(vm.selection) ?: return null
        val w = preview?.map(b) ?: b
        val vp = vm.viewport
        return Bounds(vp.worldToScreenX(w.left), vp.worldToScreenY(w.top), vp.worldToScreenX(w.right), vp.worldToScreenY(w.bottom))
    }

    private fun startLasso(x: Float, y: Float, id: Int) {
        lassoPointer = id
        val rect = selectionScreenRect()
        val world = worldPoint(x, y)
        dragStartX = world[0]
        dragStartY = world[1]
        when {
            rect != null && hypot(x - rect.right, y - rect.bottom) <= HANDLE_RADIUS_DP * density -> beginDrag(LassoMode.SCALE)
            rect != null && rect.inflate(SELECT_TOLERANCE_DP * density).contains(x, y) -> beginDrag(LassoMode.MOVE)
            else -> {
                lassoMode = LassoMode.LOOP
                loopXs.clear(); loopYs.clear()
                loopXs += world[0]; loopYs += world[1]
                vm.clearSelection()
            }
        }
        invalidateLayers()
    }

    private fun beginDrag(mode: LassoMode) {
        lassoMode = mode
        dragBounds = StrokeOps.bounds(vm.selection)
        previewSet = Collections.newSetFromMap(IdentityHashMap())
        previewSet.addAll(vm.selection)
        preview = SelectionTransform()
    }

    private fun moveLasso(ev: MotionEvent, i: Int) {
        val w = worldPoint(ev.getX(i), ev.getY(i))
        when (lassoMode) {
            LassoMode.LOOP -> {
                loopXs += w[0]; loopYs += w[1]
            }
            LassoMode.MOVE -> preview = SelectionTransform(dx = w[0] - dragStartX, dy = w[1] - dragStartY)
            LassoMode.SCALE -> {
                val b = dragBounds ?: return
                // Равномерный масштаб с якорем в левом верхнем углу рамки
                val startDist = hypot(b.right - b.left, b.bottom - b.top)
                if (startDist <= 0f) return
                val dist = hypot(w[0] - b.left, w[1] - b.top)
                val scale = (dist / startDist).coerceIn(MIN_SELECTION_SCALE, MAX_SELECTION_SCALE)
                preview = SelectionTransform(scale = scale, anchorX = b.left, anchorY = b.top)
            }
        }
        invalidateLayers()
    }

    private fun finishLasso() {
        when (lassoMode) {
            LassoMode.LOOP -> if (loopXs.size >= 3) {
                val polygon = LassoPolygon(loopXs.toFloatArray(), loopYs.toFloatArray())
                val pb = polygon.bounds
                val picked = vm.strokes.filter { s ->
                    val sb = StrokeOps.bounds(listOf(s)) ?: return@filter false
                    // Быстрый отсев по рамкам, затем точная проверка точек штриха
                    val overlap = sb.right >= pb.left && sb.left <= pb.right && sb.bottom >= pb.top && sb.top <= pb.bottom
                    overlap && StrokeOps.points(s).let { (xs, ys) -> polygon.selects(xs, ys) }
                }
                if (picked.isEmpty()) vm.clearSelection() else vm.select(picked)
            }
            LassoMode.MOVE, LassoMode.SCALE -> {
                val t = preview
                if (t != null && !t.isIdentity) {
                    vm.replaceSelection(vm.selection.map { StrokeOps.transformed(it, t) })
                }
            }
        }
        cancelLasso()
    }

    /** Сбросить состояние лассо без применения. */
    private fun cancelLasso() {
        lassoPointer = null
        lassoMode = LassoMode.LOOP
        loopXs.clear(); loopYs.clear()
        preview = null
        dragBounds = null
        previewSet.clear()
        invalidateLayers()
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
                if (showGrid) drawGrid(canvas)
            } else {
                canvas.drawColor(DESK)
                canvas.save()
                canvas.concat(matrix)
                // Своя подложка (например, страница PDF); если её нет — шаблон листа
                val custom = pageBackground?.invoke(canvas, spec, vm.viewport.scale) { invalidate() } ?: false
                if (!custom) drawPage(canvas, spec, vm.viewport.scale)
                canvas.restore()
            }
            // Во время перетаскивания выделенные штрихи рисует оверлей
            // Ink не применяет переданную матрицу к холсту, а только учитывает её при расчёте толщины:
            // положение штрихов задаёт сам холст, поэтому матрицу вида применяем к нему явно
            val hidden = if (preview != null) previewSet else null
            canvas.save()
            canvas.concat(matrix)
            for (s in vm.strokes) if (hidden == null || s !in hidden) renderer.draw(canvas, s, matrix)
            canvas.restore()
        }
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xA9, 0xB4, 0xC2)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.4f
    }
    private var gridPoints = FloatArray(0)

    /** Точечная сетка видимой области: шаг 5 мм, удваивается, пока на экране не станет >= 12 dp. */
    private fun drawGrid(canvas: Canvas) {
        val vp = vm.viewport
        var step = GRID_STEP_WORLD
        while (step * vp.scale < GRID_MIN_STEP_DP * density) step *= 2
        val left = floor(vp.screenToWorldX(0f) / step) * step
        val top = floor(vp.screenToWorldY(0f) / step) * step
        val right = vp.screenToWorldX(width.toFloat())
        val bottom = vp.screenToWorldY(height.toFloat())
        val cols = ((right - left) / step).toInt() + 2
        val rows = ((bottom - top) / step).toInt() + 2
        val need = cols * rows * 2
        if (gridPoints.size < need) gridPoints = FloatArray(need)
        var n = 0
        for (r in 0 until rows) {
            val sy = vp.worldToScreenY(top + r * step)
            for (c in 0 until cols) {
                gridPoints[n++] = vp.worldToScreenX(left + c * step)
                gridPoints[n++] = sy
            }
        }
        // drawPoints с круглым концом — быстрее тысяч drawCircle
        canvas.drawPoints(gridPoints, 0, n, gridPaint)
    }

    /**
     * Мини-карта: содержимое и текущий вид. Касание или перетаскивание переносит центр вида;
     * на время жеста раскладка карты замораживается, чтобы она не «прыгала» вслед за видом.
     */
    private inner class MinimapLayer(context: Context) : View(context) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0xD8, 0xFF, 0xFF, 0xFF) }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(0xC8, 0xC4, 0xBC)
            strokeWidth = 1f * density
        }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.rgb(0x44, 0x44, 0x44)
            strokeCap = Paint.Cap.ROUND
        }
        private val viewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = SELECTION_COLOR
            strokeWidth = 1.5f * density
        }
        private val clip = Path()
        private val m = Matrix()

        // Кэш содержимого по версии модели
        private var cachedVersion = -1
        private var cachedStrokes: List<Stroke>? = null
        private var contentBounds: Bounds? = null
        private val contentPath = Path()

        // Замороженная раскладка на время жеста: [wl, wt, k, ox, oy]
        private var pointer: Int? = null
        private var frozen: FloatArray? = null

        private fun rect(): Bounds {
            val w = MINIMAP_W_DP * density
            val h = MINIMAP_H_DP * density
            val margin = MINIMAP_MARGIN_DP * density
            return Bounds(width - margin - w, height - margin - h, width - margin, height - margin)
        }

        private fun refreshContent() {
            if (cachedVersion == vm.version && cachedStrokes === vm.strokes) return
            cachedVersion = vm.version
            cachedStrokes = vm.strokes
            val strokes = vm.strokes
            contentBounds = StrokeOps.bounds(strokes)
            contentPath.reset()
            if (strokes.size > MINIMAP_DETAIL_LIMIT) {
                // Много штрихов — только их рамки
                for (s in strokes) StrokeOps.bounds(listOf(s))?.let {
                    contentPath.addRect(it.left, it.top, it.right, it.bottom, Path.Direction.CW)
                }
            } else {
                // Штрихи ломаными по каждой 4-й точке
                for (s in strokes) {
                    val (xs, ys) = StrokeOps.points(s)
                    if (xs.isEmpty()) continue
                    contentPath.moveTo(xs[0], ys[0])
                    var k = 4
                    while (k < xs.size) {
                        contentPath.lineTo(xs[k], ys[k])
                        k += 4
                    }
                    contentPath.lineTo(xs.last(), ys.last())
                }
            }
        }

        /** Видимая область в мировых координатах. */
        private fun viewWorld(): Bounds {
            val vp = vm.viewport
            return Bounds(
                vp.screenToWorldX(0f), vp.screenToWorldY(0f),
                vp.screenToWorldX(width.toFloat()), vp.screenToWorldY(height.toFloat()),
            )
        }

        /** Раскладка [wl, wt, k, ox, oy]: экран карты = rect.left + ox + (x - wl) * k. */
        private fun layout(content: Bounds): FloatArray {
            val r = rect()
            var world = content.union(viewWorld())
            world = world.inflate(maxOf(world.width, world.height) * 0.05f)
            val k = minOf(r.width / world.width, r.height / world.height)
            val ox = (r.width - world.width * k) / 2
            val oy = (r.height - world.height * k) / 2
            return floatArrayOf(world.left, world.top, k, ox, oy)
        }

        private fun visible(): Boolean {
            if (!showMinimap || width == 0) return false
            refreshContent()
            return contentBounds != null
        }

        override fun onDraw(canvas: Canvas) {
            if (!visible()) return
            val content = contentBounds ?: return
            val r = rect()
            val l = frozen ?: layout(content)
            val radius = 8f * density
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, bg)
            canvas.save()
            clip.reset()
            clip.addRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, Path.Direction.CW)
            canvas.clipPath(clip)
            m.setTranslate(-l[0], -l[1])
            m.postScale(l[2], l[2])
            m.postTranslate(r.left + l[3], r.top + l[4])
            canvas.save()
            canvas.concat(m)
            ink.strokeWidth = 1.2f * density / l[2]
            canvas.drawPath(contentPath, ink)
            canvas.restore()
            val v = viewWorld()
            val x0 = r.left + l[3]
            val y0 = r.top + l[4]
            canvas.drawRect(
                x0 + (v.left - l[0]) * l[2], y0 + (v.top - l[1]) * l[2],
                x0 + (v.right - l[0]) * l[2], y0 + (v.bottom - l[1]) * l[2],
                viewPaint,
            )
            canvas.restore()
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, border)
        }

        /** Разобрать касание; true — касание принадлежит мини-карте. */
        fun handleTouch(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!visible()) return false
                    val x = ev.getX(0)
                    val y = ev.getY(0)
                    if (!rect().contains(x, y)) return false
                    pointer = ev.getPointerId(0)
                    frozen = layout(contentBounds!!)
                    jumpTo(x, y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val id = pointer ?: return false
                    val i = ev.findPointerIndex(id)
                    if (i >= 0) jumpTo(ev.getX(i), ev.getY(i))
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pointer == null) return false
                    pointer = null
                    frozen = null
                    invalidate()
                    return true
                }
                else -> return pointer != null
            }
        }

        /** Перенести центр вида в мировую точку под точкой карты. */
        private fun jumpTo(x: Float, y: Float) {
            val l = frozen ?: return
            val r = rect()
            val wx = l[0] + (x - r.left - l[3]) / l[2]
            val wy = l[1] + (y - r.top - l[4]) / l[2]
            val vp = vm.viewport
            val cx = vp.screenToWorldX(width / 2f)
            val cy = vp.screenToWorldY(height / 2f)
            vp.panBy((cx - wx) * vp.scale, (cy - wy) * vp.scale)
            vm.viewportChanged()
            invalidateLayers()
        }
    }

    /** Оверлей лассо: петля, рамка выделения с маркером масштаба, превью перемещения. */
    private inner class SelectionOverlay(context: Context) : View(context) {
        private val matrix = Matrix()
        private val transformMatrix = Matrix()
        private val path = Path()
        private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = SELECTION_COLOR
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
        }
        private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = SELECTION_COLOR
        }

        override fun onDraw(canvas: Canvas) {
            val vp = vm.viewport

            // Превью: выделенные штрихи с матрицей вида × преобразование выделения
            preview?.let { t ->
                vm.viewMatrix(matrix)
                transformMatrix.setScale(t.scale, t.scale, t.anchorX, t.anchorY)
                transformMatrix.postTranslate(t.dx, t.dy)
                matrix.preConcat(transformMatrix)
                canvas.save()
                canvas.concat(matrix)
                for (s in vm.selection) renderer.draw(canvas, s, matrix)
                canvas.restore()
            }

            // Петля, которую сейчас рисуют
            if (lassoPointer != null && lassoMode == LassoMode.LOOP && loopXs.size > 1) {
                path.reset()
                path.moveTo(vp.worldToScreenX(loopXs[0]), vp.worldToScreenY(loopYs[0]))
                for (k in 1 until loopXs.size) path.lineTo(vp.worldToScreenX(loopXs[k]), vp.worldToScreenY(loopYs[k]))
                canvas.drawPath(path, dash)
            }

            // Рамка выделения и маркер масштаба в правом нижнем углу
            val r = selectionScreenRect() ?: return
            val pad = 4f * density
            canvas.drawRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, dash)
            val h = HANDLE_SIZE_DP * density / 2
            canvas.drawRect(r.right - h, r.bottom - h, r.right + h, r.bottom + h, handleFill)
            canvas.drawRect(r.right - h, r.bottom - h, r.right + h, r.bottom + h, handleStroke)
        }
    }

    companion object {
        const val ERASER_RADIUS_DP = 10f
        const val TAP_MAX_MS = 250L
        const val TAP_MAX_TRAVEL_DP = 24f
        const val MIN_PINCH_PX = 10f

        // Лассо: допуск попадания в рамку, радиус маркера масштаба, пределы масштаба
        const val SELECT_TOLERANCE_DP = 12f
        const val HANDLE_RADIUS_DP = 24f
        const val HANDLE_SIZE_DP = 12f
        const val MIN_SELECTION_SCALE = 0.1f
        const val MAX_SELECTION_SCALE = 10f
        val SELECTION_COLOR = Color.rgb(0x1F, 0x4E, 0xC9)
        // Доска: шаг сетки 5 мм, минимальный шаг на экране, размеры мини-карты
        const val GRID_STEP_WORLD = 5 * PageSpec.UNITS_PER_MM
        const val GRID_MIN_STEP_DP = 12f
        const val MINIMAP_W_DP = 180f
        const val MINIMAP_H_DP = 120f
        const val MINIMAP_MARGIN_DP = 8f
        const val MINIMAP_DETAIL_LIMIT = 300
        val BACKGROUND = Color.rgb(0xFB, 0xFA, 0xF7)
        val DESK = Color.rgb(0xE4, 0xE1, 0xDA)
    }
}
