package com.zzzudroid.pen.canvas

import android.graphics.Color
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel

/** Инструмент панели. */
enum class CanvasTool(val title: String) {
    PEN("Ручка"),
    MARKER("Маркер"),
    HIGHLIGHTER("Выделитель"),
    ERASER("Ластик"),
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

/**
 * Состояние холста: штрихи в мировых координатах, история, вид и текущая кисть.
 * Живёт в ViewModel, чтобы пережить поворот экрана.
 */
class CanvasViewModel : ViewModel() {

    val strokes = mutableListOf<Stroke>()
    val viewport = Viewport()
    private val history = History<CanvasCommand>()

    // Счётчик изменений: Compose по нему обновляет кнопки и перерисовывает холст
    var version by mutableIntStateOf(0); private set

    var tool by mutableStateOf(CanvasTool.PEN)
    var colorIndex by mutableIntStateOf(0)
    var sizeIndex by mutableIntStateOf(1)
    var drawWithFinger by mutableStateOf(false)

    val canUndo get() = version.let { history.canUndo }
    val canRedo get() = version.let { history.canRedo }

    fun addStroke(stroke: Stroke) {
        strokes += stroke
        history.push(CanvasCommand.Add(stroke))
        changed()
    }

    /** Стёртые одним движением ластика штрихи — одна команда. */
    fun commitErase(erased: List<Stroke>) {
        if (erased.isEmpty()) return
        history.push(CanvasCommand.Erase(erased))
        changed()
    }

    /** Удалить штрих из модели во время движения ластика (команда — в commitErase). */
    fun removeLive(stroke: Stroke) {
        strokes.removeAll { it === stroke }
    }

    fun undo() {
        when (val c = history.undo() ?: return) {
            is CanvasCommand.Add -> strokes.removeAll { it === c.stroke }
            is CanvasCommand.Erase -> strokes += c.strokes
        }
        changed()
    }

    fun redo() {
        when (val c = history.redo() ?: return) {
            is CanvasCommand.Add -> strokes += c.stroke
            is CanvasCommand.Erase -> c.strokes.forEach { s -> strokes.removeAll { it === s } }
        }
        changed()
    }

    fun resetView() {
        viewport.reset()
        changed()
    }

    fun viewportChanged() = changed()

    /** Кисть для нового штриха. density — плотность экрана, размеры заданы в dp при масштабе 100%. */
    fun brush(density: Float, eraserOverride: Boolean = false): Brush? {
        if (eraserOverride || tool == CanvasTool.ERASER) return null
        val base = PALETTE[colorIndex]
        val (family, sizesDp, color) = when (tool) {
            CanvasTool.PEN -> Triple(StockBrushes.pressurePen(), PEN_SIZES, base)
            CanvasTool.MARKER -> Triple(StockBrushes.marker(), MARKER_SIZES, base)
            CanvasTool.HIGHLIGHTER -> Triple(StockBrushes.highlighter(), HIGHLIGHTER_SIZES, highlighterColor(base))
            CanvasTool.ERASER -> return null
        }
        return Brush.createWithColorIntArgb(family, color, sizesDp[sizeIndex] * density, BRUSH_EPSILON)
    }

    /** Матрица мир → экран. */
    fun viewMatrix(out: Matrix = Matrix()): Matrix = out.apply {
        setScale(viewport.scale, viewport.scale)
        postTranslate(viewport.tx, viewport.ty)
    }

    private fun changed() {
        version++
    }

    companion object {
        val PEN_SIZES = listOf(1.2f, 2.5f, 5f)
        val MARKER_SIZES = listOf(3f, 6f, 12f)
        val HIGHLIGHTER_SIZES = listOf(10f, 18f, 28f)
        const val BRUSH_EPSILON = 0.1f

        private fun highlighterColor(c: Int) = Color.argb(0x66, Color.red(c), Color.green(c), Color.blue(c))
    }
}
