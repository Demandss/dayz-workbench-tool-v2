package com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.imageset.ImageFlags
import com.github.demandss.dayz_workbench_tool.imageset.ImageSetTexture
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ImageEntry
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ImageProblem
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ImageSetDocument
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.checkImageSet
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.pickFile
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.io.File
import kotlin.math.roundToInt

/** The editor of an `.imageset`: the toolbar and the atlas on the left, the list and the properties on the right. */
@Composable
fun ImageSetEditor(
    state: LayoutEditorState,
    doc: ImageSetDocument,
    modifier: Modifier = Modifier,
) {
    val view = remember(doc.path) { ImageSetView() }
    Row(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ImageSetToolbar(state, doc, view)
            ImageSetViewport(state, doc, view, Modifier.weight(1f).fillMaxWidth())
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))
        ImageSetPanel(state, doc, view)
    }
}

@Composable
private fun ImageSetToolbar(
    state: LayoutEditorState,
    doc: ImageSetDocument,
    view: ImageSetView,
) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).background(FrameBackground),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Spacer(Modifier.width(4.dp))
        DarkIconButton(
            AppIcon.UI_SAVE,
            stringResource(Res.string.save_ctrl_s),
            onClick = { state.saveActiveImageSet() },
            enabled = doc.isModified,
        )
        DarkIconButton(AppIcon.UI_UNDO, stringResource(Res.string.undo_ctrl_z), onClick = { doc.undo() }, enabled = doc.canUndo)
        DarkIconButton(AppIcon.UI_REDO, stringResource(Res.string.redo_ctrl_y), onClick = { doc.redo() }, enabled = doc.canRedo)
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(20.dp)
                .background(DarkenedBorder),
        )
        DarkIconButton(AppIcon.UI_ZOOMOUT, stringResource(Res.string.zoom_out), onClick = { view.zoomAbout(1f / 1.25f) })
        DarkToolButton(
            "${(view.zoom * 100).roundToInt()}%",
            { view.zoomAbout(1f, Offset.Zero) },
            tooltip = stringResource(Res.string.current_zoom),
        )
        DarkIconButton(AppIcon.UI_ZOOMIN, stringResource(Res.string.zoom_in), onClick = { view.zoomAbout(1.25f) })
        DarkIconButton(
            AppIcon.UI_FIT,
            stringResource(Res.string.fit_the_atlas_to_the_window_shift_1),
            onClick = { view.fit(doc.model.refWidth, doc.model.refHeight) },
        )
        DarkToolButton("1:1", {
            val centre = Offset(view.canvas.width / 2f, view.canvas.height / 2f)
            view.zoomAbout(1f / view.zoom, centre)
        }, tooltip = stringResource(Res.string.one_texture_pixel_per_screen_pixel))
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(20.dp)
                .background(DarkenedBorder),
        )
        DarkToolButton(
            stringResource(Res.string.new_area),
            { view.drawMode = !view.drawMode },
            icon = AppIcon.UI_PLUS,
            active = view.drawMode,
            tooltip = stringResource(Res.string.draw_a_new_area_with_a_frame_without),
        )
        DarkIconButton(
            AppIcon.UI_COPY,
            stringResource(Res.string.duplicate_the_selected_ones_ctrl_d),
            onClick = { duplicateSelected(doc) },
            enabled = doc.selected.isNotEmpty(),
        )
        DarkIconButton(
            AppIcon.UI_TRASH,
            stringResource(Res.string.delete_the_selected_ones_delete),
            onClick = {
                val ids = doc.selected.toSet()
                doc.edit { it.withRemoved(ids) }
                doc.selected.clear()
            },
            enabled = doc.selected.isNotEmpty(),
        )
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(20.dp)
                .background(DarkenedBorder),
        )
        DarkToolButton(
            "3×3",
            {
                val name = doc.model.uniqueGroupName("Frame")
                var created: List<Int> = emptyList()
                doc.edit { m ->
                    val (next, ids) = m.withNineQuads(name)
                    created = ids
                    next
                }
                doc.selected.clear()
                doc.selected.addAll(created)
            },
            tooltip = stringResource(Res.string.add_a_group_of_nine_30_30_areas_like_add),
        )
        DarkToolButton(
            stringResource(Res.string.cut),
            {
                val id = doc.selected.singleOrNull()
                val d = id?.let { doc.model.entry(it)?.def }
                if (id != null && d != null) {
                    val corner = maxOf(1, minOf(d.width, d.height) / 3)
                    var cut: List<Int>? = null
                    doc.edit { m ->
                        val result = m.withNineSlice(id, corner)
                        cut = result?.second
                        result?.first ?: m
                    }
                    val ids = cut
                    if (ids != null) {
                        doc.selected.clear()
                        doc.selected.addAll(ids)
                    } else {
                        state.notify(str(Res.string.the_area_is_too_small_to_cut_into_nine))
                    }
                }
            },
            tooltip = stringResource(Res.string.cut_the_selected_area_into_nine_parts),
        )
        DarkIconButton(AppIcon.UI_GRID, stringResource(Res.string.a_pixel_grid_at_high_zoom), onClick = {
            view.showGrid = !view.showGrid
        }, active = view.showGrid)
        DarkToolButton(
            stringResource(Res.string.names),
            { view.showNames = !view.showNames },
            active = view.showNames,
            tooltip = stringResource(Res.string.labels_of_the_areas_on_the_atlas),
        )
    }
}

