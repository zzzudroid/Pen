package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzzudroid.pen.canvas.DrawingSurface
import com.zzzudroid.pen.notes.NotebookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(onBack: () -> Unit, vm: NotebookViewModel = viewModel()) {
    // Сохранение при сворачивании приложения и при уходе с экрана
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.saveNow()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.saveNow()
        }
    }

    var confirmDeletePage by remember { mutableStateOf(false) }
    val meta = vm.meta

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meta?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (vm.saveError != null) {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = "Не сохранено: ${vm.saveError}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
                    }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Повторить")
                    }
                    IconButton(onClick = vm::resetView) {
                        Icon(Icons.Outlined.FitScreen, contentDescription = "Вписать страницу")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                vm.loadError != null -> Text(
                    "Не удалось открыть тетрадь: ${vm.loadError}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                meta == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize()) {
                    CanvasToolbar(vm)
                    AndroidView(
                        factory = { ctx -> DrawingSurface(ctx, vm) },
                        // Чтение version подписывает перерисовку холста на изменения модели
                        update = { surface -> vm.version; surface.refresh() },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    HorizontalDivider()
                    PageBar(
                        index = vm.pageIndex,
                        count = vm.version.let { vm.pages.size },
                        onPrev = { vm.goToPage(vm.pageIndex - 1) },
                        onNext = { vm.goToPage(vm.pageIndex + 1) },
                        onAdd = vm::addPageAfterCurrent,
                        onDelete = { confirmDeletePage = true },
                    )
                }
            }
        }
    }

    if (confirmDeletePage) {
        AlertDialog(
            onDismissRequest = { confirmDeletePage = false },
            title = { Text("Удалить страницу ${vm.pageIndex + 1}?") },
            text = { Text("Страница и всё, что на ней нарисовано, будут удалены.") },
            confirmButton = {
                TextButton(onClick = { confirmDeletePage = false; vm.deleteCurrentPage() }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePage = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PageBar(index: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit, onAdd: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onDelete, enabled = count > 1) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Удалить страницу")
        }
        Spacer(Modifier.width(16.dp))
        IconButton(onClick = onPrev, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Outlined.NavigateBefore, contentDescription = "Предыдущая страница")
        }
        Text("${index + 1} / $count", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = index < count - 1) {
            Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = "Следующая страница")
        }
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = onAdd) {
            Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Страница")
        }
    }
}
