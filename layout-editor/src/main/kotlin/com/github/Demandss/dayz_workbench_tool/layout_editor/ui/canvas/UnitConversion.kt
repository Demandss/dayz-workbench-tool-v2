package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import kotlin.math.abs
import kotlin.math.round

/** A value in pixels written as a share of its parent, or the other way round. A parent without size leaves the value as it is. */
internal fun convertUnit(
    value: Float,
    parentSize: Float,
    toPixels: Boolean,
): Float =
    when {
        parentSize == 0f -> value
        toPixels -> value * parentSize
        else -> value / parentSize
    }

/** A number as it is written in a layout: up to six decimals, no trailing zeros, no `-0`. */
internal fun formatNumber(value: Float): String {
    val rounded = round(value * 1000000f) / 1000000f
    if (abs(rounded) < 0.0000005f) return "0"
    val text = "%.6f".format(java.util.Locale.ROOT, rounded)
    return text.trimEnd('0').trimEnd('.')
}
