package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.stringtable.StringtableEntry
import com.github.demandss.dayz_workbench_tool.stringtable.StringtableRegistry
import com.github.demandss.dayz_workbench_tool.stringtable.textIn
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/** The value of the source filter that lets every table through, and the one of the vanilla table. */
private const val ALL = "*"
private const val GAME = "game"

/** What the source filter shows for a value: all tables, the vanilla table of the game, or a mod by its folder name. */
private fun sourceLabel(origin: String) =
    when (origin) {
        ALL -> tr("Все источники")
        GAME -> tr("Ваниль (игра)")
        else -> origin
    }

/** The entries of [all] that fit the source and the search, vanilla first and then the mods in the order of their names. */
internal fun filterStrings(
    all: List<StringtableEntry>,
    source: String,
    query: String,
): List<StringtableEntry> {
    val words = query.lowercase().split(' ').filter { it.isNotEmpty() }
    return all
        .asSequence()
        .filter { source == ALL || it.origin == source }
        .filter { entry ->
            if (words.isEmpty()) return@filter true
            // Every word has to be in the key or in one of the texts of the language of the interface or the English one
            val hay =
                (
                    entry.key + " " + (entry.textIn(I18n.language) ?: "") + " " + (entry.texts["english"] ?: "")
                ).lowercase()
            words.all { it in hay }
        }.sortedWith(compareBy<StringtableEntry>({ if (it.origin == GAME) 0 else 1 }, { it.origin.lowercase() }, { it.key.lowercase() }))
        .toList()
}

/** Lists the string table keys of the game and of the connected mods and puts the chosen one into the text of a widget as `#KEY`. */
@Composable
fun StringPickerDialog(state: LayoutEditorState) {
    val target = state.stringPicker ?: return
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(ALL) }
    var selected by remember { mutableStateOf<String?>(null) }
    val all = StringtableRegistry.entries
    val origins = remember(all) { all.map { it.origin }.distinct().sortedWith(compareBy({ it != GAME }, { it.lowercase() })) }
    val shown = remember(all, source, query, I18n.language) { filterStrings(all, source, query) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun choose(key: String) {
        target(key)
        state.stringPicker = null
    }

    FlatDialog(
        title = tr("Строки из stringtable"),
        onDismiss = { state.stringPicker = null },
        onConfirm = { selected?.let(::choose) },
        width = 620.dp,
        buttons = {
            DarkButton(tr("Отмена"), onClick = { state.stringPicker = null }, muted = true, modifier = Modifier.width(80.dp))
            DarkButton(
                tr("Вставить"),
                onClick = { selected?.let(::choose) },
                enabled = selected != null,
                modifier = Modifier.width(100.dp),
                tooltip = tr("Записать ключ в текст виджета как #КЛЮЧ"),
            )
        },
    ) {
        if (all.isEmpty()) {
            Text(
                tr("Таблицы строк не найдены: укажите папку игры в настройках и подключите моды."),
                color = GhostlyText,
                fontSize = 11.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = TextForeground, fontSize = 12.sp),
                cursorBrush = SolidColor(TextForeground),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .background(FrameBackground)
                            .border(1.dp, DarkenedBorder)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) Text(tr("Поиск по ключу и тексту"), color = GhostlyText, fontSize = 12.sp)
                        inner()
                    }
                },
            )
            DropdownField(source, listOf(ALL) + origins, ::sourceLabel, tooltip = tr("Источник строк")) { source = it }
        }
        Text(
            tr("Найдено: {0}. Текст показан на языке редактора: {1}.", shown.size, I18n.language.nativeName),
            color = GhostlyText,
            fontSize = 10.sp,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder),
        ) {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(shown, key = { it.origin + "/" + it.key }) { entry ->
                    StringRow(entry, entry.key == selected, onSelect = { selected = entry.key }, onChoose = { choose(entry.key) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StringRow(
    entry: StringtableEntry,
    selected: Boolean,
    onSelect: () -> Unit,
    onChoose: () -> Unit,
) {
    val language = entry.textIn(I18n.language)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) SelectColor else Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
                onDoubleClick = onChoose,
            ).padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(210.dp)) {
            Text(entry.key, color = TextForeground, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sourceLabel(entry.origin), color = GhostlyText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            language ?: (entry.texts["english"] ?: "").ifEmpty { entry.texts["original"].orEmpty() },
            // A text of another language than the interface is dimmed: the language has no column or no text for it
            color = if (language != null) TextForeground else GhostlyText,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
