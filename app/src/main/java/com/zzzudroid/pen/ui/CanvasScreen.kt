package com.zzzudroid.pen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzzudroid.pen.canvas.CanvasTool
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.DrawingSurface
import com.zzzudroid.pen.canvas.PALETTE
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(onBack: () -> Unit, vm: CanvasViewModel = viewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Холст") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
                    }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Повторить")
                    }
                    // Текущий масштаб; нажатие — вернуть 100% и исходное положение
                    TextButton(onClick = vm::resetView) {
                        Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(vm.version.let { "${(vm.viewport.scale * 100).roundToInt()}%" })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CanvasToolbar(vm)
            SelectionBar(vm)
            AndroidView(
                factory = { ctx -> DrawingSurface(ctx, vm) },
                // Чтение version подписывает перерисовку холста на изменения модели
                update = { surface -> vm.version; surface.refresh() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/** Размер значка-кнопки панели. */
private val ToolButtonSize = 44.dp

/**
 * Панель инструментов холста — общая для «Холста» и тетрадей. Только значки, без слов и прокрутки:
 * инструменты, цвета, три толщины и переключатель «стирать пальцем».
 */
@Composable
fun CanvasToolbar(vm: CanvasViewModel) {
    Row(
        // Инструментов много: при нехватке ширины (портрет) панель прокручивается, а не обрезается
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CanvasTool.entries.forEach { t ->
            FilledIconToggleButton(
                checked = vm.tool == t,
                onCheckedChange = {
                    // Уход с лассо снимает выделение
                    if (t != CanvasTool.LASSO) vm.clearSelection()
                    vm.tool = t
                },
                modifier = Modifier.size(ToolButtonSize),
            ) {
                Icon(ToolIcons.of(t), contentDescription = t.title, modifier = Modifier.size(26.dp))
            }
        }

        ToolbarDivider()

        PALETTE.forEachIndexed { i, argb ->
            val selected = vm.colorIndex == i
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { vm.colorIndex = i; if (vm.tool == CanvasTool.ERASER) vm.tool = CanvasTool.PEN },
            )
        }

        ToolbarDivider()

        // Три толщины: точки разного размера
        listOf(7.dp, 12.dp, 18.dp).forEachIndexed { i, dot ->
            val selected = vm.sizeIndex == i
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { vm.sizeIndex = i },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(dot).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
            }
        }

        ToolbarDivider()

        // «Стирать пальцем»: включено — палец стирает как ластик; выключено — палец листает, перо рисует
        FilledIconToggleButton(
            checked = vm.eraseWithFinger,
            onCheckedChange = { vm.eraseWithFinger = it },
            modifier = Modifier.size(ToolButtonSize),
        ) {
            Icon(Icons.Outlined.TouchApp, contentDescription = "Стирать пальцем", modifier = Modifier.size(26.dp))
        }
        FilledIconToggleButton(
            checked = vm.viewLocked,
            onCheckedChange = { vm.viewLocked = it },
            modifier = Modifier.size(ToolButtonSize),
        ) {
            Icon(if (vm.viewLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, contentDescription = "Заблокировать перемещение поля", modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(Modifier.padding(horizontal = 2.dp).size(1.dp, 32.dp))
}
