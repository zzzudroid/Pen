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
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.material.icons.automirrored.outlined.Label
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
import androidx.compose.runtime.LaunchedEffect
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
import com.zzzudroid.pen.canvas.Bounds
import com.zzzudroid.pen.canvas.DocLink
import com.zzzudroid.pen.canvas.DrawingSurface
import com.zzzudroid.pen.canvas.StrokeOps
import com.zzzudroid.pen.canvas.ViewState
import com.zzzudroid.pen.notes.DocIndex
import com.zzzudroid.pen.notes.LinkResolution
import com.zzzudroid.pen.notes.LinkRouting
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.notes.PdfPageCache
import java.io.File
import com.zzzudroid.pen.notes.NotebookStore
import com.zzzudroid.pen.notes.NotebookViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(onBack: () -> Unit, onNavigate: (String) -> Unit, vm: NotebookViewModel = viewModel()) {
    // Сохранение при сворачивании приложения и при уходе с экрана
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.saveNow()
            // Вернулись из другого документа (по ссылке): подхватить его правки, если он менял этот файл
            if (event == Lifecycle.Event.ON_RESUME) vm.reloadIfChangedOnDisk()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            vm.saveNow()
        }
    }

    // Не гасить экран, пока открыт редактор (настройка)
    val hostView = androidx.compose.ui.platform.LocalView.current
    val keepOn = com.zzzudroid.pen.settings.AppSettings.keepScreenOn
    DisposableEffect(keepOn) {
        hostView.keepScreenOn = keepOn
        onDispose { hostView.keepScreenOn = false }
    }

    var confirmDeletePage by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showBacklinks by remember { mutableStateOf(false) }
    val meta = vm.meta
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Ссылки: диалог выбора цели (1 — новая ссылка, 2 — сменить цель) и область новой ссылки
    var linkDialog by remember { mutableStateOf(0) }
    var linkArea by remember { mutableStateOf<Bounds?>(null) }
    var dimmed by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Недоступные цели ссылок рисуем приглушённо
    LaunchedEffect(vm.version, vm.pageIndex) {
        val links = vm.page.links
        dimmed = if (links.isEmpty()) emptySet() else {
            DocIndex.refresh(context)
            links.filter { DocIndex.find(it.target.docId) == null }.map { it.id }.toSet()
        }
    }

    // Переход по ссылке: в этом же документе — на месте, иначе открыть цель
    fun openLink(link: DocLink) {
        val t = link.target
        if (t.docId == vm.meta?.docId) {
            when {
                t.bookmarkId != null -> if (!vm.goToBookmark(t.bookmarkId)) vm.notice = "Закладка не найдена"
                t.view != null -> vm.showView(t.view)
            }
            return
        }
        scope.launch {
            DocIndex.refresh(context)
            when (val r = LinkRouting.resolve(t, DocIndex.find(t.docId))) {
                is LinkResolution.Open -> onNavigate(Routes.note(r.destination.fileName, 0, r.destination.bookmarkId, r.destination.view))
                is LinkResolution.Missing -> vm.notice = r.message
            }
        }
    }

    // Короткие сообщения модели («Закладка не найдена» и т. п.)
    LaunchedEffect(vm.notice) {
        vm.notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.notice = null
        }
    }

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
                    // Закладки — только у доски; обратные ссылки — у любого документа
                    if (meta != null && vm.isBoard) {
                        IconButton(onClick = { showBookmarks = true }) {
                            Icon(Icons.Outlined.Bookmarks, contentDescription = "Закладки")
                        }
                    }
                    if (meta != null) {
                        IconButton(onClick = { showTags = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = "Теги")
                        }
                    }
                    if (meta != null) {
                        IconButton(onClick = { showBacklinks = true }) {
                            Icon(Icons.Outlined.Link, contentDescription = "Откуда ссылаются")
                        }
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
                    SelectionBar(
                        vm,
                        onMakeLink = {
                            // Небольшое поле вокруг выделенного, чтобы попасть пальцем (1 мм = 5 ед.)
                            linkArea = StrokeOps.bounds(vm.selection)?.let { Bounds(it.left - 5f, it.top - 5f, it.right + 5f, it.bottom + 5f) }
                            if (linkArea != null) linkDialog = 1
                        },
                        onEditLink = { linkDialog = 2 },
                    )
                    AndroidView(
                        factory = { ctx ->
                            DrawingSurface(ctx, vm).also { surface ->
                                // Доске — точечная сетка и мини-карта для навигации
                                surface.showGrid = vm.isBoard && com.zzzudroid.pen.settings.AppSettings.boardGrid
                                surface.showMinimap = vm.isBoard && com.zzzudroid.pen.settings.AppSettings.boardMinimap && !(com.zzzudroid.pen.BuildConfig.DEBUG && com.zzzudroid.pen.canvas.DebugStress.noMinimap)
                                // Подложка страницы из исходного PDF; актуальные страница и источник читаются при каждой отрисовке
                                surface.pageBackground = { canvas, spec, scale, invalidate ->
                                    drawPdfBackground(canvas, spec, scale, vm.pdfSource, vm.page.pdfPage, invalidate)
                                }
                            }
                        },
                        // Чтение version подписывает перерисовку холста на изменения модели
                        update = { surface ->
                            vm.version
                            // Плавающая панель слева в полноэкранном режиме — мини-карта сдвигается правее неё
                            surface.minimapInsetStartDp = if (fullscreen) 68f else 0f
                            surface.onLinkClick = if (com.zzzudroid.pen.settings.AppSettings.linksByTap) ::openLink else null
                            surface.showGrid = vm.isBoard && com.zzzudroid.pen.settings.AppSettings.boardGrid
                            surface.showMinimap = vm.isBoard && com.zzzudroid.pen.settings.AppSettings.boardMinimap && !(com.zzzudroid.pen.BuildConfig.DEBUG && com.zzzudroid.pen.canvas.DebugStress.noMinimap)
                            surface.dimmedLinks = dimmed
                            surface.refresh()
                        },
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
                        // В разметке PDF чистые страницы не нужны, а кнопка рядом с «>» нажималась случайно
                        onAdd = if (vm.pdfSource == null) vm::addPageAfterCurrent else null,
                        onDelete = { confirmDeletePage = true },
                    )
                    }
                }
            }
            if (fullscreen && meta != null && vm.loadError == null) {
                FullscreenToolbar(
                    vm = vm,
                    onExit = { fullscreen = false },
                    modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
                )
            }
        }
    }

    if (linkDialog != 0) {
        LinkTargetDialog(
            currentDocId = meta?.docId,
            currentBookmarks = vm.bookmarks,
            currentView = if (vm.isBoard) ViewState(vm.viewport.scale, vm.viewport.tx, vm.viewport.ty) else null,
            onDismiss = { linkDialog = 0 },
            onPick = { target ->
                if (linkDialog == 1) linkArea?.let { vm.addLink(it, target) } else vm.retargetLinks(vm.selectedLinks(), target)
                linkDialog = 0
            },
        )
    }
    if (showTags) {
        // Теги всех документов — для подсказок; читаются при открытии диалога
        var known by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            known = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.zzzudroid.pen.notes.Tags.counts(NotebookStore.list().mapNotNull { it.meta?.tags })
            }
        }
        TagsDialog(
            title = "Теги: ${meta?.title.orEmpty()}",
            initial = meta?.tags.orEmpty(),
            known = known,
            onDismiss = { showTags = false },
            onSave = { vm.setTags(it); showTags = false },
        )
    }
    if (showBookmarks) BookmarksSheet(vm, onDismiss = { showBookmarks = false })
    if (showBacklinks) {
        meta?.docId?.let { docId ->
            BacklinksSheet(
                docId = docId,
                onOpen = { row ->
                    showBacklinks = false
                    onNavigate(Routes.note(row.fileName, page = row.page, focus = row.focusX to row.focusY))
                },
                onDismiss = { showBacklinks = false },
            )
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
private fun PageBar(index: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit, onAdd: (() -> Unit)?, onDelete: () -> Unit) {
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
        if (onAdd != null) {
            TextButton(onClick = onAdd) {
                Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Страница")
            }
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
