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

/**
 * A number for a field that a person edits and that is written back into a layout: four significant digits, a whole number
 * without a decimal part. With a dot whatever the language of the interface is: a comma would not be read as a number.
 */
internal fun formatShort(value: Float): String {
    val whole = value.toLong()
    return if (value == whole.toFloat()) "$whole" else "%.4g".format(java.util.Locale.ROOT, value)
}

/** A number as it is written in a layout: up to six decimals, no trailing zeros, no `-0`. */
internal fun formatNumber(value: Float): String {
    // In double, because a float this large has no room for six decimals and would print 722.499939 for 722.5
    val rounded = kotlin.math.round(value.toDouble() * 1_000_000.0) / 1_000_000.0
    if (abs(rounded) < 0.0000005) return "0"
    val text = "%.6f".format(java.util.Locale.ROOT, rounded)
    return text.trimEnd('0').trimEnd('.')
}
