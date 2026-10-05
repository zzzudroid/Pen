package com.zzzudroid.pen.notes

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.PageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Холст тетради: загрузка из файла и автосохранение. */
class NotebookViewModel(handle: SavedStateHandle) : CanvasViewModel() {

    val id: String = checkNotNull(handle["id"]) { "Не передан id тетради" }

    var meta by mutableStateOf<NotebookMeta?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var saveError by mutableStateOf<String?>(null); private set

    /** Извлечённый исходный PDF (подложка страниц с pdfPage); null — у тетради его нет. */
    var pdfSource by mutableStateOf<File?>(null); private set

    /** Ошибка автоэкспорта PDF — отдельно: .pen при этом сохранён. */
    var pdfError by mutableStateOf<String?>(null); private set

    private var loaded = false
    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val (m, pages, source) = withContext(Dispatchers.IO) {
                    // Ждём возможное незаконченное сохранение этой же тетради
                    NotebookStore.writeLock.withLock {
                        val f = NotebookStore.file(id)
                        val m = NotebookFormat.readMeta(f)
                        val source = if (m.hasPdfSource) PdfSources.extract(id) else null
                        Triple(m, NotebookFormat.readPages(f, m), source)
                    }
                }
                pdfSource = source
                replacePages(m.pageSpec, m.pages.map { pm ->
                    PageState(pm.id, spec = pm.spec, pdfPage = pm.pdfPage).also { it.strokes += pages[pm.id].orEmpty() }
                })
                meta = m
                loaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось открыть $id", e)
                loadError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    override fun onContentChanged() {
        if (!loaded) return
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    /**
     * Сохранить сразу, если есть изменения. Снимок берётся на главном потоке,
     * запись идёт в области процесса — она доживёт до конца даже после закрытия экрана.
     */
    fun saveNow() {
        startSave()
    }

    /**
     * Сохранить и дождаться окончания записи (.pen и PDF) — например, перед «Поделиться PDF».
     * Если изменений нет, всё равно ждём текущую запись, чтобы не отдать полузаписанный файл.
     */
    suspend fun saveAndAwait() {
        val job = startSave()
        if (job != null) job.join() else NotebookStore.writeLock.withLock { }
    }

    /** Запустить сохранение, если есть изменения; возвращает задачу записи или null. */
    private fun startSave(): Job? {
        saveJob?.cancel()
        val m = meta ?: return null
        if (!dirty) return null
        dirty = false
        val snapshot = pages.associate { it.id to it.strokes.toList() }
        val fallback = m.pageSpec
        val newMeta = m.copy(
            modified = System.currentTimeMillis(),
            pages = pages.map { p ->
                val spec = p.spec ?: fallback
                PageMeta(p.id, spec.width, spec.height, spec.template, p.pdfPage)
            },
        )
        meta = newMeta
        val source = pdfSource
        val file = NotebookStore.file(id)
        val pdf = NotebookStore.pdfFile(id)
        return NotebookStore.saveScope.launch {
            try {
                var pdfFailure: String? = null
                NotebookStore.writeLock.withLock {
                    NotebookFormat.write(file, newMeta, snapshot, source)
                    // PDF-копия для ПК; её сбой не делает .pen несохранённым
                    try {
                        PdfExporter.write(pdf, newMeta, snapshot, source)
                    } catch (e: Exception) {
                        Log.e(TAG, "Не удалось экспортировать PDF $id", e)
                        pdfFailure = e.message ?: e.javaClass.simpleName
                    }
                }
                withContext(Dispatchers.Main) {
                    saveError = null
                    pdfError = pdfFailure
                }
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось сохранить $id", e)
                withContext(Dispatchers.Main) {
                    dirty = true
                    saveError = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    override fun onCleared() {
        saveNow()
        super.onCleared()
    }

    companion object {
        private const val TAG = "PenNotes"
        const val AUTOSAVE_DELAY_MS = 1500L
    }
}
