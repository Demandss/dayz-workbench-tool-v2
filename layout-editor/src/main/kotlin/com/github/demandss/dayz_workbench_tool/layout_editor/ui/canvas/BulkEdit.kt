package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID

enum class BulkMode { PROPERTY, NAME }

/**
 * Replaces text in widget names or in the value of one property. [scope] limits it to those widgets, null is all of them.
 * With the property mode an empty [find] sets the property to [replace] for every widget that has it. Returns the new tree
 * and how many widgets changed.
 */
internal fun bulkReplace(
    root: EditorWidgetNode,
    scope: Set<UUID>?,
    mode: BulkMode,
    key: String,
    find: String,
    replace: String,
): Pair<EditorWidgetNode, Int> {
    var changed = 0

    fun visit(
        node: EditorWidgetNode,
        isRoot: Boolean,
    ): EditorWidgetNode {
        val kids = node.children.map { visit(it, false) }.toMutableList()
        var result = node.copy(children = kids)
        if (scope == null || node.id in scope) {
            when (mode) {
                BulkMode.NAME -> {
                    if (!isRoot && find.isNotEmpty() && node.node.name.contains(find)) {
                        changed++
                        result = result.copy(node = result.node.copy(name = sanitizeWidgetName(node.node.name.replace(find, replace))))
                    }
                }

                BulkMode.PROPERTY -> {
                    val value = node.node.properties[key]
                    if (key.isNotBlank() && value != null) {
                        val next =
                            if (find.isEmpty()) {
                                replace
                            } else if (value.contains(find)) {
                                value.replace(find, replace)
                            } else {
                                value
                            }
                        if (next != value) {
                            changed++
                            val props = LinkedHashMap(node.node.properties)
                            props[key] = next
                            result = result.copy(node = result.node.copy(properties = props))
                        }
                    }
                }
            }
        }
        return result
    }
    return visit(root, true) to changed
}

/** The different colours in a layout as they are shown, in the order of first use, at most [limit]. */
internal fun documentColors(
    root: EditorWidgetNode,
    limit: Int = 12,
): List<Color> {
    val seen = LinkedHashSet<Color>()

    fun walk(n: EditorWidgetNode) {
        n.node.properties["color"]?.let { seen += parseWidgetColor(it) }
        n.children.forEach(::walk)
    }
    walk(root)
    return seen.take(limit)
}
