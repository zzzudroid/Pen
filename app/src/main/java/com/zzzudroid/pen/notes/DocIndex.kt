package com.zzzudroid.pen.notes

import android.content.Context
import com.zzzudroid.pen.canvas.DocLink
import com.zzzudroid.pen.canvas.LinkTarget
import com.zzzudroid.pen.canvas.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Файл документа на диске: имя, время изменения и размер — ключ свежести записи индекса. */
data class FileStamp(val name: String, val lastModified: Long, val length: Long)

/** Запись индекса: что нужно знать о документе, не читая его штрихи. */
data class IndexedDoc(
    val docId: String,
    val stamp: FileStamp,
    val title: String,
    val kind: DocKind,
    val pageIds: List<String>,
    /** Исходящие ссылки документа. */
    val links: List<DocLink>,
)

/** Обратная ссылка: откуда на документ ссылаются. */
data class Backlink(
    val source: IndexedDoc,
    val link: DocLink,
    /** Индекс страницы источника с нуля; -1, если страница не найдена. */
    val pageIndex: Int,
)

/**
 * Чистая логика индекса (покрыта JVM-тестами): инкрементальное обновление и выборка обратных ссылок.
 */
object DocIndexLogic {
    /**
     * Обновить [old] по списку файлов [files]: неизменённые (то же имя, время и размер) берутся из индекса без чтения,
     * изменённые и новые читаются через [read], исчезнувшие удаляются. Файл, который [read] прочитать не смог (null),
     * в индекс не попадает — остальные не страдают.
     */
    fun update(old: List<IndexedDoc>, files: List<FileStamp>, read: (FileStamp) -> IndexedDoc?): List<IndexedDoc> {
        val byName = old.associateBy { it.stamp.name }
        return files.mapNotNull { f ->
            val cached = byName[f.name]
            if (cached != null && cached.stamp == f) cached else read(f)
        }
    }

    /**
     * Найти документ по идентификатору. Если у двух файлов один идентификатор (копия вручную),
     * берётся самый свежий по времени изменения файла.
     */
    fun find(docs: List<IndexedDoc>, docId: String): IndexedDoc? =
        docs.filter { it.docId == docId }.maxByOrNull { it.stamp.lastModified }

    /** Ссылки на документ [targetDocId] (или на его закладки) из всех документов, кроме него самого. */
    fun backlinks(docs: List<IndexedDoc>, targetDocId: String): List<Backlink> =
        docs.filter { it.docId != targetDocId }
            .flatMap { src ->
                src.links.filter { it.target.docId == targetDocId }
                    .map { Backlink(src, it, src.pageIds.indexOf(it.pageId)) }
            }
            .sortedWith(compareBy({ it.source.title.lowercase() }, { it.pageIndex }))

    // --- Сериализация индекса (хранится во внутренней памяти приложения) ---

    fun toJson(docs: List<IndexedDoc>): String = JSONArray(docs.map { d ->
        JSONObject().apply {
            put("docId", d.docId)
            put("name", d.stamp.name)
            put("lm", d.stamp.lastModified)
            put("len", d.stamp.length)
            put("title", d.title)
            put("kind", d.kind.id)
            put("pages", JSONArray(d.pageIds))
            put("links", JSONArray(d.links.map { l ->
                JSONObject().apply {
                    put("id", l.id)
                    put("pageId", l.pageId)
                    put("rect", JSONArray(listOf(l.left, l.top, l.right, l.bottom).map { it.toDouble() }))
                    put("docId", l.target.docId)
                    l.target.bookmarkId?.let { put("bm", it) }
                    l.target.view?.let { put("view", JSONArray(listOf(it.scale, it.tx, it.ty).map { v -> v.toDouble() })) }
                }
            }))
        }
    }).toString()

    fun fromJson(text: String): List<IndexedDoc> {
        val arr = JSONArray(text)
        return List(arr.length()) { i ->
            val d = arr.getJSONObject(i)
            val pages = d.getJSONArray("pages")
            val links = d.getJSONArray("links")
            IndexedDoc(
                docId = d.getString("docId"),
                stamp = FileStamp(d.getString("name"), d.getLong("lm"), d.getLong("len")),
                title = d.getString("title"),
                kind = DocKind.byId(d.optString("kind")),
                pageIds = List(pages.length()) { pages.getString(it) },
                links = List(links.length()) { k ->
                    val l = links.getJSONObject(k)
                    val r = l.getJSONArray("rect")
                    val v = l.optJSONArray("view")
                    DocLink(
                        l.getString("id"), l.getString("pageId"),
                        r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(), r.getDouble(3).toFloat(),
                        LinkTarget(
                            l.getString("docId"),
                            if (l.has("bm")) l.getString("bm") else null,
                            v?.let { ViewState(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) },
                        ),
                    )
                },
            )
        }
    }

    /** Запись индекса из метаданных документа. */
    fun entryOf(meta: NotebookMeta, stamp: FileStamp) =
        IndexedDoc(meta.docId, stamp, meta.title, meta.kind, meta.pageIds, meta.links)
}

/**
 * Индекс «идентификатор → документ» и обратных ссылок. Хранится в `filesDir/docindex.json`, обновляется инкрементально:
 * перечитывается (только `meta.json`, без штрихов) лишь то, что изменилось на диске.
 */
object DocIndex {
    private val lock = Mutex()
    private var docs: List<IndexedDoc> = emptyList()
    private var loaded = false

    /** Обновить индекс по папке документов; вызывать на любом потоке. Возвращает актуальные записи. */
    suspend fun refresh(context: Context): List<IndexedDoc> = withContext(Dispatchers.IO) {
        lock.withLock {
            val store = File(context.filesDir, "docindex.json")
            if (!loaded) {
                docs = try {
                    if (store.exists()) DocIndexLogic.fromJson(store.readText()) else emptyList()
                } catch (e: Exception) {
                    emptyList() // битый индекс — пересоберём
                }
                loaded = true
            }
            if (!NotebookStore.hasAccess()) return@withLock docs
            val stamps = NotebookStore.root.listFiles { f -> f.isFile && f.name.endsWith(FileNames.EXTENSION) }.orEmpty()
                .map { FileStamp(it.name, it.lastModified(), it.length()) }
            val updated = DocIndexLogic.update(docs, stamps) { stamp ->
                try {
                    DocIndexLogic.entryOf(NotebookFormat.readMeta(File(NotebookStore.root, stamp.name)), stamp)
                } catch (e: Exception) {
                    null
                }
            }
            if (updated != docs) {
                docs = updated
                try {
                    store.writeText(DocIndexLogic.toJson(docs))
                } catch (e: Exception) {
                    // индекс — кэш: не получилось сохранить, пересоберём в следующий раз
                }
            }
            docs
        }
    }

    /** Найти документ по идентификатору в уже обновлённом индексе (после [refresh]). */
    fun find(docId: String): IndexedDoc? = DocIndexLogic.find(docs, docId)

    /** Откуда ссылаются на документ [docId] (по уже обновлённому индексу). */
    fun backlinks(docId: String): List<Backlink> = DocIndexLogic.backlinks(docs, docId)

    /** Все записи (после [refresh]) — для выбора цели ссылки. */
    fun all(): List<IndexedDoc> = docs
}
