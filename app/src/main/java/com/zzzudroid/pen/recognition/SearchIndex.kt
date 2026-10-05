package com.zzzudroid.pen.recognition

import android.content.Context
import android.util.Log
import com.zzzudroid.pen.notes.NotebookFormat
import com.zzzudroid.pen.notes.NotebookStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Найденная страница. [pageNumber] — с единицы. */
data class SearchHit(
    val notebookId: String,
    val title: String,
    val pageNumber: Int,
    val snippet: String,
    val highlights: List<IntRange>,
)

/**
 * Индекс распознанного текста тетрадей: filesDir/recognition/index.json
 * `{notebookId: {modified, title, pages: [{pageId, pageNumber, text}]}}`.
 * Формат .pen не меняется — индекс можно удалить, он пересоберётся.
 */
object SearchIndex {
    private const val TAG = "PenSearch"

    private class PageText(val pageId: String, val pageNumber: Int, val text: String)
    private class Entry(val modified: Long, val title: String, val pages: List<PageText>)

    private val lock = Mutex()
    private var entries: MutableMap<String, Entry>? = null

    private fun file(context: Context) = File(File(context.filesDir, "recognition"), "index.json")

    /** Сколько тетрадей сейчас в индексе. */
    val size get() = entries?.size ?: 0

    /**
     * Обновить индекс: распознать тетради, которых нет в индексе или которые изменились,
     * удалить из индекса удалённые. [onProgress] — (готово, всего тетрадей к распознаванию).
     */
    suspend fun update(
        context: Context,
        onModelState: (ModelState) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ) = lock.withLock {
        withContext(Dispatchers.IO) {
            val map = load(context)
            val notebooks = NotebookStore.list().filter { it.meta != null }
            val alive = notebooks.map { it.id }.toSet()
            val removed = map.keys.retainAll(alive)
            val stale = notebooks.filter { map[it.id]?.modified != it.meta!!.modified }
            onProgress(0, stale.size)
            if (stale.isEmpty()) {
                if (removed) save(context, map)
                return@withContext
            }

            val recognizer = HandwritingRecognizer.create(onModelState)
            try {
                stale.forEachIndexed { n, nb ->
                    try {
                        val meta = NotebookFormat.readMeta(nb.file)
                        val pages = NotebookFormat.readPages(nb.file, meta)
                        val texts = meta.pageIds.mapIndexed { i, pid ->
                            PageText(pid, i + 1, recognizer.recognizePage(pages[pid].orEmpty()))
                        }
                        map[nb.id] = Entry(meta.modified, meta.title, texts)
                    } catch (e: Exception) {
                        // Сбой одной тетради не останавливает остальные
                        Log.w(TAG, "Не удалось распознать ${nb.id}", e)
                    }
                    // Сохраняем после каждой тетради — прерванная индексация не теряет сделанное
                    save(context, map)
                    onProgress(n + 1, stale.size)
                }
            } finally {
                recognizer.close()
            }
        }
    }

    /** Поиск по индексу: все слова запроса на одной странице. Порядок — как в индексе, страницы по возрастанию. */
    suspend fun search(context: Context, query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val map = lock.withLock { load(context).toMap() }
        map.flatMap { (id, e) ->
            e.pages.mapNotNull { p ->
                TextSearch.match(p.text, query)?.let { m -> SearchHit(id, e.title, p.pageNumber, m.snippet, m.highlights) }
            }
        }.sortedWith(compareBy({ it.title.lowercase() }, { it.pageNumber }))
    }

    private fun load(context: Context): MutableMap<String, Entry> {
        entries?.let { return it }
        val map = mutableMapOf<String, Entry>()
        val f = file(context)
        if (f.exists()) {
            try {
                val root = JSONObject(f.readText())
                for (id in root.keys()) {
                    val e = root.getJSONObject(id)
                    val arr = e.getJSONArray("pages")
                    val pages = List(arr.length()) { i ->
                        val p = arr.getJSONObject(i)
                        PageText(p.getString("pageId"), p.getInt("pageNumber"), p.getString("text"))
                    }
                    map[id] = Entry(e.getLong("modified"), e.optString("title", id), pages)
                }
            } catch (e: Exception) {
                // Битый индекс не страшен — пересоберётся
                Log.w(TAG, "Индекс повреждён, пересоздаю", e)
                map.clear()
            }
        }
        entries = map
        return map
    }

    private fun save(context: Context, map: Map<String, Entry>) {
        val root = JSONObject()
        for ((id, e) in map) {
            root.put(id, JSONObject().apply {
                put("modified", e.modified)
                put("title", e.title)
                put("pages", JSONArray(e.pages.map { p ->
                    JSONObject().put("pageId", p.pageId).put("pageNumber", p.pageNumber).put("text", p.text)
                }))
            })
        }
        val f = file(context)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) tmp.delete()
    }
}
