package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/** A field that opens a flat list of [options] under itself. Lines are 22 px tall and the list scrolls when it is long. */
@Composable
fun <T> DropdownField(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldHeight = with(LocalDensity.current) { 24.dp.roundToPx() }
    Box(modifier) {
        Box(
            Modifier
                .height(24.dp)
                .widthIn(min = 64.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .clickable(remember { MutableInteractionSource() }, null) { expanded = true }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(label(selected), color = TextForeground, fontSize = 11.sp, maxLines = 1)
        }
        if (expanded) {
            DropdownPopup(selected, options, label, fieldHeight, { expanded = false }, onSelect)
        }
    }
}

/** The flat list of a dropdown, [offsetY] pixels under the box it belongs to. */
@Composable
fun <T> DropdownPopup(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    offsetY: Int,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 96.dp)
                .heightIn(max = 320.dp)
                .background(ElementsBackground)
                .border(1.dp, DarkenedBorder)
                .padding(vertical = 2.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(
                            when {
                                hovered -> SubSelectColor
                                option == selected -> SelectColor
                                else -> Color.Transparent
                            },
                        ).hoverable(interaction)
                        .clickable(interaction, null) {
                            onDismiss()
                            onSelect(option)
                        }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(label(option), color = TextForeground, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}
