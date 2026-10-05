package com.zzzudroid.pen.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zzzudroid.pen.canvas.PageTemplate
import com.zzzudroid.pen.notes.NotebookEntry
import com.zzzudroid.pen.notes.NotebookFormat
import com.zzzudroid.pen.notes.NotebookStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Доступ к файлам перепроверяем при каждом возвращении на экран (например, из настроек)
    var hasAccess by remember { mutableStateOf(NotebookStore.hasAccess()) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = NotebookStore.hasAccess()
                refresh++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var entries by remember { mutableStateOf<List<NotebookEntry>?>(null) }
    LaunchedEffect(hasAccess, refresh) {
        if (hasAccess) entries = withContext(Dispatchers.IO) { NotebookStore.list() }
    }

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<NotebookEntry?>(null) }
    var deleting by remember { mutableStateOf<NotebookEntry?>(null) }

    // Выполнить операцию с хранилищем на IO и показать ошибку, если она случилась
    fun storeOp(action: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
            } catch (e: Exception) {
                snackbar.showSnackbar("Ошибка: ${e.message}")
            }
            refresh++
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Заметки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasAccess) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Новая тетрадь") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasAccess -> AccessRequest(onGrant = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                })
                entries == null -> Unit
                entries!!.isEmpty() -> Text(
                    "Тетрадей пока нет — создайте первую",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(entries!!, key = { it.id }) { e ->
                        NotebookCard(
                            entry = e,
                            onOpen = { if (e.meta != null) onOpen(e.id) },
                            onRename = { renaming = e },
                            onDelete = { deleting = e },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        CreateDialog(
            onDismiss = { creating = false },
            onCreate = { title, template ->
                creating = false
                scope.launch {
                    try {
                        val id = withContext(Dispatchers.IO) { NotebookStore.create(title, template) }
                        onOpen(id)
                    } catch (e: Exception) {
                        snackbar.showSnackbar("Не удалось создать: ${e.message}")
                    }
                }
            },
        )
    }

    renaming?.let { e ->
        TitleDialog(
            title = "Переименовать",
            initial = e.meta?.title ?: e.file.nameWithoutExtension,
            confirm = "Сохранить",
            onDismiss = { renaming = null },
            onConfirm = { newTitle ->
                renaming = null
                storeOp { NotebookStore.rename(e.id, newTitle) }
            },
        )
    }

    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить тетрадь?") },
            text = { Text("«${e.meta?.title ?: e.id}» будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    storeOp { NotebookStore.delete(e.id) }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AccessRequest(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Нужен доступ к файлам", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Тетради хранятся обычными файлами в папке Documents/Pen — так их можно забрать на компьютер " +
                "и они не пропадут при переустановке приложения. Для этого Android требует разрешение " +
                "«Доступ ко всем файлам».",
            modifier = Modifier.widthIn(max = 560.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onGrant) { Text("Разрешить доступ") }
    }
}

@Composable
private fun NotebookCard(entry: NotebookEntry, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val thumb by produceState<Bitmap?>(null, entry.file, entry.meta?.modified) {
        value = withContext(Dispatchers.IO) { NotebookFormat.readThumbnail(entry.file) }
    }
    var menu by remember { mutableStateOf(false) }

    Card(onClick = onOpen) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(210f / 297f).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (entry.meta == null) Text("Файл повреждён", color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.meta?.title ?: entry.id, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    entry.meta?.let { "${it.pageIds.size} стр. · ${formatDate(it.modified)}" } ?: (entry.error ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Действия") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (entry.meta != null) {
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                    }
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CreateDialog(onDismiss: () -> Unit, onCreate: (String, PageTemplate) -> Unit) {
    var title by remember { mutableStateOf("Тетрадь " + SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date())) }
    var template by remember { mutableStateOf(PageTemplate.LINED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая тетрадь") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название") }, singleLine = true)
                Text("Страницы", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageTemplate.entries.forEach { t ->
                        FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.title) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(title, template) }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun TitleDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(millis))
