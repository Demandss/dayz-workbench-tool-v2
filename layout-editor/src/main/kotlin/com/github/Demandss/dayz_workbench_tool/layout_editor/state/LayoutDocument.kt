package com.github.demandss.dayz_workbench_tool.layout_editor.state

import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode

data class LayoutDocument(
    val path: String,
    val root: EditorWidgetNode,
    var isModified: Boolean = false,
    val undoStack: ArrayDeque<EditorWidgetNode> = ArrayDeque(),
    val redoStack: ArrayDeque<EditorWidgetNode> = ArrayDeque()
) {
    val fileName: String get() = path.replace('\\', '/').substringAfterLast('/')
}