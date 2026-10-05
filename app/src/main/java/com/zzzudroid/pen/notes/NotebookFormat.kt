package com.zzzudroid.pen.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import com.zzzudroid.pen.canvas.Brushes
import com.zzzudroid.pen.canvas.CanvasTool
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.canvas.PageTemplate
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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Метаданные тетради — содержимое meta.json. */
data class NotebookMeta(
    val title: String,
    val template: PageTemplate,
    val pageWidth: Float,
    val pageHeight: Float,
    val created: Long,
    val modified: Long,
    val pageIds: List<String>,
) {
    val pageSpec get() = PageSpec(pageWidth, pageHeight, template)
}

/**
 * Формат .pen, версия 1 — zip:
 *  meta.json            — метаданные (читаемый JSON)
 *  pages/<id>.strokes   — штрихи страницы (DataOutputStream, см. [writeStrokes])
 *  thumb.png            — миниатюра первой страницы
 */
object NotebookFormat {
    const val FORMAT_VERSION = 1
    private const val META = "meta.json"
    private const val THUMB = "thumb.png"
    private const val THUMB_WIDTH = 300
    private fun pageEntry(id: String) = "pages/$id.strokes"

    // --- Запись ---

    /** Записать тетрадь атомарно: во временный файл, затем переименование поверх. */
    fun write(file: File, meta: NotebookMeta, pages: Map<String, List<Stroke>>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { fos ->
            ZipOutputStream(fos.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(META))
                zip.write(metaToJson(meta).toString(2).toByteArray())
                zip.closeEntry()
                for (id in meta.pageIds) {
                    zip.putNextEntry(ZipEntry(pageEntry(id)))
                    zip.write(writeStrokes(pages[id].orEmpty()))
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(THUMB))
                val thumb = renderThumbnail(meta.pageSpec, pages[meta.pageIds.firstOrNull()].orEmpty())
                thumb.compress(Bitmap.CompressFormat.PNG, 100, zip)
                thumb.recycle()
                zip.closeEntry()
                zip.flush()
                fos.fd.sync()
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Не удалось заменить ${file.name}")
        }
    }

    private fun metaToJson(m: NotebookMeta) = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("app", "Pen")
        put("title", m.title)
        put("template", m.template.id)
        put("pageWidth", m.pageWidth.toDouble())
        put("pageHeight", m.pageHeight.toDouble())
        put("unitsPerMm", PageSpec.UNITS_PER_MM.toDouble())
        put("created", m.created)
        put("modified", m.modified)
        put("pages", JSONArray(m.pageIds))
    }

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
        val j = JSONObject(text)
        val version = j.optInt("formatVersion", 1)
        if (version > FORMAT_VERSION) throw IOException("Файл новой версии формата ($version), обновите приложение")
        val ids = j.getJSONArray("pages")
        NotebookMeta(
            title = j.optString("title", file.nameWithoutExtension),
            template = PageTemplate.byId(j.optString("template")),
            pageWidth = j.getDouble("pageWidth").toFloat(),
            pageHeight = j.getDouble("pageHeight").toFloat(),
            created = j.optLong("created", file.lastModified()),
            modified = j.optLong("modified", file.lastModified()),
            pageIds = List(ids.length()) { ids.getString(it) },
        )
    }

    fun readPages(file: File, meta: NotebookMeta): Map<String, List<Stroke>> = ZipFile(file).use { zip ->
        meta.pageIds.associateWith { id ->
            val entry = zip.getEntry(pageEntry(id)) ?: return@associateWith emptyList()
            readStrokes(zip.getInputStream(entry).readBytes())
        }
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

    private fun renderThumbnail(spec: PageSpec, strokes: List<Stroke>): Bitmap {
        val scale = THUMB_WIDTH / spec.width
        val bmp = Bitmap.createBitmap(THUMB_WIDTH, (spec.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        drawPage(c, spec, scale)
        val identity = Matrix()
        for (s in strokes) renderer.draw(c, s, identity)
        return bmp
    }
}
