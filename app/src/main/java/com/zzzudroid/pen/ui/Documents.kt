package com.zzzudroid.pen.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zzzudroid.pen.canvas.PageTemplate
import com.zzzudroid.pen.notes.HomeLogic
import com.zzzudroid.pen.notes.NotebookEntry
import com.zzzudroid.pen.notes.NotebookStore
import com.zzzudroid.pen.notes.importPdf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Общая логика экранов со списками документов (главный экран и полные списки): доступ к файлам,
 * загрузка списка, быстрое создание без диалога, импорт PDF, переименование и удаление.
 */
class DocumentsState(
    private val scope: CoroutineScope,
    private val context: Context,
    val snackbar: SnackbarHostState,
) {
    /** Есть ли доступ ко всем файлам; перепроверяется при каждом возвращении на экран. */
    var hasAccess by mutableStateOf(NotebookStore.hasAccess())
    var refresh by mutableIntStateOf(0)

    /** Список документов; null — ещё загружается. */
    var entries by mutableStateOf<List<NotebookEntry>?>(null)

    var importing by mutableStateOf(false)
    var renaming by mutableStateOf<NotebookEntry?>(null)
    var deleting by mutableStateOf<NotebookEntry?>(null)

    /** Выполнить операцию с хранилищем на IO, показать ошибку, если она случилась, и обновить список. */
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

    /** Создать документ сразу, без диалога: имя — сегодняшняя дата, у тетради шаблон «линейка»; затем открыть. */
    fun createNow(board: Boolean, onOpen: (String) -> Unit) {
        scope.launch {
            try {
                val title = HomeLogic.todayTitle()
                val id = withContext(Dispatchers.IO) {
                    if (board) NotebookStore.createBoard(title) else NotebookStore.create(title, com.zzzudroid.pen.settings.AppSettings.newNoteTemplate)
                }
                onOpen(id)
            } catch (e: Exception) {
                snackbar.showSnackbar("Не удалось создать: ${e.message}")
            }
        }
    }

    /** Импорт выбранного PDF в тетрадь и открытие её. */
    fun importPdf(uri: Uri, onOpen: (String) -> Unit) {
        importing = true
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) { NotebookStore.importPdf(context, uri, pdfTitle(context, uri)) }
                onOpen(id)
            } catch (e: Exception) {
                snackbar.showSnackbar("Не удалось открыть PDF: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                importing = false
            }
        }
    }
}

@Composable
fun rememberDocumentsState(snackbar: SnackbarHostState): DocumentsState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { DocumentsState(scope, context, snackbar) }

    // Доступ к файлам перепроверяем при каждом возвращении на экран (например, из настроек)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.hasAccess = NotebookStore.hasAccess()
                state.refresh++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.hasAccess, state.refresh) {
        if (state.hasAccess) state.entries = withContext(Dispatchers.IO) { NotebookStore.list() }
    }
    return state
}

/** Запуск системного выбора PDF; после выбора файл импортируется и открывается через [onOpen]. */
@Composable
fun rememberPdfPicker(state: DocumentsState, onOpen: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.importPdf(uri, onOpen)
    }
    return { if (!state.importing) launcher.launch(arrayOf("application/pdf")) }
}

/** Диалоги: импорт PDF в процессе, переименование и удаление. */
@Composable
fun DocumentDialogs(state: DocumentsState) {
    if (state.importing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Импорт PDF…") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }

    state.renaming?.let { e ->
        TitleDialog(
            title = "Переименовать",
            initial = e.meta?.title ?: e.file.nameWithoutExtension,
            confirm = "Сохранить",
            onDismiss = { state.renaming = null },
            onConfirm = { newTitle ->
                state.renaming = null
                state.storeOp { NotebookStore.rename(e.id, newTitle) }
            },
        )
    }

    state.deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { state.deleting = null },
            title = { Text("Удалить документ?") },
            text = { Text("«${e.meta?.title ?: e.id}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    state.deleting = null
                    state.storeOp { NotebookStore.delete(e.id) }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { state.deleting = null }) { Text("Отмена") } },
        )
    }
}

/** Блок «нужен доступ к файлам» с кнопкой перехода в системные настройки. */
@Composable
fun AccessRequest(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
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
        Button(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }) { Text("Разрешить доступ") }
    }
}

@Composable
fun TitleDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Название тетради из имени выбранного PDF без расширения; запасной вариант — «PDF». */
private fun pdfTitle(context: Context, uri: Uri): String {
    val name = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
    return name?.removeSuffix(".pdf")?.removeSuffix(".PDF")?.trim()?.ifEmpty { null } ?: "PDF"
}
