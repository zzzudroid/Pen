package com.zzzudroid.pen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.Bookmark
import com.zzzudroid.pen.canvas.LinkTarget
import com.zzzudroid.pen.canvas.ViewState
import com.zzzudroid.pen.notes.DocIndex
import com.zzzudroid.pen.notes.DocKind
import com.zzzudroid.pen.notes.IndexedDoc

/**
 * Выбор цели ссылки: сначала документ (тетрадь или доска), для доски — ещё место: вся доска, текущий вид
 * (если ссылка внутри этой же доски) или одна из закладок.
 *
 * @param currentDocId идентификатор документа, в котором создаётся ссылка;
 * @param currentBookmarks актуальные закладки этого документа (в индексе они могут отставать до сохранения);
 * @param currentView текущий вид доски — для пункта «Текущий вид» при ссылке внутри той же доски.
 */
@Composable
fun LinkTargetDialog(
    currentDocId: String?,
    currentBookmarks: List<Bookmark>,
    currentView: ViewState?,
    onDismiss: () -> Unit,
    onPick: (LinkTarget) -> Unit,
) {
    val context = LocalContext.current
    var docs by remember { mutableStateOf<List<IndexedDoc>?>(null) }
    var board by remember { mutableStateOf<IndexedDoc?>(null) }

    LaunchedEffect(Unit) { docs = DocIndex.refresh(context).sortedByDescending { it.stamp.lastModified } }

    val chosen = board
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (chosen == null) "Куда ведёт ссылка?" else "Место на доске «${chosen.title}»") },
        text = {
            val list = docs
            when {
                list == null -> Text("Загрузка…")
                chosen == null && list.isEmpty() -> Text("Документов пока нет")
                chosen == null -> LazyColumn {
                    items(list, key = { it.docId }) { d ->
                        ListItem(
                            headlineContent = { Text(d.title) },
                            leadingContent = {
                                Icon(if (d.kind == DocKind.BOARD) Icons.Outlined.Dashboard else Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                // Для тетради место одно — документ целиком; для доски нужно выбрать место
                                if (d.kind == DocKind.BOARD) board = d else onPick(LinkTarget(d.docId))
                            },
                        )
                    }
                }
                else -> {
                    val own = chosen.docId == currentDocId
                    // Закладки: для этой же доски — живой список, для другой — из индекса
                    val marks = if (own) currentBookmarks else chosen.bookmarks
                    Column(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Вся доска (как открыта в последний раз)") },
                            modifier = Modifier.clickable { onPick(LinkTarget(chosen.docId)) },
                        )
                        if (own && currentView != null) {
                            ListItem(
                                headlineContent = { Text("Текущий вид") },
                                modifier = Modifier.clickable { onPick(LinkTarget(chosen.docId, view = currentView)) },
                            )
                        }
                        if (marks.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        marks.forEach { b ->
                            ListItem(
                                headlineContent = { Text(b.name) },
                                leadingContent = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                                modifier = Modifier.clickable { onPick(LinkTarget(chosen.docId, bookmarkId = b.id)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (board != null) board = null else onDismiss() }) { Text(if (board != null) "Назад" else "Отмена") }
        },
    )
}
