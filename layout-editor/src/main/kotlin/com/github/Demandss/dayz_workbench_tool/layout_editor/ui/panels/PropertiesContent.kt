package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.convertUnit
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.documentColors
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.formatNumber
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.parseWidgetColor
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.sanitizeWidgetName
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.toFileColor
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.widgetIcon
import com.github.demandss.dayz_workbench_tool.ui.pickFile
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.collections.iterator

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private val ROW_H = 22.dp
private val LABEL_W = 80.dp

private val HALIGN_GRID = listOf("left", "center_ref", "right_ref")
private val VALIGN_GRID = listOf("top", "center_ref", "bottom_ref")

private val FIXASPECT_OPTS = listOf("", "fixwidth", "fixheight")
private val MODE_OPTS = listOf("", "blend", "add", "multiply", "color", "alpha", "overlay")
private val CLAMP_OPTS = listOf("", "clamp", "wrap", "mirror")
private val STRETCH_OPTS = listOf("", "tile", "stretch")

private val WIDGET_KEYS =
    setOf(
        "position",
        "size",
        "hexactpos",
        "vexactpos",
        "hexactsize",
        "vexactsize",
        "halign",
        "valign",
        "visible",
        "disabled",
        "clipchildren",
        "inheritalpha",
        "ignorepointer",
        "ignoregloballv",
        "keepsafezone",
        "color",
        "scaled",
        "fixaspect",
        "priority",
        "userID",
        "draggable",
        "scriptclass",
        "style",
    )

private val IMAGE_KEYS =
    setOf(
        "image0",
        "imageTexture",
        "mode",
        "src alpha",
        "clamp mode",
        "stretch mode",
        "flip u",
        "flip v",
        "filter",
        "nocache",
        "rotation",
        "pivot",
        "mask",
        "transition width",
        "progress",
    )

private val IMAGE_TYPES = setOf(WidgetType.IMAGE, WidgetType.VIDEO)

/** What each property does, shown when the pointer rests on its name. */
private val PROPERTY_HINTS =
    mapOf(
        "visible" to "Вкл: показывает виджет.\nВыкл: скрывает виджет.",
        "disabled" to "Вкл: виджет не отвечает на ввод, но виден.\nВыкл: виджет работает как обычно.",
        "clipchildren" to "Вкл: дети обрезаются по границам виджета.\nВыкл: дети могут торчать наружу.",
        "inheritalpha" to "Вкл: прозрачность родителя действует и на этот виджет.\nВыкл: прозрачность родителя не влияет.",
        "ignorepointer" to "Вкл: мышь проходит сквозь виджет к тому, что под ним.\nВыкл: виджет принимает мышь.",
        "ignoregloballv" to
            "Вкл: флаг «Ignore global LV» из инспектора Workbench; что именно он делает, не проверялось.\nВыкл: флаг не задан.",
        "keepsafezone" to
            "Вкл: виджет учитывает безопасную зону экрана (рамка overscan на телевизорах).\nВыкл: безопасная зона не учитывается.",
        "color" to "Цвета в файле линейные; Workbench показывает их в sRGB. Клик по образцу открывает палитру",
        "scaled" to
            "Вкл: размеры пересчитываются под относительное разрешение (в редакторе не учитывается).\nВыкл: размеры не пересчитываются.",
        "fixaspect" to "Держит пропорции виджета с относительным размером: fixwidth берёт ширину из высоты, fixheight наоборот",
        "priority" to "Порядок отрисовки среди соседей: больше значит выше",
        "userID" to "Числовой идентификатор виджета, который скрипт читает через GetUserID()",
        "draggable" to "Вкл: виджет можно перетаскивать мышью.\nВыкл: виджет не перетаскивается.",
        "scriptclass" to "Класс скрипта, привязанный к виджету",
        "style" to "Стиль из .styles: рисует фон, рамку и состояния виджета. Список — стили игры для этого типа виджета",
        "image0" to "Картинка: путь к текстуре или запись вида set:имя image:имя из imageset",
        "imageTexture" to "Файл текстуры, который рисует виджет. Кнопка «..» выбирает файл",
        "mode" to "Как картинка смешивается с тем, что под ней: blend, add, multiply, color, alpha, overlay",
        "src alpha" to "Вкл: прозрачность берётся из альфа-канала картинки.\nВыкл: альфа-канал картинки не используется.",
        "clamp mode" to "Что за краем текстуры: clamp тянет край, wrap повторяет, mirror отражает",
        "stretch mode" to "Как картинка заполняет виджет: tile повторяет, stretch растягивает",
        "flip u" to "Вкл: картинка отражена по горизонтали.\nВыкл: картинка не отражена.",
        "flip v" to "Вкл: картинка отражена по вертикали.\nВыкл: картинка не отражена.",
        "filter" to "Вкл: картинка сглаживается при масштабировании.\nВыкл: сглаживание выключено.",
        "nocache" to "Вкл: текстура не держится в кэше.\nВыкл: текстура кэшируется.",
        "rotation" to "Поворот картинки в градусах",
        "pivot" to "Точка, вокруг которой картинка поворачивается, от 0 до 1 по каждой оси",
        "pivot x" to "Точка поворота по горизонтали, от 0 до 1",
        "pivot y" to "Точка поворота по вертикали, от 0 до 1",
        "Mask" to "Текстура маски, через которую картинка проявляется",
        "mask" to "Текстура маски, через которую картинка проявляется",
        "Transition width" to "Ширина мягкого края маски, от 0 до 1",
        "transition width" to "Ширина мягкого края маски, от 0 до 1",
        "Progress" to "Сколько картинки уже проявилось через маску, от 0 до 1",
        "progress" to "Сколько картинки уже проявилось через маску, от 0 до 1",
        "X" to "Положение по горизонтали: пиксели или доля родителя, как выбран переключатель",
        "Y" to "Положение по вертикали: пиксели или доля родителя, как выбран переключатель",
        "W" to "Ширина: пиксели или доля родителя, как выбран переключатель",
        "H" to "Высота: пиксели или доля родителя, как выбран переключатель",
    )

