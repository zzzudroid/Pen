package com.zzzudroid.pen.notes

import com.zzzudroid.pen.canvas.LinkTarget
import com.zzzudroid.pen.canvas.ViewState

/** Куда открыть документ по ссылке: имя файла и место внутри него. */
data class NoteDestination(val fileName: String, val bookmarkId: String?, val view: ViewState?)

/** Результат разрешения цели ссылки по индексу документов. */
sealed interface LinkResolution {
    data class Open(val destination: NoteDestination) : LinkResolution
    data class Missing(val message: String) : LinkResolution
}

/** Разрешение цели ссылки в место на диске — чистая логика, покрыта тестами. */
object LinkRouting {
    const val DOC_MISSING = "Документ недоступен"

    /** [doc] — запись индекса для `target.docId` или null, если документ не найден. */
    fun resolve(target: LinkTarget, doc: IndexedDoc?): LinkResolution =
        if (doc == null) LinkResolution.Missing(DOC_MISSING)
        else LinkResolution.Open(NoteDestination(doc.stamp.name, target.bookmarkId, target.view))
}
