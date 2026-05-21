package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.state.Screen
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

@Composable
fun ModsPanel(state: AppState) {
    TitledPanel(title = "Mods", modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                // Client column
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Client", color = TextForeground, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    ModChooserRow(side = ModSide.CLIENT, state = state)
                }
                // Server column
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Server", color = TextForeground, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    ModChooserRow(side = ModSide.SERVER, state = state)
                }
            }
            DarkButton(
                text = "Mods settings",
                onClick = { state.navigate(Screen.MODS) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
