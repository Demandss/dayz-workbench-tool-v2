package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max

/** The nine slots of a style, as the `.styles` files name them. */
val NINE_SLICE_SLOTS = listOf("LeftTop", "Top", "RightTop", "Left", "Center", "Right", "LeftBottom", "Bottom", "RightBottom")

/**
 * Where each image of a 9-slice goes in [dst]. [native] holds the size of every image that exists. The corners keep their
 * size, the edges take the thickness of the row or column they are in and stretch along the other axis, the centre takes
 * what is left. When [dst] is smaller than the two corners together, the sides shrink in proportion so that they never overlap.
 */
fun sliceRects(
    dst: Rect,
    native: Map<String, Size>,
): Map<String, Rect> {
    fun w(vararg slots: String) = slots.maxOfOrNull { native[it]?.width ?: 0f } ?: 0f

    fun h(vararg slots: String) = slots.maxOfOrNull { native[it]?.height ?: 0f } ?: 0f
    var left = w("LeftTop", "Left", "LeftBottom")
    var right = w("RightTop", "Right", "RightBottom")
    var top = h("LeftTop", "Top", "RightTop")
    var bottom = h("LeftBottom", "Bottom", "RightBottom")
    if (left + right > dst.width && left + right > 0f) {
        val k = dst.width / (left + right)
        left *= k
        right *= k
    }
    if (top + bottom > dst.height && top + bottom > 0f) {
        val k = dst.height / (top + bottom)
        top *= k
        bottom *= k
    }
    val xs = floatArrayOf(dst.left, dst.left + left, max(dst.left + left, dst.right - right), dst.right)
    val ys = floatArrayOf(dst.top, dst.top + top, max(dst.top + top, dst.bottom - bottom), dst.bottom)
    val out = LinkedHashMap<String, Rect>()
    NINE_SLICE_SLOTS.forEachIndexed { index, slot ->
        if (slot !in native) return@forEachIndexed
        val c = index % 3
        val r = index / 3
        out[slot] = Rect(xs[c], ys[r], xs[c + 1], ys[r + 1])
    }
    // A style with no corner images has its side edges run the whole height (and the Center too, between them): the
    // corners are covered by the sides, as Workbench draws DayZDefaultButton
    val cornersLeft = "LeftTop" in native || "LeftBottom" in native
    val cornersRight = "RightTop" in native || "RightBottom" in native
    if (!cornersLeft && "Left" in out) out["Left"] = Rect(xs[0], dst.top, xs[1], dst.bottom)
    if (!cornersRight && "Right" in out) out["Right"] = Rect(xs[2], dst.top, xs[3], dst.bottom)
    if (!cornersLeft && !cornersRight && "Center" in out) out["Center"] = Rect(xs[1], dst.top, xs[2], dst.bottom)
    return out
}
