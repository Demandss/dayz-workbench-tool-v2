package com.github.demandss.dayz_workbench_tool.layout_editor.ui.styles

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.github.demandss.dayz_workbench_tool.layout_editor.state.WidgetSkin
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.DRAWN_SLOTS
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.drawSkinWidget

/** The slots the preview knows how to draw. */
internal val PREVIEW_SLOTS: Set<String> = DRAWN_SLOTS

/** Draws what a style paints into a box in the middle of the canvas, as the canvas of the layout editor would draw it. */
internal fun DrawScope.drawStylePreview(
    skin: WidgetSkin,
    tint: Color,
): Set<String> {
    val box = Rect(size.width * 0.2f, size.height * 0.2f, size.width * 0.8f, size.height * 0.8f)
    return drawSkinWidget(skin, tint, box, 1f)
}
