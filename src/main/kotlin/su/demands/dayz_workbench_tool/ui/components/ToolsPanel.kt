package su.demands.dayz_workbench_tool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.demands.dayz_workbench_tool.manager.ProcessManager
import su.demands.dayz_workbench_tool.state.AppState

@Composable
fun ToolsPanel(state: AppState) {
    TitledPanel(title = "Tools", modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            DarkButton(text = "Workbench",     onClick = { runCatching { ProcessManager.launchWorkbench(state.config) } }, modifier = Modifier.fillMaxWidth())
            DarkButton(text = "Layout Editor", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            DarkButton(text = "Addon Builder", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
    }
}