package com.zzzudroid.pen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.CanvasTool
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.ColorMath
import com.zzzudroid.pen.settings.AppSettings

/**
 * Один из трёх кружков-пресетов цвета. Короткое нажатие выбирает пресет, долгое — выдвигает панель настройки цвета
 * (сетка тонов или полосы, прозрачность, пипетка, образец).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ColorPresetButton(vm: CanvasViewModel, index: Int, buttonSize: Dp, circle: Dp = 28.dp) {
    var open by rememberSaveable { mutableStateOf(false) }
    val argb = AppSettings.presets[index]
    val active = AppSettings.activePreset == index
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(buttonSize)) {
        Box(
            Modifier
                .size(circle)
                .clip(CircleShape)
                .background(Color.White)
                // Цвет с прозрачностью рисуется поверх белого — виден реальный оттенок
                .background(Color(argb))
                .border(
                    width = if (active) 3.dp else 1.dp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
                .combinedClickable(
                    // Короткое нажатие — просто выбрать пресет; долгое — выдвинуть панель настройки цвета
                    onClick = {
                        AppSettings.putActivePreset(index)
                        // Выбрать цвет — значит рисовать им: из ластика переходим на ручку
                        if (vm.tool == CanvasTool.ERASER) vm.tool = CanvasTool.PEN
                    },
                    onLongClick = {
                        AppSettings.putActivePreset(index)
                        if (vm.tool == CanvasTool.ERASER) vm.tool = CanvasTool.PEN
                        open = true
                    },
                ),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ColorPanel(vm, index, onEyedropper = { open = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPanel(vm: CanvasViewModel, index: Int, onEyedropper: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val argb = AppSettings.presets[index]

    Column(Modifier.width(360.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Вкладки: сетка / полосы
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Сетка") }, modifier = Modifier.weight(1f))
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Полосы прокрутки", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.weight(2.2f))
        }

        if (tab == 0) {
            Text("Тон", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            ColorGrid(argb) { picked -> AppSettings.putPreset(index, ColorMath.withRgb(argb, picked)) }
        } else {
            HsvSliders(argb) { AppSettings.putPreset(index, it) }
        }

        // Прозрачность
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Прозрачность", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${ColorMath.opacityPercent(argb)} %", style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = ColorMath.opacityPercent(argb).toFloat(),
            onValueChange = { AppSettings.putPreset(index, ColorMath.withOpacity(argb, it.toInt())) },
            valueRange = 0f..100f,
        )

        // Пипетка и образец
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                vm.eyedropper = index
                onEyedropper()
                android.widget.Toast.makeText(context, "Коснитесь пальцем штриха, цвет которого взять", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Outlined.Colorize, contentDescription = "Пипетка") }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(64.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .background(Color(argb))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            )
        }
    }
}

/** Сетка тонов: [ColorMath.GRID_HUES] цветов и столбец серых, ряды от светлых к тёмным. Нажатие отдаёт RGB ячейки. */
@Composable
private fun ColorGrid(current: Int, onPick: (Int) -> Unit) {
    val cols = ColorMath.GRID_COLUMNS
    val rows = ColorMath.GRID_ROWS
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(cols / rows.toFloat())
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val c = (off.x / size.width * cols).toInt().coerceIn(0, cols - 1)
                    val r = (off.y / size.height * rows).toInt().coerceIn(0, rows - 1)
                    onPick(ColorMath.gridColor(c, r))
                }
            },
    ) {
        // Ячейки рисуются без швов: каждая чуть заходит на соседнюю
        val cw = size.width / cols
        val ch = size.height / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = ColorMath.gridColor(c, r)
                drawRect(Color(cell), Offset(c * cw, r * ch), Size(cw + 1f, ch + 1f))
                if ((cell and 0xFFFFFF) == (current and 0xFFFFFF)) {
                    drawRect(Color.White, Offset(c * cw + 1.5f, r * ch + 1.5f), Size(cw - 3f, ch - 3f), style = Stroke(width = 3f))
                }
            }
        }
    }
}

/** Вкладка «Полосы прокрутки»: тон, насыщенность и яркость ползунками. */
@Composable
private fun HsvSliders(argb: Int, onChange: (Int) -> Unit) {
    val hsv = ColorMath.argbToHsv(argb)
    fun update(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) =
        onChange(ColorMath.hsvToArgb(h, s, v, argb ushr 24))
    Column(Modifier.padding(top = 12.dp)) {
        SliderRow("Тон", hsv[0], 0f..360f) { update(h = it) }
        SliderRow("Насыщенность", hsv[1] * 100f, 0f..100f) { update(s = it / 100f) }
        SliderRow("Яркость", hsv[2] * 100f, 0f..100f) { update(v = it / 100f) }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value.toInt().toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}