// ---------------------------------------------------------------------------
// The panel on the right
// ---------------------------------------------------------------------------

@Composable
private fun ImageSetPanel(
    state: LayoutEditorState,
    doc: ImageSetDocument,
    view: ImageSetView,
) {
    val model = doc.model
    val textureRef = model.textures.minByOrNull { it.mpix }
    val file = remember(doc.path, textureRef?.path) { textureRef?.let { state.resolveImageSetTexture(doc.path, it.path) } }
    val bitmap = state.imageSetBitmap(file)
    val problems = remember(model, bitmap?.width, bitmap?.height) { checkImageSet(model, bitmap?.width ?: 0, bitmap?.height ?: 0) }

    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(FrameBackground)
            .verticalScroll(rememberScrollState()),
    ) {
        SetSection(state, doc, bitmap?.width, bitmap?.height, file)
        SelectionSection(doc, view)
        ImageList(doc, view, bitmap, file, state.imageSetSearchTick)
        ProblemsSection(doc, problems)
    }
}

@Composable
internal fun PanelHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    tooltip: String? = null,
) {
    Tip(tooltip) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(ElementsBackground)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onToggle)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconView(if (expanded) AppIcon.UI_DOWN else AppIcon.UI_RIGHT, size = 12.dp, tint = GhostlyText)
            Spacer(Modifier.width(6.dp))
            Text(title, color = TextForeground, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun LabelRow(
    label: String,
    tooltip: String?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Tip(tooltip) { Text(label, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(62.dp), maxLines = 1) }
        content()
    }
}

/** A one-line field that commits on Enter or when it loses the focus. [focusTick] puts the cursor in it and selects the text. */
@Composable
internal fun CommitField(
    value: String,
    modifier: Modifier = Modifier,
    focusTick: Int = 0,
    mono: Boolean = false,
    onCommit: (String) -> Unit,
) {
    var field by remember(value) { mutableStateOf(TextFieldValue(value)) }
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            runCatching { requester.requestFocus() }
            field = field.copy(selection = TextRange(0, field.text.length))
        }
    }
    BasicTextField(
        value = field,
        onValueChange = { field = it },
        singleLine = true,
        textStyle =
            TextStyle(
                color = TextForeground,
                fontSize = 11.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
        cursorBrush = SolidColor(TextForeground),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(DarkenedBorder)
                    .border(1.dp, if (focused) SubSelectColor else Color(0xFF3A3C40))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
        modifier =
            modifier
                .focusRequester(requester)
                .onFocusChanged { fs ->
                    if (focused && !fs.isFocused && field.text != value) onCommit(field.text)
                    focused = fs.isFocused
                }.onKeyEvent { ev ->
                    if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown) {
                        if (field.text != value) onCommit(field.text)
                        true
                    } else {
                        false
                    }
                },
    )
}

