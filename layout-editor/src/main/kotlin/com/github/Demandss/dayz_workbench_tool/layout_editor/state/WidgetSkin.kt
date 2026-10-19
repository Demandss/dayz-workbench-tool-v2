package com.github.demandss.dayz_workbench_tool.layout_editor.state

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect

/**
 * What a style paints for the normal state of a widget: the pieces of the atlas by slot name (`LeftTop`, `Center`...),
 * their sizes in the reference pixels of the imageset (to lay out a 9-slice), and the colour of the style.
 */
class WidgetSkin(
    val atlas: ImageBitmap,
    val source: Map<String, IntRect>,
    val native: Map<String, Size>,
    val styleColor: Color,
    // ImageFlags bits per slot: a tiled image repeats at its own size instead of stretching
    val flags: Map<String, Int> = emptyMap(),
)

/** Resolved parts of a skin that do not depend on the bitmap being loaded yet. */
internal class SkinPlan(
    val textureFile: java.io.File,
    val refWidth: Int,
    val refHeight: Int,
    val images: Map<String, com.github.demandss.dayz_workbench_tool.imageset.ImageDef>,
    val styleColor: Color,
)

/** The style colour is an ARGB number written as a decimal (`4294967295` is opaque white). */
internal fun styleColorOf(text: String): Color {
    val v = text.trim().toLongOrNull() ?: return Color.White
    return Color(
        alpha = ((v shr 24) and 0xFF) / 255f,
        red = ((v shr 16) and 0xFF) / 255f,
        green = ((v shr 8) and 0xFF) / 255f,
        blue = (v and 0xFF) / 255f,
    )
}
