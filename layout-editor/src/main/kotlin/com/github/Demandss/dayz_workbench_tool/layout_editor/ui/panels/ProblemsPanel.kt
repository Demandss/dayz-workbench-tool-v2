package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.Severity
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.findProblems
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/** What looks wrong in the open layout. A click selects the widget. */
@Composable
fun ProblemsPanel(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val doc = state.activeFile
    val problems = remember(doc?.root) { doc?.let { findProblems(it.root) }.orEmpty() }

    if (problems.isEmpty()) {
        Box(modifier.background(FrameBackground).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (doc == null) "Нет открытых файлов" else "Проблем не найдено", color = GhostlyText, fontSize = 11.sp)
        }
        return
    }
    LazyColumn(modifier.background(FrameBackground)) {
        items(problems) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        state.selectedIds.clear()
                        state.selectedIds.add(p.nodeId)
                    }.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AppIconView(
                    AppIcon.UI_WARN,
                    size = 14.dp,
                    tint =
                        if (p.severity ==
                            Severity.ERROR
                        ) {
                            androidx.compose.ui.graphics
                                .Color(0xFFFF5555)
                        } else {
                            null
                        },
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(p.widgetName, color = TextForeground, fontSize = 11.sp)
                    Text(p.message, color = GhostlyText, fontSize = 10.sp)
                }
            }
        }
    }
}
