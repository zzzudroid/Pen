package com.zzzudroid.pen.canvas

import android.graphics.Color
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushCoat
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushTip
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import java.util.UUID

/** Инструмент панели; id кистей хранится в файле тетради. */
enum class CanvasTool(val title: String, val id: String) {
    PEN("Ручка", "pen"),
    MARKER("Маркер", "marker"),
    HIGHLIGHTER("Выделитель", "highlighter"),
    CALLIGRAPHY("Каллиграфия", "calligraphy"),
    ERASER("Ластик", "eraser"),
    LASSO("Лассо", "lasso"),
}

/** Команда истории холста. */
sealed interface CanvasCommand {
    class Add(val stroke: Stroke) : CanvasCommand
    class Erase(val strokes: List<Stroke>) : CanvasCommand

    /**
     * Замена штрихов: перемещение, масштаб, перекраска выделения. [linksOld] → [linksNew] — ссылки, которые поехали
     * вместе со штрихами (целиком внутри выделения); пары по порядку.
     */
    class Replace(
        val removed: List<Stroke>,
        val added: List<Stroke>,
        val linksOld: List<DocLink> = emptyList(),
        val linksNew: List<DocLink> = emptyList(),
    ) : CanvasCommand

    /** Ссылки: создание, удаление и смена цели/области — как команды истории, чтобы «Отменить» работало. */
    class LinkAdded(val link: DocLink) : CanvasCommand
    class LinkRemoved(val links: List<DocLink>) : CanvasCommand
    class LinkChanged(val old: List<DocLink>, val new: List<DocLink>) : CanvasCommand

    /** Добавление нескольких штрихов разом (копия выделения). */
    class AddMany(val strokes: List<Stroke>) : CanvasCommand
}

/** Палитра: чёрный, синий, красный, зелёный, оранжевый, фиолетовый. */
val PALETTE = listOf(
    Color.rgb(0x1A, 0x1A, 0x1A),
    Color.rgb(0x1F, 0x4E, 0xC9),
    Color.rgb(0xD3, 0x2F, 0x2F),
    Color.rgb(0x2E, 0x7D, 0x32),
    Color.rgb(0xEF, 0x8A, 0x00),
    Color.rgb(0x7B, 0x1F, 0xA2),
)

/** Стоковые семейства кистей Ink — создаются один раз. */
@OptIn(androidx.ink.brush.ExperimentalInkCustomBrushApi::class)
object Brushes {
    val pen: BrushFamily by lazy { StockBrushes.pressurePen() }
    val marker: BrushFamily by lazy { StockBrushes.marker() }
    val highlighter: BrushFamily by lazy { StockBrushes.highlighter() }

    /**
     * Каллиграфическое перо: узкий наконечник под постоянным углом (плоское перо), поэтому линия толще поперёк
     * наклона пера и тоньше вдоль него; нажим меняет толщину. Угол не привязан к направлению штриха.
     */
    val calligraphy: BrushFamily by lazy {
        val pressure = BrushBehavior(
            source = BrushBehavior.Source.NORMALIZED_PRESSURE,
            target = BrushBehavior.Target.SIZE_MULTIPLIER,
            sourceValueRangeStart = 0f,
            sourceValueRangeEnd = 1f,
            // Нажим (и скорость письма, см. DrawingSurface) меняют толщину в 3 раза: как у INKredible
            targetModifierRangeStart = 0.35f,
            targetModifierRangeEnd = 1.15f,
        )
        val tip = BrushTip(
            scaleX = 0.22f,
            scaleY = 1f,
            cornerRounding = 0.15f,
            rotationDegrees = 45f,
            behaviors = listOf(pressure),
        )
        BrushFamily(coats = listOf(BrushCoat(tip)), clientBrushFamilyId = "calligraphy")
    }

    fun family(tool: CanvasTool): BrushFamily? = when (tool) {
        CanvasTool.PEN -> pen
        CanvasTool.MARKER -> marker
        CanvasTool.HIGHLIGHTER -> highlighter
        CanvasTool.CALLIGRAPHY -> calligraphy
        CanvasTool.ERASER, CanvasTool.LASSO -> null
    }