private const val UNKNOWN_PROPERTY_HINT = "Свойство из файла, которого нет в списке известных редактору"

// ---------------------------------------------------------------------------
// Commit helpers
// ---------------------------------------------------------------------------

private fun commitName(
    state: LayoutEditorState,
    id: UUID,
    name: String,
) {
    val idx = state.activeFileIndex
    val doc = state.openFiles.getOrNull(idx) ?: return
    // Script finds a widget by its name, so the name is kept to Latin letters, digits and underscores
    val clean = sanitizeWidgetName(name)
    if (clean != name.trim()) state.notify("Имя виджета записано латиницей: $clean")
    state.pushUndo(idx, doc.root, "Переименование в $clean")
    state.updateActiveRoot(doc.root.updateNode(id) { it.copy(node = it.node.copy(name = clean)) })
}

private fun commitType(
    state: LayoutEditorState,
    id: UUID,
    type: WidgetType,
) {
    val idx = state.activeFileIndex
    val doc = state.openFiles.getOrNull(idx) ?: return
    state.pushUndo(idx, doc.root, "Тип ${type.className.removeSuffix("WidgetClass")}")
    state.updateActiveRoot(doc.root.updateNode(id) { it.copy(node = it.node.copy(type = type, originalClassName = type.className)) })
}

private fun commitProps(
    state: LayoutEditorState,
    id: UUID,
    values: Map<String, String>,
    label: String = values.keys.joinToString(", "),
) {
    val idx = state.activeFileIndex
    val doc = state.openFiles.getOrNull(idx) ?: return
    state.pushUndo(idx, doc.root, label)
    state.updateActiveRoot(
        doc.root.updateNode(id) { n ->
            n.copy(node = n.node.copy(properties = LinkedHashMap(n.node.properties).also { it.putAll(values) }))
        },
    )
}

