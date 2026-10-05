package com.zzzudroid.pen.canvas

import android.os.SystemClock
import android.view.MotionEvent

/**
 * Где перо: обновляется из MainActivity по сырым hover-событиям — до Compose,
 * чтобы не зависеть от того, пробрасывает ли Compose hover во вложенные View.
 */
object StylusPresence {
    private var lastHoverUptime = 0L

    /** Окно после последнего hover, в течение которого пальцы считаются ладонью. */
    const val HOVER_GRACE_MS = 500L

    fun onGenericMotion(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT -> {
                val tool = ev.getToolType(0)
                if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                    lastHoverUptime = ev.eventTime
                }
            }
        }
    }

    /** Перо висит над экраном (или только что висело). */
    fun isHovering(): Boolean = SystemClock.uptimeMillis() - lastHoverUptime < HOVER_GRACE_MS
}
