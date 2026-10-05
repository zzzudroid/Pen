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

    /** Сглаживание пера: 0 — выкл, 1…3 — лёгкое, среднее, сильное. */
    var smoothing by mutableStateOf(1); private set

    /** Цвет пера по умолчанию — номер в палитре. */
    var defaultColor by mutableStateOf(0); private set

    /** Толщина пера по умолчанию — 0, 1 или 2. */
    var defaultSize by mutableStateOf(1); private set

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
        smoothing = p.getInt("smoothing", 1).coerceIn(0, 3)
        defaultColor = p.getInt("defaultColor", 0)
        defaultSize = p.getInt("defaultSize", 1).coerceIn(0, 2)
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
    fun putSmoothing(v: Int) { smoothing = v; save { putInt("smoothing", v) } }
    fun putDefaultColor(v: Int) { defaultColor = v; save { putInt("defaultColor", v) } }
    fun putDefaultSize(v: Int) { defaultSize = v; save { putInt("defaultSize", v) } }
}
