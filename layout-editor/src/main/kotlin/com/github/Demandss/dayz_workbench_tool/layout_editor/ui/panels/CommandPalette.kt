package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.McpHost
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.copyMcpAddress
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.PaletteMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.AlignMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.BulkMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.Reorder
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.TemplateKind
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.fileIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import java.io.File

/** One entry of the palette. A disabled one is shown dimmed and does nothing. */
class Command(
    val title: String,
    val icon: AppIcon,
    val hint: String = "",
    val enabled: Boolean = true,
    val run: () -> Unit,
)

/** Everything the editor can do from the keyboard, plus the files used last. */
fun buildCommands(
    state: LayoutEditorState,
    openFileDialog: () -> Unit,
): List<Command> {
    val hasDoc = state.activeFile != null
    val hasSelection = hasDoc && state.selectedIds.any { it != state.activeFile?.root?.id }
    val several = hasSelection && state.selectedIds.size >= 2

    fun toggle(
        title: String,
        icon: AppIcon,
        on: Boolean,
        flip: () -> Unit,
    ) = Command((if (on) tr("Выключить: ") else tr("Включить: ")) + title, icon, run = flip)

    return buildList {
        add(Command(tr("Открыть файл…"), AppIcon.FOLDER_OPEN, run = openFileDialog))
        add(Command(tr("Сохранить"), AppIcon.UI_SAVE, "Ctrl+S", hasDoc) { state.saveActiveFile() })
        add(Command(tr("Отменить"), AppIcon.UI_UNDO, "Ctrl+Z", hasDoc) { state.undo(state.activeFileIndex) })
        add(Command(tr("Повторить"), AppIcon.UI_REDO, "Ctrl+Y", hasDoc) { state.redo(state.activeFileIndex) })
        add(Command(tr("Дублировать выбранное"), AppIcon.UI_COPY, "Ctrl+D", hasSelection) { state.duplicateSelected() })
        add(Command(tr("Удалить выбранное"), AppIcon.UI_TRASH, "Delete", hasSelection) { state.deleteSelected() })
        add(Command(tr("Вписать в окно"), AppIcon.UI_FIT, "Shift+1", hasDoc) { state.centerView() })
        add(Command(tr("Масштаб по выделению"), AppIcon.UI_ZOOMIN, "Shift+2", hasSelection) { state.zoomToSelection() })
        add(Command(tr("Сгруппировать в Frame"), AppIcon.UI_LAYERS, "Ctrl+G", hasSelection) { state.groupSelected() })
        add(
            Command(tr("Сгруппировать в WrapSpacer"), AppIcon.WIDGET_WRAP, "Shift+A", hasSelection) {
                state.groupSelected(WidgetType.WRAP_SPACER, "Auto")
            },
        )
        add(Command(tr("Разгруппировать"), AppIcon.UI_LAYERS, "Ctrl+Shift+G", hasSelection) { state.ungroupSelected() })
        add(Command(tr("Выше в порядке отрисовки"), AppIcon.UI_DOWN, "]", hasSelection) { state.reorderSelected(Reorder.FORWARD) })
        add(Command(tr("Ниже в порядке отрисовки"), AppIcon.UI_DOWN, "[", hasSelection) { state.reorderSelected(Reorder.BACKWARD) })
        add(Command(tr("На самый верх"), AppIcon.UI_DOWN, "Ctrl+]", hasSelection) { state.reorderSelected(Reorder.FRONT) })
        add(Command(tr("На самый низ"), AppIcon.UI_DOWN, "Ctrl+[", hasSelection) { state.reorderSelected(Reorder.BACK) })
        add(Command(tr("Равные интервалы по горизонтали"), AppIcon.UI_ALIGNCH, enabled = several) { state.distributeSelected(true) })
        add(Command(tr("Равные интервалы по вертикали"), AppIcon.UI_ALIGNMV, enabled = several) { state.distributeSelected(false) })
        add(Command(tr("Копировать свойства"), AppIcon.UI_COPY, "Ctrl+Alt+C", hasSelection) { state.copyAppearance() })
        add(Command(tr("Вставить свойства"), AppIcon.UI_COPY, "Ctrl+Alt+V", hasSelection) { state.pasteAppearance() })
        add(Command(tr("Переименовать"), AppIcon.UI_SEARCH, "F2", hasSelection) { state.requestRename() })
        TemplateKind.entries.forEach { kind ->
            add(Command(tr("Шаблон скрипта: {0}", kind.title), AppIcon.UI_COPY, tr("в буфер"), hasDoc) { state.copyScriptTemplate(kind) })
        }
        add(Command(tr("Настройки: MCP и отображение"), AppIcon.UI_CMD, "Ctrl+,") { state.preferencesOpen = true })
        McpHost.url?.let { address ->
            add(Command(tr("Скопировать адрес MCP сервера"), AppIcon.UI_CMD, address) { state.copyMcpAddress(address) })
        }
        add(Command(tr("Скопировать код доступа к виджету"), AppIcon.UI_CMD, "Enforce Script", hasSelection) { state.copyScriptSnippet() })
        add(Command(tr("Заменить в именах виджетов…"), AppIcon.UI_SEARCH, enabled = hasDoc) { state.bulkDialog = BulkMode.NAME })
        add(Command(tr("Заменить значение свойства…"), AppIcon.UI_SEARCH, enabled = hasDoc) { state.bulkDialog = BulkMode.PROPERTY })
        add(
            toggle(tr("сравнение разрешений"), AppIcon.UI_PIXEL, state.compareResolutions) {
                state.compareResolutions =
                    !state.compareResolutions
            },
        )
        add(
            Command(tr("Масштаб 100%"), AppIcon.UI_ZOOMIN, "Ctrl+0", hasDoc) { state.zoomActual() },
        )
        add(
            toggle(
                tr("Цвета как в Workbench 1.30 (F6)"),
                AppIcon.UI_LAYERS,
                state.workbenchColors,
            ) { state.workbenchColors = !state.workbenchColors },
        )
        add(
            toggle(tr("подписи виджетов (F7)"), AppIcon.UI_INFO, state.showWidgetNames) {
                state.showWidgetNames = !state.showWidgetNames
                state.savePreferences()
            },
        )
        add(toggle("Live Preview (F5)", AppIcon.UI_PLAY, state.livePreview) { state.livePreview = !state.livePreview })
        add(toggle(tr("сетку"), AppIcon.UI_GRID, state.showGrid) { state.showGrid = !state.showGrid })
        add(toggle(tr("привязку к сетке"), AppIcon.UI_MAGNET, state.snapToGrid) { state.snapToGrid = !state.snapToGrid })
        add(toggle(tr("рамки виджетов"), AppIcon.UI_GRID, state.showBorders) { state.showBorders = !state.showBorders })
        add(toggle(tr("безопасную зону"), AppIcon.UI_SAFE, state.showSafeFrame) { state.showSafeFrame = !state.showSafeFrame })
        add(toggle(tr("расстояния"), AppIcon.UI_MEASURE, state.showMeasure) { state.showMeasure = !state.showMeasure })
        add(toggle(tr("эталон"), AppIcon.UI_COMPARE, state.showReference) { state.showReference = !state.showReference })
        add(toggle(tr("линейки"), AppIcon.UI_RULER, state.showRulers) { state.showRulers = !state.showRulers })
        add(toggle(tr("миникарту"), AppIcon.UI_FIT, state.showMinimap) { state.showMinimap = !state.showMinimap })
        for ((title, icon, mode) in listOf(
            Triple(tr("Выровнять по левому краю"), AppIcon.UI_ALIGNL, AlignMode.LEFT),
            Triple(tr("Выровнять по центру по горизонтали"), AppIcon.UI_ALIGNCH, AlignMode.CENTER_H),
            Triple(tr("Выровнять по правому краю"), AppIcon.UI_ALIGNR, AlignMode.RIGHT),
            Triple(tr("Выровнять по верхнему краю"), AppIcon.UI_ALIGNT, AlignMode.TOP),
            Triple(tr("Выровнять по центру по вертикали"), AppIcon.UI_ALIGNMV, AlignMode.CENTER_V),
            Triple(tr("Выровнять по нижнему краю"), AppIcon.UI_ALIGNB, AlignMode.BOTTOM),
        )) {
            add(Command(title, icon, enabled = several) { state.alignSelected(mode) })
        }
        for (path in state.recentFiles) {
            val name = File(path).name
            add(Command(tr("Открыть: {0}", name), fileIcon(name), File(path).parentFile?.name.orEmpty()) { state.openAny(path) })
        }
    }
}

