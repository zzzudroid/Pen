package com.zzzudroid.pen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.CanvasTool
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.PALETTE
import com.zzzudroid.pen.settings.AppSettings
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Кнопка толщины: на ней точка текущей толщины; по нажатию выпадает панель с образцом линии, ползунком (шаг, значение в мм)
 * и галочкой «Давление». Тянешь ползунок — толщина меняется сразу. У ластика и лассо толщины нет: кнопка блеклая.
 */
@Composable
fun BrushSizeButton(vm: CanvasViewModel, buttonSize: Dp) {
    var open by rememberSaveable { mutableStateOf(false) }
    val tool = vm.brushTool
    val range = CanvasViewModel.sizeRange(tool)
    val enabled = range != null
    val size = vm.sizeOf(tool)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(buttonSize)) {
        Box(
            Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .alpha(if (enabled) 1f else 0.35f)
                .clickable(enabled = enabled) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            // Точка на кнопке растёт вместе с толщиной (4…24 dp)
            val dot = if (range == null) 8.dp else (4f + 20f * (size - range.start) / (range.endInclusive - range.start)).dp
            Box(Modifier.size(dot).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
        }
        if (range != null) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                BrushPanel(vm, tool, size, range)
            }
        }
    }
}

@Composable
private fun BrushPanel(vm: CanvasViewModel, tool: CanvasTool, size: Float, range: ClosedFloatingPointRange<Float>) {
    Column(Modifier.width(320.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        StrokePreview(vm, tool, size)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val step = CanvasViewModel.sizeStep(tool)
            Slider(
                value = size,
                onValueChange = { vm.setSize(tool, it) },
                valueRange = range,
                steps = ((range.endInclusive - range.start) / step).toInt() - 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatMm(size),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp).width(84.dp),
            )
        }
        // Давление: выкл — толщина не зависит от нажима и скорости, линия одинаковая
        Row(
            Modifier.fillMaxWidth().clickable { AppSettings.putPressureOn(!AppSettings.pressureOn) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = AppSettings.pressureOn, onCheckedChange = { AppSettings.putPressureOn(it) })
            Text("Давление", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Образец: волнистая линия с заострёнными концами на «шахматном» фоне, цвет и толщина — как у пера. */
@Composable
private fun StrokePreview(vm: CanvasViewModel, tool: CanvasTool, sizeMm: Float) {
    val base = Color(vm.colorArgb)
    val color = if (tool == CanvasTool.HIGHLIGHTER && base.alpha == 1f) base.copy(alpha = 0.4f) else base
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
    ) {
        // Шахматный фон (светлый, чтобы любой цвет линии был виден)
        val cell = 12.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = 0f
            var col = row % 2
            while (x < size.width) {
                drawRect(if (col % 2 == 0) Color(0xFFE6E6E6) else Color.White, Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                x += cell; col++
            }
            y += cell; row++
        }
        // Линия: плотная цепочка отрезков с круглыми концами, толщина плавно сходит на нет к краям
        val widthPx = (sizeMm / CanvasViewModel.MM_PER_DP).dp.toPx().coerceAtLeast(1f)
        val n = 240
        val margin = 14.dp.toPx()
        fun point(i: Int): Offset {
            val t = i / n.toFloat()
            return Offset(margin + (size.width - 2 * margin) * t, size.height / 2 + sin(t * 2 * PI).toFloat() * size.height * 0.22f)
        }
        for (i in 0 until n) {
            val t = (i + 0.5f) / n
            val taper = max(sin(t * PI).toFloat(), 0.08f)
            drawLine(
                color, point(i), point(i + 1),
                strokeWidth = widthPx * (0.35f + 0.65f * taper),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/** «0,4 мм», «2 мм» — без лишних нулей. */
private fun formatMm(v: Float): String {
    val s = if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(java.util.Locale("ru"), "%.2f", v).trimEnd('0').trimEnd(',')
    return "$s мм"
}
