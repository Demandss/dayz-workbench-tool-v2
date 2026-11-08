package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.demandss.dayz_workbench_tool.manager.WorkspaceFailure
import com.github.demandss.dayz_workbench_tool.manager.WorkspacePreparationException
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ToolsPanel(state: AppState) {
    val scope = rememberCoroutineScope()

    TitledPanel(title = stringResource(Res.string.tools_title), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DarkButton(
                text = stringResource(if (state.isWorkbenchStarting) Res.string.preparing_workbench else Res.string.tool_workbench),
                onClick = { state.launchWorkbench(scope) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isWorkbenchStarting,
            )
            DarkButton(
                text = stringResource(Res.string.tool_layout_editor),
                onClick = { state.showLayoutEditor = true },
                modifier = Modifier.fillMaxWidth(),
            )
            DarkButton(
                text = stringResource(Res.string.tool_addon_builder),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            )
        }
    }
}

/** Shared by all app screens, including errors raised while the mods or settings window is open. */
@Composable
fun WorkbenchErrorDialog(state: AppState) {
    val failure = state.workbenchError ?: return
    val preparation = failure as? WorkspacePreparationException
    val reason =
        when (preparation?.reason) {
            WorkspaceFailure.INVALID_PATH -> Res.string.workspace_error_invalid_path
            WorkspaceFailure.UNAVAILABLE_PATH -> Res.string.workspace_error_unavailable_path
            WorkspaceFailure.NAME_COLLISION -> Res.string.workspace_error_name_collision
            WorkspaceFailure.PATH_CYCLE -> Res.string.workspace_error_path_cycle
            WorkspaceFailure.FOREIGN_ENTRY -> Res.string.workspace_error_foreign_entry
            WorkspaceFailure.LINK_CHANGED -> Res.string.workspace_error_link_changed
            WorkspaceFailure.LINK_OPERATION -> Res.string.workspace_error_link_operation
            WorkspaceFailure.JOURNAL -> Res.string.workspace_error_journal
            WorkspaceFailure.WORKSPACE -> Res.string.workspace_error_workspace
            null -> Res.string.workbench_error_unknown
        }
    val detail = preparation?.detail ?: failure.localizedMessage.orEmpty()

    Dialog(onDismissRequest = state::dismissWorkbenchError) {
        Column(
            modifier =
                Modifier
                    .widthIn(min = 300.dp, max = 520.dp)
                    .heightIn(max = 480.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.workbench_error_title), color = TextForeground, fontSize = 12.sp)
            Text(stringResource(reason), color = GhostlyText, fontSize = 11.sp, lineHeight = 15.sp)
            preparation?.paths?.forEach { path ->
                Text(path, color = TextForeground, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (detail.isNotBlank()) {
                Text(detail, color = GhostlyText, fontSize = 11.sp, lineHeight = 15.sp)
            }
            DarkButton(
                stringResource(Res.string.close),
                onClick = state::dismissWorkbenchError,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
