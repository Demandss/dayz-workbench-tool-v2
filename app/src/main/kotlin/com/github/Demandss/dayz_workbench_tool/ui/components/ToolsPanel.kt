package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.demandss.dayz_workbench_tool.manager.ProcessManager
import com.github.demandss.dayz_workbench_tool.resources.Res
import com.github.demandss.dayz_workbench_tool.resources.cancel
import com.github.demandss.dayz_workbench_tool.resources.launch_anyway
import com.github.demandss.dayz_workbench_tool.resources.missing_mods_message
import com.github.demandss.dayz_workbench_tool.resources.missing_mods_title
import com.github.demandss.dayz_workbench_tool.resources.tools_title
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.nio.file.Path

@Composable
fun ToolsPanel(state: AppState) {
    var missingWarning by remember { mutableStateOf<List<String>>(emptyList()) }

    TitledPanel(title = stringResource(Res.string.tools_title), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DarkButton(text = "Workbench", onClick = {
                val missing =
                    (state.config.clientMods + state.config.serverMods)
                        .filter { it.enabled && !File(it.path).exists() }
                        .map { Path.of(it.path).fileName?.toString() ?: it.path }
                if (missing.isEmpty()) {
                    runCatching { ProcessManager.launchWorkbench(state.config) }
                    ProcessManager.launchCustomScripts(state.config.customWorkbenchScripts)
                } else {
                    missingWarning = missing
                }
            }, modifier = Modifier.fillMaxWidth())
            DarkButton(text = "Layout Editor", onClick = { state.showLayoutEditor = true }, modifier = Modifier.fillMaxWidth())
            DarkButton(text = "Addon Builder", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
    }

    if (missingWarning.isNotEmpty()) {
        MissingModsDialog(
            missing = missingWarning,
            onLaunch = {
                runCatching { ProcessManager.launchWorkbench(state.config) }
                ProcessManager.launchCustomScripts(state.config.customWorkbenchScripts)
                missingWarning = emptyList()
            },
            onCancel = { missingWarning = emptyList() },
        )
    }
}

@Composable
private fun MissingModsDialog(
    missing: List<String>,
    onLaunch: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier =
                Modifier
                    .width(300.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.missing_mods_title), color = TextForeground, fontSize = 12.sp)
            Text(
                stringResource(Res.string.missing_mods_message),
                color = GhostlyText,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                missing.forEach { name ->
                    Text("• $name", color = GhostlyText, fontSize = 10.sp, lineHeight = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton(stringResource(Res.string.launch_anyway), onClick = onLaunch, modifier = Modifier.weight(1f))
                DarkButton(stringResource(Res.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            }
        }
    }
}
