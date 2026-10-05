package com.zzzudroid.pen.recognition

import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.zzzudroid.pen.canvas.StrokeOps
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Состояние модели распознавания для UI. */
sealed interface ModelState {
    data object Checking : ModelState
    data object Downloading : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Распознавание рукописи ML Kit Digital Ink (модель «ru», скачивается на устройство один раз).
 * Страница режется на строки ([LineSegmenter]), каждая строка распознаётся отдельно.
 */
class HandwritingRecognizer private constructor(private val recognizer: DigitalInkRecognizer) {
    /** Текст страницы: строки через перевод строки; пустые строки пропускаются. */
    suspend fun recognizePage(strokes: List<Stroke>): String {
        if (strokes.isEmpty()) return ""
        val boxes = strokes.mapIndexedNotNull { i, s ->
            val b = StrokeOps.bounds(listOf(s)) ?: return@mapIndexedNotNull null
            StrokeBox(i, b.left, b.top, b.right, b.bottom)
        }
        val lines = LineSegmenter.segment(boxes)
        val out = StringBuilder()
        var preContext = ""
        for (line in lines) {
            val text = recognizeLine(line.map { strokes[it] }, preContext).trim()
            if (text.isEmpty()) continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(text)
            preContext = text.takeLast(PRE_CONTEXT_LENGTH)
        }
        return out.toString()
    }

    private suspend fun recognizeLine(strokes: List<Stroke>, preContext: String): String {
        val ink = Ink.builder()
        val p = StrokeInput()
        var t0 = 0L
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (s in strokes) {
            val stroke = Ink.Stroke.builder()
            val inputs = s.inputs
            var last = 0L
            for (i in 0 until inputs.size) {
                inputs.populate(i, p)
                last = p.elapsedTimeMillis
                stroke.addPoint(Ink.Point.create(p.x, p.y, t0 + last))
                minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
                minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
            }
            ink.addStroke(stroke.build())
            // Времена внутри штриха — от его начала; между штрихами — условная пауза, чтобы время только росло
            t0 += last + STROKE_GAP_MS
        }
        val context = RecognitionContext.builder()
            .setPreContext(preContext)
            .setWritingArea(WritingArea((maxX - minX).coerceAtLeast(1f), (maxY - minY).coerceAtLeast(1f)))
            .build()
        val result = recognizer.recognize(ink.build(), context).await()
        return result?.candidates?.firstOrNull()?.text.orEmpty()
    }

    fun close() = recognizer.close()

    companion object {
        private const val LANGUAGE = "ru"
        private const val PRE_CONTEXT_LENGTH = 20
        private const val STROKE_GAP_MS = 100L

        /**
         * Подготовить распознаватель: проверить и при необходимости скачать модель.
         * Бросает исключение с понятным текстом, если ML Kit недоступен (например, без Google Play Services).
         */
        suspend fun create(onState: (ModelState) -> Unit): HandwritingRecognizer {
            onState(ModelState.Checking)
            val id = try {
                DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE)
            } catch (e: Exception) {
                null
            } ?: throw IOException("Модель распознавания для русского языка не найдена")
            val model = DigitalInkRecognitionModel.builder(id).build()
            val manager = RemoteModelManager.getInstance()
            if (manager.isModelDownloaded(model).await() != true) {
                onState(ModelState.Downloading)
                manager.download(model, DownloadConditions.Builder().build()).await()
            }
            val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            onState(ModelState.Ready)
            return HandwritingRecognizer(recognizer)
        }
    }
}

/** Ожидание Task из Google Play Services без зависимости kotlinx-coroutines-play-services. */
suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
