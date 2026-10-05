package com.zzzudroid.pen.canvas

import androidx.ink.strokes.Stroke

/** Шаблон страницы; id хранится в файле тетради. */
enum class PageTemplate(val id: String, val title: String) {
    BLANK("blank", "Чистая"),
    LINED("lined", "Линейка"),
    GRID("grid", "Клетка"),
    DOTS("dots", "Точки");

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: BLANK
    }
}

/** Границы и шаблон страницы в мировых координатах. */
data class PageSpec(val width: Float, val height: Float, val template: PageTemplate) {
    fun contains(x: Float, y: Float) = x in 0f..width && y in 0f..height

    companion object {
        /** Мировых единиц на миллиметр. */
        const val UNITS_PER_MM = 5f

        fun a4(template: PageTemplate) = PageSpec(210 * UNITS_PER_MM, 297 * UNITS_PER_MM, template)
    }
}

/**
 * Одна страница: штрихи и собственная история отмены.
 * [spec] — размер и шаблон именно этой страницы (null — общий spec тетради),
 * [pdfPage] — индекс страницы исходного PDF-подложки (0-based) или null.
 */
class PageState(val id: String, var spec: PageSpec? = null, val pdfPage: Int? = null) {
    val strokes = mutableListOf<Stroke>()
    val history = History<CanvasCommand>()
}
