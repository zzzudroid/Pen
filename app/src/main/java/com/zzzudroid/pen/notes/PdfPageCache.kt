package com.zzzudroid.pen.notes

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Кэш растров страниц PDF-подложки для экрана. Растр рисуется ступенями ширины
 * (512…4096 px), чтобы при зуме не перерисовывать на каждый кадр.
 * Вызывать [get] с главного потока.
 */
object PdfPageCache {
    private const val TAG = "PenPdf"
    private const val MAX_BYTES = 96 * 1024 * 1024

    private data class Key(val path: String, val page: Int, val width: Int)

    private val cache = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = HashSet<Key>()

    // Один открытый PdfRenderer на файл; PdfRenderer не потокобезопасен — доступ под мьютексом
    private val lock = Mutex()
    private var openPath: String? = null
    private var openFd: ParcelFileDescriptor? = null
    private var openRenderer: PdfRenderer? = null

    /**
     * Лучший готовый растр страницы (возможно, меньшего разрешения) или null.
     * Если нужной ступени нет — рисует её в фоне и вызывает [onReady] на главном потоке.
     */
    fun get(source: File, pdfPage: Int, widthPx: Int, onReady: () -> Unit): Bitmap? {
        val step = PdfUnits.cacheStep(widthPx)
        val key = Key(source.absolutePath, pdfPage, step)
        cache.get(key)?.let { return it }

        if (pending.add(key)) {
            scope.launch {
                val bmp = try {
                    lock.withLock { render(source, pdfPage, step) }
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось отрисовать страницу $pdfPage", e)
                    null
                }
                withContext(Dispatchers.Main) {
                    pending.remove(key)
                    if (bmp != null) {
                        cache.put(key, bmp)
                        onReady()
                    }
                }
            }
        }

        // Пока рисуется нужная ступень — отдаём ближайшую готовую (сначала крупнее, потом мельче)
        val steps = PdfUnits.CACHE_STEPS
        return steps.filter { it > step }.firstNotNullOfOrNull { cache.get(Key(key.path, pdfPage, it)) }
            ?: steps.filter { it < step }.reversed().firstNotNullOfOrNull { cache.get(Key(key.path, pdfPage, it)) }
    }

    fun clear() {
        cache.evictAll()
        scope.launch { lock.withLock { closeRenderer() } }
    }

    private fun render(source: File, pdfPage: Int, width: Int): Bitmap? {
        val r = renderer(source)
        if (pdfPage !in 0 until r.pageCount) return null
        val (w, h) = r.openPage(pdfPage).use { it.width to it.height }
        val height = (width.toFloat() * h / w).roundToInt().coerceAtLeast(1)
        return PdfRendering.renderPage(r, pdfPage, width, height)
    }

    private fun renderer(source: File): PdfRenderer {
        val path = source.absolutePath
        openRenderer?.let { if (openPath == path) return it }
        closeRenderer()
        val fd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(fd)
        openFd = fd
        openRenderer = r
        openPath = path
        return r
    }

    private fun closeRenderer() {
        try { openRenderer?.close() } catch (_: Exception) {}
        try { openFd?.close() } catch (_: Exception) {}
        openRenderer = null
        openFd = null
        openPath = null
    }
}
