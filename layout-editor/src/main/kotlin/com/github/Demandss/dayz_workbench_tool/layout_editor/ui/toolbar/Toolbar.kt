package com.github.demandss.dayz_workbench_tool.layout_editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
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
                .height(32.dp)
                .background(ElementsBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(Modifier.width(4.dp))

        // File
        DarkIconButton(AppIcon.FOLDER_OPEN, "Открыть layout…", onClick = onOpenFile)
        DarkIconButton(AppIcon.UI_SAVE, "Сохранить (Ctrl+S)", onClick = { state.saveActiveFile() }, enabled = state.activeFile != null)

        Divider()

        // Undo/Redo
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

        Divider()

        // Zoom controls
        DarkIconButton(AppIcon.UI_ZOOMOUT, "Уменьшить", onClick = {
            state.zoom = (state.zoom * 0.9f).coerceIn(0.1f, 8f)
        })
        ZoomLabel(state.zoom)
        DarkIconButton(AppIcon.UI_ZOOMIN, "Увеличить", onClick = {
            state.zoom = (state.zoom * 1.1f).coerceIn(0.1f, 8f)
        })
        DarkIconButton(AppIcon.UI_FIT, "Вписать в окно", onClick = { state.centerView() })
        DarkButton("100%", onClick = {
            state.zoom = 1f
            state.centerView()
        })

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
            "Сетка и привязка к ней",
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

        Spacer(Modifier.weight(1f))
        DarkIconButton(
            AppIcon.UI_PLAY,
            "Live Preview: чистый вид без рамок, подписей и выделения",
            onClick = { state.livePreview = !state.livePreview },
            active = state.livePreview,
        )

        Spacer(Modifier.width(4.dp))
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
        DarkButton(selected.label, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(ElementsBackground),
        ) {
            Resolution.entries.forEach { res ->
                DropdownMenuItem(
                    text = { Text(res.label, color = TextForeground, fontSize = 12.sp) },
                    onClick = {
                        onSelect(res)
                        expanded = false
                    },
                    modifier = Modifier.background(if (res == selected) SelectColor else Color.Transparent),
                )
            }
        }
    }
}
