package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The edge or the corner of a widget that is grabbed. */
enum class ResizeHandle(
    val left: Boolean = false,
    val right: Boolean = false,
    val top: Boolean = false,
    val bottom: Boolean = false,
) {
    W(left = true),
    E(right = true),
    N(top = true),
    S(bottom = true),
    NW(left = true, top = true),
    NE(right = true, top = true),
    SW(left = true, bottom = true),
    SE(right = true, bottom = true),
}

/**
 * The handle under the point [p] (layout pixels) for a widget at [rect], or null. The strip along the edges is [tolerance]
 * wide on both sides of the edge, but never more than a third of a small widget, so that a tiny one can still be moved.
 */
internal fun handleAt(
    rect: Rect,
    p: Offset,
    tolerance: Float,
): ResizeHandle? {
    val t = min(tolerance, min(rect.width, rect.height) / 3f)
    if (p.x < rect.left - t || p.x > rect.right + t || p.y < rect.top - t || p.y > rect.bottom + t) return null
    val nearL = abs(p.x - rect.left) <= t
    val nearR = abs(p.x - rect.right) <= t
    val nearT = abs(p.y - rect.top) <= t
    val nearB = abs(p.y - rect.bottom) <= t
    // On a narrow widget both sides can match, the closer one wins
    val left = nearL && (!nearR || abs(p.x - rect.left) <= abs(p.x - rect.right))
    val right = nearR && !left
    val top = nearT && (!nearB || abs(p.y - rect.top) <= abs(p.y - rect.bottom))
    val bottom = nearB && !top
    return ResizeHandle.entries.firstOrNull { it.left == left && it.right == right && it.top == top && it.bottom == bottom }
}

/**
 * The rectangle after the [handle] was dragged by [delta]. [keepAspect] holds the ratio of [start] (corners only grow
 * along the dominant axis), [fromCentre] moves the opposite side the other way as well. The size never goes below [minSize].
 */
internal fun resizedRect(
    start: Rect,
    handle: ResizeHandle,
    delta: Offset,
    minSize: Float = 1f,
    keepAspect: Boolean = false,
    fromCentre: Boolean = false,
): Rect {
    var dx = delta.x
    var dy = delta.y
    if (keepAspect && start.width > 0f && start.height > 0f) {
        val ratio = start.width / start.height
        val growX =
            if (handle.left) {
                -dx
            } else if (handle.right) {
                dx
            } else {
                0f
            }
        val growY =
            if (handle.top) {
                -dy
            } else if (handle.bottom) {
                dy
            } else {
                0f
            }
        // The axis that changes most decides, the other one follows
        if (handle.left || handle.right) {
            if (handle.top || handle.bottom) {
                if (abs(growX) >= abs(growY * ratio)) {
                    val g = growX / ratio
                    dy = if (handle.top) -g else g
                } else {
                    val g = growY * ratio
                    dx = if (handle.left) -g else g
                }
            } else {
                val g = growX / ratio
                dy = 0f
                // A side handle changes the other axis around the centre
                return resizedRect(start, handle, Offset(dx, 0f), minSize, false, fromCentre)
                    .let {
                        val h = it.width / ratio
                        Rect(it.left, start.center.y - h / 2f, it.right, start.center.y + h / 2f)
                    }.also { g.hashCode() }
            }
        } else {
            return resizedRect(start, handle, Offset(0f, dy), minSize, false, fromCentre).let {
                val w = it.height * ratio
                Rect(start.center.x - w / 2f, it.top, start.center.x + w / 2f, it.bottom)
            }
        }
    }
    var l = start.left
    var r = start.right
    var t = start.top
    var b = start.bottom
    if (handle.left) l += dx
    if (handle.right) r += dx
    if (handle.top) t += dy
    if (handle.bottom) b += dy
    if (fromCentre) {
        if (handle.left) r -= dx
        if (handle.right) l -= dx
        if (handle.top) b -= dy
        if (handle.bottom) t -= dy
    }
    // Dragged past the other side it stops at the minimum size instead of turning over
    if (r - l < minSize) {
        if (handle.left) l = r - minSize else r = l + minSize
    }
    if (b - t < minSize) {
        if (handle.top) t = b - minSize else b = t + minSize
    }
    return Rect(l, t, r, b)
}

