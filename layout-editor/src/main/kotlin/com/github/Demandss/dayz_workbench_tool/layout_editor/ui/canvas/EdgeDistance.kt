package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** A measured gap between a widget and the edge or the centre of its parent, in layout pixels. */
data class EdgeDistance(
    val from: Offset,
    val to: Offset,
    val value: Float,
)

/**
 * The distances that place a widget: from the left or right edge of the parent, or from its centre, depending on the
 * widget's anchor, and the same on the vertical axis. The lines run through the middle of the widget.
 * A bare `center`, `left` or anything unknown measures from the left or top edge, as the canvas places it.
 * Gaps shorter than half a pixel are left out.
 */
fun edgeDistances(
    rect: Rect,
    parent: Rect,
    halign: String?,
    valign: String?,
): List<EdgeDistance> {
    val midX = rect.center.x
    val midY = rect.center.y
    val horizontal =
        when (halign) {
            "right_ref", "right" -> EdgeDistance(Offset(rect.right, midY), Offset(parent.right, midY), parent.right - rect.right)
            "center_ref" -> EdgeDistance(Offset(parent.center.x, midY), Offset(midX, midY), midX - parent.center.x)
            else -> EdgeDistance(Offset(parent.left, midY), Offset(rect.left, midY), rect.left - parent.left)
        }
    val vertical =
        when (valign) {
            "bottom_ref", "bottom" -> EdgeDistance(Offset(midX, rect.bottom), Offset(midX, parent.bottom), parent.bottom - rect.bottom)
            "center_ref" -> EdgeDistance(Offset(midX, parent.center.y), Offset(midX, midY), midY - parent.center.y)
            else -> EdgeDistance(Offset(midX, parent.top), Offset(midX, rect.top), rect.top - parent.top)
        }
    return listOf(horizontal, vertical).filter { abs(it.value) >= 0.5f }
}
