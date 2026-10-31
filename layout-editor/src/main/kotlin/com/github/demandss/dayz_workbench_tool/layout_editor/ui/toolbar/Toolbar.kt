package com.github.demandss.dayz_workbench_tool.layout_editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.BehaviorCatalogue
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.useBehavior
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.McpHost
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.TemplateKind
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ContextMenu
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ContextMenuCaption
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ContextMenuItem
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.DropdownPopup
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun Toolbar(
    state: LayoutEditorState,
    onOpenFile: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(FrameBackground)
                .drawBehind { drawLine(Color(0xFF1E1F22), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(Modifier.width(4.dp))

        // File
        DarkIconButton(AppIcon.FOLDER_OPEN, stringResource(Res.string.open_layout), onClick = onOpenFile)

        Divider()

        // Zoom controls
        DarkIconButton(AppIcon.UI_ZOOMOUT, stringResource(Res.string.zoom_out), onClick = {
            state.zoom = (state.zoom * 0.9f).coerceIn(0.1f, 8f)
        })
        Tip(stringResource(Res.string.current_canvas_zoom)) { ZoomLabel(state.zoom) }
        DarkIconButton(AppIcon.UI_ZOOMIN, stringResource(Res.string.zoom_in), onClick = {
            state.zoom = (state.zoom * 1.1f).coerceIn(0.1f, 8f)
        })
        DarkIconButton(AppIcon.UI_FIT, stringResource(Res.string.fit_to_window), onClick = { state.centerView() })
        DarkButton("100%", onClick = { state.zoomActual() }, tooltip = stringResource(Res.string.zoom_100))

        Divider()

        // Resolution picker
        ResolutionPicker(
            selected = state.selectedResolution,
            onSelect = { state.selectedResolution = it },
        )

        Divider()

        // View toggles
        DarkIconButton(
            AppIcon.WIDGET_FRAME,
            stringResource(Res.string.widget_borders_2),
            onClick = { state.showBorders = !state.showBorders },
            active = state.showBorders,
        )
        DarkIconButton(
            AppIcon.UI_INFO,
            stringResource(Res.string.widget_labels_on_the_canvas_f7),
            onClick = {
                state.showWidgetNames = !state.showWidgetNames
                state.savePreferences()
            },
            active = state.showWidgetNames,
        )
        DarkIconButton(
            AppIcon.UI_GRID,
            stringResource(Res.string.show_the_grid),
            onClick = { state.showGrid = !state.showGrid },
            active = state.showGrid,
        )
        if (state.showGrid || state.snapToGrid) GridSizeField(state)
        DarkIconButton(
            AppIcon.UI_MAGNET,
            stringResource(Res.string.snap_to_the_grid_when_moving_ctrl_turns),
            onClick = { state.snapToGrid = !state.snapToGrid },
            active = state.snapToGrid,
        )
        DarkIconButton(
            AppIcon.UI_SAFE,
            stringResource(Res.string.safe_zone),
            onClick = { state.showSafeFrame = !state.showSafeFrame },
            active = state.showSafeFrame,
        )
        DarkIconButton(
            AppIcon.UI_MEASURE,
            stringResource(Res.string.distances_to_the_edges),
            onClick = { state.showMeasure = !state.showMeasure },
            active = state.showMeasure,
        )
        DarkIconButton(
            AppIcon.UI_COMPARE,
            stringResource(Res.string.workbench_reference),
            onClick = { state.showReference = !state.showReference },
            active = state.showReference,
        )

        DarkIconButton(AppIcon.UI_RULER, stringResource(Res.string.rulers), onClick = {
            state.showRulers = !state.showRulers
        }, active = state.showRulers)
        DarkIconButton(AppIcon.UI_FIT, stringResource(Res.string.minimap), onClick = {
            state.showMinimap = !state.showMinimap
        }, active = state.showMinimap)
        DarkIconButton(AppIcon.UI_PIXEL, stringResource(Res.string.compare_4_3_5_4_and_16_9), onClick = {
            state.compareResolutions = !state.compareResolutions
        }, active = state.compareResolutions)

        Spacer(Modifier.weight(1f))
        DarkIconButton(
            AppIcon.UI_UNDO,
            stringResource(Res.string.undo_ctrl_z),
            onClick = { state.undo(state.activeFileIndex) },
            enabled = state.activeFile?.undoStack?.isNotEmpty() == true,
        )
        DarkIconButton(
            AppIcon.UI_REDO,
            stringResource(Res.string.redo_ctrl_y),
            onClick = { state.redo(state.activeFileIndex) },
            enabled = state.activeFile?.redoStack?.isNotEmpty() == true,
        )
        DarkIconButton(
            AppIcon.UI_SAVE,
            stringResource(Res.string.save_ctrl_s),
            onClick = { state.saveActiveFile() },
            enabled =
                state.activeFile != null,
        )
        ScriptTemplateButton(state)
        Spacer(Modifier.width(4.dp))
        BehaviorButton(state)
        Spacer(Modifier.width(4.dp))
        DarkToolButton(
            "Live Preview",
            tooltip = stringResource(Res.string.a_clean_view_without_borders_labels_and),
            onClick = { state.livePreview = !state.livePreview },
            icon = AppIcon.UI_PLAY,
            active = state.livePreview,
            primary = true,
        )
        Spacer(Modifier.width(4.dp))
        // The MCP connection has its own button: lit when the server works, opens the page with its address
        DarkToolButton(
            "MCP",
            tooltip = stringResource(Res.string.connection_mcp) + if (McpHost.url != null) stringResource(Res.string.mcp_running) else "",
            onClick = { state.mcpInfoOpen = true },
            dot =
                when {
                    McpHost.url != null -> Color(0xFF5CB85C)
                    state.mcpEnabled -> Color(0xFFD06A6A)
                    else -> Color(0xFF777777)
                },
        )
        state.openAppSettings?.let { open ->
            DarkIconButton(AppIcon.UI_GEAR, stringResource(Res.string.settings_ctrl), onClick = { open() })
        }
        Spacer(Modifier.width(4.dp))
        DarkToolButton(
            "WB 1.30",
            tooltip = stringResource(Res.string.like_workbench_1_30_color_is_read_as),
            onClick = { state.workbenchColors = !state.workbenchColors },
            icon = AppIcon.UI_LAYERS,
            active = state.workbenchColors,
        )
        Spacer(Modifier.width(4.dp))
    }
}

/** Copies an Enforce Script class for the open layout: a menu, a component or just the variables. */
@Composable
private fun ScriptTemplateButton(state: LayoutEditorState) {
    var open by remember { mutableStateOf(false) }
    Box {
        DarkIconButton(
            AppIcon.UI_COPY,
            stringResource(Res.string.copy_a_script_template_for_the_layout),
            onClick = { open = true },
            enabled = state.activeFile != null,
        )
        if (open) {
            ContextMenu(IntOffset(0, with(LocalDensity.current) { 28.dp.roundToPx() }), onDismiss = { open = false }) {
                ContextMenuCaption(str(Res.string.script_template_to_the_clipboard))
                TemplateKind.entries.forEach { kind ->
                    ContextMenuItem(kind.title, icon = AppIcon.UI_COPY) {
                        open = false
                        state.copyScriptTemplate(kind)
                    }
                }
            }
        }
    }
}

/** The step of the grid in pixels of the screen the layout is made for. */
@Composable
private fun GridSizeField(state: LayoutEditorState) {
    var text by remember(state.gridSize) { mutableStateOf(state.gridSize.toString()) }
    var focused by remember { mutableStateOf(false) }

    // Applied with Enter or when the field loses the focus, held inside 1 to 512
    fun apply() {
        val number = text.toIntOrNull()?.coerceIn(1, 512)
        if (number == null) {
            text = state.gridSize.toString()
        } else {
            text = number.toString()
            state.gridSize = number
        }
    }
    Tip(stringResource(Res.string.grid_step_in_pixels)) {
        Row(
            Modifier
                .height(22.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(3) },
                singleLine = true,
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
                cursorBrush = SolidColor(TextForeground),
                modifier =
                    Modifier
                        .width(24.dp)
                        .onFocusChanged { fs ->
                            if (focused && !fs.isFocused) apply()
                            focused = fs.isFocused
                        }.onKeyEvent { ev ->
                            if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown) {
                                apply()
                                true
                            } else {
                                false
                            }
                        },
            )
            Text("px", color = GhostlyText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).height(20.dp).background(DarkenedBorder))
    Spacer(Modifier.width(2.dp))
}

