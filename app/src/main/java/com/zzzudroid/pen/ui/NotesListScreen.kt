package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import com.zzzudroid.pen.notes.Tags
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.notes.DocKind
import com.zzzudroid.pen.notes.HomeLogic
import com.zzzudroid.pen.notes.NotebookStore

/**
 * Полный список документов одного вида — тетради или доски: видно все, не только последние.
 * Первая ячейка — плитка «создать» (создаёт и открывает сразу, без диалога).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(kind: DocKind, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val docs = rememberDocumentsState(snackbar)
    val pickPdf = rememberPdfPicker(docs, onOpen)
    val isBoard = kind == DocKind.BOARD
    var selectedTag by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (isBoard) "Холсты" else "Заметки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Импорт PDF создаёт тетрадь, поэтому ссылка — только в списке заметок
                    if (!isBoard && docs.hasAccess) TextButton(onClick = pickPdf) { Text("Открыть PDF") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val entries = docs.entries
            when {
                !docs.hasAccess -> AccessRequest()
                entries == null -> Unit
                else -> Column(Modifier.fillMaxSize()) {
                // Фильтр по тегам этого вида документов: «Все» и теги от частых к редким
                val ofKind = HomeLogic.all(entries, kind)
                val counts = Tags.counts(ofKind.mapNotNull { it.meta?.tags })
                // Выбранный тег, которого больше нет ни у одного документа, сбрасывается
                val shown = selectedTag?.takeIf { t -> counts.any { it.first.equals(t, ignoreCase = true) } }
                if (counts.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "all") { FilterChip(selected = shown == null, onClick = { selectedTag = null }, label = { Text("Все") }) }
                        items(counts, key = { it.first.lowercase() }) { (tag, n) ->
                            FilterChip(
                                selected = shown?.equals(tag, ignoreCase = true) == true,
                                onClick = { selectedTag = if (shown?.equals(tag, ignoreCase = true) == true) null else tag },
                                label = { Text("$tag · $n") },
                            )
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "create") { CreateTile(isBoard = isBoard, onClick = { docs.createNow(isBoard, onOpen) }) }
                    items(ofKind.filter { Tags.matches(it.meta?.tags.orEmpty(), shown) }, key = { it.id }) { e ->
                        NotebookCard(
                            entry = e,
                            onOpen = { if (e.meta != null) onOpen(e.id) },
                            onSharePdf = { sharePdf(context, NotebookStore.pdfFile(e.id)) },
                            onRename = { docs.renaming = e },
                            onDelete = { docs.deleting = e },
                            onTags = { docs.tagging = e },
                        )
                    }
                }
                }
            }
            if (docs.hasAccess && entries != null && entries.none { (it.meta?.kind ?: DocKind.NOTEBOOK) == kind }) {
                Text(
                    if (isBoard) "Холстов пока нет" else "Заметок пока нет",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    DocumentDialogs(docs)
}
