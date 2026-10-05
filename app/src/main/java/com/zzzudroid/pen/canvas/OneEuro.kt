package com.zzzudroid.pen.canvas

import kotlin.math.PI
import kotlin.math.abs

/**
 * Адаптивный фильтр «One Euro» (Casiez, Roussel, Vogel, 2012) для одной координаты: на медленном движении частота среза
 * низкая — дрожание гасится, на быстром растёт пропорционально скорости ([beta]) — линия почти не отстаёт.
 * Время — в миллисекундах от начала штрих. Чистый Kotlin, покрыт тестами.
 */
class OneEuro(val minCutoff: Float, val beta: Float, private val derivativeCutoff: Float = 8f) {
    private var x = 0f
    private var dx = 0f
    private var lastT = 0L
    private var started = false

    /** Начать заново с точки [value] в момент [t]. */
    fun reset(value: Float, t: Long) {
        x = value
        dx = 0f
        lastT = t
        started = true
    }

    /** Сглаженное значение для [value] в момент [t] (мс). */
    fun filter(value: Float, t: Long): Float {
        if (!started) { reset(value, t); return value }
        val dt = (t - lastT) / 1000f
        if (dt <= 0f) return x // события в одну и ту же миллисекунду — не двигаемся
        lastT = t
        // Сглаженная скорость и по ней — частота среза
        val rawDx = (value - x) / dt
        dx += alpha(derivativeCutoff, dt) * (rawDx - dx)
        val cutoff = minCutoff + beta * abs(dx)
        x += alpha(cutoff, dt) * (value - x)
        return x
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }
}
