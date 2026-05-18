package su.demands.dayz_workbench_tool.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.demands.dayz_workbench_tool.model.Modification
import su.demands.dayz_workbench_tool.model.ModSide
import su.demands.dayz_workbench_tool.model.ModType
import su.demands.dayz_workbench_tool.ui.theme.*

@Composable
fun ModItem(
    mod: Modification,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onToggleEnabled: () -> Unit,
    onSwitchSide: () -> Unit,
) {
    val colors = LocalAppColors.current
    val bg = if (isSelected) SelectColor else FrameBackground

    Row(
        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SideButton(mod.side, colors, onSwitchSide)
        Spacer(Modifier.width(3.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .border(1.dp, SubSelectColor)
                .background(bg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (onSelect != null) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSelect
                        ) else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mod.type == ModType.WORKSHOP) {
                    Image(
                        painter = painterResource("assets/images/workshop.svg"),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.modIconWorkshop),
                        modifier = Modifier.size(14.dp).padding(start = 2.dp)
                    )
                } else {
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = mod.name,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    color = if (mod.enabled) TextForeground else GhostlyText,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1
                )
            }

            EnableButton(mod.enabled, colors, onToggleEnabled)
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun SideButton(side: ModSide, colors: su.demands.dayz_workbench_tool.ui.theme.AppColors, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(18.dp)
            .background(if (isPressed) SelectColor else FrameBackground)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(
                if (side == ModSide.CLIENT) "assets/images/mod_client_side.svg"
                else "assets/images/mod_server_side.svg"
            ),
            contentDescription = if (side == ModSide.CLIENT) "client side" else "server side",
            colorFilter = ColorFilter.tint(if (side == ModSide.CLIENT) colors.modIconClientSide else colors.modIconServerSide),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun EnableButton(enabled: Boolean, colors: su.demands.dayz_workbench_tool.ui.theme.AppColors, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .width(16.dp)
            .fillMaxHeight()
            .background(if (isPressed) SelectColor else FrameBackground)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(
                if (enabled) "assets/images/mod_enabled.svg"
                else "assets/images/mod_disebled.svg"
            ),
            contentDescription = if (enabled) "enabled" else "disabled",
            colorFilter = ColorFilter.tint(if (enabled) colors.modIconEnabled else colors.modIconDisabled),
            modifier = Modifier.size(14.dp)
        )
    }
}