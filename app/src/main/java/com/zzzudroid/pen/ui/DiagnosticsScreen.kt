package com.zzzudroid.pen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zzzudroid.pen.diag.DiagSnapshot
import com.zzzudroid.pen.diag.DiagnosticsViewModel
import com.zzzudroid.pen.diag.InputDeviceInfo
import com.zzzudroid.pen.diag.PenInputView
import com.zzzudroid.pen.diag.PointerLive
import com.zzzudroid.pen.diag.ToolSnapshot
import com.zzzudroid.pen.diag.buildReport
import com.zzzudroid.pen.diag.buttonNames
import com.zzzudroid.pen.diag.readInputDevices
import com.zzzudroid.pen.diag.saveReport
import com.zzzudroid.pen.diag.toDeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(vm: DiagnosticsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Сбор событий — только пока экран открыт
    DisposableEffect(Unit) {
        vm.active = true
        onDispose { vm.active = false }
    }

    // UI обновляется по таймеру, а не на каждое событие: перо шлёт сотни событий в секунду
    var snap by remember { mutableStateOf(vm.snapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            snap = vm.snapshot()
            delay(100)
        }
    }
    val devices = remember { readInputDevices(context) }
    var inputView by remember { mutableStateOf<PenInputView?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Диагностика пера") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.reset(); snap = vm.snapshot() }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "Сбросить сводку")
                    }
                    IconButton(onClick = { vm.clearStrokes(); inputView?.redrawAll() }) {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = "Очистить холст")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val msg = try {
                                val path = withContext(Dispatchers.IO) {
                                    saveReport(context, buildReport(context, vm.snapshot(), readInputDevices(context)))
                                }
                                "Отчёт сохранён: $path"
                            } catch (e: Exception) {
                                "Не удалось сохранить отчёт: ${e.message}"
                            }
                            snackbar.showSnackbar(msg)
                        }
                    }) {
                        Icon(Icons.Outlined.Save, contentDescription = "Сохранить отчёт")
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight
            val panel: @Composable (Modifier) -> Unit = { mod ->
                InfoPanel(
                    snap = snap,
                    devices = devices,
                    onUnbufferedChange = { vm.unbuffered = it; snap = vm.snapshot() },
                    modifier = mod,
                )
            }
            val canvas: @Composable (Modifier) -> Unit = { mod ->
                AndroidView(
                    factory = { ctx -> PenInputView(ctx, vm).also { inputView = it } },
                    modifier = mod,
                )
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    panel(Modifier.width(440.dp).fillMaxHeight())
                    canvas(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    canvas(Modifier.weight(1f).fillMaxWidth())
                    panel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(
    snap: DiagSnapshot,
    devices: List<InputDeviceInfo>,
    onUnbufferedChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Unbuffered dispatch", fontWeight = FontWeight.Medium)
                Text(
                    "Все события сразу, без группировки по кадрам",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = snap.unbuffered, onCheckedChange = onUnbufferedChange)
        }

        Section("Указатели")
        if (snap.pointers.isEmpty()) Hint("Коснитесь экрана пером или пальцем")
        snap.pointers.forEach { PointerCard(it) }
        Mono("Hover: " + if (snap.hoverSeen) "события приходят" else "событий не было (поднесите перо к экрану)")

        Section("Сводка за сессию · событий ${snap.totalEvents}")
        if (snap.tools.isEmpty()) Hint("Проведите несколько штрихов от лёгкого до сильного нажима")
        snap.tools.forEach { ToolCard(it) }
        Mono("Кнопки (MotionEvent): " + snap.buttonStates.joinToString { buttonNames(it) }.ifEmpty { "не нажимались" })
        Mono("Клавиши (KeyEvent): " + snap.keyCodes.joinToString().ifEmpty { "не было" })

        Section("Журнал событий")
        if (snap.log.isEmpty()) Hint("Здесь появятся касания, hover и кнопки")
        snap.log.take(20).forEach { Mono(it) }

        Section("Устройства ввода")
        devices.forEach { DeviceCard(it) }
    }
}

@Composable
private fun PointerCard(p: PointerLive) {
    Mono(
        "#${p.pointerId} ${p.tool.title} · ${p.action}\n" +
            "x=%.1f y=%.1f давление=%.4f размер=%.3f\n".format(p.x, p.y, p.pressure, p.size) +
            "наклон=%s азимут=%s дистанция=%.2f\n".format(deg(p.tiltRad), deg(p.orientationRad), p.distance) +
            "кнопки=${buttonNames(p.buttonState)} hist=${p.historySize}\n" +
            "${p.deviceName} [${p.source}]",
    )
}

@Composable
private fun ToolCard(t: ToolSnapshot) {
    Mono(
        "${t.tool.title}: касаний ${t.contactSamples}, hover ${t.hoverSamples}\n" +
            "давление ${range(t.pressureMin, t.pressureMax, "%.4f")} · уровней ≥ ${t.pressureLevels}\n" +
            "наклон ${range(t.tiltMinRad.toDeg(), t.tiltMaxRad.toDeg(), "%.1f°")}\n" +
            "азимут ${range(t.orientationMinRad.toDeg(), t.orientationMaxRad.toDeg(), "%.1f°")}\n" +
            "частота: касание %.0f Гц, hover %.0f Гц · ист. точек/событие %.2f"
                .format(t.contactRateHz, t.hoverRateHz, t.avgHistoricalPerEvent),
    )
}

@Composable
private fun DeviceCard(d: InputDeviceInfo) {
    val head = "${d.name} (id ${d.id}, %04x:%04x)".format(d.vendorId, d.productId) +
        (if (d.isExternal) " · внешнее" else "") + (if (d.isVirtual) " · виртуальное" else "")
    val axes = d.axes.joinToString("\n") { a ->
        "  ${a.axis} [${a.source}] %.2f…%.2f res=%.2f".format(a.min, a.max, a.resolution)
    }
    Mono(head + "\n  " + d.sources.joinToString("|") + if (axes.isNotEmpty()) "\n$axes" else "")
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.padding(top = 4.dp))
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Mono(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
}

private fun deg(rad: Float) = if (rad.isNaN()) "—" else "%.1f°".format(Math.toDegrees(rad.toDouble()))

private fun range(min: Float, max: Float, fmt: String) =
    if (min.isNaN()) "нет данных" else fmt.format(min) + " … " + fmt.format(max)
