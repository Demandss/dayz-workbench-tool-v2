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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
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
        DarkIconButton(AppIcon.FOLDER_OPEN, "Открыть layout…", onClick = onOpenFile)

        Divider()

        // Zoom controls
        DarkIconButton(AppIcon.UI_ZOOMOUT, "Уменьшить", onClick = {
            state.zoom = (state.zoom * 0.9f).coerceIn(0.1f, 8f)
        })
        Tip("Текущий масштаб холста") { ZoomLabel(state.zoom) }
        DarkIconButton(AppIcon.UI_ZOOMIN, "Увеличить", onClick = {
            state.zoom = (state.zoom * 1.1f).coerceIn(0.1f, 8f)
        })
        DarkIconButton(AppIcon.UI_FIT, "Вписать в окно", onClick = { state.centerView() })
        DarkButton("100%", onClick = { state.zoomActual() }, tooltip = "Масштаб 100%")

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
            "Рамки виджетов",
            onClick = { state.showBorders = !state.showBorders },
            active = state.showBorders,
        )
        DarkIconButton(
            AppIcon.UI_GRID,
            "Показать сетку",
            onClick = { state.showGrid = !state.showGrid },
            active = state.showGrid,
        )
        if (state.showGrid || state.snapToGrid) GridSizeField(state)
        DarkIconButton(
            AppIcon.UI_MAGNET,
            "Привязка к сетке при перемещении (Ctrl отключает)",
            onClick = { state.snapToGrid = !state.snapToGrid },
            active = state.snapToGrid,
        )
        DarkIconButton(
            AppIcon.UI_SAFE,
            "Безопасная зона",
            onClick = { state.showSafeFrame = !state.showSafeFrame },
            active = state.showSafeFrame,
        )
        DarkIconButton(
            AppIcon.UI_MEASURE,
            "Расстояния до краёв",
            onClick = { state.showMeasure = !state.showMeasure },
            active = state.showMeasure,
        )
        DarkIconButton(
            AppIcon.UI_COMPARE,
            "Эталон Workbench",
            onClick = { state.showReference = !state.showReference },
            active = state.showReference,
        )

        DarkIconButton(AppIcon.UI_RULER, "Линейки", onClick = { state.showRulers = !state.showRulers }, active = state.showRulers)
        DarkIconButton(AppIcon.UI_FIT, "Миникарта", onClick = { state.showMinimap = !state.showMinimap }, active = state.showMinimap)
        DarkIconButton(AppIcon.UI_PIXEL, "Сравнить 4:3, 5:4 и 16:9", onClick = {
            state.compareResolutions = !state.compareResolutions
        }, active = state.compareResolutions)

        Spacer(Modifier.weight(1f))
        DarkIconButton(
            AppIcon.UI_UNDO,
            "Отменить (Ctrl+Z)",
            onClick = { state.undo(state.activeFileIndex) },
            enabled = state.activeFile?.undoStack?.isNotEmpty() == true,
        )
        DarkIconButton(
            AppIcon.UI_REDO,
            "Повторить (Ctrl+Y)",
            onClick = { state.redo(state.activeFileIndex) },
            enabled = state.activeFile?.redoStack?.isNotEmpty() == true,
        )
        DarkIconButton(AppIcon.UI_SAVE, "Сохранить (Ctrl+S)", onClick = { state.saveActiveFile() }, enabled = state.activeFile != null)
        Spacer(Modifier.width(4.dp))
        DarkToolButton(
            "Live Preview",
            tooltip = "Чистый вид без рамок, подписей и выделения (F5)",
            onClick = { state.livePreview = !state.livePreview },
            icon = AppIcon.UI_PLAY,
            active = state.livePreview,
            primary = true,
        )
        Spacer(Modifier.width(4.dp))
        DarkIconButton(AppIcon.UI_CMD, "Настройки: MCP и отображение (Ctrl+,)", onClick = { state.preferencesOpen = true })
        Spacer(Modifier.width(4.dp))
        DarkToolButton(
            "WB 1.30",
            tooltip = "Как Workbench 1.30: color читается как линейный свет и смешивается в нём. Выключено: как в игре (F6)",
            onClick = { state.workbenchColors = !state.workbenchColors },
            icon = AppIcon.UI_LAYERS,
            active = state.workbenchColors,
        )
        Spacer(Modifier.width(4.dp))
    }
}

/** The step of the grid in pixels of the screen the layout is made for. */
@Composable
private fun GridSizeField(state: LayoutEditorState) {
    var text by remember(state.gridSize) { mutableStateOf(state.gridSize.toString()) }
    Tip("Шаг сетки в пикселях") {
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
        DarkButton(selected.label, onClick = { expanded = true }, tooltip = "Разрешение, на котором рисуется layout")
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