/** Flips pixels and share of the parent for one axis of position or size and recalculates the number, so the widget stays where it is. */
private fun switchUnit(
    state: LayoutEditorState,
    id: UUID,
    props: Map<String, String>,
    key: String,
    axis: Int,
    flag: String,
    toPixels: Boolean,
) {
    val doc = state.activeFile ?: return
    val screen = screenRect(state)
    val parent = layoutTree(doc.root, screen).parentRects[id] ?: screen
    val parts = (props[key] ?: "0 0").trim().split(Regex("\\s+")).toMutableList()
    while (parts.size < 2) parts.add("0")
    val value = parts[axis].toFloatOrNull() ?: 0f
    parts[axis] = formatNumber(convertUnit(value, if (axis == 0) parent.width else parent.height, toPixels))
    commitProps(
        state,
        id,
        mapOf(key to "${parts[0]} ${parts[1]}", flag to if (toPixels) "1" else "0"),
        "$key: ${if (toPixels) "px" else "%"}",
    )
}

private fun commitProp(
    state: LayoutEditorState,
    id: UUID,
    key: String,
    value: String,
) {
    val idx = state.activeFileIndex
    val doc = state.openFiles.getOrNull(idx) ?: return
    state.pushUndo(idx, doc.root, "$key = $value")
    state.updateActiveRoot(
        doc.root.updateNode(id) { n ->
            n.copy(node = n.node.copy(properties = LinkedHashMap(n.node.properties).also { it[key] = value }))
        },
    )
}

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

@Composable
fun PropertiesContent(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val selectedId = state.selectedIds.firstOrNull()
    val doc = state.activeFile
    val node =
        remember(selectedId, doc?.root) {
            if (selectedId != null && doc != null) doc.root.findNode(selectedId) else null
        }

    Column(modifier.background(FrameBackground)) {
        if (node == null) {
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Ничего не выбрано", color = GhostlyText, fontSize = 11.sp)
            }
            return@Column
        }

        val props = node.node.properties
        val commit: (String, String) -> Unit = { key, value -> commitProp(state, node.id, key, value) }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            PropertiesHeader(state, node.id, node.node.name, node.node.type, props, commit)
            WidgetSection(
                props,
                commit,
                remember(doc?.root) {
                    doc?.root?.let { documentColors(it) }.orEmpty()
                },
                StyleChoice(state, node.node.type, doc?.path),
            )
            if (node.node.type in IMAGE_TYPES) ImageWidgetSection(props, commit, state.workDrivePath, state, node, doc?.path)
            val extra = props.filter { it.key !in WIDGET_KEYS && it.key !in IMAGE_KEYS }
            if (extra.isNotEmpty()) RawSection(extra, commit)
        }
    }
}

// ---------------------------------------------------------------------------
// Name, type and Transform
// ---------------------------------------------------------------------------

