package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zzzudroid.pen.recognition.ModelState
import com.zzzudroid.pen.recognition.SearchHit
import com.zzzudroid.pen.recognition.SearchViewModel

/** Поиск по рукописи. [onOpen] — (id тетради, индекс страницы с нуля). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (String, Int) -> Unit, vm: SearchViewModel = viewModel()) {
    // При открытии экрана — сразу дораспознать новые и изменённые тетради
    LaunchedEffect(Unit) { vm.updateIndex() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск по рукописи") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = vm::updateIndex, enabled = !vm.indexing && vm.hasAccess) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить индекс")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!vm.hasAccess) {
                Text(
                    "Нет доступа к тетрадям. Откройте «Заметки» и выдайте доступ к файлам.",
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            OutlinedTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Слова из рукописных заметок") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(vertical = 8.dp),
            )

            IndexStatus(vm)

            LazyColumn(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.hits, key = { "${it.notebookId}#${it.pageNumber}" }) { hit ->
                    HitCard(hit, onClick = { onOpen(hit.notebookId, hit.pageNumber - 1) })
                }
            }
            if (vm.query.isNotBlank() && vm.hits.isEmpty() && !vm.indexing) {
                Text(
                    "Ничего не найдено",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IndexStatus(vm: SearchViewModel) {
    val text = when {
        vm.error != null -> vm.error!!
        vm.indexing -> when (vm.modelState) {
            ModelState.Downloading -> "Загрузка модели распознавания (один раз, нужен интернет)…"
            ModelState.Checking -> "Подготовка распознавания…"
            else -> if (vm.progressTotal > 0) "Распознаю тетради: ${vm.progressDone} из ${vm.progressTotal}" else "Проверяю тетради…"
        }
        else -> null
    } ?: return
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(bottom = 8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (vm.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (vm.indexing) {
            Spacer(Modifier.padding(top = 4.dp))
            if (vm.progressTotal > 0 && vm.modelState == ModelState.Ready) {
                LinearProgressIndicator(
                    progress = { vm.progressDone.toFloat() / vm.progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HitCard(hit: SearchHit, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Row {
                    Text(hit.title, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text("стр. ${hit.pageNumber}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            supportingContent = { Text(highlighted(hit, MaterialTheme.colorScheme.primary)) },
        )
    }
}

/** Сниппет с подсветкой найденных слов. */
private fun highlighted(hit: SearchHit, color: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    append(hit.snippet)
    for (r in hit.highlights) {
        if (r.first >= 0 && r.last < hit.snippet.length) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color), r.first, r.last + 1)
        }
    }
}
