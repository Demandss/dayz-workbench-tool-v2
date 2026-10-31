package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.resources.*
import java.util.UUID

enum class Severity { ERROR, WARNING }

/** Something in a layout that is probably not what the author meant. [nodeId] is the widget it is about. */
data class Problem(
    val nodeId: UUID,
    val widgetName: String,
    val severity: Severity,
    val message: String,
)

/** Looks through the whole tree for the mistakes that are easy to make and hard to see on the canvas. */
fun findProblems(root: EditorWidgetNode): List<Problem> {
    val out = mutableListOf<Problem>()

    fun visit(
        node: EditorWidgetNode,
        isRoot: Boolean,
    ) {
        val name = node.node.name.ifEmpty { if (isRoot) "(root)" else "—" }

        if (!isRoot && node.node.name.isNotEmpty() && hasInvalidNameChars(node.node.name)) {
            out += Problem(node.id, name, Severity.WARNING, str(Res.string.problem_cyrillic_name))
        }
        if (node.hasBareCenter()) {
            out += Problem(node.id, name, Severity.WARNING, str(Res.string.problem_center_without_ref))
        }
        val size =
            node
                .prop("size")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toFloatOrNull() }
                .orEmpty()
        if (size.any { it <= 0f }) {
            out += Problem(node.id, name, Severity.ERROR, str(Res.string.problem_size_not_positive, "${node.prop("size")}"))
        }

        // Workbench 1.30 stops reading the layout after a widget whose "exact text size" is not a whole number (measured)
        val textSize = node.prop("exact text size")?.trim()
        if (textSize != null && textSize.toIntOrNull() == null) {
            out +=
                Problem(
                    node.id,
                    name,
                    Severity.ERROR,
                    str(Res.string.problem_text_size_fraction, "$textSize"),
                )
        }
        // The style Colorable does not exist for a panel: Workbench draws nothing for it (the flat fill is rover_sim_colorable)
        if (node.node.type == WidgetType.PANEL && node.prop("style")?.trim()?.trim('"') == "Colorable") {
            out +=
                Problem(
                    node.id,
                    name,
                    Severity.WARNING,
                    str(Res.string.problem_panel_colorable),
                )
        }

        node.children
            .filter { it.node.name.isNotEmpty() }
            .groupBy { it.node.name }
            .filterValues { it.size > 1 }
            .forEach { (dup, nodes) ->
                nodes.drop(1).forEach {
                    out += Problem(it.id, dup, Severity.WARNING, str(Res.string.problem_duplicate_name, dup))
                }
            }

        node.children.forEach { visit(it, false) }
    }

    visit(root, true)
    return out
}
