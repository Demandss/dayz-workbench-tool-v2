package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.TreeLayout
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.isVisible
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.prop
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * The script classes of the game that size and place widgets (`SizeToChild`, `SizeToParent`, `AutoHeightSpacer`, `NoticeSpacer`,
 * `HorizontalSpacer`, `VerticalSpacer`). The game runs them when a layout is created, and Workbench does the same in Live
 * Preview; here each one is rewritten as the properties it would set on the widgets, worked out from the layout the widgets
 * already have, and the layout is made again until nothing changes (a script class inside another waits for the inner one).
 */
internal object VanillaScripts {
    private val classes =
        setOf(
            "SizeToChild",
            "SizeToParent",
            "AutoHeightSpacer",
            "NoticeSpacer",
            "HorizontalSpacer",
            "VerticalSpacer",
            "HorizontalSpacerWithFixedAspect",
            "RightGap",
        )
    private const val MAX_PASSES = 8

    /** True when the tree has a widget with one of these script classes. */
    fun present(root: EditorWidgetNode): Boolean = root.scriptClassName() in classes || root.children.any { present(it) }

    /** The layout of [root] after the script classes have done their work; [layoutOf] makes the layout of a tree. */
    fun layout(
        root: EditorWidgetNode,
        layoutOf: (EditorWidgetNode) -> TreeLayout,
    ): TreeLayout {
        var tree = root
        repeat(MAX_PASSES) {
            val layout = layoutOf(tree)
            val edits = LinkedHashMap<UUID, MutableMap<String, String>>()
            collect(tree, layout, edits)
            if (edits.isEmpty()) return layout
            tree = tree.withEdits(edits)
        }
        return layoutOf(tree)
    }

    private fun collect(
        node: EditorWidgetNode,
        layout: TreeLayout,
        edits: MutableMap<UUID, MutableMap<String, String>>,
    ) {
        when (node.scriptClassName()) {
            "SizeToChild" -> sizeToChild(node, layout, edits)
            "SizeToParent" -> sizeToParent(node, edits)
            "AutoHeightSpacer", "NoticeSpacer" -> autoHeight(node, layout, edits)
            "HorizontalSpacer" -> evenly(node, layout, edits, horizontal = true)
            "VerticalSpacer" -> evenly(node, layout, edits, horizontal = false)
            "HorizontalSpacerWithFixedAspect" -> fixedAspect(node, layout, edits)
            "RightGap" -> rightGap(node, layout, edits)
        }
        node.children.forEach { collect(it, layout, edits) }
    }

    private fun params(node: EditorWidgetNode): Map<String, String> =
        node.node.scriptParams
            .orEmpty()
            .mapValues { it.value.trim('"', ' ') }

    private fun flag(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)

    private fun number(value: String?): Float = value?.toFloatOrNull() ?: 0f

    private fun find(
        node: EditorWidgetNode,
        name: String,
    ): EditorWidgetNode? {
        for (child in node.children) {
            if (child.node.name == name) return child
            find(child, name)?.let { return it }
        }
        return null
    }

    /** The two numbers of `size` or `position` as the widget writes them down. */
    private fun pair(
        node: EditorWidgetNode,
        key: String,
        default: Float,
    ): Pair<Float, Float> {
        val parts = node.prop(key)?.split(Regex("\\s+")).orEmpty()
        return (parts.getOrNull(0)?.toFloatOrNull() ?: default) to (parts.getOrNull(1)?.toFloatOrNull() ?: default)
    }

    private fun text(value: Float): String =
        if (value ==
            value.toInt().toFloat()
        ) {
            value.toInt().toString()
        } else {
            "%.3f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
        }

    private fun MutableMap<UUID, MutableMap<String, String>>.set(
        node: EditorWidgetNode,
        key: String,
        value: String,
    ) {
        if (node.prop(key) != value) getOrPut(node.id) { LinkedHashMap() }[key] = value
    }

    /** Resizes the widget to the screen size of its child, plus an offset; each axis only when it is switched on. */
    private fun sizeToChild(
        node: EditorWidgetNode,
        layout: TreeLayout,
        edits: MutableMap<UUID, MutableMap<String, String>>,
    ) {
        val p = params(node)
        val child = p["m_ChildName"]?.let { find(node, it) } ?: return
        val childRect = layout.rects[child.id] ?: return
        val rootRect = layout.rects[node.id] ?: return
        val (rawW, rawH) = pair(node, "size", 0.1f)
        val newW =
            if (flag(p["m_ResizeHorizontal"]) &&
                abs(childRect.width - rootRect.width) > 0.01f
            ) {
                childRect.width + number(p["m_HorizontalOffset"])
            } else {
                rawW
            }
        val newH =
            if (flag(p["m_ResizeVertical"]) &&
                abs(childRect.height - rootRect.height) > 0.01f
            ) {
                childRect.height + number(p["m_VerticalOffset"])
            } else {
                rawH
            }
        if (abs(newW - rawW) > 0.001f || abs(newH - rawH) > 0.001f) edits.set(node, "size", "${text(newW)} ${text(newH)}")
    }