/** Ctrl+P: the files of the work drive that can be opened, the ones used last first. */
fun buildFileCommands(state: LayoutEditorState): List<Command> {
    val root = state.workDrivePath.takeIf { it.isNotBlank() }?.let { File(it) }

    fun hintOf(file: File) = root?.let { runCatching { file.parentFile.relativeTo(it).invariantSeparatorsPath }.getOrNull() }.orEmpty()
    val recent = state.recentFiles.map { File(it) }
    val rest = state.workFiles.filter { f -> recent.none { it == f } }.sortedBy { it.name.lowercase() }
    return (recent + rest).map { file -> Command(file.name, fileIcon(file.name), hintOf(file)) { state.openAny(file.absolutePath) } }
}

/** Ctrl+F in a layout: the widgets of the open file by name; Enter selects the widget and shows it. */
fun buildWidgetCommands(state: LayoutEditorState): List<Command> {
    val root = state.activeFile?.root ?: return emptyList()
    val out = ArrayList<Command>()

    fun walk(
        node: com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode,
        path: String,
    ) {
        val title =
            node.node.name.ifEmpty {
                node.node.type.className
                    .removeSuffix("WidgetClass")
            }
        out +=
            Command(
                title,
                AppIcon.LAYOUT,
                node.node.type.className
                    .removeSuffix("WidgetClass") + if (path.isEmpty()) "" else "  ·  $path",
            ) {
                state.selectedIds.clear()
                state.selectedIds.add(node.id)
                state.zoomToSelection()
            }
        node.children.forEach { walk(it, if (path.isEmpty()) title else "$path/$title") }
    }
    walk(root, "")
    return out
}

