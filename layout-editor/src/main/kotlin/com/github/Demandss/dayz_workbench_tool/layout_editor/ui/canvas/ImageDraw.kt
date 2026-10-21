package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.math.roundToInt

/**
 * Draws the picture of an image widget over its box: tinted by `color`, mirrored by `flip u` and `flip v`, turned by the
 * z angle of `rotation` around `pivot` (the centre unless set) and cut at the box, as Workbench does with a turned picture.
 * `mode additive` adds the picture to what is behind it.
 */
internal fun DrawScope.drawImageWidget(
    node: EditorWidgetNode,
    image: ImageBitmap,
    topLeft: Offset,
    boxSize: Size,
    zoom: Float,
) {
    val tint = node.prop("color")?.let { parseWidgetColor(it) }
    val flipU = node.prop("flip u") == "1"
    val flipV = node.prop("flip v") == "1"
    val angle =
        node
            .prop("rotation")
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.getOrNull(2)
            ?.toFloatOrNull() ?: 0f
    val pivotParts = node.prop("pivot")?.trim()?.split(Regex("\\s+"))
    val pivot =
        Offset(
            topLeft.x + boxSize.width * (pivotParts?.getOrNull(0)?.toFloatOrNull() ?: 0.5f),
            topLeft.y + boxSize.height * (pivotParts?.getOrNull(1)?.toFloatOrNull() ?: 0.5f),
        )
    val box = Rect(topLeft, boxSize)
    val additive = node.prop("mode")?.trim('"') == "additive"

    fun draw() {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(boxSize.width.roundToInt().coerceAtLeast(1), boxSize.height.roundToInt().coerceAtLeast(1)),
            colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) },
            blendMode = if (additive) BlendMode.Plus else BlendMode.SrcOver,
        )
    }

    fun flipped() {
        if (flipU || flipV) {
            scale(if (flipU) -1f else 1f, if (flipV) -1f else 1f, pivot = box.center) { draw() }
        } else {
            draw()
        }
    }
    if (angle != 0f) {
        clipRect(box.left, box.top, box.right, box.bottom) { rotate(angle, pivot) { flipped() } }
    } else {
        flipped()
    }
}
