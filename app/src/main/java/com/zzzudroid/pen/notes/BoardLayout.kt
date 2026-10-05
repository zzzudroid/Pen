package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.Bounds
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.canvas.PageTemplate

/** Вид документа: тетрадь со страницами или бесконечная доска. */
enum class DocKind(val id: String) {
    NOTEBOOK("notebook"),
    BOARD("board");

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id } ?: NOTEBOOK
    }
}

/** Последний вид доски (масштаб и сдвиг), чтобы открыть её там же, где закрыли. */
data class BoardView(val scale: Float, val tx: Float, val ty: Float)

/** Страница экспорта доски: размер в мировых единицах и сдвиг содержимого на неё. */
/** [scale] < 1 — содержимое уменьшено, чтобы страница уложилась в предел размера PDF. */
data class BoardPage(val width: Float, val height: Float, val offsetX: Float, val offsetY: Float, val scale: Float = 1f)

/** Вписывание содержимого в прямоугольник: масштаб и сдвиг (экран = мир * scale + d). */
data class Fit(val scale: Float, val dx: Float, val dy: Float)

/** Расчёты раскладки доски для экспорта и миниатюры (чистый Kotlin, покрыт JVM-тестами). */
object BoardLayout {
    /** Поля страницы экспорта доски, мм. */
    const val EXPORT_MARGIN_MM = 10f

    /** Поля миниатюры — доля большей стороны содержимого. */
    const val THUMB_MARGIN_FRACTION = 0.05f

    /**
     * Страница PDF для доски: границы содержимого + поля; пустая доска — пустой лист A4.
     * Сдвиг переносит левый верхний угол содержимого в точку (поле, поле).
     */
    fun exportPage(content: Bounds?, marginMm: Float = EXPORT_MARGIN_MM, maxPagePt: Float = MAX_PAGE_PT): BoardPage {
        if (content == null) {
            val a4 = PageSpec.a4(PageTemplate.BLANK)
            return BoardPage(a4.width, a4.height, 0f, 0f)
        }
        val m = marginMm * PageSpec.UNITS_PER_MM
        val width = content.width + 2 * m
        val height = content.height + 2 * m
        // У PDF есть предел размера страницы (около 14 400 пт ≈ 5 м): у огромной схемы уменьшаем масштаб
        val biggestPt = maxOf(width, height) * POINTS_PER_UNIT
        val scale = if (biggestPt > maxPagePt) maxPagePt / biggestPt else 1f
        return BoardPage(width, height, m - content.left, m - content.top, scale)
    }

    /** Пунктов PDF на единицу холста (1 пт = 1/72 дюйма). */
    const val POINTS_PER_UNIT = 72f / 25.4f / PageSpec.UNITS_PER_MM

    /** Безопасный предел стороны страницы PDF, пт (в спецификации Adobe — 14 400). */
    const val MAX_PAGE_PT = 14_000f

    /** Вписать [content] (с полями) в прямоугольник [width]×[height] с центрированием. */
    fun fit(content: Bounds, width: Float, height: Float, marginFraction: Float = THUMB_MARGIN_FRACTION): Fit {
        val pad = maxOf(content.width, content.height) * marginFraction
        val b = content.inflate(pad)
        // Вырожденное содержимое (точка, горизонтальная линия) — не делим на ноль
        val bw = b.width.coerceAtLeast(1f)
        val bh = b.height.coerceAtLeast(1f)
        val scale = minOf(width / bw, height / bh)
        return Fit(
            scale = scale,
            dx = (width - bw * scale) / 2 - b.left * scale,
            dy = (height - bh * scale) / 2 - b.top * scale,
        )
    }
}
