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
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import java.io.File
import java.nio.file.Path

@Composable
fun ToolsPanel(state: AppState) {
    var missingWarning by remember { mutableStateOf<List<String>>(emptyList()) }

    TitledPanel(title = "Tools", modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DarkButton(text = "Workbench", onClick = {
                val missing = (state.config.clientMods + state.config.serverMods)
                    .filter { it.enabled && !File(it.path).exists() }
                    .map { Path.of(it.path).fileName?.toString() ?: it.path }
                if (missing.isEmpty()) {
                    runCatching { ProcessManager.launchWorkbench(state.config) }
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
                missingWarning = emptyList()
            },
            onCancel = { missingWarning = emptyList() }
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
            modifier = Modifier
                .width(300.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Missing mods", color = TextForeground, fontSize = 12.sp)
            Text(
                "The following enabled mods were not found and won't be used:",
                color = GhostlyText,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                missing.forEach { name ->
                    Text("• $name", color = GhostlyText, fontSize = 10.sp, lineHeight = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Launch anyway", onClick = onLaunch, modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            }
        }
    }
}