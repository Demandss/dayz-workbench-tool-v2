package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode

/*
 * What Workbench does with a few common widget properties, as measured on test layouts (see the container
 * properties notes of the dayz-ui skill): `visible 0` hides a widget with its children, `clipchildren` is on by default
 * and cuts children at the parent's edge, `priority` decides what is on top, and equal priorities keep the file order.
 */

private fun EditorWidgetNode.flag(
    name: String,
    default: Boolean,
): Boolean {
    val value = node.properties[name]?.trim() ?: return default
    return !(value == "0" || value.equals("false", ignoreCase = true))
}

internal fun EditorWidgetNode.isVisible(): Boolean = flag("visible", default = true)

internal fun EditorWidgetNode.clipsChildren(): Boolean = flag("clipchildren", default = true)

private fun EditorWidgetNode.priority(): Int = node.properties["priority"]?.trim()?.toIntOrNull() ?: 0

/** The visible children from the one drawn first to the one drawn last, which is on top. */
internal fun EditorWidgetNode.drawOrder(): List<EditorWidgetNode> = children.filter { it.isVisible() }.sortedBy { it.priority() }

/**
 * The sign with which moving the pointer changes `position`: from the right or bottom edge the value grows
 * towards the inside, so it goes the other way. Centre and the default left and top edges grow with the pointer.
 */
internal fun anchorDirection(align: String?): Float =
    when (align) {
        "right_ref", "right", "bottom_ref", "bottom" -> -1f
        else -> 1f
    }
