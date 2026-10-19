package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

/** The distance between marks on a ruler, in layout pixels: the smallest round step that is at least [minGapPx] apart on the screen. */
internal fun rulerStep(
    zoom: Float,
    minGapPx: Float = 56f,
): Int {
    val steps = intArrayOf(1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000)
    return steps.firstOrNull { it * zoom >= minGapPx } ?: steps.last()
}
