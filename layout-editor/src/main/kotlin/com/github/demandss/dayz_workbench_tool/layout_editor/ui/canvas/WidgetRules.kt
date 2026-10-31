package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.math.pow

/*
 * What Workbench does with a few common widget properties, as measured on test layouts (see the container
 * properties notes of the dayz-ui skill): `visible 0` hides a widget with its children, `clipchildren` is on by default
 * and cuts children at the parent's edge, `priority` decides what is on top, and equal priorities keep the file order.
 */

/** A property by name, ignoring case and the spaces around the value. */
internal fun EditorWidgetNode.prop(name: String): String? =
    node.properties.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.trim()

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

/**
 * A `color` value as it looks on the screen. In the game the numbers are the sRGB values themselves. The Workbench of 1.30
 * ([ColorSpaceMode.workbench130]) reads the first three as linear light and shows them in sRGB, so `0.9 0.3 0.3` is 244 148 148
 * and not 230 77 77 (measured on a filled panel). Alpha stays as it is.
 */
internal fun parseWidgetColor(value: String?): Color {
    if (value == null) return Color.Transparent
    val p = value.trim().split(Regex("\\s+"))

    fun channel(i: Int): Float {
        val v = (p.getOrNull(i)?.toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
        return if (ColorSpaceMode.workbench130) linearToSrgb(v) else v
    }
    return Color(channel(0), channel(1), channel(2), (p.getOrNull(3)?.toFloatOrNull() ?: 1f).coerceIn(0f, 1f))
}

// Styles that paint nothing. Without a `style` a panel draws nothing either (seen in Workbench)
private val EMPTY_STYLES = setOf("default", "blank", "empty")

/**
 * The solid fill a container draws, or null when its style paints nothing. `Colorable` has a transparent style colour,
 * so it needs `color`; `rover_sim_colorable` is white and takes the colour as a tint. Any other style is drawn as a
 * flat fill of `color`, which is only an approximation of its images.
 */
internal fun EditorWidgetNode.fillColor(): Color? {
    // A ScrollWidget only clips and scrolls: Workbench draws no fill for it, whatever style and colour it has
    if (node.type == WidgetType.SCROLL) return null
    val style = node.properties["style"]?.trim()?.lowercase() ?: return null
    if (style in EMPTY_STYLES) return null
    val color = node.properties["color"]?.let(::parseWidgetColor)
    return if (style == "colorable") color else color ?: Color.White
}

/** A bare `center` is not an alignment the layout knows: the widget stays at the start edge. `center_ref` is what centres it. */
internal fun EditorWidgetNode.hasBareCenter(): Boolean = prop("halign") == "center" || prop("valign") == "center"
