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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.StylesDocument
import com.github.demandss.dayz_workbench_tool.layout_editor.state.styleColorOf
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.drawSkinTinted
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.CommitField
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.LabelRow
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.PanelHeader
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.DropdownField
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
                    Text("Выберите стиль слева", color = GhostlyText, fontSize = 12.sp)
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
        DarkIconButton(AppIcon.UI_SAVE, "Сохранить (Ctrl+S)", onClick = { state.saveActiveStyles() }, enabled = doc.isModified)
        DarkIconButton(AppIcon.UI_UNDO, "Отменить (Ctrl+Z)", onClick = { doc.undo() }, enabled = doc.canUndo)
        DarkIconButton(AppIcon.UI_REDO, "Повторить (Ctrl+Y)", onClick = { doc.redo() }, enabled = doc.canRedo)
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
            DropdownField("+ Тип", unused, { it }, tooltip = "Добавить тип виджета, для которого будут стили") { name ->
                doc.edit { it.copy(widgets = it.widgets + WidgetStyles(name, emptyList())) }
                doc.select(doc.sheet.widgets.lastIndex, -1)
            }
        }
        DarkToolButton(
            "+ Стиль",
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
            tooltip = "Добавить стиль к выбранному типу виджета",
        )
        DarkIconButton(
            AppIcon.UI_COPY,
            "Дублировать стиль",
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
            "Удалить стиль",
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
                    if (query.isEmpty()) Text("Поиск стиля…", color = GhostlyText, fontSize = 11.sp)
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

        PanelHeader("Стиль", true, {})
        LabelRow("Name", "Имя стиля: его пишут в style у виджета") {
            CommitField(style.name, Modifier.weight(1f)) { v ->
                val clean = v.trim()
                val taken =
                    doc.sheet.widgets[w]
                        .styles
                        .withIndex()
                        .any { it.index != s && it.value.name == clean }
                if (clean.isEmpty()) {
                    state.notify(
                        "Имя стиля не может быть пустым",
                        com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.INFO,
                    )
                } else if (taken) {
                    state.notify(
                        "Стиль «$clean» уже есть у этого виджета",
                        com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.INFO,
                    )
                } else {
                    change { it.copy(name = clean) }
                }
            }
        }
        LabelRow("Font", "Путь к шрифту без расширения, например gui/fonts/MetronBook; пусто — шрифт по умолчанию") {
            CommitField(style.font, Modifier.weight(1f), mono = true) { v -> change { it.copy(font = v.trim()) } }
        }
        LabelRow("ImageSet", "Набор картинок, из которого берутся все картинки стиля") {
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
        LabelRow("Color", "Цвет стиля AARRGGBB. FFFFFFFF — без изменений, 00000000 — прозрачный, цвет виджета его заменяет") {
            Box(Modifier.size(18.dp).background(styleColorOf(style.color)).border(1.dp, DarkenedBorder))
            Spacer(Modifier.width(6.dp))
            CommitField(styleColorHex(style.color), Modifier.weight(1f), mono = true) { v ->
                val decimal = styleColorFromHex(v)
                if (decimal == null) {
                    state.notify(
                        "Цвет пишется как AARRGGBB, например FFFFFFFF",
                        com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind.INFO,
                    )
                } else {
                    change { it.copy(color = decimal) }
                }
            }
        }

        PanelHeader("Состояния", true, {})
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
                    tooltip = "Показать состояние ${st.name}",
                )
            }
            val missing = STYLE_STATES.filter { name -> style.states.none { it.name == name } }
            if (missing.isNotEmpty()) {
                DropdownField("+ состояние", missing, { it }, tooltip = "Добавить состояние") { name ->
                    change { it.withAddedState(name) }
                    doc.stateIndex = doc.selectedStyle?.states?.lastIndex ?: 0
                }
            }
            if (style.states.size > 1) {
                DarkIconButton(AppIcon.UI_TRASH, "Удалить это состояние", onClick = {
                    change { it.withoutState(stateIndex) }
                    doc.stateIndex = 0
                })
            }
        }
        val current = style.states.getOrNull(stateIndex)
        val slotSets = remember(style.imageSet, state.workDrivePath) { slotSetsOf(state, style.imageSet, doc.path) }
        if (current != null && slotSets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Из набора", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(86.dp))
                DropdownField(
                    SlotSet("Заполнить слоты по именам…", emptyMap()),
                    slotSets,
                    { if (it.images.isEmpty()) it.label else "${it.label}  (${it.images.size})" },
                    Modifier.weight(1f),
                    tooltip = "Картинки с именами вида Frame + LeftTop, Top, Center… раскладываются по слотам этого состояния",
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
                    DropdownField("+ слот", free, { it }, tooltip = "Добавить слот: LeftTop, Top, Center…") { slot ->
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
            Tip("Выбрать картинку на атласе, из списка или набором для всех слотов") {
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
                        image.ifEmpty { "— нет картинки —" },
                        color = if (image.isEmpty()) GhostlyText else TextForeground,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DarkIconButton(AppIcon.UI_SEARCH, "Выбрать картинку на атласе", onClick = onPick, enabled = setName.isNotEmpty())
        DarkIconButton(AppIcon.UI_TRASH, "Убрать слот", onClick = onRemove)
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
    val stateName = style.states.getOrNull(stateIndex)?.name ?: return
    val skin = state.previewSkin(style, stateName, doc.path)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp).border(1.dp, DarkenedBorder)) {
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
            if (skin != null) drawStylePreview(skin, skin.styleColor.takeIf { it.alpha > 0f } ?: Color.White)
        }
        val hidden =
            skin
                ?.source
                ?.keys
                .orEmpty()
                .filter { it !in PREVIEW_SLOTS }
                .sorted()
        if (skin == null) {
            Text(
                "Нет картинок для превью: слоты пусты или набор не найден",
                color = GhostlyText,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (hidden.isNotEmpty()) {
            Text(
                "Не показано превью: ${hidden.joinToString(", ")}",
                color = GhostlyText,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

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
