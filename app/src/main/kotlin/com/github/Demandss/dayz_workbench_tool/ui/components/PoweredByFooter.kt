package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.update.AppVersion
import com.github.demandss.dayz_workbench_tool.update.UpdateStatus
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.net.URI

// Color matches the original Java project's "Powered by ©Demands." label (#404348)
private val PoweredByColor = Color(0xFF404348)

@Composable
fun PoweredByFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Powered by ©Demands.",
            modifier = Modifier.opens("https://github.com/Demandss/dayz-workbench-tool-v2"),
            color = PoweredByColor,
            fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // A newer release than the installed one, with a link to download it
            UpdateStatus.available?.let { release ->
                Text(
                    text = stringResource(Res.string.update_available_x, release.version),
                    modifier = Modifier.opens(release.url),
                    color = SelectColor,
                    fontSize = 11.sp,
                )
            }
            Text(text = stringResource(Res.string.app_version_x, AppVersion.installed), color = PoweredByColor, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Modifier.opens(url: String): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { runCatching { Desktop.getDesktop().browse(URI(url)) } },
    )
