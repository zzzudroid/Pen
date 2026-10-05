package com.zzzudroid.pen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.PALETTE
import com.zzzudroid.pen.canvas.PageTemplate
import com.zzzudroid.pen.settings.AppSettings

/** Настройки приложения: значения по умолчанию для редактора и новых документов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Новые документы")
            SwitchRow(
                "Блокировать перемещение поля",
                "Замочек включён сразу после создания заметки или холста: пером можно писать, поле не сдвигается",
                AppSettings.lockNewDocs, AppSettings::putLockNewDocs,
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Шаблон новой заметки", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageTemplate.entries.forEach { t ->
                        FilterChip(
                            selected = AppSettings.newNoteTemplate == t,
                            onClick = { AppSettings.putNewNoteTemplate(t) },
                            label = { Text(t.title) },
                        )
                    }
                }
            }
            HorizontalDivider()

            Section("Перо")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Красивое перо: сила сглаживания", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Кнопка «красивое перо» на панели: сглаживание, плоское каллиграфическое перо и толщина по скорости. Чем сильнее сглаживание, тем заметнее отставание линии от кончика",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Лёгкое", "Среднее", "Сильное").forEachIndexed { i, name ->
                        FilterChip(
                            selected = AppSettings.smoothingStrength == i + 1,
                            onClick = { AppSettings.putSmoothingStrength(i + 1) },
                            label = { Text(name) },
                        )
                    }
                }
            }
            SwitchRow(
                "Стирать пальцем",
                "Палец работает как ластик, перо рисует; применяется при открытии редактора",
                AppSettings.eraseWithFinger, AppSettings::putEraseWithFinger,
            )
            HorizontalDivider()

            Section("Доски")
            SwitchRow("Точечная сетка", "Фон доски в точку", AppSettings.boardGrid, AppSettings::putBoardGrid)
            SwitchRow("Мини-карта", "Обзор содержимого доски в левом верхнем углу", AppSettings.boardMinimap, AppSettings::putBoardMinimap)
            HorizontalDivider()

            Section("Редактор")
            SwitchRow(
                "Открывать ссылки тапом",
                "Тап пальцем по подсвеченной области ссылки переходит к цели",
                AppSettings.linksByTap, AppSettings::putLinksByTap,
            )
            SwitchRow(
                "Не гасить экран",
                "Экран не выключается, пока открыта заметка или холст",
                AppSettings.keepScreenOn, AppSettings::putKeepScreenOn,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}
