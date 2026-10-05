package com.zzzudroid.pen.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import com.zzzudroid.pen.canvas.Brushes
import com.zzzudroid.pen.canvas.ViewState
import com.zzzudroid.pen.canvas.LinkTarget
import com.zzzudroid.pen.canvas.DocLink
import com.zzzudroid.pen.canvas.Bookmark
import com.zzzudroid.pen.canvas.CanvasTool
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.canvas.PageTemplate
import com.zzzudroid.pen.canvas.StrokeOps
import com.zzzudroid.pen.canvas.drawPage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Описание одной страницы в meta.json: размер, шаблон, страница PDF-подложки. */
data class PageMeta(
    val id: String,
    val width: Float,
    val height: Float,
    val template: PageTemplate,
    val pdfPage: Int? = null,
) {
    val spec get() = PageSpec(width, height, template)
}

/** Метаданные тетради — содержимое meta.json. */
data class NotebookMeta(
    val title: String,
    val template: PageTemplate,
    val pageWidth: Float,
    val pageHeight: Float,
    val created: Long,
    val modified: Long,
    val pages: List<PageMeta>,
    /** В zip лежит исходный PDF (source.pdf) — подложка страниц с pdfPage. */
    val hasPdfSource: Boolean = false,
    /** Тетрадь или бесконечная доска (у доски одна страница без границ). */
    val kind: DocKind = DocKind.NOTEBOOK,
    /** Последний вид доски; null — открыть с видом по умолчанию. */
    val boardView: BoardView? = null,
    /** Постоянный идентификатор документа (UUID): не меняется при переименовании, на него ссылаются ссылки. */
    val docId: String = UUID.randomUUID().toString(),
    /** Ссылки всех страниц. */
    val links: List<DocLink> = emptyList(),
    /** Закладки доски. */
    val bookmarks: List<Bookmark> = emptyList(),
) {
    val isBoard get() = kind == DocKind.BOARD

    /** Общий размер и шаблон тетради — для новых обычных страниц. */
    val pageSpec get() = PageSpec(pageWidth, pageHeight, template)
    val pageIds: List<String> get() = pages.map { it.id }
}

/**
 * Формат .pen, версия 3 — zip (v3 добавляет в meta.json docId, links и bookmarks):
 *  meta.json            — метаданные (читаемый JSON), pageInfo — размеры и подложки страниц
 *  pages/<id>.strokes   — штрихи страницы (DataOutputStream, см. [writeStrokes])
 *  thumb.png            — миниатюра первой страницы
 *  source.pdf           — исходный PDF, если тетрадь создана импортом PDF
 * Версия 1 отличается отсутствием pageInfo и source.pdf — читается как тетрадь с общим размером страниц.
 * Доска (kind = "board") — тот же формат: одна страница с размером 0×0 (без границ) и boardView.
 */
object NotebookFormat {
    const val FORMAT_VERSION = 3
    private const val META = "meta.json"
    private const val THUMB = "thumb.png"
    private const val SOURCE_PDF = "source.pdf"
    private const val THUMB_WIDTH = 300
    private fun pageEntry(id: String) = "pages/$id.strokes"

    // --- Запись ---

    /**
     * Записать тетрадь атомарно: во временный файл, затем переименование поверх.
     * [pdfSource] — исходный PDF-подложка; копируется в zip как source.pdf.
     */
    fun write(file: File, meta: NotebookMeta, pages: Map<String, List<Stroke>>, pdfSource: File? = null) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        val finalMeta = meta.copy(hasPdfSource = pdfSource != null)
        FileOutputStream(tmp).use { fos ->
            ZipOutputStream(fos.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(META))
                zip.write(metaToJson(finalMeta).toString(2).toByteArray())
                zip.closeEntry()
                for (id in finalMeta.pageIds) {
                    zip.putNextEntry(ZipEntry(pageEntry(id)))
                    zip.write(writeStrokes(pages[id].orEmpty()))
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(THUMB))
                val firstStrokes = pages[finalMeta.pageIds.firstOrNull()].orEmpty()
                val thumb = if (finalMeta.isBoard) renderBoardThumbnail(firstStrokes)
                else renderThumbnail(finalMeta.pages.firstOrNull(), finalMeta.pageSpec, firstStrokes, pdfSource)
                thumb.compress(Bitmap.CompressFormat.PNG, 100, zip)
                thumb.recycle()
                zip.closeEntry()
                if (pdfSource != null) {
                    zip.putNextEntry(ZipEntry(SOURCE_PDF))
                    pdfSource.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.flush()
                fos.fd.sync()
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Не удалось заменить ${file.name}")
        }
    }

    private fun metaToJson(m: NotebookMeta) = MetaJson.toJson(m)

