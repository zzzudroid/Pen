package com.zzzudroid.pen.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zzzudroid.pen.canvas.PageTemplate

/**
 * Настройки приложения: хранятся в SharedPreferences, при чтении отдают состояние Compose.
 * До [init] (например, в JVM-тестах) работают значения по умолчанию.
 */
object AppSettings {
    private var prefs: SharedPreferences? = null

    /** Только что созданный документ открывается с блокировкой перемещения поля (можно сразу писать). */
    var lockNewDocs by mutableStateOf(true); private set

    /** Палец по умолчанию стирает как ластик (в каждом новом редакторе). */
    var eraseWithFinger by mutableStateOf(false); private set

    /** Шаблон страниц новой заметки. */
    var newNoteTemplate by mutableStateOf(PageTemplate.LINED); private set

    /** Точечная сетка на досках. */
    var boardGrid by mutableStateOf(true); private set

    /** Мини-карта на досках. */
    var boardMinimap by mutableStateOf(true); private set

    /** Тап пальцем по области ссылки открывает цель. */
    var linksByTap by mutableStateOf(true); private set

    /** Не гасить экран, пока открыт редактор. */
    var keepScreenOn by mutableStateOf(false); private set

    /** Сглаживание пера включено (кнопка на панели инструментов); по умолчанию выключено. */
    var smoothingOn by mutableStateOf(false); private set

    /** Сила сглаживания: 1 — лёгкое, 2 — среднее, 3 — сильное. */
    var smoothingStrength by mutableStateOf(2); private set

    /** Действующий уровень сглаживания для пера: 0, если выключено. */
    val smoothing: Int get() = if (smoothingOn) smoothingStrength else 0

    /** Три пресета цвета пера (ARGB; прозрачность — в старшем байте) и номер выбранного. */
    val presets = androidx.compose.runtime.mutableStateListOf(0xFF1A1A1A.toInt(), 0xFF1F4EC9.toInt(), 0xFFD32F2F.toInt())
    var activePreset by mutableStateOf(0); private set

    /** Давление: влияет ли нажим и скорость письма на толщину линии. */
    var pressureOn by mutableStateOf(true); private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        lockNewDocs = p.getBoolean("lockNewDocs", true)
        eraseWithFinger = p.getBoolean("eraseWithFinger", false)
        newNoteTemplate = PageTemplate.byId(p.getString("newNoteTemplate", PageTemplate.LINED.id))
        boardGrid = p.getBoolean("boardGrid", true)
        boardMinimap = p.getBoolean("boardMinimap", true)
        linksByTap = p.getBoolean("linksByTap", true)
        keepScreenOn = p.getBoolean("keepScreenOn", false)
        smoothingOn = p.getBoolean("smoothingOn", false)
        smoothingStrength = p.getInt("smoothingStrength", 2).coerceIn(1, 3)
        for (i in presets.indices) presets[i] = p.getInt("preset$i", presets[i])
        activePreset = p.getInt("activePreset", 0).coerceIn(0, presets.size - 1)
        pressureOn = p.getBoolean("pressureOn", true)
    }

    private fun save(edit: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit()?.apply(edit)?.apply()
    }

    fun putLockNewDocs(v: Boolean) { lockNewDocs = v; save { putBoolean("lockNewDocs", v) } }
    fun putEraseWithFinger(v: Boolean) { eraseWithFinger = v; save { putBoolean("eraseWithFinger", v) } }
    fun putNewNoteTemplate(v: PageTemplate) { newNoteTemplate = v; save { putString("newNoteTemplate", v.id) } }
    fun putBoardGrid(v: Boolean) { boardGrid = v; save { putBoolean("boardGrid", v) } }
    fun putBoardMinimap(v: Boolean) { boardMinimap = v; save { putBoolean("boardMinimap", v) } }
    fun putLinksByTap(v: Boolean) { linksByTap = v; save { putBoolean("linksByTap", v) } }
    fun putKeepScreenOn(v: Boolean) { keepScreenOn = v; save { putBoolean("keepScreenOn", v) } }
    fun putSmoothingOn(v: Boolean) { smoothingOn = v; save { putBoolean("smoothingOn", v) } }
    fun putSmoothingStrength(v: Int) { smoothingStrength = v; save { putInt("smoothingStrength", v) } }
    fun putPreset(i: Int, argb: Int) { presets[i] = argb; save { putInt("preset$i", argb) } }
    fun putActivePreset(i: Int) { activePreset = i; save { putInt("activePreset", i) } }
    fun putPressureOn(v: Boolean) { pressureOn = v; save { putBoolean("pressureOn", v) } }

    /** Запомненная толщина инструмента [id], мм; [def], если ещё не задавали. */
    fun toolSizeMm(id: String, def: Float): Float = prefs?.getFloat("sizeMm_$id", def) ?: def

    fun putToolSizeMm(id: String, v: Float) { save { putFloat("sizeMm_$id", v) } }
}