    /** Инструмент по семейству кисти штриха (сравнение по значению). */
    fun toolOf(family: BrushFamily): CanvasTool = when (family) {
        marker -> CanvasTool.MARKER
        highlighter -> CanvasTool.HIGHLIGHTER
        calligraphy -> CanvasTool.CALLIGRAPHY
        else -> CanvasTool.PEN
    }
}

/**
 * Состояние холста: страницы со штрихами в мировых координатах, история, вид и текущая кисть.
 * Живёт в ViewModel, чтобы пережить поворот экрана. Без [pageSpec] — одна безграничная страница.
 */
open class CanvasViewModel : ViewModel() {

    val pages = mutableListOf(PageState(newPageId()))
    var pageIndex by mutableIntStateOf(0); private set
    val page get() = pages[pageIndex]
    val strokes get() = page.strokes

    /** Общие границы и шаблон страниц тетради; null — безграничный холст. */
    protected var defaultPageSpec: PageSpec? = null

    /** Границы и шаблон текущей страницы (свои у PDF-страниц); null — безграничный холст. */
    val pageSpec: PageSpec? get() = page.spec ?: defaultPageSpec

    val viewport = Viewport()
    private var viewWidth = 0
    private var viewHeight = 0
    private var density = 1f

    // Счётчик изменений: Compose по нему обновляет кнопки и перерисовывает холст
    var version by mutableIntStateOf(0); private set

    var tool by mutableStateOf(CanvasTool.PEN)
    /** Цвет пера — выбранный пресет (ARGB, с прозрачностью). */
    val colorArgb: Int get() = com.zzzudroid.pen.settings.AppSettings.presets[com.zzzudroid.pen.settings.AppSettings.activePreset]

    /** Пипетка: номер пресета, в который возьмём цвет штриха под пальцем; null — выключена. */
    var eyedropper by mutableStateOf<Int?>(null)

    /** Цвет ближайшего к точке (x, y) штриха (в пределах [radius] ед. мира) или null. */
    fun pickColorAt(x: Float, y: Float, radius: Float = 24f): Int? {
        var best: Stroke? = null
        var bestD = radius * radius
        for (s in strokes) {
            val box = s.shape.computeBoundingBox() ?: continue
            if (x < box.xMin - radius || x > box.xMax + radius || y < box.yMin - radius || y > box.yMax + radius) continue
            val (xs, ys) = StrokeOps.points(s)
            for (i in xs.indices) {
                val d = (xs[i] - x) * (xs[i] - x) + (ys[i] - y) * (ys[i] - y)
                if (d < bestD) { bestD = d; best = s }
            }
        }
        return best?.brush?.colorIntArgb
    }
    // Толщина каждого инструмента, мм: тянется ползунком (BrushSizeButton), запоминается между запусками
    private val brushSizesMm = androidx.compose.runtime.mutableStateMapOf<CanvasTool, Float>().apply {
        for (t in CanvasTool.entries) put(t, com.zzzudroid.pen.settings.AppSettings.toolSizeMm(t.id, DEFAULT_SIZE_MM[t] ?: 0.4f))
    }

    /** Инструмент, которым реально рисуется: при включённом «красивом пере» ручка — каллиграфическое перо. */
    val brushTool: CanvasTool get() = if (tool == CanvasTool.PEN && com.zzzudroid.pen.settings.AppSettings.smoothingOn) CanvasTool.CALLIGRAPHY else tool

    fun sizeOf(t: CanvasTool): Float = brushSizesMm[t] ?: 0.4f

    fun setSize(t: CanvasTool, mm: Float) {
        brushSizesMm[t] = mm
        com.zzzudroid.pen.settings.AppSettings.putToolSizeMm(t.id, mm)
    }
    /** Стирать пальцем: палец работает как штриховой ластик (перо при этом рисует по-прежнему). */
    var eraseWithFinger by mutableStateOf(com.zzzudroid.pen.settings.AppSettings.eraseWithFinger)
    /** Вид заблокирован: пальцы не двигают и не масштабируют поле — только пишем пером. */
    var viewLocked by mutableStateOf(false)

