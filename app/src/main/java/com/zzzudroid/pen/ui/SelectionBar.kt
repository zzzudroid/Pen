package com.zzzudroid.pen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.PALETTE
import com.zzzudroid.pen.canvas.SelectionTransform
import com.zzzudroid.pen.canvas.StrokeOps

/** Сдвиг копии выделения в мировых единицах (4 мм на странице тетради). */
private const val COPY_OFFSET = 20f

/**
 * Панель действий с выделением лассо; видна, только пока что-то выделено.
 * [onMakeLink] и [onEditLink] — действия со ссылками; null — ссылки на этом экране выключены.
 */
@Composable
fun SelectionBar(vm: CanvasViewModel, onMakeLink: (() -> Unit)? = null, onEditLink: (() -> Unit)? = null) {
    val selection = vm.selection
    if (selection.isEmpty()) return
    // Ссылки под рамкой выделения; пересчитываем при любом изменении модели
    val links = remember(vm.version, selection) { if (onMakeLink != null) vm.selectedLinks() else emptyList() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Выделено: ${selection.size}", style = MaterialTheme.typography.bodyMedium)

        VerticalDivider(Modifier.padding(horizontal = 4.dp).size(1.dp, 32.dp))

        TextButton(onClick = {
            val t = SelectionTransform(dx = COPY_OFFSET, dy = COPY_OFFSET)
            vm.addCopies(vm.selection.map { StrokeOps.transformed(it, t) })
        }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Копировать")
        }
        TextButton(onClick = vm::deleteSelection) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Удалить")
        }

        if (onMakeLink != null) {
            TextButton(onClick = onMakeLink) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Сделать ссылкой")
            }
            if (links.isNotEmpty() && onEditLink != null) {
                TextButton(onClick = onEditLink) { Text("Изменить ссылку") }
                TextButton(onClick = { vm.removeLinks(links) }) {
                    Icon(Icons.Outlined.LinkOff, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Удалить ссылку")
                }
            }
        }

        VerticalDivider(Modifier.padding(horizontal = 4.dp).size(1.dp, 32.dp))

        // Перекраска выделения
        PALETTE.forEach { argb ->
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { vm.replaceSelection(vm.selection.map { StrokeOps.recolored(it, argb) }) },
            )
        }

        VerticalDivider(Modifier.padding(horizontal = 4.dp).size(1.dp, 32.dp))

        TextButton(onClick = vm::clearSelection) {
            Icon(Icons.Outlined.Close, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Снять")
        }
    }
}
