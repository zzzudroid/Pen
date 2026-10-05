package com.zzzudroid.pen.canvas

/**
 * История действий: стек отмены и стек повтора.
 * Само применение команд — на стороне вызывающего кода.
 */
class History<C> {
    private val undoStack = ArrayDeque<C>()
    private val redoStack = ArrayDeque<C>()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Новое действие: ветка повтора больше не имеет смысла. */
    fun push(command: C) {
        undoStack.addLast(command)
        redoStack.clear()
    }

    /** Команда, которую нужно откатить, или null. */
    fun undo(): C? = undoStack.removeLastOrNull()?.also { redoStack.addLast(it) }

    /** Команда, которую нужно применить заново, или null. */
    fun redo(): C? = redoStack.removeLastOrNull()?.also { undoStack.addLast(it) }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
