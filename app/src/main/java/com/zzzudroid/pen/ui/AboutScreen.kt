package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О программе") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Pen", style = MaterialTheme.typography.headlineMedium)
            Text("Приложение для рукописного ввода стилусом", style = MaterialTheme.typography.bodyLarge)
            InfoRow("Версия", "${BuildConfig.VERSION_NAME} (код ${BuildConfig.VERSION_CODE})")
            InfoRow("Сборка", BuildConfig.BUILD_TIME)
            InfoRow(
                "Коммит",
                BuildConfig.GIT_HASH + if (BuildConfig.GIT_DIRTY) " (есть незакоммиченные изменения)" else "",
            )
            InfoRow("Тип", BuildConfig.BUILD_TYPE)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
