package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.BulkMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.bulkReplace
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource

/** Find and replace over widget names or over one property, for the selected widgets or for the whole layout. */
@Composable
fun BulkEditDialog(state: LayoutEditorState) {
    val mode = state.bulkDialog ?: return
    val doc = state.activeFile ?: return
    var key by remember(mode) { mutableStateOf("") }
    var find by remember(mode) { mutableStateOf("") }
    var replace by remember(mode) { mutableStateOf("") }
    var selectedOnly by remember(mode) { mutableStateOf(state.selectedIds.any { it != doc.root.id }) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(mode) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    val scope = if (selectedOnly) state.selectedIds.toSet() else null
    val count = bulkReplace(doc.root, scope, mode, key.trim(), find, replace).second

    fun apply() {
        state.applyBulk(mode, key.trim(), find, replace, selectedOnly)
        state.bulkDialog = null
    }

    FlatDialog(
        title =
            if (mode ==
                BulkMode.NAME
            ) {
                stringResource(Res.string.replace_in_widget_names)
            } else {
                stringResource(Res.string.replace_a_property_value)
            },
        onDismiss = { state.bulkDialog = null },
        onConfirm = { if (count > 0) apply() },
        buttons = {
            DarkButton(
                stringResource(Res.string.cancel),
                onClick = { state.bulkDialog = null },
                muted = true,
                modifier = Modifier.width(80.dp),
            )
            DarkButton(
                stringResource(Res.string.replace_x, count),
                onClick = ::apply,
                enabled = count > 0,
                modifier = Modifier.width(110.dp),
                tooltip = stringResource(Res.string.how_many_widgets_will_change),
            )
        },
    ) {
        Row(
            horizontalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(6.dp),
        ) {
            DarkToolButton(stringResource(Res.string.selected_ones), {
                selectedOnly = true
            }, active = selectedOnly, tooltip = stringResource(Res.string.selected_widgets_only))
            DarkToolButton(stringResource(Res.string.whole_layout), {
                selectedOnly = false
            }, active = !selectedOnly, tooltip = stringResource(Res.string.all_widgets_of_the_file))
        }
        if (mode ==
            BulkMode.PROPERTY
        ) {
            Field(stringResource(Res.string.property), key, stringResource(Res.string.for_example_style_color_priority), {
                key = it
            }, Modifier.focusRequester(focus), ::apply)
        }
        Field(
            if (mode ==
                BulkMode.NAME
            ) {
                stringResource(Res.string.what_to_replace)
            } else {
                stringResource(Res.string.what_to_replace_empty_any_value)
            },
            find,
            "",
            { find = it },
            if (mode == BulkMode.NAME) Modifier.focusRequester(focus) else Modifier,
            ::apply,
        )
        Field(stringResource(Res.string.replace_with), replace, "", { replace = it }, Modifier, ::apply)
        Text(
            if (count == 0) stringResource(Res.string.nothing_matches) else stringResource(Res.string.widgets_to_change_x, count),
            color = GhostlyText,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    onEnter: () -> Unit,
) {
    Column(
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(3.dp),
    ) {
        Text(label, color = GhostlyText, fontSize = 11.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 12.sp),
            cursorBrush = SolidColor(TextForeground),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            26.dp,
                        ).background(FrameBackground)
                        .border(1.dp, DarkenedBorder)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = GhostlyText, fontSize = 12.sp)
                    inner()
                }
            },
            modifier =
                modifier.fillMaxWidth().onKeyEvent { ev ->
                    if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown) {
                        onEnter()
                        true
                    } else {
                        false
                    }
                },
        )
    }
}
