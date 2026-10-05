package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.notes.Tags

/**
 * Правка тегов документа: выбранные теги — чипы с крестиком, поле ввода (Enter или «Добавить» добавляет тег)
 * и подсказки — теги других документов, которых ещё нет у этого.
 *
 * @param known все теги с числом документов (для подсказок).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagsDialog(
    title: String,
    initial: List<String>,
    known: List<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var tags by remember { mutableStateOf(Tags.clean(initial)) }
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun commitTyped() {
        tags = Tags.add(tags, typed)
        typed = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { t ->
                            InputChip(
                                selected = false,
                                onClick = { tags = Tags.remove(tags, t) },
                                label = { Text(t) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Убрать тег $t", modifier = Modifier.padding(0.dp)) },
                            )
                        }
                    }
                } else {
                    Text("Тегов пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(Tags.MAX_LENGTH + 1) },
                        label = { Text("Новый тег") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitTyped() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    TextButton(onClick = { commitTyped() }, enabled = Tags.normalize(typed) != null) { Text("Добавить") }
                }
                val suggestions = Tags.suggestions(known, tags, typed)
                if (suggestions.isNotEmpty()) {
                    Text("Подсказки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        suggestions.forEach { s ->
                            AssistChip(onClick = { tags = Tags.add(tags, s) }, label = { Text(s) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Tags.add(tags, typed)) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
