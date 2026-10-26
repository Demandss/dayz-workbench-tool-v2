package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/** The messages of [LayoutEditorState.notify], stacked in a corner above the status bar. */
@Composable
fun ToastHost(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
        state.toasts.forEach { toast ->
            val accent =
                when (toast.kind) {
                    ToastKind.SUCCESS -> Color(0xFF4CAF50)
                    ToastKind.ERROR -> Color(0xFFFF5555)
                    ToastKind.INFO -> Color(0xFF4A9EFF)
                }
            Row(
                Modifier
                    .widthIn(max = 420.dp)
                    .background(ElementsBackground)
                    .border(1.dp, accent)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconView(if (toast.kind == ToastKind.ERROR) AppIcon.UI_WARN else AppIcon.UI_INFO, size = 14.dp, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(toast.text, color = TextForeground, fontSize = 11.sp)
            }
        }
    }
}
