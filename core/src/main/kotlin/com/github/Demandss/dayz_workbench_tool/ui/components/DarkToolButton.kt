package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/**
 * A toolbar button with an icon, a text or both. [active] makes it a toggle (null is a plain button).
 * [primary] paints it with the accent colour, like Live Preview. [dot] puts a dot of that colour before the text, e.g. the state of a connection.
 */
@Composable
fun DarkToolButton(
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    active: Boolean? = null,
    primary: Boolean = false,
    tooltip: String? = label,
    dot: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background =
        when {
            primary && active == true -> SubSelectColor
            primary -> SelectColor
            active == true -> SelectColor
            hovered -> Color(0x1AFFFFFF)
            else -> Color.Transparent
        }
    val foreground = if (primary || active == true) Color.White else TextForeground

    Tip(tooltip) {
        Row(
            modifier
                .height(26.dp)
                .background(background)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(5.dp))
            }
            if (icon != null) AppIconView(icon, size = 16.dp, tint = if (active == false) GhostlyText else foreground)
            if (icon != null && label != null) Spacer(Modifier.width(5.dp))
            if (label != null) Text(label, color = foreground, fontSize = 11.sp, maxLines = 1)
        }
    }
}
