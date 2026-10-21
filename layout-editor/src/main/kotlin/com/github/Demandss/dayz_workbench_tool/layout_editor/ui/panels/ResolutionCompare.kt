package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.drawWidgetTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText

private val COMPARED = listOf(Resolution.R800x600, Resolution.R1280x1024, Resolution.R1920x1080)

/** The layout drawn at 4:3, 5:4 and 16:9 side by side, to see at once what the anchors and sizes do on other screens. */
@Composable
fun ResolutionCompare(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val doc = state.activeFile ?: return
    val measurer = rememberTextMeasurer()
    val miniHeight = 96.dp
    Row(
        modifier.background(ElementsBackground).border(1.dp, DarkenedBorder).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (res in COMPARED) {
            val width = miniHeight * (res.width.toFloat() / res.height)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Canvas(Modifier.width(width).height(miniHeight).border(1.dp, DarkenedBorder)) {
                    val screen = Rect(0f, 0f, res.width.toFloat(), res.height.toFloat())
                    val zoom = size.width / res.width
                    drawRect(Color(0xFF232528))
                    clipRect {
                        drawWidgetTree(
                            doc.root,
                            layoutTree(doc.root, screen),
                            true,
                            emptySet(),
                            state.hiddenIds.toHashSet(),
                            false,
                            false,
                            measurer,
                            zoom,
                            Offset.Zero,
                            state::textureFor,
                            { n -> state.skinFor(n, doc.path) },
                            { _ -> false },
                            { n -> state.pictureFor(n, doc.path) },
                            { n -> state.textColorFor(n, doc.path) },
                            fontFor = { n -> state.sdfFontFor(state.fontNameFor(n, doc.path), doc.path) },
                            fontNameFor = { n -> state.fontNameFor(n, doc.path) },
                        )
                    }
                }
                Text(res.label, color = GhostlyText, fontSize = 10.sp)
            }
        }
    }
}
