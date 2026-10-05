package com.zzzudroid.pen.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzzudroid.pen.canvas.DrawingSurface
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.notes.PdfPageCache
import java.io.File
import com.zzzudroid.pen.notes.NotebookStore
import com.zzzudroid.pen.notes.NotebookViewModel
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Полноэкранный режим: рабочая область на весь экран, без панелей и системных полос
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    SystemBarsHidden(fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    Scaffold(
        topBar = {
            if (!fullscreen) TopAppBar(
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
                    if (vm.pdfError != null) {
                        Icon(
                            Icons.Outlined.PictureAsPdf,
                            contentDescription = "PDF не обновлён: ${vm.pdfError}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    // Сначала дописываем свежие изменения в .pen и PDF, потом отдаём файл
                    IconButton(onClick = {
                        scope.launch {
                            vm.saveAndAwait()
                            sharePdf(context, NotebookStore.pdfFile(vm.id))
                        }
                    }, enabled = meta != null) {
                        Icon(Icons.Outlined.Share, contentDescription = "Поделиться PDF")
                    }
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
                    }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Повторить")
                    }
                    // Страница целиком ↔ по ширине (у доски — вернуться в начало)
                    IconButton(onClick = vm::resetView) {
                        Icon(
                            Icons.Outlined.CropFree,
                            contentDescription = if (vm.isBoard) "Вернуться в начало" else "Страница целиком",
                        )
                    }
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Outlined.Fullscreen, contentDescription = "На весь экран")
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
                    if (!fullscreen) CanvasToolbar(vm)
                    SelectionBar(vm)
                    AndroidView(
                        factory = { ctx ->
                            DrawingSurface(ctx, vm).also { surface ->
                                // Доске — точечная сетка и мини-карта для навигации
                                surface.showGrid = vm.isBoard
                                surface.showMinimap = vm.isBoard
                                // Подложка страницы из исходного PDF; актуальные страница и источник читаются при каждой отрисовке
                                surface.pageBackground = { canvas, spec, scale, invalidate ->
                                    drawPdfBackground(canvas, spec, scale, vm.pdfSource, vm.page.pdfPage, invalidate)
                                }
                            }
                        },
                        // Чтение version подписывает перерисовку холста на изменения модели
                        update = { surface -> vm.version; surface.refresh() },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    // У доски нет страниц — панель страниц не нужна
                    if (!vm.isBoard && !fullscreen) {
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
            if (fullscreen && meta != null && vm.loadError == null) {
                FullscreenControls(
                    canUndo = vm.canUndo,
                    canRedo = vm.canRedo,
                    onUndo = vm::undo,
                    onRedo = vm::redo,
                    onExit = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                )
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

private val pdfSheetPaint = Paint().apply { color = android.graphics.Color.WHITE }
private val pdfBitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
private val pdfBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.rgb(0xC8, 0xC4, 0xBC)
    style = Paint.Style.STROKE
}

/** Нарисовать страницу PDF подложкой листа; false — у страницы нет PDF (рисуется шаблон). */
private fun drawPdfBackground(
    canvas: Canvas,
    spec: PageSpec,
    scale: Float,
    source: File?,
    pdfPage: Int?,
    invalidate: () -> Unit,
): Boolean {
    if (source == null || pdfPage == null) return false
    val rect = RectF(0f, 0f, spec.width, spec.height)
    canvas.drawRect(rect, pdfSheetPaint)
    val widthPx = (spec.width * scale).toInt().coerceAtLeast(1)
    PdfPageCache.get(source, pdfPage, widthPx, invalidate)?.let { canvas.drawBitmap(it, null, rect, pdfBitmapPaint) }
    pdfBorderPaint.strokeWidth = 1f / scale
    canvas.drawRect(rect, pdfBorderPaint)
    return true
}

/** Маленькая плавающая панель полноэкранного режима: отменить, повторить, выйти. */
@Composable
private fun FullscreenControls(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 3.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Отменить")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Повторить")
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Outlined.FullscreenExit, contentDescription = "Выйти из полноэкранного режима")
            }
        }
    }
}

/** Прячет системные полосы (статус и навигация) на время [hidden]; по свайпу от края появляются на миг. */
@Composable
private fun SystemBarsHidden(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hidden) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