@Composable
private fun PropertiesHeader(
    state: LayoutEditorState,
    id: UUID,
    name: String,
    type: WidgetType,
    props: Map<String, String>,
    commit: (String, String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIconView(widgetIcon(type), size = 18.dp)
        StringField(name, Modifier.weight(1f), focusTick = state.focusNameTick) { commitName(state, id, it) }
        TypeDropdown(type) { commitType(state, id, it) }
    }

    var open by remember { mutableStateOf(true) }
    SectionHeader("Transform", open) { open = !open }
    if (!open) return

    val hexactpos = props["hexactpos"] == "1"
    val vexactpos = props["vexactpos"] == "1"
    val hexactsize = props["hexactsize"] == "1"
    val vexactsize = props["vexactsize"] == "1"

    val posParts = (props["position"] ?: "0 0").trim().split(Regex("\\s+"))
    val szParts = (props["size"] ?: "0 0").trim().split(Regex("\\s+"))
    var px by remember(props["position"]) { mutableStateOf(posParts.getOrNull(0) ?: "0") }
    var py by remember(props["position"]) { mutableStateOf(posParts.getOrNull(1) ?: "0") }
    var sw by remember(props["size"]) { mutableStateOf(szParts.getOrNull(0) ?: "0") }
    var sh by remember(props["size"]) { mutableStateOf(szParts.getOrNull(1) ?: "0") }

    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("X", px, hexactpos, Modifier.weight(1f), { switchUnit(state, id, props, "position", 0, "hexactpos", it) }) { v ->
                px = v
                commit("position", "$v $py")
            }
            NumberField("Y", py, vexactpos, Modifier.weight(1f), { switchUnit(state, id, props, "position", 1, "vexactpos", it) }) { v ->
                py = v
                commit("position", "$px $v")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("W", sw, hexactsize, Modifier.weight(1f), { switchUnit(state, id, props, "size", 0, "hexactsize", it) }) { v ->
                sw = v
                commit("size", "$v $sh")
            }
            NumberField("H", sh, vexactsize, Modifier.weight(1f), { switchUnit(state, id, props, "size", 1, "vexactsize", it) }) { v ->
                sh = v
                commit("size", "$sw $v")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlignmentGrid(props["halign"].orEmpty(), props["valign"].orEmpty()) { h, v ->
                state.reanchor(id, h, v)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AlignText("halign", props["halign"])
                AlignText("valign", props["valign"])
            }
        }

        if (props["halign"] == "center" || props["valign"] == "center") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3A3220))
                    .border(1.dp, Color(0xFF6B5A2B))
                    .padding(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AppIconView(AppIcon.UI_WARN, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "В файле стоит голый center. Такой виджет остаётся у начального края. Нужен center_ref.",
                    color = Color(0xFFF0D9A0),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun AlignText(
    key: String,
    value: String?,
) {
    val shown =
        when (value) {
            null, "", "left", "top" -> "— (${value?.ifEmpty { null } ?: if (key == "halign") "left" else "top"})"
            "center" -> "center (голый)"
            else -> value
        }
    Row {
        Text("$key ", color = GhostlyText, fontSize = 11.sp)
        Text(shown, color = TextForeground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// AlignmentGrid 3×3
// ---------------------------------------------------------------------------

@Composable
private fun AlignmentGrid(
    halign: String,
    valign: String,
    onSet: (String, String) -> Unit,
) {
    val h0 = halign.ifEmpty { "left" }
    val v0 = valign.ifEmpty { "top" }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (v in VALIGN_GRID) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (h in HALIGN_GRID) {
                    val on = h0 == h && v0 == v
                    Tip("halign $h, valign $v") {
                        Box(
                            Modifier
                                .size(18.dp)
                                .background(if (on) SubSelectColor else DarkenedBorder)
                                .border(1.dp, if (on) SubSelectColor else Color(0xFF3A3C40))
                                .clickable(remember { MutableInteractionSource() }, null) { onSet(h, v) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(4.dp).background(if (on) Color.White else Color(0xFF8A8F96)))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Widget section
// ---------------------------------------------------------------------------

@Composable
private fun WidgetSection(
    props: Map<String, String>,
    commit: (String, String) -> Unit,
    documentColors: List<Color>,
    styles: StyleChoice,
) {
    var expanded by remember { mutableStateOf(true) }
    SectionHeader("Widget", expanded) { expanded = !expanded }
    if (!expanded) return

    CheckRow("visible", props["visible"], commit, default = true)
    CheckRow("disabled", props["disabled"], commit)
    CheckRow("clipchildren", props["clipchildren"], commit, default = true)
    CheckRow("inheritalpha", props["inheritalpha"], commit)
    CheckRow("ignorepointer", props["ignorepointer"], commit)
    CheckRow("ignoregloballv", props["ignoregloballv"], commit)
    CheckRow("keepsafezone", props["keepsafezone"], commit)

    ColorRows(props, commit, documentColors)

    CheckRow("scaled", props["scaled"], commit)
    PropRow(
        "fixaspect",
        "fixaspect" in props,
        "Держит пропорции виджета с относительным размером: fixwidth берёт ширину из высоты, fixheight наоборот",
    ) {
        CompactDropdown(props["fixaspect"] ?: "", FIXASPECT_OPTS) { commit("fixaspect", it) }
    }
    PropRow("priority", "priority" in props, "Порядок отрисовки среди соседей: больше значит выше") {
        StringField(props["priority"] ?: "", Modifier.weight(1f)) { commit("priority", it) }
    }
    PropRow("userID", "userID" in props) {
        StringField(props["userID"] ?: "", Modifier.weight(1f)) { commit("userID", it) }
    }
    CheckRow("draggable", props["draggable"], commit)
    PropRow("scriptclass", "scriptclass" in props) {
        StringField(props["scriptclass"] ?: "", Modifier.weight(1f)) { commit("scriptclass", it) }
    }
    PropRow("style", "style" in props) {
        val names = styles.names
        val current = props["style"]?.trim('"').orEmpty()
        if (names.isEmpty()) {
            StringField(current, Modifier.weight(1f)) { commit("style", it) }
        } else {
            // The list holds the styles of the game for this widget; a style from elsewhere stays in the list as it is
            val options = (listOf("") + names + listOfNotNull(current.takeIf { it.isNotEmpty() && it !in names })).distinct()
            DropdownField(
                current,
                options,
                {
                    if (it.isEmpty()) {
                        "— (нет)"
                    } else if (it !in names) {
                        "$it (свой)"
                    } else {
                        it
                    }
                },
                Modifier.weight(1f),
                tooltip = "Стиль виджета",
            ) { commit("style", it) }
        }
    }
}

// ---------------------------------------------------------------------------
// ImageWidget section
// ---------------------------------------------------------------------------

@Composable
private fun ImageWidgetSection(
    props: Map<String, String>,
    commit: (String, String) -> Unit,
    workDrivePath: String,
    state: LayoutEditorState,
    node: EditorWidgetNode,
    nearPath: String?,
) {
    var expanded by remember { mutableStateOf(true) }
    SectionHeader("ImageWidget", expanded) { expanded = !expanded }
    if (!expanded) return

    val scope = rememberCoroutineScope()
    val basePath = workDrivePath.ifBlank { null }

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PropLabel("image0", "image0" in props, null, Modifier.width(LABEL_W))
        StringField(props["image0"] ?: "", Modifier.weight(1f)) { commit("image0", it) }
        SmallButton("..") { scope.launch { pickFile("Выбрать текстуру", "*", basePath)?.let { commit("image0", it) } } }
    }
    HLine()

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PropLabel("imageTexture", "imageTexture" in props, null, Modifier.width(LABEL_W))
        StringField(props["imageTexture"] ?: "", Modifier.weight(1f)) { commit("imageTexture", it) }
        Spacer(Modifier.width(2.dp))
        SmallButton("..") { scope.launch { pickFile("Выбрать текстуру", "*", basePath)?.let { commit("imageTexture", it) } } }
    }
    HLine()
    ImagePreview(state.pictureFor(node, nearPath), (props["image0"] ?: props["imageTexture"]).orEmpty().isNotBlank())

    PropRow("mode", "mode" in props) { CompactDropdown(props["mode"] ?: "", MODE_OPTS) { commit("mode", it) } }
    CheckRow("src alpha", props["src alpha"], commit)
    PropRow("clamp mode", "clamp mode" in props) { CompactDropdown(props["clamp mode"] ?: "", CLAMP_OPTS) { commit("clamp mode", it) } }
    PropRow("stretch mode", "stretch mode" in props) {
        CompactDropdown(props["stretch mode"] ?: "", STRETCH_OPTS) { commit("stretch mode", it) }
    }
    CheckRow("flip u", props["flip u"], commit)
    CheckRow("flip v", props["flip v"], commit)
    CheckRow("filter", props["filter"], commit)
    CheckRow("nocache", props["nocache"], commit)

    PropRow("rotation", "rotation" in props) { StringField(props["rotation"] ?: "", Modifier.weight(1f)) { commit("rotation", it) } }

    val pivotParts = (props["pivot"] ?: "0 0").trim().split(Regex("\\s+"))
    var pivotX by remember(props["pivot"]) { mutableStateOf(pivotParts.getOrNull(0) ?: "0") }
    var pivotY by remember(props["pivot"]) { mutableStateOf(pivotParts.getOrNull(1) ?: "0") }
    SubRow("  pivot x") {
        FloatInput("", pivotX, Modifier.weight(1f)) { v ->
            pivotX = v
            commit("pivot", "$v $pivotY")
        }
    }
    SubRow("  pivot y") {
        FloatInput("", pivotY, Modifier.weight(1f)) { v ->
            pivotY = v
            commit("pivot", "$pivotX $v")
        }
    }

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PropLabel("Mask", "mask" in props, null, Modifier.width(LABEL_W))
        Spacer(Modifier.weight(1f))
        SmallButton("..") { scope.launch { pickFile("Выбрать маску", "*", basePath)?.let { commit("mask", it) } } }
    }
    HLine()

    SliderRow("Transition width", props["transition width"], 0f, 1f) { commit("transition width", it) }
    SliderRow("Progress", props["progress"], 0f, 1f) { commit("progress", it) }
}

/** A small look at the picture of an image widget, on a chequered ground so that a transparent one can be seen. */
@Composable
private fun ImagePreview(
    picture: androidx.compose.ui.graphics.ImageBitmap?,
    named: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(LABEL_W))
        androidx.compose.foundation.Canvas(
            Modifier
                .size(72.dp)
                .border(1.dp, Color(0xFF3A3C40)),
        ) {
            val cell = 8.dp.toPx()
            val cols = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    drawRect(
                        if ((x + y) % 2 == 0) Color(0xFF3A3A3A) else Color(0xFF2C2C2C),
                        Offset(x * cell, y * cell),
                        Size(cell, cell),
                    )
                }
            }
            if (picture != null) {
                val scale = minOf(size.width / picture.width, size.height / picture.height)
                val w = (picture.width * scale).toInt().coerceAtLeast(1)
                val h = (picture.height * scale).toInt().coerceAtLeast(1)
                drawImage(
                    picture,
                    srcSize =
                        androidx.compose.ui.unit
                            .IntSize(picture.width, picture.height),
                    dstOffset =
                        androidx.compose.ui.unit.IntOffset(
                            ((size.width - w) / 2).toInt(),
                            ((size.height - h) / 2).toInt(),
                        ),
                    dstSize =
                        androidx.compose.ui.unit
                            .IntSize(w, h),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                )
            }
        }
        Text(
            when {
                picture != null -> "${picture.width} × ${picture.height}"
                named -> "не найдена"
                else -> "нет картинки"
            },
            color = GhostlyText,
            fontSize = 10.sp,
        )
    }
    HLine()
}

// ---------------------------------------------------------------------------
// Raw extra properties
// ---------------------------------------------------------------------------

@Composable
private fun RawSection(
    props: Map<String, String>,
    commit: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    SectionHeader("Extra", expanded) { expanded = !expanded }
    if (!expanded) return
    for ((key, value) in props) {
        PropRow(key, set = true, hint = PROPERTY_HINTS[key] ?: UNKNOWN_PROPERTY_HINT) {
            StringField(value, Modifier.weight(1f)) { commit(key, it) }
        }
    }
}

// ---------------------------------------------------------------------------
// Color rows
// ---------------------------------------------------------------------------

@Composable
private fun ColorRows(
    props: Map<String, String>,
    commit: (String, String) -> Unit,
    documentColors: List<Color>,
) {
    val cv = props["color"] ?: ""
    val parts = cv.trim().split(Regex("\\s+"))
    var r by remember(cv) { mutableStateOf(parts.getOrNull(0) ?: "0") }
    var g by remember(cv) { mutableStateOf(parts.getOrNull(1) ?: "0") }
    var b by remember(cv) { mutableStateOf(parts.getOrNull(2) ?: "0") }
    var a by remember(cv) { mutableStateOf(parts.getOrNull(3) ?: "1") }

    fun save() = commit("color", "$r $g $b $a")

    val shown = remember(r, g, b, a) { parseWidgetColor("$r $g $b $a") }

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PropLabel("color", "color" in props, "Цвета в файле линейные; Workbench показывает их в sRGB", Modifier.width(LABEL_W))
        var picking by remember { mutableStateOf(false) }
        val anchorHeight = with(LocalDensity.current) { 22.dp.roundToPx() }
        Box {
            Tip("Выбрать цвет") {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(Color(0xFF2B2B30))
                        .background(shown)
                        .border(1.dp, Color(0xFF3A3C40))
                        .clickable(remember { MutableInteractionSource() }, null) { picking = true },
                )
            }
            if (picking) {
                ColorPickerPopup(
                    initial = shown,
                    anchorHeightPx = anchorHeight,
                    onDismiss = { picking = false },
                    documentColors = documentColors,
                    onCommit = { commit("color", it.toFileColor()) },
                )
            }
        }
        FloatInput("R", r, Modifier.weight(1f)) { v ->
            r = v
            save()
        }
        FloatInput("G", g, Modifier.weight(1f)) { v ->
            g = v
            save()
        }
        FloatInput("B", b, Modifier.weight(1f)) { v ->
            b = v
            save()
        }
        FloatInput("A", a, Modifier.weight(1f)) { v ->
            a = v
            save()
        }
    }
    HLine()
}

