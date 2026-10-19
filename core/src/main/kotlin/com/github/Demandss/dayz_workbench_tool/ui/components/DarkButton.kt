package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

@Composable
fun DarkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
    tooltip: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Tip(tooltip) {
        Box(
            modifier =
                modifier
                    .height(24.dp)
                    .background(
                        when {
                            !enabled -> ElementsBackground.copy(alpha = 0.5f)
                            isPressed -> SelectColor
                            else -> ElementsBackground
                        },
                    ).clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color =
                    when {
                        !enabled -> GhostlyText.copy(alpha = 0.5f)
                        muted -> GhostlyText
                        else -> TextForeground
                    },
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
