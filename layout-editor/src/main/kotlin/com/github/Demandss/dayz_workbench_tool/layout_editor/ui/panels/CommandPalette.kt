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
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.McpHost
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.copyMcpAddress
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.PaletteMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.AlignMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.BulkMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.Reorder
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.TemplateKind
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.fileIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource
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
    ) = Command((if (on) str(Res.string.turn_off) else str(Res.string.turn_on)) + title, icon, run = flip)

    return buildList {
        add(Command(str(Res.string.open_file), AppIcon.FOLDER_OPEN, run = openFileDialog))
        add(Command(str(Res.string.save), AppIcon.UI_SAVE, "Ctrl+S", hasDoc) { state.saveActiveFile() })
        add(Command(str(Res.string.undo), AppIcon.UI_UNDO, "Ctrl+Z", hasDoc) { state.undo(state.activeFileIndex) })
        add(Command(str(Res.string.redo), AppIcon.UI_REDO, "Ctrl+Y", hasDoc) { state.redo(state.activeFileIndex) })
        add(Command(str(Res.string.duplicate_the_selection), AppIcon.UI_COPY, "Ctrl+D", hasSelection) { state.duplicateSelected() })
        add(Command(str(Res.string.delete_the_selection), AppIcon.UI_TRASH, "Delete", hasSelection) { state.deleteSelected() })
        add(Command(str(Res.string.fit_to_window), AppIcon.UI_FIT, "Shift+1", hasDoc) { state.centerView() })
        add(Command(str(Res.string.zoom_to_selection), AppIcon.UI_ZOOMIN, "Shift+2", hasSelection) { state.zoomToSelection() })
        add(Command(str(Res.string.group_into_a_frame), AppIcon.UI_LAYERS, "Ctrl+G", hasSelection) { state.groupSelected() })
        add(
            Command(str(Res.string.group_into_a_wrapspacer), AppIcon.WIDGET_WRAP, "Shift+A", hasSelection) {
                state.groupSelected(WidgetType.WRAP_SPACER, "Auto")
            },
        )
        add(Command(str(Res.string.ungroup), AppIcon.UI_LAYERS, "Ctrl+Shift+G", hasSelection) { state.ungroupSelected() })
        add(
            Command(
                str(Res.string.higher_in_the_drawing_order),
                AppIcon.UI_DOWN,
                "]",
                hasSelection,
            ) { state.reorderSelected(Reorder.FORWARD) },
        )
        add(
            Command(
                str(Res.string.lower_in_the_drawing_order),
                AppIcon.UI_DOWN,
                "[",
                hasSelection,
            ) { state.reorderSelected(Reorder.BACKWARD) },
        )
        add(Command(str(Res.string.to_the_very_top), AppIcon.UI_DOWN, "Ctrl+]", hasSelection) { state.reorderSelected(Reorder.FRONT) })
        add(Command(str(Res.string.to_the_very_bottom), AppIcon.UI_DOWN, "Ctrl+[", hasSelection) { state.reorderSelected(Reorder.BACK) })
        add(Command(str(Res.string.equal_horizontal_spacing), AppIcon.UI_ALIGNCH, enabled = several) { state.distributeSelected(true) })
        add(Command(str(Res.string.equal_vertical_spacing), AppIcon.UI_ALIGNMV, enabled = several) { state.distributeSelected(false) })
        add(Command(str(Res.string.copy_properties), AppIcon.UI_COPY, "Ctrl+Alt+C", hasSelection) { state.copyAppearance() })
        add(Command(str(Res.string.paste_properties), AppIcon.UI_COPY, "Ctrl+Alt+V", hasSelection) { state.pasteAppearance() })
        add(Command(str(Res.string.rename), AppIcon.UI_SEARCH, "F2", hasSelection) { state.requestRename() })
        TemplateKind.entries.forEach { kind ->
            add(
                Command(str(Res.string.script_template_x, kind.title), AppIcon.UI_COPY, str(Res.string.to_the_clipboard), hasDoc) {
                    state.copyScriptTemplate(kind)
                },
            )
        }
        add(Command(str(Res.string.import_a_font_2), AppIcon.UI_COPY, "") { state.fontImportOpen = true })
        state.openAppSettings?.let { open -> add(Command(str(Res.string.settings), AppIcon.UI_GEAR, "Ctrl+,") { open() }) }
        McpHost.url?.let { address ->
            add(Command(str(Res.string.copy_the_address_of_the_mcp_server), AppIcon.UI_CMD, address) { state.copyMcpAddress(address) })
        }
        add(
            Command(str(Res.string.copy_the_code_that_accesses_the_widget), AppIcon.UI_CMD, "Enforce Script", hasSelection) {
                state.copyScriptSnippet()
            },
        )
        add(Command(str(Res.string.replace_in_widget_names_2), AppIcon.UI_SEARCH, enabled = hasDoc) { state.bulkDialog = BulkMode.NAME })
        add(
            Command(
                str(Res.string.replace_a_property_value_2),
                AppIcon.UI_SEARCH,
                enabled = hasDoc,
            ) { state.bulkDialog = BulkMode.PROPERTY },
        )
        add(
            toggle(str(Res.string.the_resolution_comparison), AppIcon.UI_PIXEL, state.compareResolutions) {
                state.compareResolutions =
                    !state.compareResolutions
            },
        )
        add(
            Command(str(Res.string.zoom_100), AppIcon.UI_ZOOMIN, "Ctrl+0", hasDoc) { state.zoomActual() },
        )
        add(
            toggle(
                str(Res.string.colours_as_in_workbench_1_30_f6),
                AppIcon.UI_LAYERS,
                state.workbenchColors,
            ) { state.workbenchColors = !state.workbenchColors },
        )
        add(
            toggle(str(Res.string.widget_labels_f7), AppIcon.UI_INFO, state.showWidgetNames) {
                state.showWidgetNames = !state.showWidgetNames
                state.savePreferences()
            },
        )
        add(toggle("Live Preview (F5)", AppIcon.UI_PLAY, state.livePreview) { state.livePreview = !state.livePreview })
        add(toggle(str(Res.string.the_grid), AppIcon.UI_GRID, state.showGrid) { state.showGrid = !state.showGrid })
        add(toggle(str(Res.string.snapping_to_the_grid), AppIcon.UI_MAGNET, state.snapToGrid) { state.snapToGrid = !state.snapToGrid })
        add(toggle(str(Res.string.widget_borders), AppIcon.UI_GRID, state.showBorders) { state.showBorders = !state.showBorders })
        add(toggle(str(Res.string.the_safe_zone), AppIcon.UI_SAFE, state.showSafeFrame) { state.showSafeFrame = !state.showSafeFrame })
        add(toggle(str(Res.string.the_distances), AppIcon.UI_MEASURE, state.showMeasure) { state.showMeasure = !state.showMeasure })
        add(toggle(str(Res.string.the_reference), AppIcon.UI_COMPARE, state.showReference) { state.showReference = !state.showReference })
        add(toggle(str(Res.string.the_rulers), AppIcon.UI_RULER, state.showRulers) { state.showRulers = !state.showRulers })
        add(toggle(str(Res.string.the_minimap), AppIcon.UI_FIT, state.showMinimap) { state.showMinimap = !state.showMinimap })
        for ((title, icon, mode) in listOf(
            Triple(str(Res.string.align_to_the_left_edge), AppIcon.UI_ALIGNL, AlignMode.LEFT),
            Triple(str(Res.string.align_to_the_horizontal_centre), AppIcon.UI_ALIGNCH, AlignMode.CENTER_H),
            Triple(str(Res.string.align_to_the_right_edge), AppIcon.UI_ALIGNR, AlignMode.RIGHT),
            Triple(str(Res.string.align_to_the_top_edge), AppIcon.UI_ALIGNT, AlignMode.TOP),
            Triple(str(Res.string.align_to_the_vertical_centre), AppIcon.UI_ALIGNMV, AlignMode.CENTER_V),
            Triple(str(Res.string.align_to_the_bottom_edge), AppIcon.UI_ALIGNB, AlignMode.BOTTOM),
        )) {
            add(Command(title, icon, enabled = several) { state.alignSelected(mode) })
        }
        for (path in state.recentFiles) {
            val name = File(path).name
            add(Command(str(Res.string.open_x, name), fileIcon(name), File(path).parentFile?.name.orEmpty()) { state.openAny(path) })
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
                                PaletteMode.COMMANDS -> stringResource(Res.string.command)
                                PaletteMode.FILES -> stringResource(Res.string.file_by_name)
                                PaletteMode.WIDGETS -> stringResource(Res.string.widget_by_name)
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
                Text(stringResource(Res.string.nothing_found), color = GhostlyText, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
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