// ---------------------------------------------------------------------------
// Slider row
// ---------------------------------------------------------------------------

@Composable
private fun SliderRow(
    label: String,
    raw: String?,
    min: Float,
    max: Float,
    onCommit: (String) -> Unit,
) {
    val current = raw?.toFloatOrNull()?.coerceIn(min, max) ?: min
    var text by remember(raw) { mutableStateOf(raw ?: fmtF(min)) }
    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PropLabel(label, raw != null, null, Modifier.width(LABEL_W))
        Slider(
            value = current,
            onValueChange = { v ->
                text = fmtF(v)
                onCommit(fmtF(v))
            },
            valueRange = min..max,
            modifier = Modifier.weight(1f).height(ROW_H),
            colors = SliderDefaults.colors(thumbColor = SelectColor, activeTrackColor = SelectColor, inactiveTrackColor = DarkenedBorder),
        )
        var focused by remember { mutableStateOf(false) }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(TextForeground),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .width(
                            40.dp,
                        ).background(FrameBackground)
                        .border(1.dp, DarkenedBorder)
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                ) { inner() }
            },
            modifier =
                Modifier
                    .onFocusChanged { fs ->
                        if (focused && !fs.isFocused) onCommit(text)
                        focused = fs.isFocused
                    }.onKeyEvent { ev ->
                        if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown) {
                            onCommit(text)
                            true
                        } else {
                            false
                        }
                    },
        )
    }
    HLine()
}