    /** Страница показана целиком (иначе — вписана по ширине). */
    var fitWhole by mutableStateOf(false); private set

    /** Выделенные лассо штрихи текущей страницы (ссылки на объекты из [strokes]). */
    var selection by mutableStateOf<List<Stroke>>(emptyList()); private set

    val canUndo get() = version.let { page.history.canUndo }
    val canRedo get() = version.let { page.history.canRedo }

    fun addStroke(stroke: Stroke) {
        strokes += stroke
        page.history.push(CanvasCommand.Add(stroke))
        changed(content = true)
    }

    /** Стёртые одним движением ластика штрихи — одна команда. */
    fun commitErase(erased: List<Stroke>) {
        if (erased.isEmpty()) return
        selection = emptyList()
        page.history.push(CanvasCommand.Erase(erased))
        changed(content = true)
    }

    /** Удалить штрих из модели во время движения ластика (команда — в commitErase). */
    fun removeLive(stroke: Stroke) {
        strokes.removeAll { it === stroke }
    }

    fun undo() {
        val c = page.history.undo() ?: return
        selection = emptyList()
        when (c) {
            is CanvasCommand.Add -> removeAll(listOf(c.stroke))
            is CanvasCommand.Erase -> strokes += c.strokes
            is CanvasCommand.Replace -> { removeAll(c.added); strokes += c.removed; swapLinks(c.linksNew, c.linksOld) }
            is CanvasCommand.AddMany -> removeAll(c.strokes)
            is CanvasCommand.LinkAdded -> page.links.removeAll { it.id == c.link.id }
            is CanvasCommand.LinkRemoved -> page.links += c.links
            is CanvasCommand.LinkChanged -> swapLinks(c.new, c.old)
        }
        changed(content = true)
    }

    fun redo() {
        val c = page.history.redo() ?: return
        selection = emptyList()
        when (c) {
            is CanvasCommand.Add -> strokes += c.stroke
            is CanvasCommand.Erase -> removeAll(c.strokes)
            is CanvasCommand.Replace -> { removeAll(c.removed); strokes += c.added; swapLinks(c.linksOld, c.linksNew) }
            is CanvasCommand.AddMany -> strokes += c.strokes
            is CanvasCommand.LinkAdded -> page.links += c.link
            is CanvasCommand.LinkRemoved -> page.links.removeAll { l -> c.links.any { it.id == l.id } }
            is CanvasCommand.LinkChanged -> swapLinks(c.old, c.new)
        }
        changed(content = true)
    }

    private fun removeAll(list: List<Stroke>) {
        strokes.removeAll { s -> list.any { it === s } }
    }

    // --- Ссылки на выделенное ---

    /** Заменить ссылки [from] ссылками [to] по идентификатору (id сохраняется при переносе). */
    private fun swapLinks(from: List<DocLink>, to: List<DocLink>) {
        if (from.isEmpty() && to.isEmpty()) return
        val ids = from.map { it.id }.toSet()
        page.links.removeAll { it.id in ids }
        page.links += to
    }

    /** Ссылки текущей страницы, область которых пересекает рамку выделения — их можно изменить или удалить. */
    fun selectedLinks(): List<DocLink> {
        val area = StrokeOps.bounds(selection) ?: return emptyList()
        return page.links.filter { it.bounds.intersects(area) }
    }

    /** Создать ссылку на область [area] текущей страницы с целью [target] (одна команда истории). */
    fun addLink(area: Bounds, target: LinkTarget): DocLink {
        val link = DocLink(DocLink.newId(), page.id, area.left, area.top, area.right, area.bottom, target)
        page.links += link
        page.history.push(CanvasCommand.LinkAdded(link))
        changed(content = true)
        return link
    }

    /** Удалить ссылки (одна команда истории). */
    fun removeLinks(links: List<DocLink>) {
        if (links.isEmpty()) return
        val ids = links.map { it.id }.toSet()
        val removed = page.links.filter { it.id in ids }
        page.links.removeAll { it.id in ids }
        page.history.push(CanvasCommand.LinkRemoved(removed))
        changed(content = true)
    }

