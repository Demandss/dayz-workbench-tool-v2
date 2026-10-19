package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/*
 * Layout of the containers that place their children themselves, as measured in Workbench (see the container
 * properties notes of the dayz-ui skill). A child of a spacer ignores its own position and anchors.
 *
 * - `Padding` is the gap between children, `Margin` is the frame around all of them (both default to 2).
 * - WrapSpacer fills rows left to right in the file order and wraps at the width left inside the margin.
 * - GridSpacer splits the inside into equal cells, `Columns` by `Rows`, and fills them row by row.
 * - A spacer inside a WrapSpacer ignores its own size. For the rows it is as big as the whole parent (or as high as its
 *   content with "Size To Content V"), but what it lays out and draws is cut off at the parent's right and bottom edge.
 *   "Size To Content H" changes nothing in Workbench.
 */

/** The rectangle a spacer ends up with (it may grow to fit its content) and the rectangles of its children. */
internal class SpacerLayout(
    val rect: Rect,
    val children: Map<UUID, Rect>,
)

internal fun layoutSpacer(
    node: EditorWidgetNode,
    rect: Rect,
): SpacerLayout? =
    when (node.node.type) {
        WidgetType.WRAP_SPACER -> layoutWrap(node, rect)
        WidgetType.GRID_SPACER -> layoutGrid(node, rect)
        else -> null
    }

private const val DEFAULT_GAP = 2

private fun EditorWidgetNode.intProp(
    name: String,
    default: Int,
): Int = prop(name)?.toIntOrNull() ?: default

private fun EditorWidgetNode.boolProp(
    name: String,
    default: Boolean,
): Boolean {
    val value = prop(name) ?: return default
    return !(value == "0" || value.equals("false", ignoreCase = true))
}

/** The size a spacer child asks for: pixels when its exact flag is on, otherwise a fraction of [width] and [height]. */
private fun childSize(
    child: EditorWidgetNode,
    width: Float,
    height: Float,
): Size {
    val parts = (child.prop("size") ?: "0.1 0.1").split(Regex("\\s+"))
    val w = parts.getOrNull(0)?.toFloatOrNull() ?: 0.1f
    val h = parts.getOrNull(1)?.toFloatOrNull() ?: 0.1f
    return Size(
        if (child.prop("hexactsize") == "1") w else w * width,
        if (child.prop("vexactsize") == "1") h else h * height,
    )
}

private fun EditorWidgetNode.isSpacer(): Boolean = node.type == WidgetType.WRAP_SPACER || node.type == WidgetType.GRID_SPACER

/** The size a spacer inside a WrapSpacer takes in the rows: the whole parent, or only as high as its own content. */
private fun spacerChildSize(
    child: EditorWidgetNode,
    parent: Rect,
    margin: Float,
): Size {
    val height =
        if (child.node.type == WidgetType.WRAP_SPACER && child.boolProp("Size To Content V", false)) {
            layoutSpacer(child, Rect(0f, 0f, parent.width - margin, parent.height))?.rect?.height ?: parent.height
        } else {
            parent.height
        }
    return Size(parent.width, height)
}

/** The children that take part: hidden ones leave their slot unless "Ignore invisible" is off. */
private fun EditorWidgetNode.spacerChildren(): List<EditorWidgetNode> {
    val skipHidden = boolProp("Ignore invisible", true)
    return children.filter { !skipHidden || it.isVisible() }
}

