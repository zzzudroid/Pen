package com.zzzudroid.pen.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.CanvasTool

/**
 * Векторные значки инструментов холста (в наборе Material нет ластика, маркера и выделителя).
 * Все — на сетке 24×24, одним цветом: итоговый цвет задаёт Icon через tint.
 */
object ToolIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private val fill = SolidColor(Color.Black)

    /** Ручка: перо с прорезью и отверстием. */
    val Pen: ImageVector by lazy {
        icon("Pen") {
            path(fill = fill, pathFillType = PathFillType.EvenOdd) {
                // Корпус пера
                moveTo(12f, 2f)
                curveTo(14.6f, 5.6f, 16.6f, 8.8f, 16.6f, 12.4f)
                lineTo(12f, 22f)
                lineTo(7.4f, 12.4f)
                curveTo(7.4f, 8.8f, 9.4f, 5.6f, 12f, 2f)
                close()
                // Отверстие
                moveTo(12f, 9.6f)
                curveTo(11.1f, 9.6f, 10.4f, 10.3f, 10.4f, 11.2f)
                curveTo(10.4f, 12.1f, 11.1f, 12.8f, 12f, 12.8f)
                curveTo(12.9f, 12.8f, 13.6f, 12.1f, 13.6f, 11.2f)
                curveTo(13.6f, 10.3f, 12.9f, 9.6f, 12f, 9.6f)
                close()
                // Прорезь
                moveTo(11.6f, 12.6f)
                lineTo(12.4f, 12.6f)
                lineTo(12.4f, 19f)
                lineTo(11.6f, 19f)
                close()
            }
        }
    }

    /** Маркер: корпус с окошком и тонким наконечником. */
    val Marker: ImageVector by lazy {
        icon("Marker") {
            path(fill = fill, pathFillType = PathFillType.EvenOdd) {
                // Корпус
                moveTo(8f, 2f)
                lineTo(16f, 2f)
                lineTo(16f, 14f)
                lineTo(8f, 14f)
                close()
                // Окошко
                moveTo(9.6f, 5f)
                lineTo(14.4f, 5f)
                lineTo(14.4f, 8f)
                lineTo(9.6f, 8f)
                close()
            }
            path(fill = fill) {
                // Наконечник
                moveTo(8.6f, 15.2f)
                lineTo(15.4f, 15.2f)
                lineTo(13.6f, 20.4f)
                lineTo(12f, 22f)
                lineTo(10.4f, 20.4f)
                close()
            }
        }
    }

    /** Выделитель: широкий скошенный наконечник и цветная полоса под ним. */
    val Highlighter: ImageVector by lazy {
        icon("Highlighter") {
            path(fill = fill) {
                // Корпус
                moveTo(7f, 2f)
                lineTo(17f, 2f)
                lineTo(17f, 11f)
                lineTo(7f, 11f)
                close()
                // Скошенный наконечник
                moveTo(7f, 12.2f)
                lineTo(17f, 12.2f)
                lineTo(14.6f, 17f)
                lineTo(9.4f, 17f)
                close()
                // Полоса выделения
                moveTo(4.5f, 19f)
                lineTo(19.5f, 19f)
                lineTo(19.5f, 22f)
                lineTo(4.5f, 22f)
                close()
            }
        }
    }

    /** Ластик: скошенный брусок с полосой и линия под ним. */
    val Eraser: ImageVector by lazy {
        icon("Eraser") {
            path(fill = fill, pathFillType = PathFillType.EvenOdd) {
                // Брусок
                moveTo(15.4f, 3.2f)
                lineTo(20.8f, 8.6f)
                lineTo(10.4f, 19f)
                lineTo(5f, 13.6f)
                close()
                // Полоса, отделяющая стирающую часть
                moveTo(10.7f, 8.0f)
                lineTo(16.0f, 13.3f)
                lineTo(14.9f, 14.4f)
                lineTo(9.6f, 9.1f)
                close()
            }
            path(fill = fill) {
                // Линия-«стол» под ластиком
                moveTo(11f, 20.4f)
                lineTo(21f, 20.4f)
                lineTo(21f, 22f)
                lineTo(11f, 22f)
                close()
            }
        }
    }

    /** Каллиграфия: плоское перо — широкий скошенный наконечник на ручке. */
    val Calligraphy: ImageVector by lazy {
        icon("Calligraphy") {
            path(fill = fill, pathFillType = PathFillType.EvenOdd) {
                // Скошенный плоский наконечник
                moveTo(5f, 21f)
                lineTo(8.2f, 11.2f)
                lineTo(12.8f, 15.8f)
                close()
                // Ручка
                moveTo(10f, 9.4f)
                lineTo(17.8f, 1.6f)
                lineTo(22.4f, 6.2f)
                lineTo(14.6f, 14f)
                close()
            }
        }
    }

    fun of(tool: CanvasTool): ImageVector = when (tool) {
        CanvasTool.PEN -> Pen
        CanvasTool.CALLIGRAPHY -> Calligraphy
        CanvasTool.MARKER -> Marker
        CanvasTool.HIGHLIGHTER -> Highlighter
        CanvasTool.ERASER -> Eraser
        CanvasTool.LASSO -> Icons.Outlined.HighlightAlt
    }
}
