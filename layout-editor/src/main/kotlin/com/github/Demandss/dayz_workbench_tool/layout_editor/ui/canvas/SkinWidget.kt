package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.state.WidgetSkin
import com.github.demandss.dayz_workbench_tool.layout_editor.state.renamed
import kotlin.math.roundToInt

// Widgets other than panels name their slots differently; each group below is drawn as the part of a widget it belongs to.
// The frame of a list box has no corners at the bottom and calls its edges `...Center`.
internal val FRAME_SLOTS =
    mapOf(
        "LeftTop" to listOf("LeftTop", "LeftTopWithTitle"),
        "Top" to listOf("Top", "TopCenter"),
        "RightTop" to listOf("RightTop", "RightTopWithTitle"),
        "Left" to listOf("Left", "LeftCenter"),
        "Center" to listOf("Center"),
        "Right" to listOf("Right", "RightCenter"),
        "LeftBottom" to listOf("LeftBottom"),
        "Bottom" to listOf("Bottom", "BottomCenter"),
        "RightBottom" to listOf("RightBottom"),
    )

private fun prefixed(prefix: String) = NINE_SLICE_SLOTS.associateWith { listOf(prefix + it) }

internal val TITLE_SLOTS = prefixed("Title")
internal val BAR_SLOTS = prefixed("Bar")

/** The slots [drawSkinWidget] knows how to draw. */
internal val DRAWN_SLOTS: Set<String> =
    FRAME_SLOTS.values.flatten().toSet() + TITLE_SLOTS.values.flatten() + BAR_SLOTS.values.flatten() +
        setOf("Overlay", "CheckBox", "ArrowLeft", "ArrowRight")

/** How much of a progress bar is filled when nothing sets it: Workbench shows half. */
private const val PROGRESS_SHARE = 0.5f

/**
 * Draws the images of a style into [box] the way Workbench lays them out: the frame over the whole box, a title strip for
 * windows, the bar over a share of a progress bar, and single images (check mark, arrows, highlight) where their widget
 * keeps them. [zoom] scales the sizes of the images, which are in texture pixels. Returns the slots that were drawn.
 */
internal fun DrawScope.drawSkinWidget(
    skin: WidgetSkin,
    tint: Color,
    rawBox: Rect,
    zoom: Float,
    type: WidgetType? = null,
    // SimpleProgressBarWidget has no type of its own in the model
    progress: Boolean = type == WidgetType.PROGRESS_BAR,
    // How much of a progress bar is filled, and the direction it fills in (`current`, `vertical` and `flipped` of the widget)
    share: Float = PROGRESS_SHARE,
    vertical: Boolean = false,
    flipped: Boolean = false,
): Set<String> {
    // Workbench lays pixels on the pixel grid. Edges that fall inside a pixel would leave the resting look showing through
    // the rim of an image drawn over it, as thin lines
    val box =
        Rect(
            rawBox.left.roundToInt().toFloat(),
            rawBox.top.roundToInt().toFloat(),
            rawBox.right.roundToInt().toFloat(),
            rawBox.bottom.roundToInt().toFloat(),
        )
    val used = HashSet<String>()
    skin.under?.let { used += drawSkinWidget(it, tint, rawBox, zoom, type, progress, share, vertical, flipped) }

    fun group(
        names: Map<String, List<String>>,
        area: Rect,
    ) {
        val part = skin.renamed(names)
        if (part.source.isEmpty()) return
        names.values.forEach { options -> options.firstOrNull { it in skin.source }?.let(used::add) }
        drawSkinTinted(part, tint, area.topLeft, area.size, zoom)
    }

    fun sizeOf(slot: String): Size? = skin.native[slot]?.let { Size(it.width * zoom, it.height * zoom) }

    group(FRAME_SLOTS, box)

    // The title strip of a window is not drawn by Workbench without a title (measured on the Simple and debugUI styles), and
    // the bar of a slider is its thumb, which only moves while it is used
    if (BAR_SLOTS.values.any { it.single() in skin.source } && type != WidgetType.SLIDER) {
        if (progress) {
            // The bar is the image stretched over the whole widget and cut at the share that is filled, so a logo is
            // cut and not squeezed (measured on the DayZLoading style)
            val filled =
                when {
                    // A vertical bar fills from the top down, flipped from the bottom up (measured)
                    vertical && flipped -> Rect(box.left, box.bottom - box.height * share, box.right, box.bottom)

                    vertical -> Rect(box.left, box.top, box.right, box.top + box.height * share)

                    flipped -> Rect(box.right - box.width * share, box.top, box.right, box.bottom)

                    else -> Rect(box.left, box.top, box.left + box.width * share, box.bottom)
                }
            clipRect(filled.left, filled.top, filled.right, filled.bottom) { group(BAR_SLOTS, box) }
        } else {
            val h = maxOf(14f * zoom, listOf("BarTop", "BarCenter").mapNotNull { sizeOf(it)?.height }.sum())
            val y = box.center.y - h / 2f
            group(BAR_SLOTS, Rect(box.left + 8f * zoom, y, box.left + 8f * zoom + (box.width - 16f * zoom) * 0.6f, y + h))
        }
    }

    // A list box paints its row highlight and scroll thumb only while it is used, so Workbench draws neither at rest
    sizeOf("Overlay")?.let {
        used += "Overlay"
        drawSkinSlot(skin, "Overlay", box, tint, zoom)
    }
    sizeOf("CheckBox")?.let {
        used += "CheckBox"
        // An image taller than the widget is scaled down to its height (measured on the Editor check box style)
        val fit = minOf(1f, box.height / it.height)
        val shown = Size(it.width * fit, it.height * fit)
        drawSkinSlot(skin, "CheckBox", Rect(Offset(box.left, box.center.y - shown.height / 2f), shown), tint, zoom * fit)
    }
    sizeOf("ArrowLeft")?.let {
        used += "ArrowLeft"
        drawSkinSlot(skin, "ArrowLeft", Rect(Offset(box.left, box.center.y - it.height / 2f), it), tint, zoom)
    }
    sizeOf("ArrowRight")?.let {
        used += "ArrowRight"
        drawSkinSlot(skin, "ArrowRight", Rect(Offset(box.right - it.width, box.center.y - it.height / 2f), it), tint, zoom)
    }
    return used
}