@Composable
private fun IntField(
    value: Int,
    modifier: Modifier = Modifier,
    onCommit: (Int) -> Unit,
) {
    CommitField(value.toString(), modifier, mono = true) { text -> text.trim().toIntOrNull()?.let(onCommit) }
}

@Composable
private fun SetSection(
    state: LayoutEditorState,
    doc: ImageSetDocument,
    textureWidth: Int?,
    textureHeight: Int?,
    textureFile: File?,
) {
    var open by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val model = doc.model
    PanelHeader("Imageset", open, { open = !open })
    if (!open) return
    LabelRow("Name", stringResource(Res.string.the_name_of_the_set_a_layout_finds)) {
        CommitField(model.name, Modifier.weight(1f)) { v -> doc.edit { it.copy(name = v.trim()) } }
    }
    LabelRow("RefSize", stringResource(Res.string.the_size_of_the_texture_in_the_pixels)) {
        IntField(model.refWidth, Modifier.weight(1f)) { v -> doc.edit { it.copy(refWidth = v.coerceAtLeast(1)) } }
        Text("×", color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
        IntField(model.refHeight, Modifier.weight(1f)) { v -> doc.edit { it.copy(refHeight = v.coerceAtLeast(1)) } }
    }
    if (textureWidth != null && textureHeight != null && (textureWidth != model.refWidth || textureHeight != model.refHeight)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.texture_x_x, textureWidth, textureHeight),
                color = GhostlyText,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            DarkButton(
                stringResource(Res.string.take),
                onClick = { doc.edit { it.copy(refWidth = textureWidth, refHeight = textureHeight) } },
                modifier = Modifier.width(60.dp),
                tooltip = stringResource(Res.string.write_the_size_of_the_texture_to_refsize),
            )
        }
    }
    Text(stringResource(Res.string.textures), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
    model.textures.forEachIndexed { index, texture ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Tip(stringResource(Res.string.mpix_level_of_detail_0_is_the_main)) {
                IntField(texture.mpix, Modifier.width(34.dp)) { v ->
                    doc.edit { m ->
                        m.copy(
                            textures =
                                m.textures.mapIndexed { i, t ->
                                    if (i ==
                                        index
                                    ) {
                                        t.copy(mpix = v)
                                    } else {
                                        t
                                    }
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            CommitField(texture.path, Modifier.weight(1f)) { v ->
                doc.edit { m ->
                    m.copy(
                        textures =
                            m.textures.mapIndexed { i, t ->
                                if (i ==
                                    index
                                ) {
                                    t.copy(path = v.trim())
                                } else {
                                    t
                                }
                            },
                    )
                }
            }
            DarkIconButton(
                AppIcon.UI_SEARCH,
                stringResource(Res.string.choose_a_texture_file),
                onClick = {
                    scope.launch {
                        pickFile(str(Res.string.imageset_texture), "*", state.workDrivePath.ifBlank { null })?.let { chosen ->
                            val ref = state.resourceReference(File(chosen))
                            doc.edit { m -> m.copy(textures = m.textures.mapIndexed { i, t -> if (i == index) t.copy(path = ref) else t }) }
                            state.reloadImageSetTextures()
                        }
                    }
                },
            )
            DarkIconButton(AppIcon.UI_TRASH, stringResource(Res.string.remove_the_texture), onClick = {
                doc.edit { m ->
                    m.copy(
                        textures =
                            m.textures.filterIndexed { i, _ ->
                                i !=
                                    index
                            },
                    )
                }
            })
        }
    }
    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        DarkButton(
            stringResource(Res.string.add_a_texture),
            onClick = {
                doc.edit { m ->
                    m.copy(
                        textures =
                            m.textures + ImageSetTexture(if (m.textures.isEmpty()) 0 else m.textures.maxOf { it.mpix } + 1, ""),
                    )
                }
            },
            modifier = Modifier.width(140.dp),
            tooltip = stringResource(Res.string.one_more_texture_file_for_another_mpix),
        )
    }
    if (textureFile == null && model.textures.isNotEmpty()) {
        Text(
            stringResource(Res.string.the_texture_file_was_not_found_the),
            color = Color(0xFFF0D9A0),
            fontSize = 10.sp,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun SelectionSection(
    doc: ImageSetDocument,
    view: ImageSetView,
) {
    var open by remember { mutableStateOf(true) }
    PanelHeader(stringResource(Res.string.selected), open, { open = !open })
    if (!open) return
    val model = doc.model
    val selected = doc.selected.mapNotNull { model.entry(it) }
    when {
        selected.isEmpty() -> {
            Text(
                stringResource(Res.string.pick_an_area_on_the_atlas_or_in_the_list),
                color = GhostlyText,
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp),
            )
        }

        selected.size > 1 -> {
            Text(
                stringResource(Res.string.selected_x, selected.size),
                color = TextForeground,
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp),
            )
            GroupRow(doc, selected)
        }

        else -> {
            val entry = selected[0]
            val def = entry.def

            fun change(
                f: (com.github.demandss.dayz_workbench_tool.imageset.ImageDef) -> com.github.demandss.dayz_workbench_tool.imageset.ImageDef,
            ) = doc.edit {
                it.withUpdated(entry.id, f)
            }
            LabelRow("Name", stringResource(Res.string.the_name_of_the_image_in_set_name_image)) {
                CommitField(def.name, Modifier.weight(1f), focusTick = doc.focusNameTick) { v ->
                    val clean = v.trim()
                    if (clean.isNotEmpty() &&
                        clean != def.name
                    ) {
                        doc.edit { m ->
                            m.withUpdated(entry.id) {
                                it.copy(
                                    name =
                                        m
                                            .copy(
                                                entries =
                                                    m.entries.filter { e ->
                                                        e.id !=
                                                            entry.id
                                                    },
                                            ).uniqueImageName(clean),
                                )
                            }
                        }
                    }
                }
            }
            LabelRow("Pos", stringResource(Res.string.the_top_left_corner_of_the_area_in)) {
                IntField(def.x, Modifier.weight(1f)) { v -> change { it.copy(x = v) } }
                Spacer(Modifier.width(4.dp))
                IntField(def.y, Modifier.weight(1f)) { v -> change { it.copy(y = v) } }
            }
            LabelRow("Size", stringResource(Res.string.the_width_and_height_of_the_area_in)) {
                IntField(def.width, Modifier.weight(1f)) { v -> change { it.copy(width = v.coerceAtLeast(0)) } }
                Spacer(Modifier.width(4.dp))
                IntField(def.height, Modifier.weight(1f)) { v -> change { it.copy(height = v.coerceAtLeast(0)) } }
            }
            LabelRow("Flags", stringResource(Res.string.how_the_image_repeats_when_stretched_in)) {
                FlagBox("H", stringResource(Res.string.ishorizontaltile_repeat_horizontally), def.flags and ImageFlags.H_TILE != 0) { on ->
                    change { it.withFlags(if (on) it.flags or ImageFlags.H_TILE else it.flags and ImageFlags.H_TILE.inv()) }
                }
                Spacer(Modifier.width(8.dp))
                FlagBox("V", stringResource(Res.string.isverticaltile_repeat_vertically), def.flags and ImageFlags.V_TILE != 0) { on ->
                    change { it.withFlags(if (on) it.flags or ImageFlags.V_TILE else it.flags and ImageFlags.V_TILE.inv()) }
                }
            }
            GroupRow(doc, selected)
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                DarkButton(
                    stringResource(Res.string.show),
                    onClick = { view.zoomTo(def.rect()) },
                    modifier = Modifier.width(80.dp),
                    tooltip = stringResource(Res.string.zoom_in_on_this_area_shift_2),
                )
            }
        }
    }
}

@Composable
private fun FlagBox(
    label: String,
    tooltip: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Tip(tooltip) {
        Row(
            Modifier.clickable(remember { MutableInteractionSource() }, null) { onChange(!on) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(
                        14.dp,
                    ).background(if (on) SubSelectColor else DarkenedBorder)
                    .border(1.dp, if (on) SubSelectColor else Color(0xFF3A3C40)),
                contentAlignment = Alignment.Center,
            ) { if (on) Text("✓", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp) }
            Spacer(Modifier.width(5.dp))
            Text(label, color = TextForeground, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GroupRow(
    doc: ImageSetDocument,
    selected: List<ImageEntry>,
) {
    val current = selected.map { it.group }.distinct().singleOrNull()
    LabelRow(
        stringResource(Res.string.group),
        stringResource(Res.string.a_group_only_sets_the_order_in_the),
    ) {
        CommitField(current.orEmpty(), Modifier.weight(1f)) { v ->
            val ids = selected.map { it.id }.toSet()
            val name = v.trim().ifEmpty { null }
            doc.edit { it.withGroup(ids, name) }
        }
    }
}

@Composable
private fun ImageList(
    doc: ImageSetDocument,
    view: ImageSetView,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    file: File?,
    searchTick: Int,
) {
    var open by remember { mutableStateOf(true) }
    val searchFocus =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }
    LaunchedEffect(searchTick) {
        if (searchTick > 0) {
            open = true
            kotlinx.coroutines.yield()
            runCatching { searchFocus.requestFocus() }
        }
    }
    var query by remember { mutableStateOf("") }
    val model = doc.model
    PanelHeader(stringResource(Res.string.images_x, model.entries.size), open, { open = !open })
    if (!open) return
    Box(Modifier.fillMaxWidth().padding(8.dp)) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
            cursorBrush = SolidColor(TextForeground),
            modifier = Modifier.focusRequester(searchFocus),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            24.dp,
                        ).background(DarkenedBorder)
                        .border(1.dp, Color(0xFF3A3C40))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) Text(stringResource(Res.string.search_by_name), color = GhostlyText, fontSize = 11.sp)
                    inner()
                }
            },
        )
    }
    val shown = model.entries.filter { query.isBlank() || it.def.name.contains(query.trim(), ignoreCase = true) }
    val groups = (listOf<String?>(null) + model.groups).filter { g -> shown.any { it.group == g } }
    // Hundreds of rows with thumbnails make the whole panel slow, so the list grows on request
    var limit by remember(query) { mutableStateOf(IMAGE_ROWS_STEP) }
    val selectedShown = shown.filter { it.id in doc.selected }
    val visible = (shown.take(limit) + selectedShown).distinctBy { it.id }.map { it.id }.toSet()
    Column(Modifier.fillMaxWidth()) {
        for (group in groups) {
            if (group !=
                null
            ) {
                Text(
                    group.uppercase(),
                    color = GhostlyText,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                )
            }
            for (entry in shown.filter { it.group == group && it.id in visible }) ImageRow(doc, view, entry, bitmap)
        }
        if (shown.size > limit) {
            Text(
                stringResource(Res.string.showing_x_of_x_show_more, limit, shown.size),
                color = GhostlyText,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().clickable { limit += IMAGE_ROWS_STEP }.padding(8.dp),
            )
        }
    }
    if (file == null && bitmap == null) Spacer(Modifier.height(1.dp))
}

