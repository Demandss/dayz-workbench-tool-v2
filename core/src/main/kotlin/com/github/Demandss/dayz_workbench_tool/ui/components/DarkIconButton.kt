package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/**
 * A square toolbar button with an icon and a tooltip. [active] makes it a toggle:
 * on is highlighted, off is muted, null is a plain button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DarkIconButton(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    TooltipArea(
        tooltip = {
            Text(
                label,
                color = TextForeground,
                fontSize = 11.sp,
                modifier =
                    Modifier
                        .background(ElementsBackground)
                        .border(1.dp, DarkenedBorder)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        delayMillis = 500,
    ) {
        Box(
            modifier =
                modifier
                    .size(26.dp)
                    .background(
                        when {
                            !enabled -> Color.Transparent
                            active == true || pressed -> SelectColor
                            hovered -> Color(0x1AFFFFFF)
                            else -> Color.Transparent
                        },
                    ).hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(
                icon,
                tint =
                    when {
                        !enabled -> GhostlyText.copy(alpha = 0.4f)
                        active == false -> GhostlyText
                        else -> icon.tint?.let { Color(it) } ?: TextForeground
                    },
            )
        }
    }
}