    /** Сменить цель ссылок (одна команда истории). */
    fun retargetLinks(links: List<DocLink>, target: LinkTarget) {
        if (links.isEmpty()) return
        val ids = links.map { it.id }.toSet()
        val old = page.links.filter { it.id in ids }
        val new = old.map { it.copy(target = target) }
        swapLinks(old, new)
        page.history.push(CanvasCommand.LinkChanged(old, new))
        changed(content = true)
    }

    /** Ссылка под точкой страницы (x, y), если есть; верхняя — последняя созданная. */
    fun linkAt(x: Float, y: Float): DocLink? = page.links.lastOrNull { it.contains(x, y) }

    // --- Выделение (лассо) ---

    fun select(strokes: List<Stroke>) {
        selection = strokes
        changed(content = false)
    }

    fun clearSelection() {
        if (selection.isEmpty()) return
        selection = emptyList()
        changed(content = false)
    }

    /**
     * Заменить выделенные штрихи новыми (перемещение, масштаб, перекраска) одной командой.
     * [replacement] — штрихи в том же порядке; новые становятся выделением.
     */
    fun replaceSelection(replacement: List<Stroke>, transform: SelectionTransform? = null) {
        val old = selection
        if (old.isEmpty() || replacement.isEmpty()) return
        // Ссылки, целиком попавшие в рамку выделения, едут и масштабируются вместе со штрихами; задетые частично остаются
        var linksOld = emptyList<DocLink>()
        var linksNew = emptyList<DocLink>()
        if (transform != null && !transform.isIdentity) {
            StrokeOps.bounds(old)?.let { area ->
                linksOld = page.links.filter { it.insideOf(area) }
                linksNew = linksOld.map { it.transformed(transform) }
            }
        }
        // Новые штрихи встают на место старых — порядок наложения сохраняется
        val firstIndex = strokes.indexOfFirst { s -> old.any { it === s } }.coerceAtLeast(0)
        removeAll(old)
        strokes.addAll(firstIndex.coerceAtMost(strokes.size), replacement)
        page.history.push(CanvasCommand.Replace(old, replacement, linksOld, linksNew))
        swapLinks(linksOld, linksNew)
        selection = replacement
        changed(content = true)
    }

    fun deleteSelection() {
        val old = selection
        if (old.isEmpty()) return
        removeAll(old)
        page.history.push(CanvasCommand.Erase(old))
        selection = emptyList()
        changed(content = true)
    }

    /** Добавить копии (уже сдвинутые вызывающим кодом) поверх и выделить их. */
    fun addCopies(copies: List<Stroke>) {
        if (copies.isEmpty()) return
        strokes += copies
        page.history.push(CanvasCommand.AddMany(copies))
        selection = copies
        changed(content = true)
    }

    // Границы штрихов по геометрии: считаются один раз (штрихи неизменяемы), чтобы отсекать невидимые без вызовов в нативный код
    private val boundsCache = java.util.WeakHashMap<Stroke, Bounds>()

    /** Границы штриха в мировых координатах (с кэшем). */
    fun strokeBounds(s: Stroke): Bounds = boundsCache.getOrPut(s) {
        s.shape.computeBoundingBox()?.let { Bounds(it.xMin, it.yMin, it.xMax, it.yMax) } ?: Bounds(0f, 0f, 0f, 0f)
    }

    /** Штрихи текущей страницы, чьи границы пересекают [area] (мировые координаты). */
    fun strokesIn(area: Bounds): List<Stroke> = strokes.filter { s ->
        val b = strokeBounds(s)
        b.right >= area.left && b.left <= area.right && b.bottom >= area.top && b.top <= area.bottom
    }

    /** Только отладка: добавить штрихи без истории и выделения (нагрузочный тест). */
    fun debugAddStrokes(list: List<Stroke>) {
        strokes += list
        changed(content = true)
    }

    // --- Страницы ---

    fun goToPage(index: Int) {
        if (index !in pages.indices || index == pageIndex) return
        selection = emptyList()
        pageIndex = index
        fitPage()
        changed(content = false)
    }