// ---------------------------------------------------------------------------
// Row primitives
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
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

@Composable
private fun PropRow(
    label: String,
    set: Boolean = false,
    hint: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PropLabel(label, set, hint, Modifier.width(LABEL_W))
        content()
    }
    HLine()
}

/** A property name. Bold with a lighter colour when the file sets it; a hint shows in a tooltip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PropLabel(
    label: String,
    set: Boolean,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    val tip = hint ?: PROPERTY_HINTS[label.trim()]
    val text: @Composable () -> Unit = {
        Text(
            label.trim(),
            color = if (set) TextForeground else GhostlyText,
            fontWeight = if (set) FontWeight.SemiBold else null,
            fontSize = 10.sp,
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (tip == null) {
        text()
    } else {
        TooltipArea(
            tooltip = {
                Text(
                    tip,
                    color = TextForeground,
                    fontSize = 11.sp,
                    modifier =
                        Modifier
                            .widthIn(
                                max = 240.dp,
                            ).background(ElementsBackground)
                            .border(1.dp, DarkenedBorder)
                            .padding(8.dp, 4.dp),
                )
            },
            delayMillis = 400,
        ) { text() }
    }
}

@Composable
private fun SubRow(
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_H).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PropLabel(label, false, null, Modifier.width(LABEL_W))
        content()
    }
    HLine()
}

@Composable
private fun CheckRow(
    key: String,
    value: String?,
    commit: (String, String) -> Unit,
    hint: String? = null,
    default: Boolean = false,
) {
    val checked = value?.let { it.trim() == "1" } ?: default
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable(remember { MutableInteractionSource() }, null) { commit(key, if (checked) "0" else "1") }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(if (checked) SubSelectColor else DarkenedBorder)
                .border(1.dp, if (checked) SubSelectColor else Color(0xFF3A3C40)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
        PropLabel(key, value != null, hint, Modifier)
    }
}

@Composable
private fun HLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
}

// ---------------------------------------------------------------------------
// NumberField
// ---------------------------------------------------------------------------

/** A label, a one-line number and its own px/% switch. Arrows and the wheel step by 1, with Shift by 10. */
@Composable
private fun NumberField(
    label: String,
    value: String,
    isPx: Boolean,
    modifier: Modifier = Modifier,
    onUnit: (Boolean) -> Unit,
    onCommit: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }

    fun step(delta: Float) {
        val v = text.toFloatOrNull() ?: 0f
        val result = fmtF(v + delta)
        text = result
        onCommit(result)
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        PropLabel(label, false, null, Modifier.width(14.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle =
                TextStyle(
                    color = TextForeground,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (text.toFloatOrNull()?.let { it != 0f } == true) FontWeight.Bold else null,
                    textAlign = TextAlign.End,
                ),
            cursorBrush = SolidColor(TextForeground),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(DarkenedBorder)
                        .border(1.dp, if (focused) SubSelectColor else Color(0xFF3A3C40))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) { inner() }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.type != PointerEventType.Scroll) continue
                                val dy =
                                    event.changes
                                        .firstOrNull()
                                        ?.scrollDelta
                                        ?.y ?: 0f
                                if (dy == 0f) continue
                                val delta = if (event.keyboardModifiers.isShiftPressed) 10f else 1f
                                step(if (dy > 0f) -delta else delta)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }.onFocusChanged { fs ->
                        if (focused && !fs.isFocused && text != value) onCommit(text)
                        focused = fs.isFocused
                    }.onKeyEvent { ev ->
                        when {
                            ev.key == Key.Enter && ev.type == KeyEventType.KeyDown -> {
                                if (text != value) onCommit(text)
                                true
                            }

                            ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyDown -> {
                                step(if (ev.isShiftPressed) 10f else 1f)
                                true
                            }

                            ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyDown -> {
                                step(if (ev.isShiftPressed) -10f else -1f)
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
        )
        UnitSwitch(isPx, onUnit)
    }
}

