package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder

private val Back = Color(0xE61E1F22)
private val Shape = Color(0xFF6F737A)
private val View = Color(0xFF4A9EFF)

/** The whole work area in small, with the part that the canvas shows. A click or drag moves the view. */
@Composable
fun Minimap(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val doc = state.activeFile ?: return
    val screen = screenRect(state)
    if (!(screen.width > 0f && screen.height > 0f)) return
    val width = 168.dp
    val height = width * (screen.height / screen.width)

    // The rectangles of the widgets change with the tree and the selection only, so they are a layer of their own and are not
    // drawn again while the view is dragged; the layer above holds the rectangle of the view
    val layout = remember(doc.root, screen) { layoutTree(doc.root, screen) }
    Tip(tr("Миникарта: клик или перетаскивание переносит вид")) {
        Box(
            modifier
                .size(width, height)
                .background(Back)
                .border(1.dp, DarkenedBorder)
                .clipToBounds(),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / screen.width
                for ((id, rect) in layout.rects) {
                    if (id == doc.root.id || id in state.hiddenIds) continue
                    val selected = id in state.selectedIds
                    drawRect(
                        if (selected) View else Shape,
                        Offset(rect.left * scale, rect.top * scale),
                        Size(rect.width * scale, rect.height * scale),
                        style = Stroke(if (selected) 1.5f else 1f),
                    )
                }
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(screen, state.lastCanvasSize) {
                        awaitPointerEventScope {
                            var down = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                val scale = size.width / screen.width

                                fun centreOn(p: Offset) {
                                    val world = Offset(p.x / scale, p.y / scale)
                                    val canvas = state.lastCanvasSize
                                    state.panOffset =
                                        Offset(canvas.width / 2f - world.x * state.zoom, canvas.height / 2f - world.y * state.zoom)
                                }
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        down = true
                                        centreOn(pos)
                                        event.changes.forEach { it.consume() }
                                    }

                                    PointerEventType.Move -> {
                                        if (down) {
                                            centreOn(pos)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        down = false
                                    }

                                    else -> {}
                                }
                            }
                        }
                    },
            ) {
                val scale = size.width / screen.width
                // What the canvas shows now
                val canvas = state.lastCanvasSize
                if (state.zoom > 0f && canvas.width > 0f) {
                    val left = -state.panOffset.x / state.zoom * scale
                    val top = -state.panOffset.y / state.zoom * scale
                    drawRect(
                        View.copy(alpha = 0.18f),
                        Offset(left, top),
                        Size(canvas.width / state.zoom * scale, canvas.height / state.zoom * scale),
                    )
                    drawRect(
                        View,
                        Offset(left, top),
                        Size(canvas.width / state.zoom * scale, canvas.height / state.zoom * scale),
                        style = Stroke(1f),
                    )
                }
            }
        }
    }
}
