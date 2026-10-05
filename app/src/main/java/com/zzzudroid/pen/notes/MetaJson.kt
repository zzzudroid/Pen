package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.Bookmark
import com.zzzudroid.pen.canvas.DocLink
import com.zzzudroid.pen.canvas.LinkTarget
import com.zzzudroid.pen.canvas.PageSpec
import com.zzzudroid.pen.canvas.PageTemplate
import com.zzzudroid.pen.canvas.ViewState
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * Запись и разбор `meta.json` формата .pen (v1–v3). Чистый Kotlin + org.json, без Android-классов,
 * поэтому покрывается JVM-тестами.
 */
object MetaJson {

    fun toJson(m: NotebookMeta): JSONObject = JSONObject().apply {
        put("formatVersion", NotebookFormat.FORMAT_VERSION)
        put("app", "Pen")
        put("title", m.title)
        put("template", m.template.id)
        put("pageWidth", m.pageWidth.toDouble())
        put("pageHeight", m.pageHeight.toDouble())
        put("unitsPerMm", PageSpec.UNITS_PER_MM.toDouble())
        put("created", m.created)
        put("modified", m.modified)
        put("pages", JSONArray(m.pageIds))
        put("hasPdfSource", m.hasPdfSource)
        put("kind", m.kind.id)
        m.boardView?.let { v ->
            put("boardView", JSONObject().put("scale", v.scale.toDouble()).put("tx", v.tx.toDouble()).put("ty", v.ty.toDouble()))
        }
        put("docId", m.docId)
        put("links", JSONArray(m.links.map(::linkToJson)))
        if (m.tags.isNotEmpty()) put("tags", JSONArray(m.tags))
        put("bookmarks", JSONArray(m.bookmarks.map { b ->
            JSONObject().put("id", b.id).put("name", b.name).put("view", viewToJson(b.view))
        }))
        put("pageInfo", JSONArray(m.pages.map { p ->
            JSONObject().apply {
                put("id", p.id)
                put("width", p.width.toDouble())
                put("height", p.height.toDouble())
                put("template", p.template.id)
                p.pdfPage?.let { put("pdfPage", it) }
            }
        }))
    }

    private fun viewToJson(v: ViewState) =
        JSONObject().put("scale", v.scale.toDouble()).put("tx", v.tx.toDouble()).put("ty", v.ty.toDouble())

    private fun viewFromJson(j: JSONObject) =
        ViewState(j.getDouble("scale").toFloat(), j.getDouble("tx").toFloat(), j.getDouble("ty").toFloat())

    private fun linkToJson(l: DocLink) = JSONObject().apply {
        put("id", l.id)
        put("pageId", l.pageId)
        put("rect", JSONArray(listOf(l.left, l.top, l.right, l.bottom).map { it.toDouble() }))
        put("target", JSONObject().apply {
            put("docId", l.target.docId)
            l.target.bookmarkId?.let { put("bookmarkId", it) }
            l.target.view?.let { put("view", viewToJson(it)) }
        })
    }

    private fun linkFromJson(j: JSONObject): DocLink {
        val r = j.getJSONArray("rect")
        val t = j.getJSONObject("target")
        return DocLink(
            id = j.getString("id"),
            pageId = j.getString("pageId"),
            left = r.getDouble(0).toFloat(), top = r.getDouble(1).toFloat(),
            right = r.getDouble(2).toFloat(), bottom = r.getDouble(3).toFloat(),
            target = LinkTarget(
                docId = t.getString("docId"),
                bookmarkId = if (t.has("bookmarkId")) t.getString("bookmarkId") else null,
                view = t.optJSONObject("view")?.let(::viewFromJson),
            ),
        )
    }

    /**
     * Разбор meta.json (v1–v3). Документ без `docId` (v1/v2) получает постоянный идентификатор, выведенный из имени файла:
     * он одинаков при каждом открытии и записывается в файл при первом сохранении (в том числе при переименовании).
     */
    fun parse(text: String, fileName: String, fileModified: Long, hasPdfSource: Boolean): NotebookMeta {
        val j = JSONObject(text)
        val version = j.optInt("formatVersion", 1)
        if (version > NotebookFormat.FORMAT_VERSION) throw IOException("Файл новой версии формата ($version), обновите приложение")
        val template = PageTemplate.byId(j.optString("template"))
        val width = j.getDouble("pageWidth").toFloat()
        val height = j.getDouble("pageHeight").toFloat()
        val info = j.optJSONArray("pageInfo")
        val pages = if (info != null) {
            List(info.length()) { i ->
                val p = info.getJSONObject(i)
                PageMeta(
                    id = p.getString("id"),
                    width = p.optDouble("width", width.toDouble()).toFloat(),
                    height = p.optDouble("height", height.toDouble()).toFloat(),
                    template = PageTemplate.byId(p.optString("template", template.id)),
                    pdfPage = if (p.has("pdfPage")) p.getInt("pdfPage") else null,
                )
            }
        } else {
            // Версия 1: все страницы одного размера и шаблона
            val ids = j.getJSONArray("pages")
            List(ids.length()) { PageMeta(ids.getString(it), width, height, template) }
        }
        val links = j.optJSONArray("links")
        val bookmarks = j.optJSONArray("bookmarks")
        val tags = j.optJSONArray("tags")
        return NotebookMeta(
            title = j.optString("title", fileName.removeSuffix(FileNames.EXTENSION)),
            template = template,
            pageWidth = width,
            pageHeight = height,
            created = j.optLong("created", fileModified),
            modified = j.optLong("modified", fileModified),
            pages = pages,
            hasPdfSource = hasPdfSource,
            kind = DocKind.byId(j.optString("kind", DocKind.NOTEBOOK.id)),
            boardView = j.optJSONObject("boardView")?.let { v ->
                BoardView(v.getDouble("scale").toFloat(), v.getDouble("tx").toFloat(), v.getDouble("ty").toFloat())
            },
            docId = j.optString("docId").ifBlank { legacyDocId(fileName) },
            links = if (links == null) emptyList() else List(links.length()) { linkFromJson(links.getJSONObject(it)) },
            tags = if (tags == null) emptyList() else Tags.clean(List(tags.length()) { tags.optString(it) }),
            bookmarks = if (bookmarks == null) emptyList() else List(bookmarks.length()) {
                val b = bookmarks.getJSONObject(it)
                Bookmark(b.getString("id"), b.getString("name"), viewFromJson(b.getJSONObject("view")))
            },
        )
    }

    /** Постоянный идентификатор для документа без `docId`: один и тот же для одного имени файла. */
    fun legacyDocId(fileName: String): String = UUID.nameUUIDFromBytes(("legacy:$fileName").toByteArray()).toString()

    /** meta.json как текст. */
    fun text(m: NotebookMeta): String = toJson(m).toString(2)
}
