package com.zzzudroid.pen.canvas

import android.graphics.Color
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import java.util.UUID

/** Инструмент панели; id кистей хранится в файле тетради. */
enum class CanvasTool(val title: String, val id: String) {
    PEN("Ручка", "pen"),
    MARKER("Маркер", "marker"),
    HIGHLIGHTER("Выделитель", "highlighter"),
    ERASER("Ластик", "eraser"),
    LASSO("Лассо", "lasso"),
}

/** Команда истории холста. */
sealed interface CanvasCommand {
    class Add(val stroke: Stroke) : CanvasCommand
    class Erase(val strokes: List<Stroke>) : CanvasCommand

    /** Замена штрихов: перемещение, масштаб, перекраска выделения. */
    class Replace(val removed: List<Stroke>, val added: List<Stroke>) : CanvasCommand

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
object Brushes {
    val pen: BrushFamily by lazy { StockBrushes.pressurePen() }
    val marker: BrushFamily by lazy { StockBrushes.marker() }
    val highlighter: BrushFamily by lazy { StockBrushes.highlighter() }

    fun family(tool: CanvasTool): BrushFamily? = when (tool) {
        CanvasTool.PEN -> pen
        CanvasTool.MARKER -> marker
        CanvasTool.HIGHLIGHTER -> highlighter
        CanvasTool.ERASER, CanvasTool.LASSO -> null
    }

    /** Инструмент по семейству кисти штриха (сравнение по значению). */
    fun toolOf(family: BrushFamily): CanvasTool = when (family) {
        marker -> CanvasTool.MARKER
        highlighter -> CanvasTool.HIGHLIGHTER
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
    var colorIndex by mutableIntStateOf(0)
    var sizeIndex by mutableIntStateOf(1)
    /** Стирать пальцем: палец работает как штриховой ластик (перо при этом рисует по-прежнему). */
    var eraseWithFinger by mutableStateOf(false)

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
            is CanvasCommand.Replace -> { removeAll(c.added); strokes += c.removed }
            is CanvasCommand.AddMany -> removeAll(c.strokes)
        }
        changed(content = true)
    }

    fun redo() {
        val c = page.history.redo() ?: return
        selection = emptyList()
        when (c) {
            is CanvasCommand.Add -> strokes += c.stroke
            is CanvasCommand.Erase -> removeAll(c.strokes)
            is CanvasCommand.Replace -> { removeAll(c.removed); strokes += c.added }
            is CanvasCommand.AddMany -> strokes += c.strokes
        }
        changed(content = true)
    }

    private fun removeAll(list: List<Stroke>) {
        strokes.removeAll { s -> list.any { it === s } }
    }

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
    fun replaceSelection(replacement: List<Stroke>) {
        val old = selection
        if (old.isEmpty() || replacement.isEmpty()) return
        // Новые штрихи встают на место старых — порядок наложения сохраняется
        val firstIndex = strokes.indexOfFirst { s -> old.any { it === s } }.coerceAtLeast(0)
        removeAll(old)
        strokes.addAll(firstIndex.coerceAtMost(strokes.size), replacement)
        page.history.push(CanvasCommand.Replace(old, replacement))
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
        changed(content = false)
    }

    /** Восстановить сохранённый вид (доска открывается там, где её закрыли). */
    protected fun restoreView(scale: Float, tx: Float, ty: Float) {
        viewport.set(scale, tx, ty)
        changed(content = false)
    }

    /** Кисть для нового штриха. Размеры заданы в dp при масштабе 100%. */
    fun brush(density: Float, eraserOverride: Boolean = false): Brush? {
        if (eraserOverride) return null
        val family = Brushes.family(tool) ?: return null
        val base = PALETTE[colorIndex]
        val sizes = when (tool) {
            CanvasTool.MARKER -> MARKER_SIZES
            CanvasTool.HIGHLIGHTER -> HIGHLIGHTER_SIZES
            else -> PEN_SIZES
        }
        val color = if (tool == CanvasTool.HIGHLIGHTER) highlighterColor(base) else base
        // Размер хранится в единицах холста: делим на масштаб вида, чтобы на экране линия была заданной толщины
        // при любом увеличении (лист вписан по ширине — масштаб около 3)
        return Brush.createWithColorIntArgb(family, color, sizes[sizeIndex] * density / viewport.scale, BRUSH_EPSILON)
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
        val PEN_SIZES = listOf(1.2f, 2.5f, 5f)
        val MARKER_SIZES = listOf(3f, 6f, 12f)
        val HIGHLIGHTER_SIZES = listOf(10f, 18f, 28f)
        const val BRUSH_EPSILON = 0.1f
        const val PAGE_MARGIN_DP = 16f

        /** Границы безграничного холста: ±100 000 ед. ≈ ±20 м. Дальше штрихи не рисуются и вид не уходит. */
        const val WORLD_LIMIT = 100_000f

        /** Минимальный масштаб безграничного холста: экран шириной 3200 пикселей охватывает 160 000 ед. ≈ 32 м. */
        const val BOARD_MIN_SCALE = 0.02f

        fun newPageId(): String = UUID.randomUUID().toString().substring(0, 8)

        private fun highlighterColor(c: Int) = Color.argb(0x66, Color.red(c), Color.green(c), Color.blue(c))
    }
}
