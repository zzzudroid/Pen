package com.zzzudroid.pen.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.PageTemplate
import java.io.File
import java.io.IOException

/** Работа с исходным PDF через системный PdfRenderer. Блокирующие вызовы — только на IO. */
object PdfRendering {

    /** Размеры страниц PDF в пунктах. */
    fun pageSizesPt(file: File): List<Pair<Int, Int>> = open(file) { r ->
        List(r.pageCount) { i -> r.openPage(i).use { it.width to it.height } }
    }

    /** Растр страницы [index] размером [width]×[height] на белом фоне, или null при ошибке. */
    fun renderPage(file: File, index: Int, width: Int, height: Int): Bitmap? = try {
        open(file) { r -> renderPage(r, index, width, height) }
    } catch (e: Exception) {
        Log.w(TAG, "Не удалось отрисовать страницу $index из ${file.name}", e)
        null
    }

    /** Растр страницы из уже открытого рендерера (вызывающий отвечает за синхронизацию). */
    fun renderPage(r: PdfRenderer, index: Int, width: Int, height: Int): Bitmap? {
        if (index !in 0 until r.pageCount || width <= 0 || height <= 0) return null
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        r.openPage(index).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        return bmp
    }

    fun <T> open(file: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use(block)
        }

    private const val TAG = "PenPdf"
}

/** Импорт PDF: тетрадь, где каждая страница — страница PDF своего размера с подложкой. */
fun NotebookStore.importPdf(context: Context, uri: Uri, title: String): String {
    root.mkdirs()
    // PdfRenderer нужен seekable-файл — копируем источник во временный файл
    val tmp = File(context.cacheDir, "import-${System.currentTimeMillis()}.pdf")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Не удалось открыть файл")

        val sizes = try {
            PdfRendering.pageSizesPt(tmp)
        } catch (e: Exception) {
            throw IOException("Файл не читается как PDF: ${e.message}", e)
        }
        if (sizes.isEmpty()) throw IOException("В PDF нет страниц")

        val pages = sizes.mapIndexed { i, (w, h) ->
            PageMeta(
                id = CanvasViewModel.newPageId(),
                width = PdfUnits.ptToUnits(w.toFloat()),
                height = PdfUnits.ptToUnits(h.toFloat()),
                template = PageTemplate.BLANK,
                pdfPage = i,
            )
        }
        // Общий размер тетради (для добавляемых чистых страниц) — по первой странице PDF
        val first = pages.first()
        val now = System.currentTimeMillis()
        val meta = NotebookMeta(
            title = title.trim().ifEmpty { "Без названия" },
            template = PageTemplate.BLANK,
            pageWidth = first.width,
            pageHeight = first.height,
            created = now,
            modified = now,
            pages = pages,
            hasPdfSource = true,
        )
        val name = FileNames.unique(meta.title) { candidate ->
            root.list().orEmpty().any { it.equals(candidate, ignoreCase = true) }
        }
        NotebookFormat.write(file(name), meta, emptyMap(), pdfSource = tmp)
        try {
            PdfExporter.write(pdfFile(name), meta, emptyMap(), pdfSource = tmp)
        } catch (e: Exception) {
            Log.w("PenNotes", "Не удалось создать PDF для $name", e)
        }
        return name
    } finally {
        tmp.delete()
    }
}

/**
 * Извлечённые PDF-подложки тетрадей: cacheDir/pdf-src/<имя>.pdf.
 * Нужна инициализация [init] (из MainActivity.onCreate); без неё — папка .cache рядом с тетрадями.
 */
object PdfSources {
    @Volatile
    private var cacheRoot: File? = null

    fun init(context: Context) {
        cacheRoot = File(context.applicationContext.cacheDir, "pdf-src")
    }

    private fun dir(): File = cacheRoot ?: File(NotebookStore.root, ".cache/pdf-src")

    /**
     * Извлечь source.pdf тетради [id] (блокирующий, IO). Переиспользует ранее извлечённый файл,
     * если .pen не новее. null — у тетради нет подложки.
     */
    fun extract(id: String): File? {
        val pen = NotebookStore.file(id)
        val dst = File(dir(), id.removeSuffix(FileNames.EXTENSION) + ".pdf")
        if (dst.exists() && dst.lastModified() >= pen.lastModified()) return dst
        return if (NotebookFormat.extractPdfSource(pen, dst)) dst else null
    }
}
