package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText

/** The confirmation of [LayoutEditorState.confirmation] as a flat dialog. */
@Composable
fun ConfirmDialog(state: LayoutEditorState) {
    val question = state.confirmation ?: return
    FlatDialog(
        title = question.title,
        onDismiss = { state.confirmation = null },
        width = 340.dp,
        buttons = {
            DarkButton("Отмена", onClick = { state.confirmation = null }, muted = true, modifier = Modifier.width(80.dp))
            DarkButton(
                question.confirmLabel,
                onClick = {
                    state.confirmation = null
                    question.onConfirm()
                },
                modifier = Modifier.width(100.dp),
            )
        },
    ) {
        Text(question.message, color = GhostlyText, fontSize = 12.sp)
    }
}
