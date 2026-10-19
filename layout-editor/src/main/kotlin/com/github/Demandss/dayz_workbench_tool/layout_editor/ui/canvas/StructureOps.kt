package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
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
 * Puts the widgets [ids] into a new container of [type]. They must share one parent. The container takes the place of the
 * widgets and its rectangle is the one around them; the widgets keep what they look like, in pixels from its corner.
 * Returns the new tree and the id of the container, or null when the widgets cannot be wrapped.
 */
internal fun wrapped(
    root: EditorWidgetNode,
    ids: List<UUID>,
    layout: TreeLayout,
    type: WidgetType,
    name: String,
): Pair<EditorWidgetNode, UUID>? {
    val parent = ids.firstNotNullOfOrNull { root.parentOf(it) } ?: return null
    if (ids.any { root.parentOf(it)?.id != parent.id }) return null
    val rects = ids.mapNotNull { layout.rects[it] }
    val parentRect = layout.rects[parent.id] ?: return null
    if (rects.size != ids.size) return null
    val union = Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })

    val members = parent.children.filter { it.id in ids }
    val container =
        EditorWidgetNode(
            UUID.randomUUID(),
            WidgetNode(
                type,
                type.className,
                name,
                linkedMapOf(
                    "position" to "${fmt(union.left - parentRect.left)} ${fmt(union.top - parentRect.top)}",
                    "size" to "${fmt(union.width)} ${fmt(union.height)}",
                    "hexactpos" to "1",
                    "vexactpos" to "1",
                    "hexactsize" to "1",
                    "vexactsize" to "1",
                ),
            ),
            members.map { m -> m.placedAt(layout.rects.getValue(m.id), union) }.toMutableList(),
        )
    val at = parent.children.indexOfFirst { it.id in ids }
    val rest = parent.children.filter { it.id !in ids }.toMutableList()
    rest.add(at.coerceAtMost(rest.size), container)
    return root.updateNode(parent.id) { it.copy(children = rest) } to container.id
}

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