private const val IMAGE_ROWS_STEP = 60

@Composable
private fun ImageRow(
    doc: ImageSetDocument,
    view: ImageSetView,
    entry: ImageEntry,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
) {
    val selected = entry.id in doc.selected
    val def = entry.def
    var lastClick by remember { mutableStateOf(0L) }
    Tip("${def.name}: ${def.x}, ${def.y}  ${def.width} × ${def.height}") {
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(if (selected) SelectColor else Color.Transparent)
                .clickable(remember { MutableInteractionSource() }, null) {
                    val now = System.currentTimeMillis()
                    if (now - lastClick < 400) view.zoomTo(def.rect())
                    lastClick = now
                    doc.select(entry.id)
                }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cut once into a small picture; drawing a piece of a 2048 px atlas for every row on every frame is very slow
            val thumb =
                remember(bitmap, def.x, def.y, def.width, def.height, doc.model.refWidth, doc.model.refHeight) {
                    bitmap?.let { thumbnailOf(it, def, doc.model.refWidth, doc.model.refHeight) }
                }
            Canvas(Modifier.size(22.dp).background(Color(0xFF2B2B30))) {
                if (thumb != null) {
                    val k = (size.width / thumb.width).coerceAtMost(size.height / thumb.height)
                    val w = (thumb.width * k).coerceAtLeast(1f)
                    val h = (thumb.height * k).coerceAtLeast(1f)
                    drawImage(
                        thumb,
                        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                def.name,
                color = TextForeground,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${def.width}×${def.height}", color = GhostlyText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ProblemsSection(
    doc: ImageSetDocument,
    problems: List<ImageProblem>,
) {
    var open by remember { mutableStateOf(true) }
    PanelHeader(
        if (problems.isEmpty()) stringResource(Res.string.check) else stringResource(Res.string.check_x, problems.size),
        open,
        { open = !open },
        tooltip = stringResource(Res.string.overlaps_going_beyond_refsize_repeated),
    )
    if (!open) return
    if (problems.isEmpty()) {
        Text(stringResource(Res.string.no_remarks), color = GhostlyText, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
        return
    }
    for (p in problems.take(50)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) {
                    if (p.ids.isNotEmpty()) {
                        doc.selected.clear()
                        doc.selected.addAll(p.ids)
                    }
                }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AppIconView(AppIcon.UI_WARN, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(p.message, color = TextForeground, fontSize = 11.sp)
        }
    }
    if (problems.size >
        50
    ) {
        Text(
            stringResource(Res.string.and_x_more, problems.size - 50),
            color = GhostlyText,
            fontSize = 11.sp,
            modifier = Modifier.padding(8.dp),
        )
    }
}

private val awtOfAtlas = java.util.WeakHashMap<androidx.compose.ui.graphics.ImageBitmap, java.awt.image.BufferedImage>()

/** The part of the atlas an image covers, at most 64 px on a side. Null when the image is empty or outside the texture. */
internal fun thumbnailOf(
    atlas: androidx.compose.ui.graphics.ImageBitmap,
    def: com.github.demandss.dayz_workbench_tool.imageset.ImageDef,
    refWidth: Int,
    refHeight: Int,
): androidx.compose.ui.graphics.ImageBitmap? {
    if (def.width <= 0 || def.height <= 0 || refWidth <= 0 || refHeight <= 0) return null
    val sx = atlas.width.toDouble() / refWidth
    val sy = atlas.height.toDouble() / refHeight
    val x = (def.x * sx).roundToInt().coerceIn(0, atlas.width - 1)
    val y = (def.y * sy).roundToInt().coerceIn(0, atlas.height - 1)
    val w = (def.width * sx).roundToInt().coerceIn(1, atlas.width - x)
    val h = (def.height * sy).roundToInt().coerceIn(1, atlas.height - y)
    return runCatching {
        val full = synchronized(awtOfAtlas) { awtOfAtlas.getOrPut(atlas) { atlas.toAwtImage() } }
        val part = full.getSubimage(x, y, w, h)
        val k = minOf(1.0, 64.0 / maxOf(w, h))
        val tw = (w * k).roundToInt().coerceAtLeast(1)
        val th = (h * k).roundToInt().coerceAtLeast(1)
        val out = java.awt.image.BufferedImage(tw, th, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(part, 0, 0, tw, th, null)
        g.dispose()
        out.toComposeImageBitmap()
    }.getOrNull()
}
