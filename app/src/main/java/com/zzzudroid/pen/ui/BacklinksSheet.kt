package com.zzzudroid.pen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.notes.BacklinkRow
import com.zzzudroid.pen.notes.BacklinkRows
import com.zzzudroid.pen.notes.DocIndex

/**
 * Панель «Откуда ссылаются»: в каких заметках и досках есть ссылки на этот документ или его закладки.
 * Нажатие открывает источник на странице и прокручивает к месту ссылки; «Назад» возвращает сюда.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklinksSheet(docId: String, onOpen: (BacklinkRow) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // null — индекс ещё обновляется
    var rows by remember { mutableStateOf<List<BacklinkRow>?>(null) }
    LaunchedEffect(docId) {
        DocIndex.refresh(context)
        rows = BacklinkRows.rows(DocIndex.backlinks(docId))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Откуда ссылаются", style = MaterialTheme.typography.titleLarge)
            val list = rows
            when {
                list == null -> Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.isEmpty() -> Text("Ссылок на этот документ пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> list.forEach { row ->
                    Text(
                        row.text,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(row) }.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
