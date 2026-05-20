@file:OptIn(ExperimentalComposeUiApi::class)

package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.model.Modification
import com.github.demandss.dayz_workbench_tool.ui.theme.*

@Composable
fun ModItem(
    mod: Modification,
    isSelected: Boolean = false,
    isMissing: Boolean = false,
    onSelect: ((isShift: Boolean, isCtrl: Boolean) -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    onToggleEnabled: () -> Unit,
    onSwitchSide: () -> Unit,
) {
    val colors = LocalAppColors.current
    val bg = if (isSelected) SelectColor else FrameBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .then(
                if (onRightClick != null) Modifier.pointerInput(onRightClick) {
                    awaitPointerEventScope {
                        var secondaryDown = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.button == PointerButton.Secondary) {
                                if (!secondaryDown) {
                                    secondaryDown = true
                                    event.changes.forEach { it.consume() }
                                    onRightClick()
                                } else {
                                    secondaryDown = false
                                }
                            }
                        }
                    }
                } else Modifier
            ),
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
                        if (onSelect != null || onDoubleClick != null) Modifier.pointerInput(onSelect, onDoubleClick) {
                            awaitPointerEventScope {
                                var primaryDown = false
                                var lastClickTime = 0L
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.button == PointerButton.Primary) {
                                        if (!primaryDown) {
                                            primaryDown = true
                                            val now = System.currentTimeMillis()
                                            val isShift = event.keyboardModifiers.isShiftPressed
                                            val isCtrl = event.keyboardModifiers.isCtrlPressed
                                            event.changes.forEach { it.consume() }
                                            if (onDoubleClick != null && now - lastClickTime < 300L) {
                                                lastClickTime = 0L
                                                onDoubleClick()
                                            } else {
                                                lastClickTime = now
                                                onSelect?.invoke(isShift, isCtrl)
                                            }
                                        } else {
                                            primaryDown = false
                                        }
                                    }
                                }
                            }
                        } else Modifier
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
                    color = if (mod.enabled && !isMissing) TextForeground else GhostlyText,
                    style = TextStyle(
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        textDecoration = if (isMissing) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1
                )
            }

            EnableButton(mod.enabled, colors, onToggleEnabled)
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun SideButton(side: ModSide, colors: AppColors, onClick: () -> Unit) {
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
private fun EnableButton(enabled: Boolean, colors: AppColors, onToggle: () -> Unit) {
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