@Composable
private fun ZoomLabel(zoom: Float) {
    Box(
        modifier =
            Modifier
                .widthIn(min = 38.dp)
                .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${(zoom * 100).roundToInt()}%",
            color = GhostlyText,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ResolutionPicker(
    selected: Resolution,
    onSelect: (Resolution) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DarkButton(
            selected.label,
            onClick = { expanded = true },
            tooltip = stringResource(Res.string.the_resolution_the_layout_is_drawn_at),
        )
        if (expanded) {
            DropdownPopup(
                selected,
                Resolution.entries,
                { it.label },
                with(LocalDensity.current) { 24.dp.roundToPx() },
                { expanded = false },
                onSelect,
            )
        }
    }
}

/** The script classes that give a widget a behaviour: picking one puts it on the selected widget and copies the script. */
@Composable
private fun BehaviorButton(state: LayoutEditorState) {
    var open by remember { mutableStateOf(false) }
    Box {
        DarkIconButton(
            AppIcon.UI_ANCHOR,
            stringResource(Res.string.behaviours_put_a_script_class_on_the),
            onClick = { open = true },
            enabled = state.activeFile != null,
        )
        if (open) {
            ContextMenu(IntOffset(0, with(LocalDensity.current) { 28.dp.roundToPx() }), onDismiss = { open = false }) {
                ContextMenuCaption(str(Res.string.a_script_class_onto_the_selected_widget))
                BehaviorCatalogue.entries.groupBy { it.group }.forEach { (group, entries) ->
                    ContextMenuCaption(group)
                    entries.forEach { entry ->
                        ContextMenuItem(entry.name, icon = if (entry.spawner != null) AppIcon.UI_LAYERS else AppIcon.UI_ANCHOR) {
                            open = false
                            state.useBehavior(entry.name)
                        }
                    }
                }
            }
        }
    }
}
