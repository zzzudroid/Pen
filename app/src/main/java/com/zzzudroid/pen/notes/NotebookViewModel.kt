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

/** Холст тетради: загрузка из файла и автосохранение. */
class NotebookViewModel(handle: SavedStateHandle) : CanvasViewModel() {

    val id: String = checkNotNull(handle["id"]) { "Не передан id тетради" }

    var meta by mutableStateOf<NotebookMeta?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var saveError by mutableStateOf<String?>(null); private set

    private var loaded = false
    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val (m, pages) = withContext(Dispatchers.IO) {
                    // Ждём возможное незаконченное сохранение этой же тетради
                    NotebookStore.writeLock.withLock {
                        val f = NotebookStore.file(id)
                        val m = NotebookFormat.readMeta(f)
                        m to NotebookFormat.readPages(f, m)
                    }
                }
                replacePages(m.pageSpec, m.pageIds.map { pid ->
                    PageState(pid).also { it.strokes += pages[pid].orEmpty() }
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
        saveJob?.cancel()
        val m = meta ?: return
        if (!dirty) return
        dirty = false
        val snapshot = pages.associate { it.id to it.strokes.toList() }
        val newMeta = m.copy(modified = System.currentTimeMillis(), pageIds = pages.map { it.id })
        meta = newMeta
        val file = NotebookStore.file(id)
        NotebookStore.saveScope.launch {
            try {
                NotebookStore.writeLock.withLock { NotebookFormat.write(file, newMeta, snapshot) }
                withContext(Dispatchers.Main) { saveError = null }
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
