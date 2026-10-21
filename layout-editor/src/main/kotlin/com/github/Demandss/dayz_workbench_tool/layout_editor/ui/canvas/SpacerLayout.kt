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

/**
 * The size the text of a widget needs for the box it is given, for `size to text h` and `size to text v`; set by [layoutTree]
 * while it lays a tree out. The editor draws on one thread, so one slot is enough.
 */
internal var textSizeProvider: ((EditorWidgetNode, Float, Float) -> Size?)? = null

/** Whether a text follows the size of its text on the horizontal and on the vertical axis. */
internal fun EditorWidgetNode.sizeToText(): Pair<Boolean, Boolean> = (prop("size to text h") == "1") to (prop("size to text v") == "1")

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
    val asked =
        Size(
            if (child.prop("hexactsize") == "1") w else w * width,
            if (child.prop("vexactsize") == "1") h else h * height,
        )
    val (toWidth, toHeight) = child.sizeToText()
    if (!toWidth && !toHeight) return asked
    val text = textSizeProvider?.invoke(child, asked.width, asked.height) ?: return asked
    return Size(if (toWidth) text.width else asked.width, if (toHeight) text.height else asked.height)
}

private fun EditorWidgetNode.isSpacer(): Boolean = node.type == WidgetType.WRAP_SPACER || node.type == WidgetType.GRID_SPACER

/** The size a spacer inside a WrapSpacer takes in the rows: the whole parent, or only as high as its own content. */
private fun spacerChildSize(
    child: EditorWidgetNode,
    parent: Rect,
    margin: Float,
): Size {
    val height =
        if (child.boolProp("Size To Content V", false)) {
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
    // Aligned to the bottom the rows are filled from the bottom edge up, so the first row ends up lowest (measured on the
    // vanilla main menu, where the play button is the first child and sits at the bottom)
    val fromBottom = valign == "bottom"
    var bottomEdge = own.top + margin + availH

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
        val rowTop = if (fromBottom) bottomEdge - rowHeights[r] else y
        for (i in row) {
            val size = sizes[i]
            val left = if (halign == "right") x - size.width else x
            placed[kids[i].id] =
                if (kids[i].isSpacer()) {
                    // Cut off at the parent. A spacer that sizes to its content keeps its height
                    val bottom =
                        if (kids[i].boolProp("Size To Content V", false)) rowTop + size.height else min(rowTop + size.height, own.bottom)
                    Rect(left, rowTop, min(left + size.width, own.right), max(rowTop, bottom))
                } else {
                    Rect(left, rowTop, left + size.width, rowTop + size.height)
                }
            x = if (halign == "right") left - gap else x + size.width + gap
        }
        y += rowHeights[r] + gap
        bottomEdge -= rowHeights[r] + gap
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
    val toWidth = node.boolProp("Size To Content H", false)
    val toHeight = node.boolProp("Size To Content V", false)
    val all = node.spacerChildren()
    // "Size To Content V" gives as many rows as the children need (five children in one column make five rows, whatever
    // `Rows` says); without it the grid has `Rows` rows and ignores the children beyond the last cell (measured in Workbench)
    val rowCount = if (toHeight) ((all.size + columns - 1) / columns).coerceAtLeast(1) else node.intProp("Rows", 3).coerceAtLeast(1)
    val kids = all.take(columns * rowCount)
    val availW = (rect.width - 2 * margin - (columns - 1) * gap).coerceAtLeast(0f)
    val availH = (rect.height - 2 * margin - (rowCount - 1) * gap).coerceAtLeast(0f)
    val cell = Rect(0f, 0f, availW / columns, availH / rowCount)

    // What a child asks for: a pixel size, or what its own content needs for a spacer that sizes to its content. A child with a
    // relative size asks for nothing and fills what the grid gives it.
    fun asked(child: EditorWidgetNode): Pair<Float?, Float?> {
        val inner = layoutSpacer(child, cell)
        val size = childSize(child, cell.width, cell.height)
        val w =
            if (inner !=
                null
            ) {
                inner.rect.width.takeIf { child.boolProp("Size To Content H", false) }
            } else {
                size.width.takeIf {
                    child.prop("hexactsize") ==
                        "1"
                }
            }
        val h =
            if (inner !=
                null
            ) {
                inner.rect.height.takeIf { child.boolProp("Size To Content V", false) }
            } else {
                size.height.takeIf {
                    child.prop("vexactsize") ==
                        "1"
                }
            }
        return w to h
    }
    val wishes = kids.map(::asked)

    // With "Size To Content H" a column is as wide as its widest child; otherwise the columns share the width equally
    val colW =
        FloatArray(columns) { c ->
            val wanted = kids.indices.filter { it % columns == c }.mapNotNull { wishes[it].first }
            if (toWidth && wanted.isNotEmpty()) wanted.max() else cell.width
        }
    val rowH =
        FloatArray(rowCount) { r ->
            val wanted = kids.indices.filter { it / columns == r }.mapNotNull { wishes[it].second }
            if (toHeight && wanted.isNotEmpty()) wanted.max() else cell.height
        }
    val width = if (toWidth) 2 * margin + colW.sum() + (columns - 1) * gap else rect.width
    val height = if (toHeight) 2 * margin + rowH.sum() + (rowCount - 1) * gap else rect.height
    val own = Rect(rect.left, rect.top, rect.left + width, rect.top + height)

    val placed = HashMap<UUID, Rect>()
    kids.forEachIndexed { i, child ->
        val c = i % columns
        val r = i / columns
        val left = own.left + margin + (0 until c).sumOf { colW[it].toDouble() }.toFloat() + c * gap
        val top = own.top + margin + (0 until r).sumOf { rowH[it].toDouble() }.toFloat() + r * gap
        // Every child fills its cell, whatever size it writes down
        placed[child.id] = Rect(left, top, left + colW[c], top + rowH[r])
    }
    return SpacerLayout(own, placed)
}
