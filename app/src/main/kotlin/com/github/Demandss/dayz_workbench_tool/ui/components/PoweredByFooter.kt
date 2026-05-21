package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import java.awt.Desktop
import java.net.URI

// Color matches the original Java project's "Powered by ©Demands." label (#404348)
private val PoweredByColor = Color(0xFF404348)

@Composable
fun PoweredByFooter() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, top = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Powered by ©Demands.",
            modifier =
                Modifier
                    .padding(start = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            runCatching {
                                Desktop.getDesktop().browse(
                                    URI("https://github.com/Demandss/dayz-workbench-tool-v2"),
                                )
                            }
                        },
                    ),
            color = PoweredByColor,
            fontSize = 11.sp,
        )
    }
}