@Composable
private fun UnitSwitch(
    isPx: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Tip(if (isPx) "Пиксели. Клик переключает на долю родителя" else "Доля родителя. Клик переключает на пиксели") {
        Box(
            Modifier
                .height(24.dp)
                .widthIn(min = 24.dp)
                .background(ElementsBackground)
                .border(1.dp, Color(0xFF3A3C40))
                .clickable(remember { MutableInteractionSource() }, null) { onChange(!isPx) }
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (isPx) "px" else "%", color = GhostlyText, fontSize = 10.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

@Composable
private fun TypeDropdown(
    current: WidgetType,
    onSelect: (WidgetType) -> Unit,
) {
    DropdownField(
        current,
        WidgetType.entries.filter {
            it != WidgetType.UNKNOWN
        },
        { it.className.removeSuffix("WidgetClass") },
        onSelect = onSelect,
    )
}

@Composable
private fun CompactDropdown(
    value: String,
    options: List<String>,
    onCommit: (String) -> Unit,
) {
    DropdownField(value, options, { it.ifEmpty { "—" } }, onSelect = onCommit)
}

@Composable
private fun SmallButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .background(ElementsBackground)
            .border(1.dp, DarkenedBorder)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = GhostlyText, fontSize = 10.sp)
    }
}

