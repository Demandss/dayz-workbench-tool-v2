package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode

/**
 * Objects the drawing needs again and again: a colour filter and a repeating shader are native objects, and making them for every
 * piece of every widget on every frame is most of what dragging the view costs. Each cache keeps the latest few.
 */
private class Lru<K, V>(
    private val limit: Int,
) {
    private val map =
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > limit
        }

    fun get(
        key: K,
        make: () -> V,
    ): V = map.getOrPut(key, make)
}

private val tintFilters = Lru<Color, ColorFilter>(256)
private val tileBrushes = Lru<ImageBitmap, ShaderBrush>(128)
private val skinFilters = Lru<Pair<Float, Color>, ColorFilter>(256)

/** A multiplication by [tint], the same object for the same colour. */
internal fun tintFilter(tint: Color): ColorFilter = tintFilters.get(tint) { ColorFilter.tint(tint, BlendMode.Modulate) }

/** [image] repeated in both directions, the same shader for the same picture. */
internal fun tileBrush(image: ImageBitmap): ShaderBrush =
    tileBrushes.get(image) { ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)) }

/** The filter of a skin with the given lift and tint, made by [make] once per pair. */
internal fun skinFilterFor(
    lift: Float,
    tint: Color,
    make: () -> ColorFilter,
): ColorFilter = skinFilters.get(lift to tint, make)
