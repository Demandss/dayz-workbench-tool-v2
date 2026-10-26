package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.pathTo
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import java.util.UUID

enum class Reorder { FORWARD, BACKWARD, FRONT, BACK }

private fun fmt(v: Float) = formatNumber(v)

/** Gives the widget the place and size of [rect] inside [container] in pixels, measured from the top left corner. */
private fun EditorWidgetNode.placedAt(
    rect: Rect,
    container: Rect,
): EditorWidgetNode {
    val props = LinkedHashMap(node.properties)
    props["position"] = "${fmt(rect.left - container.left)} ${fmt(rect.top - container.top)}"
    props["size"] = "${fmt(rect.width)} ${fmt(rect.height)}"
    for (flag in listOf("hexactpos", "vexactpos", "hexactsize", "vexactsize")) props[flag] = "1"
    props.remove("halign")
    props.remove("valign")
    return copy(node = node.copy(properties = props))
}

/**
 * Puts the widgets [ids] into a new container of [type]. The widgets may sit in different parents: the container goes into the
 * nearest widget that holds all of them (where the topmost of them was, drawn over what they were in), and the widgets keep what
 * they look like, in pixels from its corner. A widget inside another selected one goes along with it and is not moved on its own.
 * Returns the new tree and the id of the container, or null when the widgets cannot be wrapped.
 */
internal fun wrapped(
    root: EditorWidgetNode,
    ids: List<UUID>,
    layout: TreeLayout,
    type: WidgetType,
    name: String,
): Pair<EditorWidgetNode, UUID>? {
    val tops = topLevel(root, ids).filter { it != root.id }
    if (tops.isEmpty()) return null
    val rects = tops.mapNotNull { layout.rects[it] }
    if (rects.size != tops.size) return null
    // The nearest widget above all of them: the longest start the paths from the root to their parents have in common
    val parentPaths = tops.map { id -> root.parentOf(id)?.let { root.pathTo(it.id) } ?: return null }
    var shared = 0
    while (parentPaths.all { it.size > shared && it[shared].id == parentPaths[0][shared].id }) shared++
    if (shared == 0) return null
    val holder = parentPaths[0][shared - 1]
    val holderRect = layout.rects[holder.id] ?: return null
    val union = Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })

    val members = tops.mapNotNull { root.findNode(it) }
    val container =
        EditorWidgetNode(
            UUID.randomUUID(),
            WidgetNode(
                type,
                type.className,
                name,
                linkedMapOf(
                    "position" to "${fmt(union.left - holderRect.left)} ${fmt(union.top - holderRect.top)}",
                    "size" to "${fmt(union.width)} ${fmt(union.height)}",
                    "hexactpos" to "1",
                    "vexactpos" to "1",
                    "hexactsize" to "1",
                    "vexactsize" to "1",
                ),
            ),
            members.map { m -> m.placedAt(layout.rects.getValue(m.id), union) }.toMutableList(),
        )
    // The group takes the place of the topmost of the widgets: after the last child of the holder that is a member or holds one
    val topSet = tops.toSet()
    val last = holder.children.indexOfLast { child -> child.id in topSet || tops.any { child.findNode(it) != null } }
    val into = holder.children.take(last + 1).count { it.id !in topSet }
    val without = root.withoutNodes(topSet)
    val kept = without.findNode(holder.id)?.children ?: return null
    val list = kept.toMutableList().also { it.add(into.coerceIn(0, kept.size), container) }
    return without.updateNode(holder.id) { it.copy(children = list) } to container.id
}

/** A copy of the tree without the widgets [ids] (and what is in them). */
private fun EditorWidgetNode.withoutNodes(ids: Set<UUID>): EditorWidgetNode =
    copy(children = children.filter { it.id !in ids }.map { it.withoutNodes(ids) }.toMutableList())

/** Takes the children out of the container [id] and puts them where it was. Returns the new tree and the children's ids. */
internal fun ungrouped(
    root: EditorWidgetNode,
    id: UUID,
    layout: TreeLayout,
): Pair<EditorWidgetNode, List<UUID>>? {
    val parent = root.parentOf(id) ?: return null
    val container = parent.children.firstOrNull { it.id == id } ?: return null
    if (container.children.isEmpty()) return null
    val parentRect = layout.rects[parent.id] ?: return null
    val freed = container.children.map { c -> layout.rects[c.id]?.let { c.placedAt(it, parentRect) } ?: c }
    val list = ArrayList<EditorWidgetNode>()
    for (sibling in parent.children) if (sibling.id == id) list += freed else list += sibling
    return root.updateNode(parent.id) { it.copy(children = list) } to freed.map { it.id }
}

