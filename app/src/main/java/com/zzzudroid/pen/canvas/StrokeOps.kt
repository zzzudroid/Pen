package com.zzzudroid.pen.canvas

import android.graphics.Color
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput

/** Операции над штрихами Ink для лассо: штрихи неизменяемы, поэтому каждая операция создаёт новый. */
object StrokeOps {

    /** Координаты входных точек штриха (мировые). */
    fun points(stroke: Stroke): Pair<FloatArray, FloatArray> {
        val inputs = stroke.inputs
        val n = inputs.size
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val p = StrokeInput()
        for (i in 0 until n) {
            inputs.populate(i, p)
            xs[i] = p.x
            ys[i] = p.y
        }
        return xs to ys
    }

    /** Общие границы штрихов по геометрии, null если штрихов нет. */
    fun bounds(strokes: List<Stroke>): Bounds? {
        var result: Bounds? = null
        for (s in strokes) {
            val box = s.shape.computeBoundingBox() ?: continue
            val b = Bounds(box.xMin, box.yMin, box.xMax, box.yMax)
            result = result?.union(b) ?: b
        }
        return result
    }

    /** Новый штрих с преобразованными точками; размер кисти умножается на масштаб. */
    fun transformed(stroke: Stroke, t: SelectionTransform): Stroke {
        val inputs = stroke.inputs
        val out = MutableStrokeInputBatch()
        val p = StrokeInput()
        for (i in 0 until inputs.size) {
            inputs.populate(i, p)
            // Порядок параметров update: x, y, время, инструмент, длина единицы, давление, наклон, азимут
            p.update(
                t.mapX(p.x), t.mapY(p.y), p.elapsedTimeMillis, p.toolType,
                p.strokeUnitLengthCm, p.pressure, p.tiltRadians, p.orientationRadians,
            )
            out.add(p)
        }
        val brush = if (t.scale == 1f) stroke.brush else stroke.brush.copy(size = stroke.brush.size * t.scale)
        return Stroke(brush, out)
    }

    /** Новый штрих с цветом [argb]; выделитель сохраняет свою прозрачность. */
    fun recolored(stroke: Stroke, argb: Int): Stroke {
        val color = if (Brushes.toolOf(stroke.brush.family) == CanvasTool.HIGHLIGHTER) {
            Color.argb(Color.alpha(stroke.brush.colorIntArgb), Color.red(argb), Color.green(argb), Color.blue(argb))
        } else {
            argb
        }
        return stroke.copy(stroke.brush.copyWithColorIntArgb(color))
    }
}
