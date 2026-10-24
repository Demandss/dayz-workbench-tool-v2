package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.EDITOR
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.ScriptClassIndex
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.ScriptClassInfo
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.ScriptKind
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.exportScriptClass
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.kind
import com.github.demandss.dayz_workbench_tool.layout_editor.behavior.useBehavior
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

private sealed interface Line {
    data class Caption(
        val text: String,
    ) : Line

    data class Item(
        val info: ScriptClassInfo?,
    ) : Line
}

/**
 * The choice of the `scriptclass` of a widget from every class that is available (the classes of the editor, of the game and of
 * the connected mods), with a search, and a button that copies the chosen class. [onPick] sets the name on the widget; a class of
 * the editor is put on it by the editor itself, together with its fields.
 */
@Composable
internal fun RowScope.ScriptClassPicker(
    state: LayoutEditorState,
    node: EditorWidgetNode,
    current: String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val below = with(LocalDensity.current) { 24.dp.roundToPx() }
    // The tooltip of the field tells what the class on the widget does; a class of the editor may carry the prefix of the mod
    val known =
        state.scriptClasses.firstOrNull { it.name == current }
            ?: state.scriptClasses.firstOrNull { it.source == EDITOR && current.isNotEmpty() && current.endsWith(it.name) }
    val fieldTip =
        known?.let { ScriptClassIndex.tooltip(it) + System.lineSeparator() + "Клик: выбрать другой класс" }
            ?: "Скрипт-класс виджета: выбрать из классов игры, модов и редактора"
    Box(Modifier.weight(1f)) {
        Tip(fieldTip) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .clickable(remember { MutableInteractionSource() }, null) { open = true }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        current.ifEmpty { "— (нет)" },
                        color = if (current.isEmpty()) GhostlyText else TextForeground,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    known?.let { KindBadge(it) }
                }
            }
        }
        if (open) {
            ScriptClassPopup(state.scriptClasses, current, below, { open = false }) { picked ->
                when {
                    picked == null -> onPick("")
                    picked.source == EDITOR -> state.useBehavior(picked.name)
                    else -> onPick("\"${picked.name}\"")
                }
            }
        }
    }
    Tip("Скопировать выбранный скрипт-класс: для классов редактора скрипт, фрагмент layout и строки для лайаута, для остальных исходник") {
        Box(
            Modifier
                .height(24.dp)
                .background(ElementsBackground)
                .border(1.dp, DarkenedBorder)
                .clickable(remember { MutableInteractionSource() }, null, enabled = current.isNotEmpty()) { state.exportScriptClass(node) }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Экспорт", color = if (current.isEmpty()) GhostlyText else TextForeground, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ScriptClassPopup(
    classes: List<ScriptClassInfo>,
    current: String,
    offsetY: Int,
    onDismiss: () -> Unit,
    onPick: (ScriptClassInfo?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    val lines =
        remember(classes, query) {
            val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val shown =
                classes.filter { c ->
                    words.all { c.name.contains(it, ignoreCase = true) || c.source.contains(it, ignoreCase = true) }
                }
            buildList<Line> {
                if (words.isEmpty()) add(Line.Item(null))
                var group: String? = null
                for (c in shown) {
                    if (c.source != group) {
                        group = c.source
                        add(Line.Caption(c.source))
                    }
                    add(Line.Item(c))
                }
            }
        }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(300.dp)
                .background(ElementsBackground)
                .border(1.dp, DarkenedBorder)
                .padding(vertical = 2.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
                cursorBrush = SolidColor(TextForeground),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .background(FrameBackground)
                            .border(1.dp, DarkenedBorder)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        if (query.isEmpty()) Text("Поиск по названию или источнику", color = GhostlyText, fontSize = 11.sp)
                        inner()
                    }
                },
                modifier = Modifier.focusRequester(requester),
            )
            LazyColumn(Modifier.heightIn(max = 320.dp).weight(1f, fill = false)) {
                items(lines) { line ->
                    when (line) {
                        is Line.Caption -> {
                            Text(
                                line.text.uppercase(),
                                color = GhostlyText,
                                fontSize = 9.sp,
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                            )
                        }

                        is Line.Item -> {
                            val info = line.info
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            Tip(info?.let { ScriptClassIndex.tooltip(it) }) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                        .background(
                                            when {
                                                hovered -> SubSelectColor
                                                info?.name == current || (info == null && current.isEmpty()) -> SelectColor
                                                else -> Color.Transparent
                                            },
                                        ).hoverable(interaction)
                                        .clickable(interaction, null) {
                                            onDismiss()
                                            onPick(info)
                                        }.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(info?.name ?: "— (нет)", color = TextForeground, fontSize = 11.sp, maxLines = 1)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (info != null && info.parent.isNotEmpty() && info.parent != "ScriptedWidgetEventHandler") {
                                            Text(info.parent, color = GhostlyText, fontSize = 9.sp, maxLines = 1)
                                        }
                                        info?.let { KindBadge(it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            KindLegend()
        }
    }
}

private fun kindColor(kind: ScriptKind): Color =
    when (kind) {
        ScriptKind.CUSTOM -> Color(0xFFE8622A)
        ScriptKind.VANILLA -> Color(0xFF4A9EFF)
        ScriptKind.MOD -> Color(0xFF5CB85C)
    }

/** An icon that tells whether a class was made for the editor, belongs to the game or comes from a mod; the tooltip of the row says it in words. */
@Composable
private fun KindBadge(info: ScriptClassInfo) {
    val kind = info.kind()
    // No tooltip of its own: it sits inside a row or a field that has one, and two tooltips would lie on top of each other
    AppIconView(kind.icon, size = 13.dp, tint = kindColor(kind))
}

/** What the icons of the list stand for, in a line under it. */
@Composable
private fun KindLegend() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(FrameBackground)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (kind in ScriptKind.entries) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppIconView(kind.icon, size = 12.dp, tint = kindColor(kind))
                Text(
                    when (kind) {
                        ScriptKind.CUSTOM -> "редактора"
                        ScriptKind.VANILLA -> "игры"
                        ScriptKind.MOD -> "мода"
                    },
                    color = GhostlyText,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
