package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.pathTo
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import java.util.UUID

/** How a click changes the selection: it replaces it, adds or removes one widget (Ctrl), or takes a range (Shift). */
internal enum class SelectMode { REPLACE, TOGGLE, RANGE, RANGE_ADD }

internal fun selectMode(
    shift: Boolean,
    ctrl: Boolean,
): SelectMode =
    when {
        shift && ctrl -> SelectMode.RANGE_ADD
        shift -> SelectMode.RANGE
        ctrl -> SelectMode.TOGGLE
        else -> SelectMode.REPLACE
    }

/**
 * The selection after a click on [clicked]. [order] is the widgets in the order the user sees them (the rows of the hierarchy), the
 * range runs from [anchor], the widget that was clicked last without Shift, to [clicked]. Returns the selection and the new anchor.
 */
internal fun applySelection(
    current: List<UUID>,
    anchor: UUID?,
    clicked: UUID,
    order: List<UUID>,
    mode: SelectMode,
): Pair<List<UUID>, UUID?> {
    when (mode) {
        SelectMode.REPLACE -> {
            return listOf(clicked) to clicked
        }

        SelectMode.TOGGLE -> {
            return if (clicked in current) {
                (current - clicked) to (anchor?.takeIf { it != clicked } ?: current.lastOrNull { it != clicked })
            } else {
                (current + clicked) to clicked
            }
        }

        SelectMode.RANGE, SelectMode.RANGE_ADD -> {
            val from = order.indexOf(anchor)
            val to = order.indexOf(clicked)
            if (anchor == null || from < 0 || to < 0) return listOf(clicked) to clicked
            val range = order.subList(minOf(from, to), maxOf(from, to) + 1)
            val selection = if (mode == SelectMode.RANGE_ADD) (current + range).distinct() else range
            return selection to anchor
        }
    }
}

/** The widget ids of [ids] that are not inside another widget of [ids]: moving or scaling these carries the rest along. */
internal fun topLevel(
    root: EditorWidgetNode,
    ids: List<UUID>,
): List<UUID> =
    ids.filter { id ->
        var parent = root.parentOf(id)
        var inside = false
        while (parent != null) {
            if (parent.id in ids) inside = true
            parent = root.parentOf(parent.id)
        }
        !inside
    }

/** The rectangle around the widgets [ids], in layout pixels; null when none has one. */
internal fun selectionBounds(
    ids: List<UUID>,
    layout: TreeLayout,
): Rect? {
    val rects = ids.mapNotNull { layout.rects[it] }
    if (rects.isEmpty()) return null
    return Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
}

/** The widgets [ids] moved by [dx] and [dy] layout pixels. */
internal fun movedSelection(
    root: EditorWidgetNode,
    ids: List<UUID>,
    layout: TreeLayout,
    screen: Rect,
    dx: Float,
    dy: Float,
): EditorWidgetNode = topLevel(root, ids).fold(root) { acc, id -> acc.movedBy(id, dx, dy, layout.parentRects[id] ?: screen) }

/** The widgets [ids] scaled together so that the rectangle around them is [width] by [height] pixels; null keeps that side. */
internal fun scaledSelection(
    root: EditorWidgetNode,
    ids: List<UUID>,
    layout: TreeLayout,
    screen: Rect,
    width: Float?,
    height: Float?,
): EditorWidgetNode {
    val union = selectionBounds(ids, layout) ?: return root
    val kx = if (width != null && union.width > 0f) width / union.width else 1f
    val ky = if (height != null && union.height > 0f) height / union.height else 1f
    return topLevel(root, ids).fold(root) { acc, id ->
        val r = layout.rects[id] ?: return@fold acc
        val scaled =
            Rect(
                union.left + (r.left - union.left) * kx,
                union.top + (r.top - union.top) * ky,
                union.left + (r.right - union.left) * kx,
                union.top + (r.bottom - union.top) * ky,
            )
        withRect(acc, id, scaled, layout.parentRects[id] ?: screen)
    }
}

/** The text the fields of the properties show where the selected widgets have different values. */
internal const val MIXED = "Mixed"

/**
 * The properties of several widgets as one: a value they all have is kept, a value that differs, or that only some have, becomes
 * [MIXED]; a property none has is left out.
 */
internal fun mergedProperties(nodes: List<EditorWidgetNode>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val keys = nodes.flatMap { it.node.properties.keys }.distinct()
    for (key in keys) {
        val values = nodes.map { it.node.properties[key] }
        val first = values.first()
        out[key] = if (first != null && values.all { it == first }) first else MIXED
    }
    return out
}

// ---- the editor state

/** Applies a click on [clicked] to the selection of the editor; [order] is what a range runs through. */
internal fun LayoutEditorState.selectBy(
    clicked: UUID,
    order: List<UUID>,
    mode: SelectMode,
) {
    val (selection, anchor) = applySelection(selectedIds.toList(), selectionAnchor, clicked, order, mode)
    selectedIds.clear()
    selectedIds.addAll(selection)
    selectionAnchor = anchor
}

