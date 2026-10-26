package com.github.demandss.dayz_workbench_tool.layout_editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** An open `.imageset`: the model being edited, what was saved last, the history and the selected images. */
class ImageSetDocument(
    val path: String,
    initial: ImageSetModel,
) {
    var model by mutableStateOf(initial)
        private set
    private var saved by mutableStateOf(initial)
    private val undoStack = ArrayDeque<ImageSetModel>()
    private val redoStack = ArrayDeque<ImageSetModel>()
    val selected = mutableStateListOf<Int>()

    // F2 in the viewport sends the cursor to the name field
    var focusNameTick by mutableStateOf(0)

    val isModified: Boolean get() = model != saved
    val fileName: String get() = path.replace('\\', '/').substringAfterLast('/')
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private var gestureOpen = false

    /** One step of the history: [change] makes a new model from the current one. */
    fun edit(change: (ImageSetModel) -> ImageSetModel) {
        val next = change(model)
        if (next == model) return
        pushUndo()
        model = next
    }

    /**
     * A drag changes the model many times but is one step of the history: the first call saves the model before the
     * gesture, the next ones only replace it. [endGesture] closes it.
     */
    fun editInGesture(change: (ImageSetModel) -> ImageSetModel) {
        val next = change(model)
        if (next == model) return
        if (!gestureOpen) {
            pushUndo()
            gestureOpen = true
        }
        model = next
    }

    fun endGesture() {
        gestureOpen = false
    }

    private fun pushUndo() {
        undoStack.addLast(model)
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(model)
        model = undoStack.removeLast()
        selected.removeAll { model.entry(it) == null }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(model)
        model = redoStack.removeLast()
        selected.removeAll { model.entry(it) == null }
    }

    fun markSaved() {
        saved = model
    }

    fun select(id: Int?) {
        selected.clear()
        if (id != null) selected.add(id)
    }

    fun toggleSelected(id: Int) {
        if (!selected.remove(id)) selected.add(id)
    }

    private companion object {
        const val UNDO_LIMIT = 200
    }
}
