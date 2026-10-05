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
}

/** Команда истории холста. */
sealed interface CanvasCommand {
    class Add(val stroke: Stroke) : CanvasCommand
    class Erase(val strokes: List<Stroke>) : CanvasCommand
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
        CanvasTool.ERASER -> null
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

    /** Границы и шаблон страниц; null — безграничный холст. */
    var pageSpec: PageSpec? = null; protected set

    val viewport = Viewport()
    private var viewWidth = 0
    private var viewHeight = 0
    private var density = 1f

    // Счётчик изменений: Compose по нему обновляет кнопки и перерисовывает холст
    var version by mutableIntStateOf(0); private set

    var tool by mutableStateOf(CanvasTool.PEN)
    var colorIndex by mutableIntStateOf(0)
    var sizeIndex by mutableIntStateOf(1)
    var drawWithFinger by mutableStateOf(false)

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
        page.history.push(CanvasCommand.Erase(erased))
        changed(content = true)
    }

    /** Удалить штрих из модели во время движения ластика (команда — в commitErase). */
    fun removeLive(stroke: Stroke) {
        strokes.removeAll { it === stroke }
    }

    fun undo() {
        when (val c = page.history.undo() ?: return) {
            is CanvasCommand.Add -> strokes.removeAll { it === c.stroke }
            is CanvasCommand.Erase -> strokes += c.strokes
        }
        changed(content = true)
    }

    fun redo() {
        when (val c = page.history.redo() ?: return) {
            is CanvasCommand.Add -> strokes += c.stroke
            is CanvasCommand.Erase -> c.strokes.forEach { s -> strokes.removeAll { it === s } }
        }
        changed(content = true)
    }

    // --- Страницы ---

    fun goToPage(index: Int) {
        if (index !in pages.indices || index == pageIndex) return
        pageIndex = index
        fitPage()
        changed(content = false)
    }

    /** Добавить пустую страницу после текущей и перейти на неё. */
    fun addPageAfterCurrent() {
        pages.add(pageIndex + 1, PageState(newPageId()))
        pageIndex++
        fitPage()
        changed(content = true)
    }

    /** Удалить текущую страницу (последнюю удалить нельзя). */
    fun deleteCurrentPage(): Boolean {
        if (pages.size <= 1) return false
        pages.removeAt(pageIndex)
        if (pageIndex >= pages.size) pageIndex = pages.size - 1
        fitPage()
        changed(content = true)
        return true
    }

    /** Заменить содержимое (загрузка тетради). */
    protected fun replacePages(spec: PageSpec?, newPages: List<PageState>) {
        pageSpec = spec
        pages.clear()
        pages += newPages.ifEmpty { listOf(PageState(newPageId())) }
        pageIndex = 0
        fitPage()
        changed(content = false)
    }

    // --- Вид ---

    /** Размер поверхности рисования изменился (первый показ, поворот). */
    fun onViewSize(width: Int, height: Int, density: Float) {
        viewWidth = width
        viewHeight = height
        this.density = density
        fitPage()
        changed(content = false)
    }

    /** Вписать лист по ширине с полями и прижать к верху. Без страницы — ничего. */
    fun fitPage() {
        val spec = pageSpec ?: return
        if (viewWidth == 0) return
        val margin = PAGE_MARGIN_DP * density
        val scale = (viewWidth - 2 * margin) / spec.width
        viewport.set(scale, (viewWidth - spec.width * scale) / 2, margin)
    }

    fun resetView() {
        if (pageSpec != null) fitPage() else viewport.reset()
        changed(content = false)
    }

    fun viewportChanged() = changed(content = false)

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
        return Brush.createWithColorIntArgb(family, color, sizes[sizeIndex] * density, BRUSH_EPSILON)
    }

    /** Матрица мир → экран. */
    fun viewMatrix(out: Matrix = Matrix()): Matrix = out.apply {
        setScale(viewport.scale, viewport.scale)
        postTranslate(viewport.tx, viewport.ty)
    }

    /** Содержимое (штрихи, страницы) изменилось — наследники сохраняют. */
    protected open fun onContentChanged() {}

    private fun changed(content: Boolean) {
        version++
        if (content) onContentChanged()
    }

    companion object {
        val PEN_SIZES = listOf(1.2f, 2.5f, 5f)
        val MARKER_SIZES = listOf(3f, 6f, 12f)
        val HIGHLIGHTER_SIZES = listOf(10f, 18f, 28f)
        const val BRUSH_EPSILON = 0.1f
        const val PAGE_MARGIN_DP = 16f

        fun newPageId(): String = UUID.randomUUID().toString().substring(0, 8)

        private fun highlighterColor(c: Int) = Color.argb(0x66, Color.red(c), Color.green(c), Color.blue(c))
    }
}
