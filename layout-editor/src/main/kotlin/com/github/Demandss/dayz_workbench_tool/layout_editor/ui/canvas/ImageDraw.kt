package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
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
    alphaFactor: Float = 1f,
) {
    val tint =
        (node.prop("color")?.let { parseWidgetColor(it) } ?: Color.White).let { it.copy(alpha = it.alpha * alphaFactor) }
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

    // `stretch mode none` with `clamp mode wrap` repeats the picture at its own size instead of stretching it (a grid background)
    val tiled = node.prop("stretch mode")?.trim('"') == "none" && node.prop("clamp mode")?.trim('"') == "wrap"

    fun drawTiles() {
        val tileW = (image.width * zoom).coerceAtLeast(1f)
        val tileH = (image.height * zoom).coerceAtLeast(1f)
        clipRect(box.left, box.top, box.right, box.bottom) {
            var y = box.top
            while (y < box.bottom) {
                var x = box.left
                while (x < box.right) {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                        dstSize = IntSize(tileW.roundToInt().coerceAtLeast(1), tileH.roundToInt().coerceAtLeast(1)),
                        colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
                        blendMode = if (additive) BlendMode.Plus else BlendMode.SrcOver,
                    )
                    x += tileW
                }
                y += tileH
            }
        }
    }

    fun draw() {
        if (tiled) {
            drawTiles()
            return
        }
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(boxSize.width.roundToInt().coerceAtLeast(1), boxSize.height.roundToInt().coerceAtLeast(1)),
            colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
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
