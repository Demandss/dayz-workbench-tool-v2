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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.tr
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
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.*
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
        DarkIconButton(AppIcon.FOLDER_OPEN, tr("Открыть layout…"), onClick = onOpenFile)

        Divider()

        // Zoom controls
        DarkIconButton(AppIcon.UI_ZOOMOUT, tr("Уменьшить"), onClick = {
            state.zoom = (state.zoom * 0.9f).coerceIn(0.1f, 8f)
        })
        Tip(tr("Текущий масштаб холста")) { ZoomLabel(state.zoom) }
        DarkIconButton(AppIcon.UI_ZOOMIN, tr("Увеличить"), onClick = {
            state.zoom = (state.zoom * 1.1f).coerceIn(0.1f, 8f)
        })
        DarkIconButton(AppIcon.UI_FIT, tr("Вписать в окно"), onClick = { state.centerView() })
        DarkButton("100%", onClick = { state.zoomActual() }, tooltip = tr("Масштаб 100%"))

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
            tr("Рамки виджетов"),
            onClick = { state.showBorders = !state.showBorders },
            active = state.showBorders,
        )
        DarkIconButton(
            AppIcon.UI_INFO,
            tr("Подписи виджетов на холсте (F7)"),
            onClick = {
                state.showWidgetNames = !state.showWidgetNames
                state.savePreferences()
            },
            active = state.showWidgetNames,
        )
        DarkIconButton(
            AppIcon.UI_GRID,
            tr("Показать сетку"),
            onClick = { state.showGrid = !state.showGrid },
            active = state.showGrid,
        )
        if (state.showGrid || state.snapToGrid) GridSizeField(state)
        DarkIconButton(
            AppIcon.UI_MAGNET,
            tr("Привязка к сетке при перемещении (Ctrl отключает)"),
            onClick = { state.snapToGrid = !state.snapToGrid },
            active = state.snapToGrid,
        )
        DarkIconButton(
            AppIcon.UI_SAFE,
            tr("Безопасная зона"),
            onClick = { state.showSafeFrame = !state.showSafeFrame },
            active = state.showSafeFrame,
        )
        DarkIconButton(
            AppIcon.UI_MEASURE,
            tr("Расстояния до краёв"),
            onClick = { state.showMeasure = !state.showMeasure },
            active = state.showMeasure,
        )
        DarkIconButton(
            AppIcon.UI_COMPARE,
            tr("Эталон Workbench"),
            onClick = { state.showReference = !state.showReference },
            active = state.showReference,
        )

        DarkIconButton(AppIcon.UI_RULER, tr("Линейки"), onClick = { state.showRulers = !state.showRulers }, active = state.showRulers)
        DarkIconButton(AppIcon.UI_FIT, tr("Миникарта"), onClick = { state.showMinimap = !state.showMinimap }, active = state.showMinimap)
        DarkIconButton(AppIcon.UI_PIXEL, tr("Сравнить 4:3, 5:4 и 16:9"), onClick = {
            state.compareResolutions = !state.compareResolutions
        }, active = state.compareResolutions)

        Spacer(Modifier.weight(1f))
        DarkIconButton(
            AppIcon.UI_UNDO,
            tr("Отменить (Ctrl+Z)"),
            onClick = { state.undo(state.activeFileIndex) },
            enabled = state.activeFile?.undoStack?.isNotEmpty() == true,
        )
        DarkIconButton(
            AppIcon.UI_REDO,
            tr("Повторить (Ctrl+Y)"),
            onClick = { state.redo(state.activeFileIndex) },
            enabled = state.activeFile?.redoStack?.isNotEmpty() == true,
        )
        DarkIconButton(AppIcon.UI_SAVE, tr("Сохранить (Ctrl+S)"), onClick = { state.saveActiveFile() }, enabled = state.activeFile != null)
        ScriptTemplateButton(state)
        Spacer(Modifier.width(4.dp))
        BehaviorButton(state)
        Spacer(Modifier.width(4.dp))
        DarkToolButton(
            "Live Preview",
            tooltip = tr("Чистый вид без рамок, подписей и выделения (F5)"),
            onClick = { state.livePreview = !state.livePreview },
            icon = AppIcon.UI_PLAY,
            active = state.livePreview,
            primary = true,
        )
        Spacer(Modifier.width(4.dp))
        // The colour of the icon tells the state of the MCP connection: green works, red is on but not running
        DarkIconButton(
            AppIcon.UI_CMD,
            tr("Настройки: MCP и отображение (Ctrl+,)") + if (McpHost.url != null) tr("  ·  MCP работает") else "",
            onClick = { state.preferencesOpen = true },
            tint =
                when {
                    McpHost.url != null -> Color(0xFF5CB85C)
                    state.mcpEnabled -> Color(0xFFD06A6A)
                    else -> null
                },
        )
        Spacer(Modifier.width(4.dp))
        DarkToolButton(
            "WB 1.30",
            tooltip = tr("Как Workbench 1.30: color читается как линейный свет и смешивается в нём. Выключено: как в игре (F6)"),
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
            tr("Скопировать шаблон скрипта для лайаута"),
            onClick = { open = true },
            enabled = state.activeFile != null,
        )
        if (open) {
            ContextMenu(IntOffset(0, with(LocalDensity.current) { 28.dp.roundToPx() }), onDismiss = { open = false }) {
                ContextMenuCaption(tr("Шаблон скрипта в буфер"))
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
    Tip(tr("Шаг сетки в пикселях")) {
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
                onValueChange = { v ->
                    text = v.filter(Char::isDigit).take(3)
                    text.toIntOrNull()?.takeIf { it in 1..512 }?.let { state.gridSize = it }
                },
                singleLine = true,
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
                cursorBrush = SolidColor(TextForeground),
                modifier = Modifier.width(24.dp),
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
        DarkButton(selected.label, onClick = { expanded = true }, tooltip = tr("Разрешение, на котором рисуется layout"))
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
            tr("Поведения: поставить скрипт-класс на выбранный виджет и скопировать скрипт"),
            onClick = { open = true },
            enabled = state.activeFile != null,
        )
        if (open) {
            ContextMenu(IntOffset(0, with(LocalDensity.current) { 28.dp.roundToPx() }), onDismiss = { open = false }) {
                ContextMenuCaption(tr("Скрипт-класс на выбранный виджет и в буфер"))
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
