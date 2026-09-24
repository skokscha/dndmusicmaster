package com.example.dndsound.core.scene

import com.example.dndsound.core.model.SceneSnapshot

/**
 * Undo stack for the "return to previous state" button. Keeps up to
 * [maxSize] snapshots (default ~10); pushing an identical consecutive
 * snapshot is a no-op.
 */
class SceneHistory(private val maxSize: Int = 10) {

    private val stack = ArrayDeque<SceneSnapshot>()

    val size: Int get() = stack.size

    fun push(snapshot: SceneSnapshot) {
        if (stack.lastOrNull() == snapshot) return
        stack.addLast(snapshot)
        while (stack.size > maxSize) {
            stack.removeFirst()
        }
    }

    /**
     * Returns the most recently pushed snapshot and removes it, i.e. the
     * state to jump back to. Null when the stack is empty.
     */
    fun undo(): SceneSnapshot? = stack.removeLastOrNull()

    fun canUndo(): Boolean = stack.isNotEmpty()

    fun clear() = stack.clear()
}
