package com.github.demandss.dayz_workbench_tool.layout_editor.ui.styles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.github.demandss.dayz_workbench_tool.layout_editor.state.WidgetSkin
import com.github.demandss.dayz_workbench_tool.layout_editor.state.renamed
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.NINE_SLICE_SLOTS
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.drawSkinSlot
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.drawSkinTinted

// Widgets other than panels name their slots differently; each group below is drawn as the part of a widget it belongs to.
// The frame of a list box has no corners at the bottom and calls its edges `...Center`.
private val FRAME =
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

private val TITLE = prefixed("Title")
private val BAR = prefixed("Bar")

/** The slots the preview knows how to draw. */
internal val PREVIEW_SLOTS: Set<String> =
    FRAME.values.flatten().toSet() + TITLE.values.flatten() + BAR.values.flatten() +
        setOf("Highlight", "Overlay", "CheckBox", "ArrowLeft", "ArrowRight", "ScrollBarCenter")

/**
 * Draws what a style paints into a box in the middle of the canvas: the frame, a title strip, a bar, a highlight, arrows,
 * a check mark. Returns the slots that were drawn, so that the editor can say which ones it does not show.
 */
internal fun DrawScope.drawStylePreview(
    skin: WidgetSkin,
    tint: Color,
): Set<String> {
    val used = HashSet<String>()
    val box =
        Rect(
            size.width * 0.2f,
            size.height * 0.2f,
            size.width * 0.8f,
            size.height * 0.8f,
        )

    fun group(
        names: Map<String, List<String>>,
        area: Rect,
    ) {
        val part = skin.renamed(names)
        if (part.source.isEmpty()) return
        names.values.forEach { options -> options.firstOrNull { it in skin.source }?.let(used::add) }
        drawSkinTinted(part, tint, area.topLeft, area.size, 1f)
    }

    fun nativeOf(slot: String) = skin.native[slot]

    group(FRAME, box)

    val titleHeight = NINE_SLICE_SLOTS.mapNotNull { nativeOf("Title$it")?.height }.maxOrNull()
    if (titleHeight != null) group(TITLE, Rect(box.left, box.top, box.right, box.top + maxOf(26f, titleHeight)))

    val barHeight = listOf("BarTop", "BarCenter").mapNotNull { nativeOf(it)?.height }.sum().takeIf { it > 0f }
    if (BAR.values.any { it.single() in skin.source }) {
        val h = maxOf(14f, barHeight ?: 14f)
        val y = box.center.y - h / 2f
        group(BAR, Rect(box.left + 8f, y, box.left + 8f + (box.width - 16f) * 0.6f, y + h))
    }

    skin.native["Highlight"]?.let {
        used += "Highlight"
        drawSkinSlot(skin, "Highlight", Rect(box.left + 4f, box.center.y - 12f, box.right - 4f, box.center.y + 12f), tint, 1f)
    }
    skin.native["Overlay"]?.let {
        used += "Overlay"
        drawSkinSlot(skin, "Overlay", box, tint, 1f)
    }
    skin.native["CheckBox"]?.let {
        used += "CheckBox"
        drawSkinSlot(skin, "CheckBox", Rect(Offset(box.left + 12f, box.center.y - it.height / 2f), it), tint, 1f)
    }
    skin.native["ArrowLeft"]?.let {
        used += "ArrowLeft"
        drawSkinSlot(skin, "ArrowLeft", Rect(Offset(box.left + 6f, box.center.y - it.height / 2f), it), tint, 1f)
    }
    skin.native["ArrowRight"]?.let {
        used += "ArrowRight"
        drawSkinSlot(skin, "ArrowRight", Rect(Offset(box.right - 6f - it.width, box.center.y - it.height / 2f), it), tint, 1f)
    }
    skin.native["ScrollBarCenter"]?.let {
        used += "ScrollBarCenter"
        drawSkinSlot(
            skin,
            "ScrollBarCenter",
            Rect(Offset(box.right - 4f - it.width, box.top + 4f), Size(it.width, box.height - 8f)),
            tint,
            1f,
        )
    }
    return used
}
