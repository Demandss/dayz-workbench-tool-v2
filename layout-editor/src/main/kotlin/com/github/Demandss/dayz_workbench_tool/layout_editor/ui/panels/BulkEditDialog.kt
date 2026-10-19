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
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

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
        title = if (mode == BulkMode.NAME) "Заменить в именах виджетов" else "Заменить значение свойства",
        onDismiss = { state.bulkDialog = null },
        buttons = {
            DarkButton("Отмена", onClick = { state.bulkDialog = null }, muted = true, modifier = Modifier.width(80.dp))
            DarkButton(
                "Заменить ($count)",
                onClick = ::apply,
                enabled = count > 0,
                modifier = Modifier.width(110.dp),
                tooltip = "Сколько виджетов изменится",
            )
        },
    ) {
        Row(
            horizontalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(6.dp),
        ) {
            DarkToolButton("Выбранные", { selectedOnly = true }, active = selectedOnly, tooltip = "Только выбранные виджеты")
            DarkToolButton("Весь layout", { selectedOnly = false }, active = !selectedOnly, tooltip = "Все виджеты файла")
        }
        if (mode ==
            BulkMode.PROPERTY
        ) {
            Field("Свойство", key, "например style, color, priority", { key = it }, Modifier.focusRequester(focus), ::apply)
        }
        Field(
            if (mode == BulkMode.NAME) "Что заменить" else "Что заменить (пусто: любое значение)",
            find,
            "",
            { find = it },
            if (mode == BulkMode.NAME) Modifier.focusRequester(focus) else Modifier,
            ::apply,
        )
        Field("На что", replace, "", { replace = it }, Modifier, ::apply)
        Text(
            if (count == 0) "Ничего не подходит" else "Изменится виджетов: $count",
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
