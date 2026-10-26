package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode

/** `ButtonWidget` for `ButtonWidgetClass`, the way the class is called in script. */
internal fun scriptType(node: EditorWidgetNode): String {
    val raw = if (node.node.type == WidgetType.UNKNOWN) node.node.originalClassName else node.node.type.className
    return raw.removeSuffix("Class").ifEmpty { "Widget" }
}

/** `px_box` becomes `m_PxBox`. */
internal fun memberName(widgetName: String): String {
    val words = widgetName.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    val pascal = words.joinToString("") { w -> w.replaceFirstChar { it.uppercase() } }
    return "m_" + pascal.ifEmpty { "Widget" }
}

/** Enforce Script that declares and finds the widgets: the members first, then the lookups to put in the constructor. */
internal fun scriptSnippet(widgets: List<EditorWidgetNode>): String {
    val declarations = widgets.joinToString("\n") { "${scriptType(it)} ${memberName(it.node.name)};" }
    val lookups =
        widgets.joinToString("\n") {
            "${memberName(it.node.name)} = ${scriptType(it)}.Cast(layoutRoot.FindAnyWidget(\"${it.node.name}\"));"
        }
    return declarations + "\n\n" + lookups
}
