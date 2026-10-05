package com.zzzudroid.pen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zzzudroid.pen.notes.Formatting
import com.zzzudroid.pen.notes.NotebookEntry
import com.zzzudroid.pen.notes.NotebookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Карточка документа (тетрадь или доска): миниатюра с размером файла сверху, значок типа,
 * название, дата `дд.мм.гггг` (у тетради ещё число страниц) и меню действий.
 * Переиспользуется списком заметок и главным экраном.
 */
@Composable
fun NotebookCard(
    entry: NotebookEntry,
    onOpen: () -> Unit,
    onSharePdf: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onTags: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Цвет надписей на светлой пастельной подложке главного экрана; null — обычная тёмная/светлая карточка темы. */
    ink: Color? = null,
) {
    val thumb by produceState<Bitmap?>(null, entry.file, entry.meta?.modified) {
        value = withContext(Dispatchers.IO) { NotebookFormat.readThumbnail(entry.file) }
    }
    // Размер файла — из самого файла, поэтому показывается и у повреждённого документа
    val fileSize = remember(entry.file, entry.meta?.modified) { Formatting.formatFileSize(entry.file.length()) }
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        modifier = modifier,
        colors = if (ink != null) androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.82f), contentColor = ink)
        else androidx.compose.material3.CardDefaults.cardColors(),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(210f / 297f).background(if (ink != null) Color(0xFFF2F4EE) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (entry.meta == null) Text("Файл повреждён", color = MaterialTheme.colorScheme.error)
            // Меню действий — кружок в левом верхнем углу миниатюры, чтобы подпись занимала всю ширину карточки
            Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                Box(
                    Modifier.size(32.dp).background(Color(0xB3000000), CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.MoreVert, contentDescription = "Действия", modifier = Modifier.size(20.dp), tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (entry.meta != null) {
                        DropdownMenuItem(text = { Text("Поделиться PDF") }, onClick = { menu = false; onSharePdf() })
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Теги…") }, onClick = { menu = false; onTags() })
                    }
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
            // Теги — ярлыки внизу миниатюры: два первых и «+N»
            val tags = entry.meta?.tags.orEmpty()
            if (tags.isNotEmpty()) {
                Row(Modifier.align(Alignment.BottomStart).padding(6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    tags.take(2).forEach { TagBadge(it) }
                    if (tags.size > 2) TagBadge("+${tags.size - 2}")
                }
            }
            // Размер файла сверху на миниатюре; тёмная полупрозрачная подложка читается и на светлой странице, и на тёмной доске
            Text(
                fileSize,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        // Миниатюра отделена от блока подписи: линия и слегка затемнённая подложка подписи
        if (ink != null) androidx.compose.material3.HorizontalDivider(color = ink.copy(alpha = 0.50f), thickness = 2.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (ink != null) Modifier.background(ink.copy(alpha = 0.17f)) else Modifier)
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Значок типа одного размера и на одном месте у тетради и у доски; тип словами не пишем
                    entry.meta?.let { meta ->
                        Icon(
                            if (meta.isBoard) Icons.Outlined.Dashboard else Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = if (meta.isBoard) "Доска" else "Тетрадь",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(entry.meta?.title ?: entry.id, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp))
                }
                Text(
                    // Только дата; число страниц не показываем
                    entry.meta?.let { Formatting.formatDate(it.modified) } ?: (entry.error ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = ink?.copy(alpha = 0.65f) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Ярлык тега на миниатюре: тёмная полупрозрачная подложка читается и на светлой странице, и на тёмной доске. */
@Composable
private fun TagBadge(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .widthIn(max = 84.dp)
            .background(Color(0xB3000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
