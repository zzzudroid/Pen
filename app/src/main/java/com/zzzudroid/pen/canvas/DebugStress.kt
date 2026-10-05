package com.zzzudroid.pen.canvas

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import kotlin.random.Random

/**
 * Нагрузочный тест (только отладочная сборка): набор случайных каракулей для замеров плавности и сохранения.
 * Запуск: adb shell am start -n com.zzzudroid.pen/.MainActivity --ei stress 3000 — штрихи добавятся в первую
 * открытую после этого тетрадь/доску (и сохранятся в неё, поэтому проверять только на своей тестовой).
 */
object DebugStress {
    @Volatile
    var pending = 0

    /** Сторона квадрата разброса штрихов, ед. холста (по умолчанию — большое поле), и масштаб вида после добавления (0 — не менять). */
    @Volatile
    var span = 0f

    @Volatile
    var viewScale = 0f

    /** Отладка: выключить мини-карту, чтобы замерить её вклад в стоимость кадра. */
    @Volatile
    var noMinimap = false

    /** Забрать и сбросить запрошенное число штрихов. */
    fun take(): Int = pending.also { pending = 0 }

    /** [count] штрихов по ~30 точек, разбросанных по квадрату [span] × [span] единиц холста. */
    fun strokes(count: Int, span: Float): List<Stroke> {
        val rnd = Random(count * 31 + span.toInt())
        val brush = androidx.ink.brush.Brush.createWithColorIntArgb(Brushes.pen, 0xFF1A1A1A.toInt(), 2.5f, CanvasViewModel.BRUSH_EPSILON)
        // Как в реальной схеме: штрихи группами по 40 рядом (слово, узел), а не россыпью по всему полю
        var cx = 0f
        var cy = 0f
        return List(count) { n ->
            if (n % 40 == 0) {
                cx = rnd.nextFloat() * span
                cy = rnd.nextFloat() * span
            }
            var x = cx + rnd.nextFloat() * 300f - 150f
            var y = cy + rnd.nextFloat() * 300f - 150f
            val batch = MutableStrokeInputBatch()
            val p = StrokeInput()
            for (k in 0 until 30) {
                p.update(
                    x, y, k * 8L, InputToolType.STYLUS,
                    StrokeInput.NO_STROKE_UNIT_LENGTH, 0.5f, StrokeInput.NO_TILT, StrokeInput.NO_ORIENTATION,
                )
                batch.add(p)
                x += rnd.nextFloat() * 8f - 3f
                y += rnd.nextFloat() * 8f - 4f
            }
            Stroke(brush, batch)
        }
    }
}