/** Moves the widget [id] one place up or down among its siblings, or to the very end. Later widgets are drawn on top. */
internal fun reordered(
    root: EditorWidgetNode,
    id: UUID,
    how: Reorder,
): EditorWidgetNode? {
    val parent = root.parentOf(id) ?: return null
    val list = parent.children.toMutableList()
    val from = list.indexOfFirst { it.id == id }
    val to =
        when (how) {
            Reorder.FORWARD -> (from + 1).coerceAtMost(list.lastIndex)
            Reorder.BACKWARD -> (from - 1).coerceAtLeast(0)
            Reorder.FRONT -> list.lastIndex
            Reorder.BACK -> 0
        }
    if (to == from) return null
    list.add(to, list.removeAt(from))
    return root.updateNode(parent.id) { it.copy(children = list) }
}

/** Spreads three or more widgets so that the gaps between neighbours are equal. The two outermost stay where they are. */
internal fun distributed(
    root: EditorWidgetNode,
    ids: List<UUID>,
    layout: TreeLayout,
    horizontal: Boolean,
): EditorWidgetNode {
    val items = ids.mapNotNull { id -> layout.rects[id]?.let { id to it } }.sortedBy { if (horizontal) it.second.left else it.second.top }
    if (items.size < 3) return root

    fun start(r: Rect) = if (horizontal) r.left else r.top

    fun end(r: Rect) = if (horizontal) r.right else r.bottom
    val total = end(items.last().second) - start(items.first().second)
    val gap = (total - items.sumOf { (end(it.second) - start(it.second)).toDouble() }.toFloat()) / (items.size - 1)
    var cursor = start(items.first().second)
    var result = root
    for ((id, r) in items) {
        val shift = cursor - start(r)
        val parent = layout.parentRects[id]
        if (parent != null && shift != 0f) result = result.movedBy(id, if (horizontal) shift else 0f, if (horizontal) 0f else shift, parent)
        cursor += (end(r) - start(r)) + gap
    }
    return result
}

/**
 * Changes the anchor of a widget and sets the offset to zero on the axis whose anchor changed, so that the widget sits
 * right at its new anchor: the edge it names or the centre of the parent. An axis that kept its anchor keeps its offset.
 */
internal fun reanchored(
    root: EditorWidgetNode,
    id: java.util.UUID,
    halign: String,
    valign: String,
): EditorWidgetNode =
    root.updateNode(id) { n ->
        val props = LinkedHashMap(n.node.properties)
        val parts = (props["position"] ?: "0 0").trim().split(Regex("\\s+"))
        var x = parts.getOrNull(0) ?: "0"
        var y = parts.getOrNull(1) ?: "0"
        if (halign != (props["halign"] ?: "left")) x = "0"
        if (valign != (props["valign"] ?: "top")) y = "0"
        props["position"] = "$x $y"
        props["halign"] = halign
        props["valign"] = valign
        n.copy(node = n.node.copy(properties = props))
    }

/** What a widget looks like, apart from where it is: the keys that Copy properties carries. */
internal val APPEARANCE_KEYS =
    listOf(
        "style",
        "color",
        "image0",
        "imageTexture",
        "mode",
        "src alpha",
        "clamp mode",
        "stretch mode",
        "flip u",
        "flip v",
        "filter",
        "nocache",
        "rotation",
        "pivot",
        "mask",
        "transition width",
        "progress",
        "inheritalpha",
        "priority",
        "fixaspect",
        "scaled",
    )

internal fun EditorWidgetNode.appearance(): Map<String, String> =
    APPEARANCE_KEYS
        .mapNotNull { k ->
            node.properties[k]?.let {
                k to it
            }
        }.toMap()

internal fun EditorWidgetNode.withAppearance(values: Map<String, String>): EditorWidgetNode {
    val props = LinkedHashMap(node.properties)
    for (k in APPEARANCE_KEYS) props.remove(k)
    props.putAll(values)
    return copy(node = node.copy(properties = props))
}
