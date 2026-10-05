package com.zzzudroid.pen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.Bookmark
import com.zzzudroid.pen.notes.NotebookViewModel

/**
 * Закладки доски: список с переходом, переименованием и удалением и кнопка «Поставить закладку здесь»
 * (запоминает текущие место и масштаб). Закладки сохраняются вместе с доской автоматически.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(vm: NotebookViewModel, onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Bookmark?>(null) }
    var deleting by remember { mutableStateOf<Bookmark?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Закладки", style = MaterialTheme.typography.titleLarge)
            if (vm.bookmarks.isEmpty()) {
                Text(
                    "Закладок пока нет: сдвиньте доску к нужному месту и поставьте закладку — на неё можно ссылаться из заметок.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            vm.bookmarks.forEach { b ->
                BookmarkRow(
                    bookmark = b,
                    onOpen = { vm.goToBookmark(b.id); onDismiss() },
                    onRename = { renaming = b },
                    onDelete = { deleting = b },
                )
            }
            Button(onClick = { adding = true }) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
                Text("Поставить закладку здесь", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (adding) {
        TitleDialog(
            title = "Новая закладка",
            initial = "Закладка ${vm.bookmarks.size + 1}",
            confirm = "Поставить",
            onDismiss = { adding = false },
            onConfirm = { name -> adding = false; vm.addBookmark(name) },
        )
    }
    renaming?.let { b ->
        TitleDialog(
            title = "Переименовать закладку",
            initial = b.name,
            confirm = "Сохранить",
            onDismiss = { renaming = null },
            onConfirm = { name -> renaming = null; vm.renameBookmark(b.id, name) },
        )
    }
    deleting?.let { b ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить закладку?") },
            text = { Text("«${b.name}» будет удалена. Ссылки, которые вели на неё, откроют доску в последнем виде.") },
            confirmButton = { TextButton(onClick = { deleting = null; vm.deleteBookmark(b.id) }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(bookmark.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Действия") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
            DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
        }
    }
}
