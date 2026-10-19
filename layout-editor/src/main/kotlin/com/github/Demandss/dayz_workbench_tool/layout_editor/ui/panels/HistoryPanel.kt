package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/** The actions of the open layout, newest on top. A click goes to the state right after that action. */
@Composable
fun HistoryPanel(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val doc = state.activeFile
    if (doc == null || (doc.undoLabels.isEmpty() && doc.redoLabels.isEmpty())) {
        Box(modifier.background(FrameBackground).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Действий пока нет", color = GhostlyText, fontSize = 11.sp)
        }
        return
    }
    // The lists live in the document and change in place, so they are copied for the screen
    val redo = doc.redoLabels.toList()
    val undo = doc.undoLabels.toList().asReversed()
    LazyColumn(modifier.background(FrameBackground)) {
        // Actions that were undone: the farthest first. Going forward to one takes redo.size - index steps
        redo.forEachIndexed { i, label ->
            item { HistoryRow(label, current = false, undone = true) { state.jumpHistory(-(redo.size - i)) } }
        }
        undo.forEachIndexed { i, label ->
            item { HistoryRow(label, current = i == 0, undone = false) { state.jumpHistory(i) } }
        }
    }
}

@Composable
private fun HistoryRow(
    label: String,
    current: Boolean,
    undone: Boolean,
    onClick: () -> Unit,
) {
    Tip(if (current) "Текущее состояние" else "Перейти к состоянию после этого действия") {
        Row(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(if (current) SelectColor else Color.Transparent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (undone) GhostlyText else TextForeground, fontSize = 11.sp, maxLines = 1)
        }
    }
}
