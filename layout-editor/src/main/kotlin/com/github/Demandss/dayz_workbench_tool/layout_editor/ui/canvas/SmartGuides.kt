package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A guide line shown while a widget is dragged: [at] on its axis, from [from] to [to] along it. */
data class GuideLine(
    val vertical: Boolean,
    val at: Float,
    val from: Float,
    val to: Float,
)

/** How far to move the dragged rectangle so that it snaps to the others, and the guides that explain it. */
internal class SnapResult(
    val dx: Float,
    val dy: Float,
    val guides: List<GuideLine>,
)

private fun Rect.xs() = floatArrayOf(left, center.x, right)

private fun Rect.ys() = floatArrayOf(top, center.y, bottom)

/**
 * Snaps the rectangle [moving] after it was moved by [delta] to the edges and centres of [others] when they are closer
 * than [threshold]. Each axis snaps on its own, to the nearest pair.
 */
internal fun snapMove(
    moving: Rect,
    delta: Offset,
    others: List<Rect>,
    threshold: Float,
): SnapResult {
    val moved = moving.translate(delta)

    fun best(
        mine: FloatArray,
        theirs: (Rect) -> FloatArray,
    ): Pair<Float, Rect>? {
        var bestShift = 0f
        var bestRect: Rect? = null
        var bestDist = threshold + 1f
        for (other in others) {
            for (t in theirs(other)) {
                for (m in mine) {
                    val d = abs(t - m)
                    if (d < bestDist) {
                        bestDist = d
                        bestShift = t - m
                        bestRect = other
                    }
                }
            }
        }
        return if (bestRect != null && bestDist <= threshold) bestShift to bestRect else null
    }

    val x = best(moved.xs()) { it.xs() }
    val y = best(moved.ys()) { it.ys() }
    val dx = x?.first ?: 0f
    val dy = y?.first ?: 0f
    val snapped = moved.translate(Offset(dx, dy))

    val guides = mutableListOf<GuideLine>()
    if (x != null) {
        // Every edge of the moving rectangle that now lines up with an edge of any other rectangle gets a line
        for (other in others) {
            for (t in other.xs()) {
                for (m in snapped.xs()) {
                    if (abs(t - m) < 0.01f) {
                        guides += GuideLine(true, t, min(snapped.top, other.top), max(snapped.bottom, other.bottom))
                    }
                }
            }
        }
    }
    if (y != null) {
        for (other in others) {
            for (t in other.ys()) {
                for (m in snapped.ys()) {
                    if (abs(t - m) < 0.01f) {
                        guides += GuideLine(false, t, min(snapped.left, other.left), max(snapped.right, other.right))
                    }
                }
            }
        }
    }
    return SnapResult(dx, dy, guides.distinct())
}

/**
 * The gaps between the rectangle [a] and the rectangle [b], as when Alt is held over another widget in Figma: the empty
 * space between them when they do not overlap on an axis, otherwise the distance between their facing edges.
 */
internal fun distancesBetween(
    a: Rect,
    b: Rect,
): List<EdgeDistance> {
    val out = mutableListOf<EdgeDistance>()
    // The line goes through the middle of what the two have in common on the other axis, or through the middle of a
    val midY = if (a.top < b.bottom && b.top < a.bottom) (max(a.top, b.top) + min(a.bottom, b.bottom)) / 2f else a.center.y
    val midX = if (a.left < b.right && b.left < a.right) (max(a.left, b.left) + min(a.right, b.right)) / 2f else a.center.x
    when {
        a.right <= b.left -> {
            out += EdgeDistance(Offset(a.right, midY), Offset(b.left, midY), b.left - a.right)
        }

        b.right <= a.left -> {
            out += EdgeDistance(Offset(b.right, midY), Offset(a.left, midY), a.left - b.right)
        }

        else -> {
            out += EdgeDistance(Offset(min(a.left, b.left), midY), Offset(max(a.left, b.left), midY), abs(a.left - b.left))
            out += EdgeDistance(Offset(min(a.right, b.right), midY), Offset(max(a.right, b.right), midY), abs(a.right - b.right))
        }
    }
    when {
        a.bottom <= b.top -> {
            out += EdgeDistance(Offset(midX, a.bottom), Offset(midX, b.top), b.top - a.bottom)
        }

        b.bottom <= a.top -> {
            out += EdgeDistance(Offset(midX, b.bottom), Offset(midX, a.top), a.top - b.bottom)
        }

        else -> {
            out += EdgeDistance(Offset(midX, min(a.top, b.top)), Offset(midX, max(a.top, b.top)), abs(a.top - b.top))
            out += EdgeDistance(Offset(midX, min(a.bottom, b.bottom)), Offset(midX, max(a.bottom, b.bottom)), abs(a.bottom - b.bottom))
        }
    }
    return out.filter { it.value >= 0.5f }
}
