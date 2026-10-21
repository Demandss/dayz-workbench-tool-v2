package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID

/** Where each widget of a tree is, and where its parent is, in layout pixels. */
internal data class TreeLayout(
    val rects: Map<UUID, Rect>,
    val parentRects: Map<UUID, Rect>,
)

/** Places every widget of the tree on the [screen]. The root takes the whole screen unless it sets a size. [textureAspect] gives the ratio of a picture's texture. */
internal fun layoutTree(
    root: EditorWidgetNode,
    screen: Rect,
    textureAspect: (EditorWidgetNode) -> Float? = { null },
): TreeLayout {
    val rects = HashMap<UUID, Rect>()
    val parents = HashMap<UUID, Rect>()

    fun visit(
        node: EditorWidgetNode,
        rect: Rect,
        parent: Rect,
    ) {
        // A spacer places its children itself and may change its own size to fit them
        var spacer = layoutSpacer(node, rect)
        // A spacer that grew or shrank to fit its content keeps the edge its alignment is tied to (a `bottom_ref` list grows
        // upwards, measured on the vanilla HUD), so it is placed again with the size it ended up with
        if (spacer != null && node !== root && (spacer.rect.width != rect.width || spacer.rect.height != rect.height)) {
            val anchored = computeRect(node, parent, spacer.rect.size)
            spacer = layoutSpacer(node, Rect(anchored.left, anchored.top, anchored.left + rect.width, anchored.top + rect.height))
        }
        val own = spacer?.rect ?: rect
        rects[node.id] = own
        parents[node.id] = parent
        for (child in node.children) {
            val placed = spacer?.children?.get(child.id) ?: fixAspect(child, computeRect(child, own), own, textureAspect(child))
            visit(child, placed, own)
        }
    }
    // The root is placed like any other widget inside the preview window, so a root with `size 0.9 0.9` and `center_ref`
    // leaves a margin (measured on the vanilla HUD). A root that sets no size takes the whole screen.
    val rootRect =
        if (root.node.properties.containsKey("size")) {
            fixAspect(root, computeRect(root, screen), screen, textureAspect(root))
        } else {
            screen
        }
    visit(root, rootRect, screen)
    return TreeLayout(rects, parents)
}

/** The rectangle of [node] from its own position, size, exact flags and anchors, inside [parentRect]. */
internal fun computeRect(
    node: EditorWidgetNode,
    parentRect: Rect,
    // The size to use in place of the one the widget writes down (a spacer that sizes itself to its content)
    exactSize: androidx.compose.ui.geometry.Size? = null,
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
    val aw = exactSize?.width ?: if (hxs) sw else sw * parentRect.width
    val ah = exactSize?.height ?: if (vys) sh else sh * parentRect.height
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
