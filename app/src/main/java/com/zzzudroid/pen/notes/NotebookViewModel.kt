package com.zzzudroid.pen.notes

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zzzudroid.pen.BuildConfig
import com.zzzudroid.pen.canvas.Bookmark
import com.zzzudroid.pen.canvas.CanvasViewModel
import com.zzzudroid.pen.canvas.DebugStress
import com.zzzudroid.pen.canvas.PageState
import com.zzzudroid.pen.canvas.PageTemplate
import com.zzzudroid.pen.canvas.ViewState
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

    /** Страница, на которой открыть тетрадь (из поиска, ссылки, обратной ссылки). */
    private val startPage: Int = handle["page"] ?: 0

    // Куда прийти из ссылки: закладка доски, прямой вид «масштаб,сдвигX,сдвигY» или точка «x,y» в центр экрана
    private val startBookmark: String? = handle["bm"]
    private val startView: ViewState? = parseFloats(handle["view"], 3)?.let { ViewState(it[0], it[1], it[2]) }
    private val startFocus: List<Float>? = parseFloats(handle["focus"], 2)

    /** Закладки доски. */
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList()); private set

    /** Короткое сообщение для показа пользователем (например, «Закладка не найдена»); интерфейс обнуляет после показа. */
    var notice by mutableStateOf<String?>(null)

    var meta by mutableStateOf<NotebookMeta?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set
    var saveError by mutableStateOf<String?>(null); private set

    /** Извлечённый исходный PDF (подложка страниц с pdfPage); null — у тетради его нет. */
    var pdfSource by mutableStateOf<File?>(null); private set

    /** Ошибка автоэкспорта PDF — отдельно: .pen при этом сохранён. */
    var pdfError by mutableStateOf<String?>(null); private set

    /** Документ — бесконечная доска (одна страница без границ, без добавления страниц). */
    val isBoard: Boolean get() = meta?.isBoard == true

    private var loaded = false
    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val (m, pages, source) = readFromDisk()
                applyLoaded(m, pages, source)
                // Только что созданный пустой документ открывается с включённой блокировкой перемещения поля — сразу писать
                if (pages.values.all { it.isEmpty() } && System.currentTimeMillis() - m.created < NEW_DOC_WINDOW_MS && com.zzzudroid.pen.settings.AppSettings.lockNewDocs) viewLocked = true
                // Открыть на странице из поиска (индекс с нуля), если она есть
                startPage.takeIf { it in this@NotebookViewModel.pages.indices }?.let { goToPage(it) }
                loaded = true
                // Место, куда ведёт ссылка: закладка / вид / точка
                if (startBookmark != null) {
                    bookmarks.firstOrNull { it.id == startBookmark }?.let { goToView(it.view) }
                        ?: run { notice = "Закладка не найдена" }
                }
                startView?.let { goToView(it) }
                startFocus?.let { focusOn(it[0], it[1]) }
                // Нагрузочный тест (только отладочная сборка)
                if (BuildConfig.DEBUG) {
                    val n = DebugStress.take()
                    if (n > 0) {
                        val span = DebugStress.span.takeIf { it > 0f } ?: if (m.isBoard) 20000f else 1000f
                        debugAddStrokes(DebugStress.strokes(n, span))
                        if (DebugStress.viewScale > 0f) restoreView(DebugStress.viewScale, 0f, 0f)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось открыть $id", e)
                loadError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    /** Отпечаток файла (время изменения, размер) на момент последнего чтения или записи нами. */
    @Volatile private var diskStamp: Pair<Long, Long>? = null

    /** Прочитать документ с диска (после возможной незаконченной записи) и запомнить отпечаток файла. */
    private suspend fun readFromDisk(): Triple<NotebookMeta, Map<String, List<androidx.ink.strokes.Stroke>>, File?> =
        withContext(Dispatchers.IO) {
            NotebookStore.writeLock.withLock {
                val f = NotebookStore.file(id)
                val m = NotebookFormat.readMeta(f)
                val source = if (m.hasPdfSource) PdfSources.extract(id) else null
                val pages = NotebookFormat.readPages(f, m)
                diskStamp = f.lastModified() to f.length()
                Triple(m, pages, source)
            }
        }

    /** Применить прочитанное: страницы со штрихами и ссылками, закладки, метаданные. */
    private fun applyLoaded(m: NotebookMeta, pages: Map<String, List<androidx.ink.strokes.Stroke>>, source: File?) {
        pdfSource = source
        if (m.isBoard) {
            // Доска: одна безграничная страница (pageSpec = null), вид — как при закрытии
            val pm = m.pages.first()
            replacePages(null, listOf(PageState(pm.id).also { it.strokes += pages[pm.id].orEmpty() }))
            m.boardView?.let { restoreView(it.scale, it.tx, it.ty) }
        } else {
            replacePages(m.pageSpec, m.pages.map { pm ->
                PageState(pm.id, spec = pm.spec, pdfPage = pm.pdfPage).also { it.strokes += pages[pm.id].orEmpty() }
            })
        }
        // Ссылки страниц и закладки доски — из метаданных
        for (ps in this.pages) ps.links += m.links.filter { it.pageId == ps.id }
        bookmarks = m.bookmarks
        meta = m
    }

    /**
     * Документ могли изменить другой экземпляр редактора (ссылка вела на него, а потом обратно) или внешняя синхронизация.
     * Если файл на диске не тот, что мы читали или писали, и у нас нет несохранённых правок — перечитать, чтобы не затереть чужие изменения.
     */
    fun reloadIfChangedOnDisk() {
        if (!loaded || dirty) return
        viewModelScope.launch {
            val f = NotebookStore.file(id)
            val stamp = withContext(Dispatchers.IO) { f.lastModified() to f.length() }
            if (stamp == diskStamp) return@launch
            try {
                val keepPage = pageIndex
                val (m, pages, source) = readFromDisk()
                applyLoaded(m, pages, source)
                if (keepPage in this@NotebookViewModel.pages.indices) goToPage(keepPage)
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось перечитать $id", e)
            }
        }
    }

    /** Перенести вид в [v] (для ссылки на место того же документа). */
    fun showView(v: ViewState) = goToView(v)

    /** Заменить теги открытого документа (сохраняются обычным автосохранением). */
    fun setTags(tags: List<String>) {
        val m = meta ?: return
        meta = m.copy(tags = Tags.clean(tags))
        dirty = true
        onContentChanged()
    }

    override fun onContentChanged() {
        if (!loaded) return
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            // На больших документах сохранение дорогое: пауза растёт вместе с временем прошлой записи,
            // чтобы запись не шла почти непрерывно, пока человек рисует
            delay(maxOf(AUTOSAVE_DELAY_MS, lastPenSaveMs * 3))
            startSave(force = false)
        }
    }

    // Адаптивное автосохранение: время прошлых записей и момент последнего PDF
    @Volatile private var lastPenSaveMs = 0L
    @Volatile private var lastPdfAt = 0L
    @Volatile private var lastPdfMs = 0L

    // Автосохранение пропустило PDF — при ближайшем принудительном сохранении его нужно обновить
    @Volatile private var pdfStale = false

    /**
     * Сохранить сразу, если есть изменения. Снимок берётся на главном потоке,
     * запись идёт в области процесса — она доживёт до конца даже после закрытия экрана.
     */
    fun saveNow() {
        startSave(force = true)
    }

    /**
     * Сохранить и дождаться окончания записи (.pen и PDF) — например, перед «Поделиться PDF».
     * Если изменений нет, всё равно ждём текущую запись, чтобы не отдать полузаписанный файл.
     */
    suspend fun saveAndAwait() {
        val job = startSave(force = true)
        if (job != null) job.join() else NotebookStore.writeLock.withLock { }
    }

    /** Запустить сохранение, если есть изменения; возвращает задачу записи или null. */
    private fun startSave(force: Boolean): Job? {
        saveJob?.cancel()
        val m = meta ?: return null
        // У доски сохраняем и вид: сдвиг/масштаб без правок тоже стоит записать
        val view = if (m.isBoard) BoardView(viewport.scale, viewport.tx, viewport.ty) else null
        // Принудительное сохранение (выход, сворачивание, «поделиться») обновляет и пропущенный ранее PDF
        if (!dirty && view == m.boardView && !(force && pdfStale)) return null
        dirty = false
        val snapshot = pages.associate { it.id to it.strokes.toList() }
        val fallback = m.pageSpec
        val newMeta = m.copy(
            modified = System.currentTimeMillis(),
            pages = pages.map { p ->
                if (m.isBoard) PageMeta(p.id, 0f, 0f, PageTemplate.BLANK)
                else (p.spec ?: fallback).let { spec -> PageMeta(p.id, spec.width, spec.height, spec.template, p.pdfPage) }
            },
            boardView = view,
            links = pages.flatMap { it.links },
            bookmarks = bookmarks,
        )
        meta = newMeta
        val source = pdfSource
        val file = NotebookStore.file(id)
        val pdf = NotebookStore.pdfFile(id)
        return NotebookStore.saveScope.launch {
            try {
                var pdfFailure: String? = null
                NotebookStore.writeLock.withLock {
                    val t0 = System.nanoTime()
                    NotebookFormat.write(file, newMeta, snapshot, source)
                    diskStamp = file.lastModified() to file.length()
                    val t1 = System.nanoTime()
                    lastPenSaveMs = (t1 - t0) / 1_000_000
                    // PDF-копия для ПК; её сбой не делает .pen несохранённым. Автосохранение на больших документах
                    // обновляет PDF не чаще, чем раз в десять его собственных длительностей; принудительное — всегда
                    val pdfDue = force || System.currentTimeMillis() - lastPdfAt >= lastPdfMs * 10
                    if (pdfDue) {
                        try {
                            val p0 = System.nanoTime()
                            PdfExporter.write(pdf, newMeta, snapshot, source)
                            lastPdfMs = (System.nanoTime() - p0) / 1_000_000
                            lastPdfAt = System.currentTimeMillis()
                            pdfStale = false
                            Log.i("PenPerf", "сохранение: .pen $lastPenSaveMs мс, pdf $lastPdfMs мс, штрихов ${snapshot.values.sumOf { it.size }}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Не удалось экспортировать PDF $id", e)
                            pdfFailure = e.message ?: e.javaClass.simpleName
                        }
                    } else {
                        pdfStale = true
                        Log.i("PenPerf", "сохранение: .pen $lastPenSaveMs мс, pdf отложен, штрихов ${snapshot.values.sumOf { it.size }}")
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

    // --- Закладки доски ---

    /** Поставить закладку с запомненным текущим видом доски. */
    fun addBookmark(name: String): Bookmark {
        val b = Bookmark(Bookmark.newId(), name.trim().ifEmpty { "Закладка" }, ViewState(viewport.scale, viewport.tx, viewport.ty))
        bookmarks = bookmarks + b
        onContentChanged()
        return b
    }

    fun renameBookmark(id: String, name: String) {
        bookmarks = bookmarks.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
        onContentChanged()
    }

    fun deleteBookmark(id: String) {
        bookmarks = bookmarks.filterNot { it.id == id }
        onContentChanged()
    }

    /** Перенести вид в место закладки; false — закладки нет. */
    fun goToBookmark(id: String): Boolean {
        val b = bookmarks.firstOrNull { it.id == id } ?: return false
        goToView(b.view)
        return true
    }

    private fun goToView(v: ViewState) {
        restoreView(v.scale, v.tx, v.ty)
        viewportChanged()
    }

    // У доски нет страниц — добавлять и удалять нечего
    override fun addPageAfterCurrent() {
        if (!isBoard) super.addPageAfterCurrent()
    }

    override fun deleteCurrentPage(): Boolean = if (isBoard) false else super.deleteCurrentPage()

    override fun onCleared() {
        saveNow()
        super.onCleared()
    }

    companion object {
        /** Документ моложе этого считается «только что созданным», мс. */
        private const val NEW_DOC_WINDOW_MS = 60_000L

        private const val TAG = "PenNotes"
        const val AUTOSAVE_DELAY_MS = 1500L
    }
}

/** «1.5,2,3» → [1.5, 2, 3], если чисел ровно [count]; иначе null. */
private fun parseFloats(text: String?, count: Int): List<Float>? =
    text?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == count }
