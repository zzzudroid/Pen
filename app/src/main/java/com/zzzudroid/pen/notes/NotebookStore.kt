package com.zzzudroid.pen.notes

import android.os.Environment
import android.util.Log
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.canvas.PageTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.io.IOException

/** Тетрадь в списке: файл и метаданные (или ошибка чтения). */
data class NotebookEntry(val file: File, val meta: NotebookMeta?, val error: String? = null) {
    val id: String get() = file.name
}

/**
 * Хранилище тетрадей: файлы *.pen в папке Documents/Pen. Требует «Доступа ко всем файлам».
 * Все методы — блокирующие, вызывать на IO.
 */
object NotebookStore {
    private const val TAG = "PenNotes"

    val root: File get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Pen")

    /** Область для сохранений, которая не отменяется вместе с экраном или ViewModel. */
    val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Сохранения тетрадей идут строго по одному. */
    val writeLock = Mutex()

    fun hasAccess() = Environment.isExternalStorageManager()

    fun file(id: String) = File(root, id)

    /** PDF-копия тетради рядом с .pen: «Название.pen» → «Название.pdf». */
    fun pdfFile(id: String): File = File(root, id.removeSuffix(FileNames.EXTENSION) + ".pdf")

    fun list(): List<NotebookEntry> {
        val files = root.listFiles { f -> f.isFile && f.name.endsWith(FileNames.EXTENSION) }.orEmpty()
        return files.map { f ->
            try {
                NotebookEntry(f, NotebookFormat.readMeta(f))
            } catch (e: Exception) {
                Log.w(TAG, "Не читается ${f.name}", e)
                NotebookEntry(f, null, e.message ?: e.javaClass.simpleName)
            }
        }.sortedByDescending { it.meta?.modified ?: it.file.lastModified() }
    }

    /** Создать пустую тетрадь с одной страницей; возвращает id (имя файла). */
    fun create(title: String, template: PageTemplate): String {
        root.mkdirs()
        val name = FileNames.unique(title) { candidate -> nameTaken(candidate) }
        val now = System.currentTimeMillis()
        val spec = PageSpec.a4(template)
        val pageId = CanvasViewModel.newPageId()
        val meta = NotebookMeta(title.trim().ifEmpty { "Без названия" }, template, spec.width, spec.height, now, now, listOf(pageId))
        NotebookFormat.write(File(root, name), meta, emptyMap())
        // PDF пустой тетради — сразу, чтобы копия для ПК была с самого начала
        try {
            PdfExporter.write(pdfFile(name), meta, emptyMap())
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось создать PDF для $name", e)
        }
        return name
    }

    /** Переименовать: меняется и название в meta.json, и имя файла. Возвращает новый id. */
    fun rename(id: String, newTitle: String): String {
        val src = file(id)
        val meta = NotebookFormat.readMeta(src)
        val pages = NotebookFormat.readPages(src, meta)
        val name = FileNames.unique(newTitle) { candidate ->
            !candidate.equals(id, ignoreCase = true) && nameTaken(candidate)
        }
        val newMeta = meta.copy(title = newTitle.trim().ifEmpty { meta.title })
        if (name.equals(id, ignoreCase = true)) {
            // /sdcard не различает регистр: пишем в тот же файл, затем меняем регистр имени через промежуточное
            NotebookFormat.write(src, newMeta, pages)
            if (name != id) {
                val tmp = File(root, "$name.rename")
                if (!src.renameTo(tmp) || !tmp.renameTo(File(root, name))) throw IOException("Не удалось переименовать $id")
            }
        } else {
            NotebookFormat.write(File(root, name), newMeta, pages)
            if (!src.delete()) throw IOException("Не удалось удалить старый файл ${src.name}")
        }
        renamePdf(id, name)
        return name
    }

    /**
     * Переименовать PDF-копию вслед за .pen. Сбой не критичен: PDF пересоздаётся
     * при следующем сохранении тетради, поэтому только логируем.
     */
    private fun renamePdf(oldId: String, newId: String) {
        val src = pdfFile(oldId)
        if (!src.exists() || oldId == newId) return
        val dst = pdfFile(newId)
        val ok = if (oldId.equals(newId, ignoreCase = true)) {
            // /sdcard не различает регистр — меняем регистр через промежуточное имя
            val tmp = File(root, dst.name + ".rename")
            src.renameTo(tmp) && tmp.renameTo(dst)
        } else {
            src.renameTo(dst)
        }
        if (!ok) Log.w(TAG, "Не удалось переименовать ${src.name} → ${dst.name}")
    }

    fun delete(id: String) {
        if (!file(id).delete()) throw IOException("Не удалось удалить $id")
        // PDF-копии может и не быть — это не ошибка
        val pdf = pdfFile(id)
        if (pdf.exists() && !pdf.delete()) Log.w(TAG, "Не удалось удалить ${pdf.name}")
    }

    private fun nameTaken(name: String) =
        root.list().orEmpty().any { it.equals(name, ignoreCase = true) }
}
