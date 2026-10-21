package com.github.demandss.dayz_workbench_tool.layout_editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.demandss.dayz_workbench_tool.styles.StyleDef
import com.github.demandss.dayz_workbench_tool.styles.StyleSheet

/** An open `.styles`: the sheet being edited, what was saved last, the history and the selected style. */
class StylesDocument(
    val path: String,
    initial: StyleSheet,
) {
    var sheet by mutableStateOf(initial)
        private set
    private var saved by mutableStateOf(initial)
    private val undoStack = ArrayDeque<StyleSheet>()
    private val redoStack = ArrayDeque<StyleSheet>()

    // The selected style: index of the widget type and index of the style in it, or -1
    var widget by mutableStateOf(-1)
        private set
    var style by mutableStateOf(-1)
        private set

    // The state (Normal, Disabled...) shown in the editor and the preview
    var stateIndex by mutableStateOf(0)

    val isModified: Boolean get() = sheet != saved
    val fileName: String get() = path.replace('\\', '/').substringAfterLast('/')
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val selectedStyle: StyleDef? get() =
        sheet.widgets
            .getOrNull(widget)
            ?.styles
            ?.getOrNull(style)

    /** One step of the history: [change] makes a new sheet from the current one. */
    fun edit(change: (StyleSheet) -> StyleSheet) {
        val next = change(sheet)
        if (next == sheet) return
        undoStack.addLast(sheet)
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        sheet = next
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(sheet)
        sheet = undoStack.removeLast()
        clampSelection()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(sheet)
        sheet = redoStack.removeLast()
        clampSelection()
    }

    fun markSaved() {
        saved = sheet
    }

    fun select(
        widget: Int,
        style: Int,
    ) {
        this.widget = widget
        this.style = style
        stateIndex = 0
    }

    private fun clampSelection() {
        if (selectedStyle == null) {
            widget = -1
            style = -1
        }
        val states = selectedStyle?.states?.size ?: 0
        if (stateIndex >= states) stateIndex = 0
    }

    private companion object {
        const val UNDO_LIMIT = 200
    }
}