    /** Добавить пустую страницу после текущей и перейти на неё. */
    open fun addPageAfterCurrent() {
        selection = emptyList()
        // Новая страница — обычный лист с шаблоном тетради, без PDF-подложки
        pages.add(pageIndex + 1, PageState(newPageId(), spec = defaultPageSpec))
        pageIndex++
        fitPage()
        changed(content = true)
    }

    /** Удалить текущую страницу (последнюю удалить нельзя). */
    open fun deleteCurrentPage(): Boolean {
        if (pages.size <= 1) return false
        selection = emptyList()
        pages.removeAt(pageIndex)
        if (pageIndex >= pages.size) pageIndex = pages.size - 1
        fitPage()
        changed(content = true)
        return true
    }

    /** Заменить содержимое (загрузка тетради). */
    protected fun replacePages(spec: PageSpec?, newPages: List<PageState>) {
        defaultPageSpec = spec
        // Безграничному холсту (доске) можно отдаляться сильнее, чем странице
        viewport.minScale = if (spec == null) BOARD_MIN_SCALE else Viewport.MIN_SCALE
        pages.clear()
        pages += newPages.ifEmpty { listOf(PageState(newPageId())) }
        pageIndex = 0
        fitPage()
        changed(content = false)
    }

    // --- Вид ---

    /** Размер поверхности рисования изменился (первый показ, поворот). */
    fun onViewSize(width: Int, height: Int, density: Float) {
        val sizeChanged = width != viewWidth || height != viewHeight
        viewWidth = width
        viewHeight = height
        this.density = density
        // Вписываем при первом показе и при смене размера (поворот); при возврате на экран («Назад» из ссылки)
        // размер тот же — вид остаётся таким, каким его оставили
        if (!viewInitialized || sizeChanged) fitPage()
        viewInitialized = true
        pendingFocus?.let { (x, y) -> pendingFocus = null; focusOn(x, y) }
        changed(content = false)
    }

    private var viewInitialized = false
    private var pendingFocus: Pair<Float, Float>? = null

    /** Показать точку холста (x, y) в центре экрана, не меняя масштаб; если размер ещё неизвестен — после первого показа. */
    fun focusOn(x: Float, y: Float) {
        if (viewWidth == 0) {
            pendingFocus = x to y
            return
        }
        val vp = viewport
        vp.panBy(viewWidth / 2f - vp.worldToScreenX(x), viewHeight / 2f - vp.worldToScreenY(y))
        viewportChanged()
    }

    /**
     * Вписать лист с полями: по ширине и прижать к верху, либо (при [fitWhole]) страницу целиком по центру.
     * Без страницы — ничего.
     */
    fun fitPage() {
        val spec = pageSpec ?: return
        if (viewWidth == 0) return
        val margin = PAGE_MARGIN_DP * density
        if (fitWhole && viewHeight > 0) {
            val scale = minOf((viewWidth - 2 * margin) / spec.width, (viewHeight - 2 * margin) / spec.height)
            viewport.set(scale, (viewWidth - spec.width * scale) / 2, (viewHeight - spec.height * scale) / 2)
        } else {
            val scale = (viewWidth - 2 * margin) / spec.width
            viewport.set(scale, (viewWidth - spec.width * scale) / 2, margin)
        }
    }

    /**
     * Кнопка «вписать»: для страницы переключает «страница целиком» ↔ «по ширине»,
     * без страницы (холст, доска) — возвращает масштаб 100%.
     */
    fun resetView() {
        if (pageSpec != null) {
            fitWhole = !fitWhole
            fitPage()
        } else {
            viewport.reset()
        }
        changed(content = false)
    }

    fun viewportChanged() {
        if (pageSpec == null && viewWidth > 0) viewport.clampCenter(WORLD_LIMIT, viewWidth.toFloat(), viewHeight.toFloat())
        clampToPdfPage()
        changed(content = false)
    }

    /** Восстановить сохранённый вид (доска открывается там, где её закрыли). */
    protected fun restoreView(scale: Float, tx: Float, ty: Float) {
        viewport.set(scale, tx, ty)
        clampToPdfPage()
        changed(content = false)
    }

