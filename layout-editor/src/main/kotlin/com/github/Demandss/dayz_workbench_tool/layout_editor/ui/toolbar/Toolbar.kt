package com.github.demandss.dayz_workbench_tool.layout_editor.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
        DarkButton("Открыть...", onClick = onOpenFile)
        DarkButton("Сохранить", onClick = { state.saveActiveFile() }, enabled = state.activeFile != null)

        Divider()

        // Undo/Redo
        DarkButton("↩ Undo", onClick = { state.undo(state.activeFileIndex) }, enabled = state.activeFile?.undoStack?.isNotEmpty() == true)
        DarkButton("↪ Redo", onClick = { state.redo(state.activeFileIndex) }, enabled = state.activeFile?.redoStack?.isNotEmpty() == true)

        Divider()

        // Zoom controls
        DarkButton("⊙", onClick = { state.centerView() })
        DarkButton("−", onClick = {
            state.zoom = (state.zoom * 0.9f).coerceIn(0.1f, 8f)
        })
        ZoomLabel(state.zoom)
        DarkButton("+", onClick = {
            state.zoom = (state.zoom * 1.1f).coerceIn(0.1f, 8f)
        })
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
        ToggleButton("Borders", state.showBorders) { state.showBorders = it }
        ToggleButton("Grid", state.snapToGrid) { state.snapToGrid = it }
        ToggleButton("Safe", state.showSafeFrame) { state.showSafeFrame = it }

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
private fun ToggleButton(
    label: String,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val bg = if (active) SelectColor else Color.Transparent
    val textColor = if (active) TextForeground else GhostlyText
    Box(
        modifier =
            Modifier
                .background(bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onToggle(!active) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = 11.sp)
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
