package com.github.demandss.dayz_workbench_tool.layout_editor

import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import java.util.UUID

data class EditorWidgetNode(
    val id: UUID = UUID.randomUUID(),
    val node: WidgetNode,
    val children: MutableList<EditorWidgetNode> = mutableListOf(),
)

fun WidgetNode.toEditorNode(): EditorWidgetNode =
    EditorWidgetNode(
        node = copy(children = mutableListOf()),
        children = children.map { it.toEditorNode() }.toMutableList(),
    )

fun EditorWidgetNode.toWidgetNode(): WidgetNode =
    node.copy(
        children = children.map { it.toWidgetNode() }.toMutableList(),
    )

fun EditorWidgetNode.updateNode(
    id: UUID,
    transform: (EditorWidgetNode) -> EditorWidgetNode,
): EditorWidgetNode {
    if (this.id == id) return transform(this)
    return copy(children = children.map { it.updateNode(id, transform) }.toMutableList())
}

fun EditorWidgetNode.findNode(id: UUID): EditorWidgetNode? {
    if (this.id == id) return this
    for (child in children) {
        child.findNode(id)?.let { return it }
    }
    return null
}

fun EditorWidgetNode.removeNode(id: UUID): EditorWidgetNode =
    copy(
        children =
            children
                .filter { it.id != id }
                .map { it.removeNode(id) }
                .toMutableList(),
    )

fun EditorWidgetNode.containsId(id: UUID): Boolean {
    if (this.id == id) return true
    return children.any { it.containsId(id) }
}

/** The widgets from this one down to the one with [id], both included, or null when [id] is not in the tree. */
fun EditorWidgetNode.pathTo(id: UUID): List<EditorWidgetNode>? {
    if (this.id == id) return listOf(this)
    for (child in children) {
        child.pathTo(id)?.let { return listOf(this) + it }
    }
    return null
}
