package com.github.demandss.dayz_workbench_tool.layout_editor.ui.styles

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.StylesDocument
import com.github.demandss.dayz_workbench_tool.layout_editor.state.styleColorOf
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.drawSkinWidget
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.CommitField
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.LabelRow
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.PanelHeader
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.DropdownField
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.styles.STYLE_SLOTS
import com.github.demandss.dayz_workbench_tool.styles.STYLE_STATES
import com.github.demandss.dayz_workbench_tool.styles.SlotSet
import com.github.demandss.dayz_workbench_tool.styles.StyleDef
import com.github.demandss.dayz_workbench_tool.styles.WidgetStyles
import com.github.demandss.dayz_workbench_tool.styles.findSlotSets
import com.github.demandss.dayz_workbench_tool.styles.styleColorFromHex
import com.github.demandss.dayz_workbench_tool.styles.styleColorHex
import com.github.demandss.dayz_workbench_tool.styles.withAddedState
import com.github.demandss.dayz_workbench_tool.styles.withAddedStyle
import com.github.demandss.dayz_workbench_tool.styles.withDuplicatedStyle
import com.github.demandss.dayz_workbench_tool.styles.withItem
import com.github.demandss.dayz_workbench_tool.styles.withStyleAt
import com.github.demandss.dayz_workbench_tool.styles.withoutItem
import com.github.demandss.dayz_workbench_tool.styles.withoutState
import com.github.demandss.dayz_workbench_tool.styles.withoutStyle
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource

/** The editor of a `.styles`: the widget types and their styles on the left, the selected style on the right. */
@Composable
fun StylesEditor(
    state: LayoutEditorState,
    doc: StylesDocument,
    modifier: Modifier = Modifier,
) {
    // The picture being chosen: the slot, the current image, and what to do with the choice
    var picking by remember(doc) { mutableStateOf<PickRequest?>(null) }
    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(280.dp).fillMaxHeight().background(FrameBackground)) {
                StylesToolbar(state, doc)
                StyleTree(doc)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))
            val style = doc.selectedStyle
            if (style == null) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.pick_a_style_on_the_left), color = GhostlyText, fontSize = 12.sp)
                }
            } else {
                StyleDetail(state, doc, style, Modifier.weight(1f).fillMaxHeight()) { request -> picking = request }
            }
        }
        val request = picking
        val setName = doc.selectedStyle?.imageSet
        if (request != null && setName != null) {
            val sets = remember(setName, state.workDrivePath) { slotSetsOf(state, setName, doc.path) }
            ImagePickerDialog(
                state,
                setName,
                doc.path,
                request.slot,
                request.image,
                sets,
                request.apply,
                request.applySet,
            ) { picking = null }
        }
    }
}

@Composable
private fun StylesToolbar(
    state: LayoutEditorState,
    doc: StylesDocument,
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
            onClick = { state.saveActiveStyles() },
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
        val unused =
            com.github.demandss.dayz_workbench_tool.layout.WidgetType.entries
                .map { it.className.removeSuffix("Class") }
                .filter { name -> doc.sheet.widgets.none { it.widget == name } }
        if (unused.isNotEmpty()) {
            DropdownField(stringResource(Res.string.type), unused, {
                it
            }, tooltip = stringResource(Res.string.add_a_widget_type_that_will_have_styles)) { name ->
                doc.edit { it.copy(widgets = it.widgets + WidgetStyles(name, emptyList())) }
                doc.select(doc.sheet.widgets.lastIndex, -1)
            }
        }
        DarkToolButton(
            stringResource(Res.string.style),
            {
                val w = doc.widget.takeIf { it >= 0 } ?: return@DarkToolButton
                val setName = doc.selectedStyle?.imageSet.orEmpty()
                var added = -1
                doc.edit { sheet ->
                    sheet.withAddedStyle(w, "new_style", setName)?.let { (next, index) ->
                        added = index
                        next
                    } ?: sheet
                }
                if (added >= 0) doc.select(w, added)
            },
            tooltip = stringResource(Res.string.add_a_style_to_the_selected_widget_type),
        )
        DarkIconButton(
            AppIcon.UI_COPY,
            stringResource(Res.string.duplicate_the_style),
            onClick = {
                var added = -1
                val w = doc.widget
                doc.edit { sheet ->
                    sheet.withDuplicatedStyle(w, doc.style)?.let { (next, index) ->
                        added = index
                        next
                    } ?: sheet
                }
                if (added >= 0) doc.select(w, added)
            },
            enabled = doc.selectedStyle != null,
        )
        DarkIconButton(
            AppIcon.UI_TRASH,
            stringResource(Res.string.delete_the_style),
            onClick = {
                val w = doc.widget
                val s = doc.style
                doc.edit { it.withoutStyle(w, s) }
                doc.select(
                    w,
                    (s - 1).coerceAtLeast(0).takeIf {
                        doc.sheet.widgets
                            .getOrNull(w)
                            ?.styles
                            ?.isNotEmpty() == true
                    } ?: -1,
                )
            },
            enabled = doc.selectedStyle != null,
        )
    }
}