    /** Штрихи страницы: число штрихов, затем для каждого — кисть и входные точки Ink. */
    private fun writeStrokes(strokes: List<Stroke>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(strokes.size)
            for (s in strokes) {
                d.writeUTF(Brushes.toolOf(s.brush.family).id)
                d.writeInt(s.brush.colorIntArgb)
                d.writeFloat(s.brush.size)
                d.writeFloat(s.brush.epsilon)
                val inputs = ByteArrayOutputStream()
                s.inputs.encode(inputs)
                d.writeInt(inputs.size())
                inputs.writeTo(d)
            }
        }
        return out.toByteArray()
    }

    // --- Чтение ---

    fun readMeta(file: File): NotebookMeta = ZipFile(file).use { zip ->
        val text = zip.getInputStream(zip.getEntry(META) ?: throw IOException("Нет $META"))
            .bufferedReader().readText()
        MetaJson.parse(text, file.name, file.lastModified(), hasPdfSource = zip.getEntry(SOURCE_PDF) != null)
    }

    fun readPages(file: File, meta: NotebookMeta): Map<String, List<Stroke>> = ZipFile(file).use { zip ->
        meta.pageIds.associateWith { id ->
            val entry = zip.getEntry(pageEntry(id)) ?: return@associateWith emptyList()
            readStrokes(zip.getInputStream(entry).readBytes())
        }
    }

    /** Скопировать source.pdf из тетради в [dst]; false — если подложки нет. */
    fun extractPdfSource(file: File, dst: File): Boolean = ZipFile(file).use { zip ->
        val entry = zip.getEntry(SOURCE_PDF) ?: return false
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, dst.name + ".tmp")
        zip.getInputStream(entry).use { input: InputStream -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(dst)) {
            tmp.delete()
            throw IOException("Не удалось извлечь PDF-подложку")
        }
        true
    }

    private fun readStrokes(bytes: ByteArray): List<Stroke> = DataInputStream(ByteArrayInputStream(bytes)).use { d ->
        val count = d.readInt()
        List(count) {
            val tool = CanvasTool.entries.firstOrNull { it.id == d.readUTF() } ?: CanvasTool.PEN
            val color = d.readInt()
            val size = d.readFloat()
            val epsilon = d.readFloat()
            val data = ByteArray(d.readInt()).also { d.readFully(it) }
            val inputs = StrokeInputBatch.decode(ByteArrayInputStream(data))
            val family = Brushes.family(tool) ?: Brushes.pen
            Stroke(Brush.createWithColorIntArgb(family, color, size, epsilon), inputs)
        }
    }

    fun readThumbnail(file: File): Bitmap? = try {
        ZipFile(file).use { zip ->
            zip.getEntry(THUMB)?.let { BitmapFactory.decodeStream(zip.getInputStream(it)) }
        }
    } catch (e: IOException) {
        null
    }

    // --- Миниатюра ---

    private val renderer by lazy { CanvasStrokeRenderer.create() }

    /** Миниатюра доски 300×400: содержимое вписано с полями; пустая доска — точечная сетка. */
    private fun renderBoardThumbnail(strokes: List<Stroke>): Bitmap {
        val w = THUMB_WIDTH
        val h = THUMB_WIDTH * 4 / 3
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BOARD_BACKGROUND)
        val content = StrokeOps.bounds(strokes)
        if (content == null) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BOARD_DOT }
            val step = 20f
            var y = step
            while (y < h) {
                var x = step
                while (x < w) { c.drawCircle(x, y, 1.5f, dot); x += step }
                y += step
            }
            return bmp
        }
        val fit = BoardLayout.fit(content, w.toFloat(), h.toFloat())
        val m = Matrix().apply {
            setScale(fit.scale, fit.scale)
            postTranslate(fit.dx, fit.dy)
        }
        // Матрицу применяем к холсту сами — Ink использует её параметр только для расчёта толщины
        c.save()
        c.concat(m)
        for (s in strokes) renderer.draw(c, s, m)
        c.restore()
        return bmp
    }

    private val BOARD_BACKGROUND = Color.rgb(0xFB, 0xFA, 0xF7)
    private val BOARD_DOT = Color.rgb(0xC8, 0xC4, 0xBC)

    private fun renderThumbnail(first: PageMeta?, fallback: PageSpec, strokes: List<Stroke>, pdfSource: File?): Bitmap {
        val spec = first?.spec ?: fallback
        val scale = THUMB_WIDTH / spec.width
        val bmp = Bitmap.createBitmap(THUMB_WIDTH, (spec.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pdfPage = first?.pdfPage
        if (pdfPage != null && pdfSource != null) {
            // Подложка первой страницы — растр исходного PDF
            PdfRendering.renderPage(pdfSource, pdfPage, bmp.width, bmp.height)?.let { page ->
                c.drawBitmap(page, 0f, 0f, null)
                page.recycle()
            }
            c.scale(scale, scale)
        } else {
            c.scale(scale, scale)
            drawPage(c, spec, scale)
        }
        // Холст уже промасштабирован выше; Ink сообщаем то же преобразование
        val canvasMatrix = Matrix().apply { setScale(scale, scale) }
        for (s in strokes) renderer.draw(c, s, canvasMatrix)
        return bmp
    }
}
