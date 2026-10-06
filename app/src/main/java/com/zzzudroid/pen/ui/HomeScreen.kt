package com.zzzudroid.pen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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

    // Служебные разделы — в левой выезжающей панели, чтобы не светились в шапке
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    fun open(route: String) {
        scope.launch { drawer.close() }
        onNavigate(route)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text("Pen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp))
                NavigationDrawerItem(
                    label = { Text("Открыть PDF") },
                    selected = false,
                    onClick = { scope.launch { drawer.close() }; pickPdf() },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                listOf(
                    "Холст" to Routes.CANVAS,
                    "Поиск по рукописи" to Routes.SEARCH,
                    "Диагностика пера" to Routes.DIAGNOSTICS,
                    "Настройки" to Routes.SETTINGS,
                    "О программе" to Routes.ABOUT,
                ).forEach { (title, route) ->
                    NavigationDrawerItem(
                        label = { Text(title) },
                        selected = false,
                        onClick = { open(route) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val entries = docs.entries
            when {
                !docs.hasAccess -> AccessRequest()
                else -> Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Half(
                        leading = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Меню")
                            }
                        },
                        title = "Заметки",
                        kind = DocKind.NOTEBOOK,
                        entries = entries,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onOpenList = onOpenList,
                        onCreate = { docs.createNow(false, onOpenDocument) },
                        onOpen = onOpenDocument,
                        onShare = { sharePdf(context, NotebookStore.pdfFile(it.id)) },
                        onRename = { docs.renaming = it },
                        onDelete = { docs.deleting = it },
                        onTags = { docs.tagging = it },
                        onInfo = { docs.info = it },
                    )
                    Half(
                        title = "Холсты",
                        kind = DocKind.BOARD,
                        entries = entries,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onOpenList = onOpenList,
                        onCreate = { docs.createNow(true, onOpenDocument) },
                        onOpen = onOpenDocument,
                        onShare = { sharePdf(context, NotebookStore.pdfFile(it.id)) },
                        onRename = { docs.renaming = it },
                        onDelete = { docs.deleting = it },
                        onTags = { docs.tagging = it },
                        onInfo = { docs.info = it },
                    )
                }
            }
        }
    }
    }

    DocumentDialogs(docs)
}

/**
 * Одна половина экрана со своим фоном: заголовок по центру (тап открывает полный список) и сетка из плитки «создать»
 * и последних документов — две колонки и целое число рядов, карточки не режутся.
 */
@Composable
private fun Half(
    title: String,
    kind: DocKind,
    entries: List<NotebookEntry>?,
    modifier: Modifier,
    onOpenList: (DocKind) -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (NotebookEntry) -> Unit,
    onRename: (NotebookEntry) -> Unit,
    onDelete: (NotebookEntry) -> Unit,
    onTags: (NotebookEntry) -> Unit,
    onInfo: (NotebookEntry) -> Unit,
    leading: (@Composable BoxScope.() -> Unit)? = null,
) {
    // Заметки — тёплый бледно-зелёный фон, холсты — бледно-голубой: лёгкие светлые пастельные тона в любой теме,
    // поэтому надписи и значки на них тёмные
    val board = kind == DocKind.BOARD
    val tint = if (board) androidx.compose.ui.graphics.Color(0xFFE1EFFA) else androidx.compose.ui.graphics.Color(0xFFE2F5EA)
    val ink = if (board) androidx.compose.ui.graphics.Color(0xFF24394A) else androidx.compose.ui.graphics.Color(0xFF2B4A3B)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ink) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(tint).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (leading != null) Box(Modifier.align(Alignment.CenterStart)) { leading() }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable { onOpenList(kind) }.padding(vertical = 12.dp, horizontal = 16.dp),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val layout = HomeLogic.layout(maxWidth.value, maxHeight.value, HomeLogic.captionDp(fontScale), HomeLogic.columnsFor(landscape))
            val recent = if (entries == null) emptyList() else HomeLogic.recent(entries, kind, layout.cells - 1)
            val cardHeight = layout.cardHeightDp.dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(layout.columns),
                modifier = Modifier.width(layout.widthDp.dp).height(layout.heightDp.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(HomeLogic.GAP_DP.dp),
                verticalArrangement = Arrangement.spacedBy(HomeLogic.GAP_DP.dp),
            ) {
                item(key = "create") { CreateTile(isBoard = board, onClick = onCreate, height = cardHeight, light = ink) }
                items(recent, key = { it.id }) { e ->
                    NotebookCard(
                        entry = e,
                        onOpen = { if (e.meta != null) onOpen(e.id) },
                        onSharePdf = { onShare(e) },
                        onRename = { onRename(e) },
                        onDelete = { onDelete(e) },
                        onTags = { onTags(e) },
                        onInfo = { onInfo(e) },
                        modifier = Modifier.height(cardHeight),
                        ink = ink,
                    )
                }
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
fun CreateTile(isBoard: Boolean, onClick: () -> Unit, height: androidx.compose.ui.unit.Dp? = null, light: androidx.compose.ui.graphics.Color? = null) {
    // Высота не задана (список) — как у карточки такой же ширины
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val h = height ?: HomeLogic.cardHeightFor(maxWidth.value).dp
        // На светлой пастельной половине плитка полупрозрачно-белая, обводка и плюс — цвета надписей половины
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(h),
            colors = if (light != null) androidx.compose.material3.CardDefaults.outlinedCardColors(containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f))
            else androidx.compose.material3.CardDefaults.outlinedCardColors(),
            border = BorderStroke(2.dp, light?.copy(alpha = 0.28f) ?: MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (isBoard) "Новый холст" else "Новая заметка",
                    modifier = Modifier.size(56.dp),
                    tint = light?.copy(alpha = 0.75f) ?: MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
