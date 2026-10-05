package com.zzzudroid.pen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.notes.DocKind
import com.zzzudroid.pen.notes.HomeLogic
import com.zzzudroid.pen.notes.NotebookEntry
import com.zzzudroid.pen.notes.NotebookStore

/**
 * Главный экран: слева — заметки (тетради), справа — холсты (доски), в каждой половине превьюшки
 * последних документов. Служебные разделы — плоским текстом в шапке. Тап по заголовку половины
 * открывает полный список, тап по плитке «+» создаёт документ сразу.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenList: (DocKind) -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val docs = rememberDocumentsState(snackbar)
    val pickPdf = rememberPdfPicker(docs, onOpenDocument)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Pen") },
                actions = {
                    // Четыре служебных пункта — простым текстом; при нехватке места ряд прокручивается, но не переносится
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { onNavigate(Routes.ABOUT) }) { Text("О программе") }
                        TextButton(onClick = { onNavigate(Routes.DIAGNOSTICS) }) { Text("Диагностика пера") }
                        TextButton(onClick = { onNavigate(Routes.CANVAS) }) { Text("Холст") }
                        TextButton(onClick = { onNavigate(Routes.SEARCH) }) { Text("Поиск по рукописи") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val entries = docs.entries
            when {
                !docs.hasAccess -> AccessRequest()
                else -> Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Half(
                        title = "Заметки",
                        kind = DocKind.NOTEBOOK,
                        entries = entries,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        headerAction = { TextButton(onClick = pickPdf) { Text("Открыть PDF") } },
                        onOpenList = onOpenList,
                        onCreate = { docs.createNow(false, onOpenDocument) },
                        onOpen = onOpenDocument,
                        onShare = { sharePdf(context, NotebookStore.pdfFile(it.id)) },
                        onRename = { docs.renaming = it },
                        onDelete = { docs.deleting = it },
                    )
                    Half(
                        title = "Холсты",
                        kind = DocKind.BOARD,
                        entries = entries,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        headerAction = null,
                        onOpenList = onOpenList,
                        onCreate = { docs.createNow(true, onOpenDocument) },
                        onOpen = onOpenDocument,
                        onShare = { sharePdf(context, NotebookStore.pdfFile(it.id)) },
                        onRename = { docs.renaming = it },
                        onDelete = { docs.deleting = it },
                    )
                }
            }
        }
    }

    DocumentDialogs(docs)
}

/** Одна половина экрана: заголовок-ссылка на полный список и сетка из плитки «создать» и последних документов. */
@Composable
private fun Half(
    title: String,
    kind: DocKind,
    entries: List<NotebookEntry>?,
    modifier: Modifier,
    headerAction: (@Composable () -> Unit)?,
    onOpenList: (DocKind) -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (NotebookEntry) -> Unit,
    onRename: (NotebookEntry) -> Unit,
    onDelete: (NotebookEntry) -> Unit,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clickable { onOpenList(kind) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Все: $title")
            }
            Box(Modifier.weight(1f))
            headerAction?.invoke()
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Колонок столько, сколько помещается; ячеек два ряда, первая — плитка «создать»
            val columns = HomeLogic.columns(maxWidth.value)
            val recent = if (entries == null) emptyList() else HomeLogic.recent(entries, kind, HomeLogic.cells(columns) - 1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(HomeLogic.GAP_DP.dp),
                verticalArrangement = Arrangement.spacedBy(HomeLogic.GAP_DP.dp),
            ) {
                item(key = "create") { CreateTile(isBoard = kind == DocKind.BOARD, onClick = onCreate) }
                items(recent, key = { it.id }) { e ->
                    NotebookCard(
                        entry = e,
                        onOpen = { if (e.meta != null) onOpen(e.id) },
                        onSharePdf = { onShare(e) },
                        onRename = { onRename(e) },
                        onDelete = { onDelete(e) },
                    )
                }
            }
        }
    }
}

/**
 * Плитка «создать»: пустая, с плюсом, по высоте как карточка документа (миниатюра формата A4 + подпись).
 * Нажатие создаёт документ и открывает его сразу, без диалога.
 */
@Composable
fun CreateTile(isBoard: Boolean, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = maxWidth * (297f / 210f) + 93.dp
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(height),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (isBoard) "Новый холст" else "Новая заметка",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