    /** Takes the size in shares of the parent on the axes that are switched on, so that the widget follows its parent. */
    private fun sizeToParent(
        node: EditorWidgetNode,
        edits: MutableMap<UUID, MutableMap<String, String>>,
    ) {
        val p = params(node)
        if (flag(p["m_ResizeHorizontal"])) edits.set(node, "hexactsize", "0")
        if (flag(p["m_ResizeVertical"])) edits.set(node, "vexactsize", "0")
    }

    /** Stacks the visible children in rows (or measures them where they are) and sets the height of the widget to the stack. */
    private fun autoHeight(
        node: EditorWidgetNode,
        layout: TreeLayout,
        edits: MutableMap<UUID, MutableMap<String, String>>,
    ) {
        val p = params(node)
        val rect = layout.rects[node.id] ?: return
        val align = flag(p["AlignChilds"])
        val gap = number(p["Gap"])
        val children = node.children.filter { it.isVisible() && it.node.name != "SelectedContainer" && it.node.name != "Icon" }
        edits.set(node, "vexactpos", "1")
        var height = 0f
        if (children.isNotEmpty()) {
            if (align) {
                var top = number(p["Top"])
                var rowRight = rect.width
                var rowHeight = 0f
                for (child in children) {
                    val size = layout.rects[child.id]?.size ?: continue
                    if (rowRight < size.width) {
                        top += rowHeight
                        if (rowHeight > 0) top += gap
                        rowRight = rect.width
                        rowHeight = 0f
                    }
                    rowHeight = max(rowHeight, size.height)
                    edits.set(child, "position", "${text(rect.width - rowRight)} ${text(top)}")
                    edits.set(child, "hexactpos", "1")
                    edits.set(child, "vexactpos", "1")
                    rowRight -= size.width + gap
                }
                height = top + rowHeight
            } else {
                var bottom = -100000f
                for (child in children) {
                    val r = layout.rects[child.id] ?: continue
                    bottom = max(bottom, r.top + r.height)
                }
                height = bottom - rect.top
            }
        }
        val maxHeight = number(p["MaxHeight"])
        if (maxHeight > 0 && height > maxHeight) height = maxHeight
        val minHeight = number(p["MinHeight"])
        if (minHeight > height) height = minHeight
        val (rawW, rawH) = pair(node, "size", 0.1f)
        if (abs(rawH - height) > 1f) edits.set(node, "size", "${text(rawW)} ${text(height)}")
    }

    /** The first child sets the size of a cell; every other child is put to the right of it, `coef` of a cell further. */
    private fun fixedAspect(
        node: EditorWidgetNode,
        layout: TreeLayout,
        edits: MutableMap<UUID, MutableMap<String, String>>,
    ) {
        val coef = number(params(node)["coef"])
        val cell = node.children.firstOrNull()?.let { layout.rects[it.id] } ?: return
        node.children.drop(1).forEach { child ->
            edits.set(child, "position", "${text(cell.width + cell.width * coef)} 0")
            edits.set(child, "hexactpos", "1")
            edits.set(child, "vexactpos", "1")
        }
    }

    /** Makes the widget as wide as its parent less `gap`, and as high as its parent, in pixels. */
    private fun rightGap(
        node: EditorWidgetNode,
        layout: TreeLayout,
        edits: MutableMap<UUID, MutableMap<String, String>>,
    ) {
        val parent = layout.parentRects[node.id] ?: return
        edits.set(node, "size", "${text(parent.width - number(params(node)["gap"]))} ${text(parent.height)}")
        edits.set(node, "hexactsize", "1")
        edits.set(node, "vexactsize", "1")
    }

    /** Divides the widget into as many equal cells as it has children, in a row or a column, with a border and a gap. */
    private fun evenly(
        node: EditorWidgetNode,
        layout: TreeLayout,
        edits: MutableMap<UUID, MutableMap<String, String>>,
        horizontal: Boolean,
    ) {
        val p = params(node)
        val rect: Rect = layout.rects[node.id] ?: return
        val count = node.children.size
        if (count == 0) return
        val border = number(if (horizontal) p["Border"] else p["border"])
        val gap = number(if (horizontal) p["Gap"] else p["gap"])
        node.children.forEachIndexed { index, child ->
            val itemW: Float
            val itemH: Float
            val x: Float
            val y: Float
            if (horizontal) {
                itemW = (rect.width - border * 2 - (count - 1) * gap) / count
                itemH = rect.height - 2 * border
                x = border + (itemW + gap) * index
                y = border
            } else {
                itemW = rect.width - 2 * border
                itemH = (rect.height - border * 2 - (count - 1) * gap) / count
                x = border
                y = border + (itemH + gap) * index
            }
            edits.set(child, "position", "${text(x)} ${text(y)}")
            edits.set(child, "size", "${text(itemW)} ${text(itemH)}")
            for (flagName in listOf("hexactpos", "vexactpos", "hexactsize", "vexactsize")) edits.set(child, flagName, "1")
        }
    }
}

/** A copy of the tree in which the widgets in [edits] have the given properties; the rest keep their objects. */
private fun EditorWidgetNode.withEdits(edits: Map<UUID, Map<String, String>>): EditorWidgetNode {
    val children = children.map { it.withEdits(edits) }
    val own = edits[id]
    if (own == null && children.indices.all { children[it] === this.children[it] }) return this
    val props = LinkedHashMap(node.properties)
    own?.forEach { (key, value) -> props[key] = value }
    return copy(node = node.copy(properties = props), children = children.toMutableList())
}