/** Moves the sides that are being dragged to the nearest edge or centre of [others] within [threshold]. */
internal fun snapResized(
    rect: Rect,
    handle: ResizeHandle,
    others: List<Rect>,
    threshold: Float,
): Pair<Rect, List<GuideLine>> {
    var l = rect.left
    var r = rect.right
    var t = rect.top
    var b = rect.bottom
    val guides = mutableListOf<GuideLine>()

    fun nearest(
        value: Float,
        pick: (Rect) -> FloatArray,
    ): Pair<Float, Rect>? {
        var best: Pair<Float, Rect>? = null
        var bestD = threshold + 1f
        for (o in others) {
            for (c in pick(o)) {
                val d = abs(c - value)
                if (d < bestD) {
                    bestD = d
                    best = c to o
                }
            }
        }
        return if (bestD <= threshold) best else null
    }

    fun xs(o: Rect) = floatArrayOf(o.left, o.center.x, o.right)

    fun ys(o: Rect) = floatArrayOf(o.top, o.center.y, o.bottom)
    if (handle.left) nearest(l, ::xs)?.let { l = it.first }
    if (handle.right) nearest(r, ::xs)?.let { r = it.first }
    if (handle.top) nearest(t, ::ys)?.let { t = it.first }
    if (handle.bottom) nearest(b, ::ys)?.let { b = it.first }
    val snapped = Rect(l, t, max(r, l + 1f), max(b, t + 1f))
    for (o in others) {
        if (handle.left || handle.right) {
            for (c in xs(o)) {
                val moving = if (handle.left) snapped.left else snapped.right
                if (abs(c - moving) < 0.01f) guides += GuideLine(true, c, min(snapped.top, o.top), max(snapped.bottom, o.bottom))
            }
        }
        if (handle.top || handle.bottom) {
            for (c in ys(o)) {
                val moving = if (handle.top) snapped.top else snapped.bottom
                if (abs(c - moving) < 0.01f) guides += GuideLine(false, c, min(snapped.left, o.left), max(snapped.right, o.right))
            }
        }
    }
    return snapped to guides.distinct()
}

/**
 * Gives the widget [id] the rectangle [rect] on screen: its size in the units of its own exact flags and its position
 * measured from the side its anchors name. [parent] is the rectangle the widget sits in.
 */
internal fun withRect(
    root: EditorWidgetNode,
    id: java.util.UUID,
    rect: Rect,
    parent: Rect,
): EditorWidgetNode =
    root.updateNode(id) { n ->
        val props = LinkedHashMap(n.node.properties)
        val exactW = props["hexactsize"] == "1"
        val exactH = props["vexactsize"] == "1"
        val exactX = props["hexactpos"] == "1"
        val exactY = props["vexactpos"] == "1"
        val w =
            if (exactW) {
                rect.width
            } else if (parent.width == 0f) {
                0f
            } else {
                rect.width / parent.width
            }
        val h =
            if (exactH) {
                rect.height
            } else if (parent.height == 0f) {
                0f
            } else {
                rect.height / parent.height
            }
        val px =
            when (props["halign"]) {
                "right_ref", "right" -> parent.right - rect.right
                "center_ref" -> rect.center.x - parent.center.x
                else -> rect.left - parent.left
            }
        val py =
            when (props["valign"]) {
                "bottom_ref", "bottom" -> parent.bottom - rect.bottom
                "center_ref" -> rect.center.y - parent.center.y
                else -> rect.top - parent.top
            }
        val x =
            if (exactX) {
                kotlin.math.round(px)
            } else if (parent.width == 0f) {
                0f
            } else {
                px / parent.width
            }
        val y =
            if (exactY) {
                kotlin.math.round(py)
            } else if (parent.height == 0f) {
                0f
            } else {
                py / parent.height
            }
        val width = if (exactW) kotlin.math.round(w) else w
        val height = if (exactH) kotlin.math.round(h) else h
        props["size"] = "${formatNumber(width)} ${formatNumber(height)}"
        props["position"] = "${formatNumber(x)} ${formatNumber(y)}"
        n.copy(node = n.node.copy(properties = props))
    }