private sealed interface TreeRow {
    data class Widget(
        val index: Int,
        val name: String,
        val count: Int,
        val open: Boolean,
    ) : TreeRow

    data class Style(
        val widget: Int,
        val index: Int,
        val name: String,
    ) : TreeRow
}

@Composable
private fun StyleTree(doc: StylesDocument) {
    var query by remember { mutableStateOf("") }
    val expanded = remember(doc) { mutableStateListOf<Int>().also { l -> doc.widget.takeIf { it >= 0 }?.let(l::add) } }
    Box(Modifier.fillMaxWidth().padding(8.dp)) {
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
                        .height(24.dp)
                        .background(DarkenedBorder)
                        .border(1.dp, Color(0xFF3A3C40))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) Text(stringResource(Res.string.search_for_a_style), color = GhostlyText, fontSize = 11.sp)
                    inner()
                }
            },
        )
    }
    val rows =
        buildList {
            val q = query.trim()
            doc.sheet.widgets.forEachIndexed { wi, widget ->
                val matching = widget.styles.withIndex().filter { q.isEmpty() || it.value.name.contains(q, ignoreCase = true) }
                if (q.isNotEmpty() && matching.isEmpty()) return@forEachIndexed
                val open = q.isNotEmpty() || wi in expanded
                add(TreeRow.Widget(wi, widget.widget, matching.size, open))
                if (open) matching.forEach { (si, style) -> add(TreeRow.Style(wi, si, style.name)) }
            }
        }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows) { row ->
            when (row) {
                is TreeRow.Widget -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(ElementsBackground)
                            .clickable(remember { MutableInteractionSource() }, null) {
                                if (!expanded.remove(row.index)) expanded.add(row.index)
                            }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconView(if (row.open) AppIcon.UI_DOWN else AppIcon.UI_RIGHT, size = 12.dp, tint = GhostlyText)
                        Spacer(Modifier.width(6.dp))
                        Text(row.name.removeSuffix("Widget"), color = TextForeground, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("${row.count}", color = GhostlyText, fontSize = 10.sp)
                    }
                }

                is TreeRow.Style -> {
                    val selected = doc.widget == row.widget && doc.style == row.index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(if (selected) SelectColor else Color.Transparent)
                            .clickable(remember { MutableInteractionSource() }, null) { doc.select(row.widget, row.index) }
                            .padding(start = 26.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.name, color = TextForeground, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleDetail(
    state: LayoutEditorState,
    doc: StylesDocument,
    style: StyleDef,
    modifier: Modifier,
    onPickImage: (PickRequest) -> Unit,
) {
    val w = doc.widget
    val s = doc.style
    val widgetName = doc.sheet.widgets[w].widget
    val setNames = remember(doc.path, state.workDrivePath) { state.imageSetNames(doc.path) }
    val imageNames = remember(style.imageSet, setNames) { state.imageNamesOf(style.imageSet, doc.path) }
    val stateIndex = doc.stateIndex.coerceIn(0, (style.states.size - 1).coerceAtLeast(0))

    fun change(f: (StyleDef) -> StyleDef) = doc.edit { it.withStyleAt(w, s, f) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            "${widgetName.removeSuffix("Widget")} · ${style.name}",
            color = TextForeground,
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp),
        )
        StylePreview(state, doc, style, stateIndex)

        PanelHeader(stringResource(Res.string.style_2), true, {})
        LabelRow("Name", stringResource(Res.string.the_style_name_it_is_written_in_the)) {
            CommitField(style.name, Modifier.weight(1f)) { v ->
                val clean = v.trim()
                val taken =
                    doc.sheet.widgets[w]
                        .styles
                        .withIndex()
                        .any { it.index != s && it.value.name == clean }
                if (clean.isEmpty()) {
                    state.notify(
                        str(Res.string.the_style_name_cannot_be_empty),
                        com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.INFO,
                    )
                } else if (taken) {
                    state.notify(
                        str(Res.string.the_style_x_is_already_on_this_widget, clean),
                        com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.INFO,
                    )
                } else {
                    change { it.copy(name = clean) }
                }
            }
        }
        LabelRow("Font", stringResource(Res.string.the_path_to_the_font_without_the)) {
            CommitField(style.font, Modifier.weight(1f), mono = true) { v -> change { it.copy(font = v.trim()) } }
        }
        LabelRow("ImageSet", stringResource(Res.string.the_set_of_images_that_all_the_images_of)) {
            val options = (setNames + style.imageSet).filter { it.isNotEmpty() }.distinct()
            if (options.isEmpty()) {
                CommitField(style.imageSet, Modifier.weight(1f)) { v -> change { it.copy(imageSet = v.trim()) } }
            } else {
                DropdownField(
                    style.imageSet,
                    options,
                    { it.ifEmpty { "—" } },
                    Modifier.weight(1f),
                ) { v -> change { it.copy(imageSet = v) } }
            }
        }
        LabelRow("Color", stringResource(Res.string.the_colour_of_the_style_as_aarrggbb)) {
            Box(Modifier.size(18.dp).background(styleColorOf(style.color)).border(1.dp, DarkenedBorder))
            Spacer(Modifier.width(6.dp))
            CommitField(styleColorHex(style.color), Modifier.weight(1f), mono = true) { v ->
                val decimal = styleColorFromHex(v)
                if (decimal == null) {
                    state.notify(
                        str(Res.string.the_colour_is_written_as_aarrggbb_for),
                        com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.INFO,
                    )
                } else {
                    change { it.copy(color = decimal) }
                }
            }
        }

        PanelHeader(stringResource(Res.string.states), true, {})
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            style.states.forEachIndexed { i, st ->
                DarkToolButton(
                    st.name,
                    { doc.stateIndex = i },
                    active = i == stateIndex,
                    primary = true,
                    tooltip = stringResource(Res.string.show_the_state_x, st.name),
                )
            }
            val missing = STYLE_STATES.filter { name -> style.states.none { it.name == name } }
            if (missing.isNotEmpty()) {
                DropdownField(stringResource(Res.string.state), missing, { it }, tooltip = stringResource(Res.string.add_a_state)) { name ->
                    change { it.withAddedState(name) }
                    doc.stateIndex = doc.selectedStyle?.states?.lastIndex ?: 0
                }
            }
            if (style.states.size > 1) {
                DarkIconButton(AppIcon.UI_TRASH, stringResource(Res.string.delete_this_state), onClick = {
                    change { it.withoutState(stateIndex) }
                    doc.stateIndex = 0
                })
            }
        }
        val current = style.states.getOrNull(stateIndex)
        val slotSets = remember(style.imageSet, state.workDrivePath) { slotSetsOf(state, style.imageSet, doc.path) }
        if (current != null && slotSets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.from_the_set), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(86.dp))
                DropdownField(
                    SlotSet(stringResource(Res.string.fill_slots_by_names), emptyMap()),
                    slotSets,
                    { if (it.images.isEmpty()) it.label else "${it.label}  (${it.images.size})" },
                    Modifier.weight(1f),
                    tooltip = stringResource(Res.string.images_named_like_frame_lefttop_top),
                ) { set ->
                    change { st -> set.images.entries.fold(st) { acc, (slot, image) -> acc.withItem(stateIndex, slot, image) } }
                }
            }
        }
        if (current != null) {
            val slots = (STYLE_SLOTS.filter { slot -> current.items.any { it.name == slot } } + current.items.map { it.name }).distinct()
            for (slot in slots) {
                val image = current.items.first { it.name == slot }.image
                ItemRow(
                    style.imageSet,
                    slot,
                    image,
                    imageNames,
                    onPick = {
                        onPickImage(
                            PickRequest(
                                slot,
                                image,
                                apply = { v -> change { it.withItem(stateIndex, slot, v) } },
                                applySet = { set ->
                                    change { st -> set.images.entries.fold(st) { acc, (s, name) -> acc.withItem(stateIndex, s, name) } }
                                },
                            ),
                        )
                    },
                ) {
                    change { it.withoutItem(stateIndex, slot) }
                }
            }
            val free = STYLE_SLOTS.filter { slot -> current.items.none { it.name == slot } }
            if (free.isNotEmpty()) {
                Row(Modifier.padding(8.dp)) {
                    DropdownField(
                        stringResource(Res.string.slot),
                        free,
                        { it },
                        tooltip = stringResource(Res.string.add_a_slot_lefttop_top_center),
                    ) { slot ->
                        change { it.withItem(stateIndex, slot, "") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    setName: String,
    slot: String,
    image: String,
    imageNames: List<String>,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val known = image.isEmpty() || image in imageNames || imageNames.isEmpty()
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(slot, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(86.dp), maxLines = 1)
        // The image is chosen on the atlas, not typed: the field opens the picker
        Box(Modifier.weight(1f)) {
            Tip(stringResource(Res.string.pick_an_image_on_the_atlas_from_the_list)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(DarkenedBorder)
                        .border(1.dp, if (known) Color(0xFF3A3C40) else Color(0xFFFF5555))
                        .clickable(remember { MutableInteractionSource() }, null, enabled = setName.isNotEmpty(), onClick = onPick)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        image.ifEmpty { stringResource(Res.string.no_image) },
                        color = if (image.isEmpty()) GhostlyText else TextForeground,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DarkIconButton(
            AppIcon.UI_SEARCH,
            stringResource(Res.string.pick_an_image_on_the_atlas),
            onClick = onPick,
            enabled = setName.isNotEmpty(),
        )
        DarkIconButton(AppIcon.UI_TRASH, stringResource(Res.string.remove_the_slot), onClick = onRemove)
        if (!known) AppIconView(AppIcon.UI_WARN, size = 14.dp)
    }
}

/** The look of one state at a fixed size over a chequered backdrop, drawn like the canvas draws a panel with that style. */
@Composable
private fun StylePreview(
    state: LayoutEditorState,
    doc: StylesDocument,
    style: StyleDef,
    stateIndex: Int,
) {
    // A text style has no states at all: the preview shows it as Normal
    val tabState = style.states.getOrNull(stateIndex)?.name ?: "Normal"
    val widgetName =
        doc.sheet.widgets
            .getOrNull(doc.widget)
            ?.widget
            .orEmpty()
    val type = WidgetType.entries.firstOrNull { it.className.removeSuffix("Class") == widgetName }
    val progress = type == WidgetType.PROGRESS_BAR || widgetName == "SimpleProgressBarWidget"
    val textual = type in TEXT_TYPES || widgetName == "HtmlWidget"
    val interactive = type == WidgetType.BUTTON || type == WidgetType.CHECKBOX || type == WidgetType.XCOMBO_BOX
    val measurer = rememberTextMeasurer()

    // The pointer over the preview does what it does over the widget in Live Preview: hover is Highlight, a held button Pushed
    var hover by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val has = { name: String -> style.states.any { it.name == name } }
    val stateName =
        when {
            tabState != "Normal" || !interactive -> tabState
            pressed && hover && has("Pushed") -> "Pushed"
            hover && has("Highlight") -> "Highlight"
            else -> tabState
        }
    val skin = state.previewSkin(style, stateName, doc.path)
    val tint = skin?.styleColor?.takeIf { it.alpha > 0f } ?: styleColorOf(style.color).takeIf { it.alpha > 0f } ?: Color.White

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, DarkenedBorder)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Enter, PointerEventType.Move -> {
                                    hover = true
                                }

                                PointerEventType.Exit -> {
                                    hover = false
                                    pressed = false
                                }

                                PointerEventType.Press -> {
                                    pressed = true
                                }

                                PointerEventType.Release -> {
                                    pressed = false
                                }

                                else -> {}
                            }
                        }
                    }
                },
        ) {
            val cell = 10f
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var col = row % 2
                while (x < size.width) {
                    drawRect(
                        if (col % 2 == 0) Color(0xFF8A8A8A) else Color(0xFF777777),
                        Offset(x, y),
                        Size(minOf(cell, size.width - x), minOf(cell, size.height - y)),
                    )
                    x += cell
                    col++
                }
                y += cell
                row++
            }
            val box = Rect(size.width * 0.2f, size.height * 0.2f, size.width * 0.8f, size.height * 0.8f)
            if (skin != null) drawSkinWidget(skin, tint, box, 1f, type, progress)
            // A style with a font carries text: the sample shows its colour and the shape of the box it sits in
            if (textual || (style.font.isNotEmpty() && skin != null && type != WidgetType.PROGRESS_BAR && type != WidgetType.SLIDER)) {
                val sample = if (type == WidgetType.MULTILINE_TEXT || type == WidgetType.RICH_TEXT) SAMPLE_LONG else SAMPLE
                val laidOut =
                    measurer.measure(
                        sample,
                        TextStyle(
                            color = tint,
                            fontSize = 18.sp,
                            // The names of the vanilla fonts say the weight (MetronBook-Bold, MetronBook-Light)
                            fontWeight =
                                when {
                                    style.font.contains("Bold", ignoreCase = true) -> androidx.compose.ui.text.font.FontWeight.Bold
                                    style.font.contains("Light", ignoreCase = true) -> androidx.compose.ui.text.font.FontWeight.Light
                                    else -> androidx.compose.ui.text.font.FontWeight.Normal
                                },
                        ),
                        constraints =
                            androidx.compose.ui.unit
                                .Constraints(maxWidth = box.width.toInt().coerceAtLeast(1)),
                    )
                val at =
                    if (textual) Offset(box.left, box.top) else Offset(box.left + 8f, box.center.y - laidOut.size.height / 2f)
                drawText(laidOut, topLeft = at)
            }
        }
        val hidden =
            skin
                ?.source
                ?.keys
                .orEmpty()
                .filter { it !in PREVIEW_SLOTS }
                .sorted()
        val note =
            when {
                textual -> {
                    "Пример текста: цвет стиля ${styleColorHex(style.color)}, шрифт ${style.font.ifEmpty { "по умолчанию" }} " +
                        "(в превью системный шрифт, у Workbench свой)"
                }

                skin == null -> {
                    stringResource(Res.string.no_images_for_the_preview_the_slots_are)
                }

                hidden.isNotEmpty() -> {
                    stringResource(Res.string.the_preview_is_not_shown_x, hidden.joinToString(", "))
                }

                interactive -> {
                    stringResource(Res.string.move_the_cursor_over_the_preview)
                }

                else -> {
                    null
                }
            }
        if (note != null) Text(note, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private val TEXT_TYPES = setOf(WidgetType.TEXT, WidgetType.MULTILINE_TEXT, WidgetType.RICH_TEXT)
private const val SAMPLE = "Пример текста Sample 0123"
private const val SAMPLE_LONG = "Пример текста в несколько строк.\nSample text on several lines, 0123456789."

/** The groups of images in the imageset [setName] that are named after the slots of a style. */
private fun slotSetsOf(
    state: LayoutEditorState,
    setName: String,
    nearPath: String,
): List<SlotSet> {
    val set = state.imageSetOf(setName, nearPath) ?: return emptyList()
    return findSlotSets(set.all.map { it.name }, set.groups.associate { g -> g.name to g.images.map { it.name } })
}

/** What the picker needs to know: the slot, its image now, and what to do with an image or a set of images chosen. */
private class PickRequest(
    val slot: String,
    val image: String,
    val apply: (String) -> Unit,
    val applySet: (SlotSet) -> Unit,
)
