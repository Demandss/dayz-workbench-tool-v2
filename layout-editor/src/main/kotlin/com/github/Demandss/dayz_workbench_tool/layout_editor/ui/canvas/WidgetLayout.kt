package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID

/** Where each widget of a tree is, and where its parent is, in layout pixels. */
internal data class TreeLayout(
    val rects: Map<UUID, Rect>,
    val parentRects: Map<UUID, Rect>,
)

/** Places every widget of the tree on the [screen]. The root takes the whole screen. */
internal fun layoutTree(
    root: EditorWidgetNode,
    screen: Rect,
): TreeLayout {
    val rects = HashMap<UUID, Rect>()
    val parents = HashMap<UUID, Rect>()

    fun visit(
        node: EditorWidgetNode,
        rect: Rect,
        parent: Rect,
    ) {
        rects[node.id] = rect
        parents[node.id] = parent
        for (child in node.children) visit(child, computeRect(child, rect), rect)
    }
    visit(root, screen, screen)
    return TreeLayout(rects, parents)
}

/** The rectangle of [node] from its own position, size, exact flags and anchors, inside [parentRect]. */
internal fun computeRect(
    node: EditorWidgetNode,
    parentRect: Rect,
): Rect {
    val props = node.node.properties
    val pp = (props["position"] ?: "0 0").trim().split(Regex("\\s+"))
    val sp = (props["size"] ?: "0.1 0.1").trim().split(Regex("\\s+"))
    val px = pp.getOrNull(0)?.toFloatOrNull() ?: 0f
    val py = pp.getOrNull(1)?.toFloatOrNull() ?: 0f
    val sw = sp.getOrNull(0)?.toFloatOrNull() ?: 0.1f
    val sh = sp.getOrNull(1)?.toFloatOrNull() ?: 0.1f
    val hxp = props["hexactpos"] == "1"
    val vyp = props["vexactpos"] == "1"
    val hxs = props["hexactsize"] == "1"
    val vys = props["vexactsize"] == "1"
    val ax = if (hxp) px else px * parentRect.width
    val ay = if (vyp) py else py * parentRect.height
    val aw = if (hxs) sw else sw * parentRect.width
    val ah = if (vys) sh else sh * parentRect.height
    val bx =
        when (props["halign"]) {
            "right_ref", "right" -> parentRect.right - aw - ax
            "center_ref" -> parentRect.left + parentRect.width / 2f - aw / 2f + ax
            else -> parentRect.left + ax
        }
    val posY =
        when (props["valign"]) {
            "bottom_ref", "bottom" -> parentRect.bottom - ah - ay
            "center_ref" -> parentRect.top + parentRect.height / 2f - ah / 2f + ay
            else -> parentRect.top + ay
        }
    return Rect(bx, posY, bx + aw, posY + ah)
}