    /** Страницу с PDF-подложкой нельзя утащить за края: при чтении лист остаётся на экране. */
    private fun clampToPdfPage() {
        if (page.pdfPage == null || viewWidth == 0 || viewHeight == 0) return
        val spec = pageSpec ?: return
        viewport.clampToRect(0f, 0f, spec.width, spec.height, viewWidth.toFloat(), viewHeight.toFloat(), PAGE_MARGIN_DP * density)
    }

    /** Кисть для нового штриха. Размеры заданы в dp при масштабе 100%. */
    fun brush(density: Float, eraserOverride: Boolean = false): Brush? {
        if (eraserOverride) return null
        // Кнопка «красивое перо» превращает ручку в каллиграфическое перо (штрихи сохраняются как «calligraphy»)
        val effective = brushTool
        val family = Brushes.family(effective) ?: return null
        val base = colorArgb
        val color = if (effective == CanvasTool.HIGHLIGHTER) highlighterColor(base) else base
        // Размер хранится в единицах холста: делим на масштаб вида, чтобы на экране линия была заданной толщины
        // при любом увеличении (лист вписан по ширине — масштаб около 3)
        return Brush.createWithColorIntArgb(family, color, sizeOf(effective) / MM_PER_DP * density / viewport.scale, BRUSH_EPSILON)
    }

    /** Матрица мир → экран. */
    fun viewMatrix(out: Matrix = Matrix()): Matrix = out.apply {
        setScale(viewport.scale, viewport.scale)
        postTranslate(viewport.tx, viewport.ty)
    }

    /** Содержимое (штрихи, страницы) изменилось — наследники сохраняют. */
    protected open fun onContentChanged() {}

    /** Растёт только при изменении содержимого (штрихи, страницы) — не при сдвиге и масштабе; по нему кэшируются тяжёлые данные. */
    var contentVersion = 0; private set

    private fun changed(content: Boolean) {
        version++
        if (content) contentVersion++
        if (content) onContentChanged()
    }

    companion object {
        /** Миллиметров в одном dp (160 dpi = 25,4 мм / 160). */
        const val MM_PER_DP = 0.15875f

        /** Толщина по умолчанию, мм (раньше: 2,5 / 6 / 18 / 4 dp). */
        val DEFAULT_SIZE_MM = mapOf(
            CanvasTool.PEN to 0.4f, CanvasTool.MARKER to 0.95f, CanvasTool.HIGHLIGHTER to 2.9f, CanvasTool.CALLIGRAPHY to 0.65f,
        )

        /** Диапазон толщины ползунка, мм; null — у инструмента нет толщины (ластик, лассо). */
        fun sizeRange(t: CanvasTool): ClosedFloatingPointRange<Float>? = when (t) {
            CanvasTool.PEN -> 0.1f..2f
            CanvasTool.MARKER -> 0.3f..4f
            CanvasTool.HIGHLIGHTER -> 1f..8f
            CanvasTool.CALLIGRAPHY -> 0.2f..3f
            else -> null
        }

        /** Шаг ползунка, мм. */
        fun sizeStep(t: CanvasTool): Float = if (t == CanvasTool.HIGHLIGHTER) 0.5f else 0.1f
        const val BRUSH_EPSILON = 0.1f
        const val PAGE_MARGIN_DP = 16f

        /** Границы безграничного холста: ±100 000 ед. ≈ ±20 м. Дальше штрихи не рисуются и вид не уходит. */
        const val WORLD_LIMIT = 100_000f

        /** Минимальный масштаб безграничного холста: экран шириной 3200 пикселей охватывает 160 000 ед. ≈ 32 м. */
        const val BOARD_MIN_SCALE = 0.02f

        fun newPageId(): String = UUID.randomUUID().toString().substring(0, 8)

        // Выделитель полупрозрачный; если пользователь задал свою прозрачность — берём её
        private fun highlighterColor(c: Int) = if (Color.alpha(c) < 255) c else Color.argb(0x66, Color.red(c), Color.green(c), Color.blue(c))
    }
}