@Composable
private fun StringField(
    value: String,
    modifier: Modifier = Modifier,
    focusTick: Int = 0,
    onCommit: (String) -> Unit,
) {
    var field by remember(value) { mutableStateOf(TextFieldValue(value)) }
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    // F2: the cursor comes here and the whole name is selected, so that typing replaces it
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
        textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(TextForeground),
        decorationBox = { inner ->
            Box(Modifier.background(FrameBackground).border(1.dp, DarkenedBorder).padding(horizontal = 4.dp, vertical = 2.dp)) { inner() }
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
private fun FloatInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) {
            Text(label, color = GhostlyText, fontSize = 9.sp, modifier = Modifier.padding(end = 2.dp))
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(TextForeground),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            FrameBackground,
                        ).border(1.dp, DarkenedBorder)
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                ) {
                    inner()
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .onFocusChanged { fs ->
                        if (focused && !fs.isFocused && text != value) onCommit(text)
                        focused = fs.isFocused
                    }.onKeyEvent { ev ->
                        if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown && text != value) {
                            onCommit(text)
                            true
                        } else {
                            false
                        }
                    },
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun fmtF(f: Float): String {
    val l = f.toLong()
    return if (f == l.toFloat()) "$l" else "%.4g".format(f)
}

/** The styles that can be chosen for the selected widget: the vanilla ones of its type. */
private class StyleChoice(
    state: LayoutEditorState,
    type: WidgetType,
    layoutPath: String?,
) {
    val names: List<String> = state.vanillaStylesFor(type.className, layoutPath).map { it.name }.distinct()
}