/** Ctrl+Shift+P, Ctrl+P and Ctrl+F: type a few letters, pick with the arrows, Enter runs it, Esc closes. */
@Composable
fun CommandPalette(
    state: LayoutEditorState,
    openFileDialog: () -> Unit,
) {
    if (!state.commandPaletteOpen) return
    val mode = state.paletteMode
    val commands =
        remember(state.commandPaletteOpen, state.selectedIds.size, state.activeFileIndex, mode, state.workFiles.size) {
            when (mode) {
                PaletteMode.COMMANDS -> buildCommands(state, openFileDialog)
                PaletteMode.FILES -> buildFileCommands(state)
                PaletteMode.WIDGETS -> buildWidgetCommands(state)
            }
        }
    var query by remember { mutableStateOf("") }
    var index by remember { mutableStateOf(0) }
    val matches = commands.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }.take(300)
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()

    fun close() {
        state.commandPaletteOpen = false
        query = ""
        index = 0
    }

    fun runAt(i: Int) {
        val command = matches.getOrNull(i)?.takeIf { it.enabled } ?: return
        close()
        command.run()
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(index) { if (matches.isNotEmpty()) list.animateScrollToItem(index.coerceIn(0, matches.lastIndex)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 90.dp)
                .width(480.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(ElementsBackground)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconView(AppIcon.UI_CMD, size = 16.dp, tint = GhostlyText)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            when (mode) {
                                PaletteMode.COMMANDS -> tr("Команда…")
                                PaletteMode.FILES -> tr("Файл по имени…")
                                PaletteMode.WIDGETS -> tr("Виджет по имени…")
                            },
                            color = GhostlyText,
                            fontSize = 13.sp,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            index = 0
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = TextForeground, fontSize = 13.sp),
                        cursorBrush = SolidColor(TextForeground),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        Key.Escape -> {
                                            close()
                                            true
                                        }

                                        Key.DirectionDown -> {
                                            if (matches.isNotEmpty()) index = (index + 1).coerceAtMost(matches.lastIndex)
                                            true
                                        }

                                        Key.DirectionUp -> {
                                            index = (index - 1).coerceAtLeast(0)
                                            true
                                        }

                                        Key.Enter -> {
                                            runAt(index)
                                            true
                                        }

                                        else -> {
                                            false
                                        }
                                    }
                                },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DarkenedBorder))
            if (matches.isEmpty()) {
                Text(tr("Ничего не найдено"), color = GhostlyText, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp), state = list) {
                    itemsIndexed(matches) { i, command ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(if (i == index) SelectColor else Color.Transparent)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { runAt(i) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconView(command.icon, size = 15.dp, tint = if (command.enabled) null else GhostlyText)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                command.title,
                                color = if (command.enabled) TextForeground else GhostlyText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (command.hint.isNotEmpty()) Text(command.hint, color = GhostlyText, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
