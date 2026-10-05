package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Пункт главного меню
private data class Section(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

private val sections = listOf(
    Section("Диагностика пера", "Давление, наклон, кнопки, hover, частота событий", Icons.Outlined.Draw, Routes.DIAGNOSTICS),
    Section("О программе", "Версия и время сборки", Icons.Outlined.Info, Routes.ABOUT),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Pen") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sections.forEach { s ->
                Card(onClick = { onOpen(s.route) }, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(s.title) },
                        supportingContent = { Text(s.subtitle) },
                        leadingContent = { Icon(s.icon, contentDescription = null) },
                    )
                }
            }
        }
    }
}
