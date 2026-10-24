package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSession
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSource
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.widgetIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.*

@Composable
fun PaletteContent(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val types =
        remember {
            WidgetType.entries.filter { it != WidgetType.UNKNOWN }.sortedBy { it.className }
        }
    val enabled = state.activeFile != null
    var query by remember { mutableStateOf("") }
    val shown = types.filter { it.className.contains(query.trim(), ignoreCase = true) }

    Column(modifier.background(FrameBackground)) {
        Box(Modifier.fillMaxWidth().padding(6.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
                cursorBrush = SolidColor(TextForeground),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(ElementsBackground)
                            .border(1.dp, DarkenedBorder)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) Text("Поиск виджета…", color = GhostlyText, fontSize = 11.sp)
                        inner()
                    }
                },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        ) {
            items(shown) { type -> PaletteRow(type, enabled, state) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Перетащите виджет на холст или на строку в иерархии. Клик добавляет в выбранный виджет.",
                    color = GhostlyText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun PaletteRow(
    type: WidgetType,
    enabled: Boolean,
    state: LayoutEditorState,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .hoverable(interactionSource)
                .padding(2.dp)
                .background(DarkenedBorder)
                .border(1.dp, if (enabled && hovered) GhostlyText else Color.Transparent)
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
                                            // A simple click adds the widget under its technical name
                                            state.addWidgetOfType(type, null, null)
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val shortName = type.className.removeSuffix("WidgetClass")
        AppIconView(widgetIcon(type), size = 16.dp, tint = if (enabled) null else GhostlyText)
        Text(shortName, color = if (enabled) TextForeground else GhostlyText, fontSize = 11.sp, maxLines = 1)
    }
}
