package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/*
 * The rows of the settings window: a label on the left and a control on the right. Shared by the settings of the app and the
 * settings of the layout editor, so that every setting looks the same.
 */

/** A label and, to the right of it, the control given by [field]. */
@Composable
fun SettingRow(
    label: String,
    field: @Composable RowScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = TextForeground,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            modifier = Modifier.width(110.dp).padding(start = 5.dp),
        )
        field()
    }
}

/** A setting that is on or off. */
@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(label) {
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .border(1.dp, SubSelectColor)
                    .background(if (checked) SelectColor else FrameBackground)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onChange(!checked) },
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = TextForeground, fontSize = 9.sp, lineHeight = 10.sp)
        }
    }
}

/** A line under a row that says more about it. */
@Composable
fun SettingHint(text: String) {
    Text(
        text = text,
        color = GhostlyText,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        modifier = Modifier.padding(start = 5.dp),
    )
}

/** A whole number typed into a small field: [onValue] gets it on Enter or when the field loses the focus, held inside [range]. */
@Composable
fun NumberField(
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }

    fun apply() {
        val number = text.toIntOrNull()?.coerceIn(range)
        if (number == null) {
            text = value.toString()
        } else {
            text = number.toString()
            if (number != value) onValue(number)
        }
    }
    BasicTextField(
        value = text,
        onValueChange = { v -> text = v.filter(Char::isDigit).take(5) },
        singleLine = true,
        textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, lineHeight = 11.sp),
        cursorBrush = SolidColor(TextForeground),
        modifier =
            modifier
                .width(60.dp)
                .height(20.dp)
                .border(1.dp, SubSelectColor)
                .background(FrameBackground)
                .padding(start = 4.dp)
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) apply()
                    focused = state.isFocused
                }.onKeyEvent { event ->
                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                        apply()
                        true
                    } else {
                        false
                    }
                },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth().height(20.dp)) { inner() }
        },
    )
}