/** Ctrl+A: every visible widget that sits next to the selected one (in the root when nothing is selected). */
internal fun LayoutEditorState.selectAllSiblings() {
    val doc = activeFile ?: return
    val parent = selectedIds.lastOrNull()?.let { doc.root.parentOf(it) } ?: doc.root
    val ids = parent.children.map { it.id }.filter { it !in hiddenIds && it !in lockedIds }
    if (ids.isEmpty()) return
    selectedIds.clear()
    selectedIds.addAll(ids)
    selectionAnchor = ids.first()
}

private fun LayoutEditorState.selectionLayout(): Pair<EditorWidgetNode, TreeLayout>? {
    val doc = activeFile ?: return null
    return doc.root to layoutTree(doc.root, screenRect(this))
}

/** The rectangle around the selected widgets in layout pixels. */
internal fun LayoutEditorState.selectionRect(): Rect? {
    val (root, layout) = selectionLayout() ?: return null
    return selectionBounds(selectedIds.filter { it != root.id }, layout)
}

/** Moves the selection so that the rectangle around it starts at [x] and [y]; null keeps that side. */
internal fun LayoutEditorState.moveSelectionTo(
    x: Float?,
    y: Float?,
) {
    val (root, layout) = selectionLayout() ?: return
    val ids = selectedIds.filter { it != root.id && it !in lockedIds }
    val bounds = selectionBounds(ids, layout) ?: return
    val dx = if (x != null) x - bounds.left else 0f
    val dy = if (y != null) y - bounds.top else 0f
    if (dx == 0f && dy == 0f) return
    pushUndo(activeFileIndex, root, "Сдвиг выбранных")
    updateActiveRoot(movedSelection(root, ids, layout, screenRect(this), dx, dy))
}

/** Scales the selection so that the rectangle around it is [width] by [height]; null keeps that side. */
internal fun LayoutEditorState.scaleSelectionTo(
    width: Float?,
    height: Float?,
) {
    val (root, layout) = selectionLayout() ?: return
    val ids = selectedIds.filter { it != root.id && it !in lockedIds }
    pushUndo(activeFileIndex, root, "Размер выбранных")
    updateActiveRoot(scaledSelection(root, ids, layout, screenRect(this), width, height))
}

/** Sets the property [key] to [value] on every selected widget, as one step of the history. */
internal fun LayoutEditorState.setOnSelected(
    key: String,
    value: String,
) {
    val doc = activeFile ?: return
    val ids = selectedIds.filter { doc.root.findNode(it) != null }
    if (ids.isEmpty()) return
    pushUndo(activeFileIndex, doc.root, "$key = $value")
    updateActiveRoot(
        ids.fold(doc.root) { acc, id ->
            acc.updateNode(id) { n -> n.copy(node = n.node.copy(properties = LinkedHashMap(n.node.properties).also { it[key] = value })) }
        },
    )
}

/** True when [this] holds the whole of [other]. */
internal fun Rect.containsRect(other: Rect): Boolean =
    left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

/**
 * True when the widget [id] is a background: another widget that is shown and can be picked lies wholly inside it, whether it is
 * its child or only sits on top of it. A press on a background does not pick it at once, so that a frame can be dragged out
 * inside it to select what lies on it; the widget is picked when the button is let go without a drag.
 */
internal fun coversOthers(
    root: EditorWidgetNode,
    id: UUID,
    layout: TreeLayout,
    hidden: Set<UUID>,
    locked: Set<UUID>,
): Boolean {
    val rect = layout.rects[id] ?: return false
    val ancestors =
        root
            .pathTo(id)
            .orEmpty()
            .map { it.id }
            .toSet()

    fun covers(node: EditorWidgetNode): Boolean {
        if (!node.isVisible() || node.id in hidden) return false
        val r = layout.rects[node.id]
        if (node.id != id && node.id !in ancestors && node.id !in locked && r != null && rect.containsRect(r)) return true
        return node.children.any { covers(it) }
    }
    return covers(root)
}

/**
 * The widgets a frame dragged out on the canvas selects: those it touches, except the ones that hold the whole frame, because a
 * frame drawn inside a background is meant for what lies on the background, not for the background itself.
 */
internal fun selectInBox(
    node: EditorWidgetNode,
    box: Rect,
    layout: TreeLayout,
    out: MutableList<UUID>,
    hidden: Set<UUID>,
    locked: Set<UUID>,
    isRoot: Boolean = false,
) {
    if (!node.isVisible() || node.id in hidden) return
    val rect = layout.rects[node.id] ?: return
    if (!isRoot && node.id !in locked && box.overlaps(rect) && !rect.containsRect(box)) out.add(node.id)
    for (child in node.children) selectInBox(child, box, layout, out, hidden, locked)
}
