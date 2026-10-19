package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.rulerStep
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import kotlin.math.ceil
import kotlin.math.floor

private val Band = Color(0xF02B2D30)
private val Tick = Color(0xFF6F737A)
private val Label = Color(0xFF9499A1)
private val Extent = Color(0x554A9EFF)

/** Rulers along the top and left edge of the canvas in layout pixels. The selected widgets are marked on both. */
@Composable
fun Rulers(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        val thickness = 18.dp.toPx()
        val zoom = state.zoom
        val pan = state.panOffset
        val step = rulerStep(zoom)
        val style = TextStyle(color = Label, fontSize = 9.sp)

        // Where the selected widgets are
        val doc = state.activeFile
        val rects =
            if (doc != null && state.selectedIds.isNotEmpty()) {
                val layout = layoutTree(doc.root, screenRect(state))
                state.selectedIds.mapNotNull { layout.rects[it] }
            } else {
                emptyList()
            }

        drawRect(Band, Offset.Zero, Size(size.width, thickness))
        drawRect(Band, Offset.Zero, Size(thickness, size.height))

        if (rects.isNotEmpty()) {
            val l = rects.minOf { it.left } * zoom + pan.x
            val r = rects.maxOf { it.right } * zoom + pan.x
            val t = rects.minOf { it.top } * zoom + pan.y
            val b = rects.maxOf { it.bottom } * zoom + pan.y
            drawRect(Extent, Offset(l, 0f), Size(r - l, thickness))
            drawRect(Extent, Offset(0f, t), Size(thickness, b - t))
        }

        // Top
        val firstX = floor((thickness - pan.x) / zoom / step).toInt()
        val lastX = ceil((size.width - pan.x) / zoom / step).toInt()
        for (i in firstX..lastX) {
            val x = i * step * zoom + pan.x
            if (x < thickness) continue
            drawLine(Tick, Offset(x, thickness * 0.55f), Offset(x, thickness))
            drawText(measurer, "${i * step}", Offset(x + 3f, 1f), style, size = Size(60f, thickness))
        }
        // Left, the numbers lie on their side
        val firstY = floor((thickness - pan.y) / zoom / step).toInt()
        val lastY = ceil((size.height - pan.y) / zoom / step).toInt()
        for (i in firstY..lastY) {
            val y = i * step * zoom + pan.y
            if (y < thickness) continue
            drawLine(Tick, Offset(thickness * 0.55f, y), Offset(thickness, y))
            val pivot = Offset(thickness / 2f, y)
            val label = "${i * step}"
            rotate(
                -90f,
                pivot,
            ) {
                drawText(
                    measurer,
                    label,
                    Offset(pivot.x + 3f, pivot.y - measurer.measure(label, style).size.height / 2f),
                    style,
                    size = Size(60f, thickness),
                )
            }
        }
        drawRect(Band, Offset.Zero, Size(thickness, thickness))
    }
}
