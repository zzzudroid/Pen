package com.zzzudroid.pen.notes

import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import androidx.ink.geometry.outlinesToPath
import androidx.ink.strokes.Stroke
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.canvas.drawPage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Экспорт тетради в PDF: по странице PDF на страницу тетради, штрихи — векторными контурами.
 * CanvasStrokeRenderer не используем: на канвасе PdfDocument меши Ink не векторизуются.
 */
object PdfExporter {

    /** Пунктов PDF на мировую единицу (1 пт = 1/72 дюйма). */
    private const val POINTS_PER_UNIT = 72f / 25.4f / PageSpec.UNITS_PER_MM

    /** Записать PDF тетради атомарно (pdf.tmp → rename). Блокирующий, вызывать на IO. */
    fun write(pdf: File, meta: NotebookMeta, pages: Map<String, List<Stroke>>) {
        val spec = meta.pageSpec
        val widthPt = (spec.width * POINTS_PER_UNIT).roundToInt()
        val heightPt = (spec.height * POINTS_PER_UNIT).roundToInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val doc = PdfDocument()
        val tmp = File(pdf.parentFile, pdf.name + ".tmp")
        try {
            meta.pageIds.forEachIndexed { index, id ->
                val info = PdfDocument.PageInfo.Builder(widthPt, heightPt, index + 1).create()
                val page = doc.startPage(info)
                val c = page.canvas
                c.scale(POINTS_PER_UNIT, POINTS_PER_UNIT)
                drawPage(c, spec, POINTS_PER_UNIT)
                for (stroke in pages[id].orEmpty()) {
                    paint.color = stroke.brush.colorIntArgb
                    val shape = stroke.shape
                    for (group in 0 until shape.getRenderGroupCount()) {
                        val path = shape.outlinesToPath(group)
                        path.fillType = Path.FillType.WINDING
                        c.drawPath(path, paint)
                    }
                }
                doc.finishPage(page)
            }
            FileOutputStream(tmp).use { fos ->
                doc.writeTo(fos)
                fos.flush()
                fos.fd.sync()
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            doc.close()
        }
        if (!tmp.renameTo(pdf)) {
            tmp.delete()
            throw IOException("Не удалось заменить ${pdf.name}")
        }
    }
}
