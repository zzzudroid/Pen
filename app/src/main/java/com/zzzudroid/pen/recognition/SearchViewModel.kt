package com.zzzudroid.pen.recognition

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zzzudroid.pen.notes.NotebookStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Состояние экрана поиска: запрос, результаты, индексация и модель распознавания. */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    var query by mutableStateOf("")
    var hits by mutableStateOf<List<SearchHit>>(emptyList()); private set
    var indexing by mutableStateOf(false); private set
    var progressDone by mutableIntStateOf(0); private set
    var progressTotal by mutableIntStateOf(0); private set
    var modelState by mutableStateOf<ModelState?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    val hasAccess get() = NotebookStore.hasAccess()

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        query = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Небольшая пауза — не искать на каждую букву
            delay(SEARCH_DEBOUNCE_MS)
            runSearch()
        }
    }

    /** Обновить индекс (распознать новые и изменённые тетради), затем повторить поиск. */
    fun updateIndex() {
        if (indexing || !hasAccess) return
        indexing = true
        error = null
        viewModelScope.launch {
            try {
                SearchIndex.update(
                    getApplication(),
                    onModelState = { s -> viewModelScope.launch(Dispatchers.Main) { modelState = s } },
                    onProgress = { done, total ->
                        viewModelScope.launch(Dispatchers.Main) {
                            progressDone = done
                            progressTotal = total
                        }
                    },
                )
            } catch (e: Throwable) {
                // ML Kit без Google Play Services или без сети для скачивания модели — не падаем
                Log.w("PenSearch", "Индексация не удалась", e)
                error = "Распознавание недоступно: ${e.message ?: e.javaClass.simpleName}"
                modelState = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                indexing = false
            }
            runSearch()
        }
    }

    private suspend fun runSearch() {
        hits = if (query.isBlank()) emptyList() else SearchIndex.search(getApplication(), query)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
