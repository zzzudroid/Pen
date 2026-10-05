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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
            AndroidView(
                factory = { ctx -> DrawingSurface(ctx, vm) },
                // Чтение version подписывает перерисовку холста на изменения модели
                update = { surface -> vm.version; surface.refresh() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/** Панель инструментов холста — общая для «Холста» и тетрадей. */
@Composable
fun CanvasToolbar(vm: CanvasViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CanvasTool.entries.forEach { t ->
            FilterChip(selected = vm.tool == t, onClick = { vm.tool = t }, label = { Text(t.title) })
        }

        VerticalDivider(Modifier.padding(horizontal = 4.dp).size(1.dp, 32.dp))

        PALETTE.forEachIndexed { i, argb ->
            val selected = vm.colorIndex == i
            Box(
                Modifier
                    .size(32.dp)
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

        VerticalDivider(Modifier.padding(horizontal = 4.dp).size(1.dp, 32.dp))

        // Три толщины: точки разного размера
        listOf(8.dp, 14.dp, 20.dp).forEachIndexed { i, dot ->
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

        VerticalDivider(Modifier.padding(horizontal = 4.dp).size(1.dp, 32.dp))

        Text("Рисовать пальцем", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = vm.drawWithFinger, onCheckedChange = { vm.drawWithFinger = it })
    }
}