private fun layoutWrap(
    node: EditorWidgetNode,
    rect: Rect,
): SpacerLayout {
    val gap = node.intProp("Padding", DEFAULT_GAP).coerceAtLeast(0).toFloat()
    val margin = node.intProp("Margin", DEFAULT_GAP).coerceAtLeast(0).toFloat()
    val kids = node.spacerChildren()
    val sizes = kids.map { if (it.isSpacer()) spacerChildSize(it, rect, margin) else childSize(it, rect.width, rect.height) }
    val availW = (rect.width - 2 * margin).coerceAtLeast(0f)

    // Rows of child indexes: a child goes to the next row when it no longer fits in the width that is left
    val rows = ArrayList<MutableList<Int>>()
    val rowWidths = ArrayList<Float>()
    for (i in kids.indices) {
        val w = sizes[i].width
        if (rows.isEmpty() || rowWidths.last() + gap + w > availW) {
            rows += mutableListOf(i)
            rowWidths += w
        } else {
            rows.last() += i
            rowWidths[rowWidths.lastIndex] = rowWidths.last() + gap + w
        }
    }
    val rowHeights = rows.map { row -> row.maxOf { sizes[it].height } }
    // Measured in Workbench: centring and "Size To Content V" count the gap after the last row too, bottom alignment does not
    val blockH = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
    val blockWithTrailingGap = rowHeights.sum() + gap * rows.size

    // "Size To Content V": the height follows the rows, the width stays as set
    val own =
        if (node.boolProp("Size To Content V", false)) {
            Rect(rect.left, rect.top, rect.right, rect.top + 2 * margin + blockWithTrailingGap)
        } else {
            rect
        }
    val availH = (own.height - 2 * margin).coerceAtLeast(0f)

    val valign = node.prop("content_valign")
    val halign = node.prop("content_halign")
    var y =
        own.top + margin +
            when (valign) {
                "center" -> (availH - blockWithTrailingGap) / 2f
                "bottom" -> availH - blockH
                else -> 0f
            }

    val placed = HashMap<UUID, Rect>()
    rows.forEachIndexed { r, row ->
        val rowW = rowWidths[r]
        var x: Float
        when (halign) {
            "center" -> x = own.left + margin + (availW - rowW) / 2f

            // Aligned to the right the row is filled from the right edge, so the first child ends up rightmost
            "right" -> x = own.left + margin + availW

            else -> x = own.left + margin
        }
        for (i in row) {
            val size = sizes[i]
            val left = if (halign == "right") x - size.width else x
            placed[kids[i].id] =
                if (kids[i].isSpacer()) {
                    // Cut off at the parent. A spacer that sizes to its content keeps its height
                    val bottom = if (kids[i].boolProp("Size To Content V", false)) y + size.height else min(y + size.height, own.bottom)
                    Rect(left, y, min(left + size.width, own.right), max(y, bottom))
                } else {
                    Rect(left, y, left + size.width, y + size.height)
                }
            x = if (halign == "right") left - gap else x + size.width + gap
        }
        y += rowHeights[r] + gap
    }
    return SpacerLayout(own, placed)
}

private fun layoutGrid(
    node: EditorWidgetNode,
    rect: Rect,
): SpacerLayout {
    val gap = node.intProp("Padding", DEFAULT_GAP).coerceAtLeast(0).toFloat()
    val margin = node.intProp("Margin", DEFAULT_GAP).coerceAtLeast(0).toFloat()
    val columns = node.intProp("Columns", 3).coerceAtLeast(1)
    val rowCount = node.intProp("Rows", 3).coerceAtLeast(1)
    val cellW = ((rect.width - 2 * margin - (columns - 1) * gap) / columns).coerceAtLeast(0f)
    val cellH = ((rect.height - 2 * margin - (rowCount - 1) * gap) / rowCount).coerceAtLeast(0f)

    val placed = HashMap<UUID, Rect>()
    node.spacerChildren().take(columns * rowCount).forEachIndexed { i, child ->
        val left = rect.left + margin + (i % columns) * (cellW + gap)
        val top = rect.top + margin + (i / columns) * (cellH + gap)
        // A child with a relative size fills its cell whatever the fraction is (0.5 was measured to fill it as well),
        // a child with a pixel size keeps it in the cell's corner
        val asked = childSize(child, cellW, cellH)
        val size =
            Size(
                if (child.prop("hexactsize") == "1") asked.width else cellW,
                if (child.prop("vexactsize") == "1") asked.height else cellH,
            )
        placed[child.id] = Rect(left, top, left + size.width, top + size.height)
    }
    return SpacerLayout(rect, placed)
}
