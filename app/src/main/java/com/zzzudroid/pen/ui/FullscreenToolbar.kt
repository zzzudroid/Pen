package com.zzzudroid.pen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.CanvasTool
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.PALETTE

private val BarWidth = 52.dp
private val ButtonSize = 40.dp

/**
 * Тонкая плавающая панель инструментов у левого края для полноэкранного режима.
 * Слева — потому что перо в правой руке и ладонь закрывает правую часть экрана.
 * Цвет и толщина свёрнуты в по одной кнопке (список выпадает по нажатию), чтобы столбец помещался
 * по высоте даже в альбомной ориентации; всю панель можно свернуть в маленький значок.
 */
@Composable
fun FullscreenToolbar(vm: CanvasViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 3.dp,
    ) {
        if (expanded) {
            Column(
                modifier = Modifier.width(BarWidth).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { expanded = false }, modifier = Modifier.size(ButtonSize)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Свернуть панель")
                }
                CanvasTool.entries.forEach { t ->
                    FilledIconToggleButton(
                        checked = vm.tool == t,
                        onCheckedChange = {
                            // Уход с лассо снимает выделение
                            if (t != CanvasTool.LASSO) vm.clearSelection()
                            vm.tool = t
                        },
                        modifier = Modifier.size(ButtonSize),
                    ) {
                        Icon(ToolIcons.of(t), contentDescription = t.title, modifier = Modifier.size(24.dp))
                    }
                }
                BarDivider()
                ColorMenuButton(vm)
                SizeMenuButton(vm)
                FilledIconToggleButton(
                    checked = vm.eraseWithFinger,
                    onCheckedChange = { vm.eraseWithFinger = it },
                    modifier = Modifier.size(ButtonSize),
                ) {
                    Icon(Icons.Outlined.TouchApp, contentDescription = "Стирать пальцем", modifier = Modifier.size(24.dp))
                }
                FilledIconToggleButton(
                    checked = vm.viewLocked,
                    onCheckedChange = { vm.viewLocked = it },
                    modifier = Modifier.size(ButtonSize),
                ) {
                    Icon(if (vm.viewLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, contentDescription = "Заблокировать перемещение поля", modifier = Modifier.size(24.dp))
                }
                BarDivider()
                IconButton(onClick = vm::undo, enabled = vm.canUndo, modifier = Modifier.size(ButtonSize)) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
                }
                IconButton(onClick = vm::redo, enabled = vm.canRedo, modifier = Modifier.size(ButtonSize)) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Повторить")
                }
                IconButton(onClick = onExit, modifier = Modifier.size(ButtonSize)) {
                    Icon(Icons.Outlined.FullscreenExit, contentDescription = "Выйти из полноэкранного режима")
                }
            }
        } else {
            // Свёрнуто: значок текущего инструмента (раскрывает панель) и выход
            Column(
                modifier = Modifier.width(BarWidth).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(ButtonSize)) {
                    Icon(ToolIcons.of(vm.tool), contentDescription = "Развернуть панель", modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onExit, modifier = Modifier.size(ButtonSize)) {
                    Icon(Icons.Outlined.FullscreenExit, contentDescription = "Выйти из полноэкранного режима")
                }
            }
        }
    }
}

@Composable
private fun BarDivider() {
    HorizontalDivider(Modifier.width(28.dp))
}

/** Кнопка текущего цвета; по нажатию выпадает ряд из шести цветов. */
@Composable
private fun ColorMenuButton(vm: CanvasViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ButtonSize)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(PALETTE[vm.colorIndex]))
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PALETTE.forEachIndexed { i, argb ->
                    val selected = vm.colorIndex == i
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable {
                                vm.colorIndex = i
                                if (vm.tool == CanvasTool.ERASER) vm.tool = CanvasTool.PEN
                                open = false
                            },
                    )
                }
            }
        }
    }
}

/** Кнопка текущей толщины; по нажатию выпадает ряд из трёх толщин. */
@Composable
private fun SizeMenuButton(vm: CanvasViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    val dots = listOf(7.dp, 12.dp, 18.dp)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ButtonSize)) {
        Box(
            Modifier.size(ButtonSize).clip(CircleShape).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(dots[vm.sizeIndex]).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dots.forEachIndexed { i, dot ->
                    val selected = vm.sizeIndex == i
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { vm.sizeIndex = i; open = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(dot).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                    }
                }
            }
        }
    }
}
