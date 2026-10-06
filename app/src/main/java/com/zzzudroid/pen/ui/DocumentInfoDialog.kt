package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.notes.DocInfo
import com.zzzudroid.pen.notes.Formatting
import com.zzzudroid.pen.notes.NotebookEntry

/** Свойства документа: тип, файл в программе, для копии PDF — откуда скопирована и есть ли заметки. */
@Composable
fun DocumentInfoDialog(entry: NotebookEntry, onDismiss: () -> Unit) {
    val meta = entry.meta
    val rows = buildList {
        add("Название" to (meta?.title ?: entry.id))
        if (meta != null) {
            add(
                "Тип" to when {
                    meta.hasPdfSource -> "Копия PDF для заметок"
                    meta.isBoard -> "Холст"
                    else -> "Заметка"
                },
            )
            if (meta.hasPdfSource) add("Заметки" to if (meta.hasInk) "есть" else "нет, только PDF")
            if (meta.hasPdfSource) add("Скопирован из" to DocInfo.originText(meta.pdfOrigin, meta.pdfOriginName))
            add("Страниц" to meta.pages.size.toString())
            add("Изменён" to Formatting.formatDate(meta.modified))
        }
        add("Размер" to Formatting.formatFileSize(entry.file.length()))
        add("Файл в программе" to entry.file.absolutePath)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свойства") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (meta?.hasPdfSource == true) {
                    Text(
                        "Это копия: исходный PDF-файл программа не меняет, и удаление копии его не затрагивает.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
