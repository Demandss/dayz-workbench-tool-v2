package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSession
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSource
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.PendingAdd
import com.github.demandss.dayz_workbench_tool.ui.theme.*

@Composable
fun PaletteContent(state: LayoutEditorState, modifier: Modifier = Modifier) {
    val types = remember {
        WidgetType.entries.filter { it != WidgetType.UNKNOWN }.sortedBy { it.className }
    }
    val enabled = state.activeFile != null

    LazyColumn(modifier.background(FrameBackground)) {
        items(types) { type ->
            PaletteRow(type, enabled, state)
        }
    }
}

@Composable
private fun PaletteRow(type: WidgetType, enabled: Boolean, state: LayoutEditorState) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .hoverable(interactionSource)
            .background(if (enabled && hovered) SubSelectColor else Color.Transparent)
            .padding(horizontal = 8.dp)
            .pointerInput(type, enabled) {
                if (!enabled) return@pointerInput
                var pressPos = Offset.Zero
                var pressed = false
                var dragStarted = false

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position ?: continue

                        when (event.type) {
                            PointerEventType.Press -> {
                                if (event.buttons.isPrimaryPressed) {
                                    pressPos = pos
                                    pressed = true
                                    dragStarted = false
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Move -> {
                                if (pressed && !dragStarted) {
                                    val dist = (pos - pressPos).getDistance()
                                    if (dist > 8f) {
                                        dragStarted = true
                                        state.dragSession = DragSession(DragSource.FromPalette(type))
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Release -> {
                                if (pressed) {
                                    pressed = false
                                    if (!dragStarted) {
                                        // Simple click — show name dialog
                                        state.pendingAdd = PendingAdd(type, null, null)
                                    }
                                    // Drag drop is handled by the root interceptor
                                    dragStarted = false
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val shortName = type.className.removeSuffix("WidgetClass")
        Text(shortName, color = if (enabled) TextForeground else GhostlyText, fontSize = 11.sp)
    }
}