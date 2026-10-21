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
    // The pieces of tiled slots cut out of the atlas, drawn with a repeating shader
    val tiles: Map<String, ImageBitmap> = emptyMap(),
    // How much white is added on top: a hovered button whose style has no Highlight images is lightened (measured in Workbench)
    val lift: Float = 0f,
)

/**
 * The same atlas with the slots renamed: [names] maps a new slot to the slots it may come from, the first one that exists
 * wins. `mapOf("Left" to listOf("LeftCenter"))` makes a skin whose `Left` is the `LeftCenter` of this one.
 */
fun WidgetSkin.renamed(names: Map<String, List<String>>): WidgetSkin {
    val from = names.mapNotNull { (to, options) -> options.firstOrNull { it in source }?.let { to to it } }.toMap()
    return WidgetSkin(
        atlas,
        from.mapValues { source.getValue(it.value) },
        from.mapValues { native.getValue(it.value) },
        styleColor,
        from.mapValues { flags[it.value] ?: 0 },
        from.mapNotNull { (to, source) -> tiles[source]?.let { to to it } }.toMap(),
    )
}

/** Resolved parts of a skin that do not depend on the bitmap being loaded yet. */
internal class SkinPlan(
    val textureFile: java.io.File,
    val refWidth: Int,
    val refHeight: Int,
    val images: Map<String, com.github.demandss.dayz_workbench_tool.imageset.ImageDef>,
    val styleColor: Color,
    // True when the Highlight state has no image of its own
    val highlightEmpty: Boolean = false,
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
