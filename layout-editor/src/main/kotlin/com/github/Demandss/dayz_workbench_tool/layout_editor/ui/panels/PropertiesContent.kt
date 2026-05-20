package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.pickFile
import com.github.demandss.dayz_workbench_tool.ui.theme.*
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
private val MODE_OPTS      = listOf("", "blend", "add", "multiply", "color", "alpha", "overlay")
private val CLAMP_OPTS     = listOf("", "clamp", "wrap", "mirror")
private val STRETCH_OPTS   = listOf("", "tile", "stretch")

private val WIDGET_KEYS = setOf(
    "position", "size", "hexactpos", "vexactpos", "hexactsize", "vexactsize",
    "halign", "valign",
    "visible", "disabled", "clipchildren", "inheritalpha", "ignorepointer",
    "ignoregloballv", "keepsafezone", "color",
    "scaled", "fixaspect", "priority", "userID", "draggable", "scriptclass"
)

private val IMAGE_KEYS = setOf(
    "image0", "imageTexture", "mode", "src alpha", "clamp mode",
    "stretch mode", "flip u", "flip v", "filter", "nocache",
    "rotation", "pivot", "mask", "transition width", "progress"
)

private val IMAGE_TYPES = setOf(WidgetType.IMAGE, WidgetType.VIDEO)

// ---------------------------------------------------------------------------
// Commit helpers
// ---------------------------------------------------------------------------

private fun commitName(state: LayoutEditorState, id: UUID, name: String) {
    val idx = state.activeFileIndex
    val doc = state.openFiles.getOrNull(idx) ?: return
    state.pushUndo(idx, doc.root)
    state.updateActiveRoot(doc.root.updateNode(id) { it.copy(node = it.node.copy(name = name)) })
}

private fun commitType(state: LayoutEditorState, id: UUID, type: WidgetType) {
    val idx = state.activeFileIndex
    val doc = state.openFiles.getOrNull(idx) ?: return
    state.pushUndo(idx, doc.root)
    state.updateActiveRoot(doc.root.updateNode(id) { it.copy(node = it.node.copy(type = type, originalClassName = type.className)) })
}

private fun commitProp(state: LayoutEditorState, id: UUID, key: String, value: String) {
    val idx = state.activeFileIndex
    val doc = state.openFiles.getOrNull(idx) ?: return
    state.pushUndo(idx, doc.root)
    state.updateActiveRoot(doc.root.updateNode(id) { n ->
        n.copy(node = n.node.copy(properties = LinkedHashMap(n.node.properties).also { it[key] = value }))
    })
}

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

@Composable
fun PropertiesContent(state: LayoutEditorState, modifier: Modifier = Modifier) {
    val selectedId = state.selectedIds.firstOrNull()
    val doc = state.activeFile
    val node = remember(selectedId, doc?.root) {
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

        PropertiesHeader(state, node.id, node.node.name, node.node.type, props, commit)
        HLine()

        var tab by remember { mutableStateOf(0) }
        Row(Modifier.fillMaxWidth().height(26.dp).background(ElementsBackground), verticalAlignment = Alignment.CenterVertically) {
            PropsTabBtn("Properties", tab == 0) { tab = 0 }
            PropsTabBtn("Classes",    tab == 1) { tab = 1 }
        }
        HLine()

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> {
                    WidgetSection(props, commit)
                    if (node.node.type in IMAGE_TYPES) ImageWidgetSection(props, commit, state.workDrivePath)
                    val extra = props.filter { it.key !in WIDGET_KEYS && it.key !in IMAGE_KEYS }
                    if (extra.isNotEmpty()) RawSection(extra, commit)
                }
                1 -> Box(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text("Классы — в разработке", color = GhostlyText, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fixed header
// ---------------------------------------------------------------------------

@Composable
private fun PropertiesHeader(
    state: LayoutEditorState, id: UUID, name: String, type: WidgetType,
    props: Map<String, String>, commit: (String, String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Name", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(40.dp))
        StringField(name, Modifier.weight(1f)) { commitName(state, id, it) }
        TypeDropdown(type) { commitType(state, id, it) }
    }
    HLine()

    val hexactpos  = props["hexactpos"]  == "1"
    val vexactpos  = props["vexactpos"]  == "1"
    val hexactsize = props["hexactsize"] == "1"
    val vexactsize = props["vexactsize"] == "1"

    val posParts = (props["position"] ?: "0 0").trim().split(Regex("\\s+"))
    val szParts  = (props["size"]     ?: "0 0").trim().split(Regex("\\s+"))
    var px by remember(props["position"]) { mutableStateOf(posParts.getOrNull(0) ?: "0") }
    var py by remember(props["position"]) { mutableStateOf(posParts.getOrNull(1) ?: "0") }
    var sw by remember(props["size"])     { mutableStateOf(szParts.getOrNull(0)  ?: "0") }
    var sh by remember(props["size"])     { mutableStateOf(szParts.getOrNull(1)  ?: "0") }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SpinLabel("X")
        SpinInput(px, hexactpos, Modifier.weight(1f)) { v -> px = v; commit("position", "$v $py") }
        SpinLabel("Y")
        SpinInput(py, vexactpos, Modifier.weight(1f)) { v -> py = v; commit("position", "$px $v") }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SpinLabel("W")
        SpinInput(sw, hexactsize, Modifier.weight(1f)) { v -> sw = v; commit("size", "$v $sh") }
        SpinLabel("H")
        SpinInput(sh, vexactsize, Modifier.weight(1f)) { v -> sh = v; commit("size", "$sw $v") }
    }
    HLine()

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlignmentGrid(props["halign"] ?: "", props["valign"] ?: "") { h, v ->
            commit("halign", h); commit("valign", v)
        }
        CoordSystemGrid("Pos", props["hexactpos"] == "1", props["vexactpos"] == "1") { hx, vy ->
            commit("hexactpos", if (hx) "1" else "0"); commit("vexactpos", if (vy) "1" else "0")
        }
        CoordSystemGrid("Size", props["hexactsize"] == "1", props["vexactsize"] == "1") { hx, vy ->
            commit("hexactsize", if (hx) "1" else "0"); commit("vexactsize", if (vy) "1" else "0")
        }
    }
}

// ---------------------------------------------------------------------------
// AlignmentGrid 3×3
// ---------------------------------------------------------------------------

@Composable
private fun AlignmentGrid(halign: String, valign: String, onSet: (String, String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Align", color = GhostlyText, fontSize = 8.sp)
        Spacer(Modifier.height(2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for ((ri, v) in VALIGN_GRID.withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    for ((ci, h) in HALIGN_GRID.withIndex()) {
                        AlignCell(ci, ri, halign == h && valign == v) { onSet(h, v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlignCell(col: Int, row: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(20.dp)
            .background(if (active) SelectColor else Color(0xFF2A2C30))
            .border(1.dp, DarkenedBorder)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = 3.5f
            drawRect(Color(0x55FFFFFF), Offset(p, p), Size(size.width - p * 2, size.height - p * 2), style = Stroke(0.75f))
            val dotX = when (col) { 0 -> p; 1 -> size.width / 2f; else -> size.width - p }
            val dotY = when (row) { 0 -> p; 1 -> size.height / 2f; else -> size.height - p }
            drawCircle(if (active) Color.White else Color(0xAAFFFFFF), 2.5f, Offset(dotX, dotY))
        }
    }
}

// ---------------------------------------------------------------------------
// CoordSystem 2×2
// ---------------------------------------------------------------------------

@Composable
private fun CoordSystemGrid(label: String, hexact: Boolean, vexact: Boolean, onSet: (Boolean, Boolean) -> Unit) {
    val cells = listOf(true to true, false to true, true to false, false to false)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = GhostlyText, fontSize = 8.sp)
        Spacer(Modifier.height(2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            cells.chunked(2).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    rowCells.forEach { (hx, vy) ->
                        CoordCell(hx, vy, hexact == hx && vexact == vy) { onSet(hx, vy) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordCell(hexact: Boolean, vexact: Boolean, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp)
            .background(if (active) SelectColor else Color(0xFF2A2C30))
            .border(1.dp, DarkenedBorder)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("↔", color = if (hexact) Color(0xFF4A9EFF) else Color(0xFFFF5555), fontSize = 9.sp, lineHeight = 10.sp)
            Text("↕", color = if (vexact) Color(0xFF4A9EFF) else Color(0xFFFF5555), fontSize = 9.sp, lineHeight = 10.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Tab button
// ---------------------------------------------------------------------------

@Composable
private fun PropsTabBtn(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (active) FrameBackground else Color.Transparent)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (active) TextForeground else GhostlyText, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------
// Widget section
// ---------------------------------------------------------------------------

@Composable
private fun WidgetSection(props: Map<String, String>, commit: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    SectionHeader("Widget", expanded) { expanded = !expanded }
    if (!expanded) return

    CheckRow("visible",       props["visible"],       commit)
    CheckRow("disabled",      props["disabled"],      commit)
    CheckRow("clipchildren",  props["clipchildren"],  commit)
    CheckRow("inheritalpha",  props["inheritalpha"],  commit)
    CheckRow("ignorepointer", props["ignorepointer"], commit)
    CheckRow("ignoregloballv",props["ignoregloballv"],commit)
    CheckRow("keepsafezone",  props["keepsafezone"],  commit)

    ColorRows(props, commit)

    CheckRow("scaled", props["scaled"], commit)
    PropRow("fixaspect") {
        CompactDropdown(props["fixaspect"] ?: "", FIXASPECT_OPTS) { commit("fixaspect", it) }
    }
    PropRow("priority") {
        StringField(props["priority"] ?: "", Modifier.weight(1f)) { commit("priority", it) }
    }
    PropRow("userID") {
        StringField(props["userID"] ?: "", Modifier.weight(1f)) { commit("userID", it) }
    }
    CheckRow("draggable",    props["draggable"],   commit)
    PropRow("scriptclass") {
        StringField(props["scriptclass"] ?: "", Modifier.weight(1f)) { commit("scriptclass", it) }
    }
}

// ---------------------------------------------------------------------------
// ImageWidget section
// ---------------------------------------------------------------------------

@Composable
private fun ImageWidgetSection(props: Map<String, String>, commit: (String, String) -> Unit, workDrivePath: String) {
    var expanded by remember { mutableStateOf(true) }
    SectionHeader("ImageWidget", expanded) { expanded = !expanded }
    if (!expanded) return

    val scope = rememberCoroutineScope()
    val basePath = workDrivePath.ifBlank { null }

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("image0", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(LABEL_W), maxLines = 1)
        StringField(props["image0"] ?: "", Modifier.weight(1f)) { commit("image0", it) }
        SmallButton("..") { scope.launch { pickFile("Выбрать текстуру", "*", basePath)?.let { commit("image0", it) } } }
    }
    HLine()

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("imageTexture", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(LABEL_W), maxLines = 1)
        Spacer(Modifier.weight(1f))
        SmallButton("..") { scope.launch { pickFile("Выбрать текстуру", "*", basePath)?.let { commit("imageTexture", it) } } }
    }
    HLine()

    PropRow("mode") { CompactDropdown(props["mode"] ?: "", MODE_OPTS) { commit("mode", it) } }
    CheckRow("src alpha",    props["src alpha"],    commit)
    PropRow("clamp mode")   { CompactDropdown(props["clamp mode"]   ?: "", CLAMP_OPTS)   { commit("clamp mode",   it) } }
    PropRow("stretch mode") { CompactDropdown(props["stretch mode"] ?: "", STRETCH_OPTS) { commit("stretch mode", it) } }
    CheckRow("flip u",   props["flip u"],  commit)
    CheckRow("flip v",   props["flip v"],  commit)
    CheckRow("filter",   props["filter"],  commit)
    CheckRow("nocache",  props["nocache"], commit)

    PropRow("rotation") { StringField(props["rotation"] ?: "", Modifier.weight(1f)) { commit("rotation", it) } }

    val pivotParts = (props["pivot"] ?: "0 0").trim().split(Regex("\\s+"))
    var pivotX by remember(props["pivot"]) { mutableStateOf(pivotParts.getOrNull(0) ?: "0") }
    var pivotY by remember(props["pivot"]) { mutableStateOf(pivotParts.getOrNull(1) ?: "0") }
    SubRow("  pivot x") { FloatInput("", pivotX, Modifier.weight(1f)) { v -> pivotX = v; commit("pivot", "$v $pivotY") } }
    SubRow("  pivot y") { FloatInput("", pivotY, Modifier.weight(1f)) { v -> pivotY = v; commit("pivot", "$pivotX $v") } }

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Mask", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(LABEL_W), maxLines = 1)
        Spacer(Modifier.weight(1f))
        SmallButton("..") { scope.launch { pickFile("Выбрать маску", "*", basePath)?.let { commit("mask", it) } } }
    }
    HLine()

    SliderRow("Transition width", props["transition width"], 0f, 1f) { commit("transition width", it) }
    SliderRow("Progress",         props["progress"],         0f, 1f) { commit("progress",         it) }
}

// ---------------------------------------------------------------------------
// Raw extra properties
// ---------------------------------------------------------------------------

@Composable
private fun RawSection(props: Map<String, String>, commit: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    SectionHeader("Extra", expanded) { expanded = !expanded }
    if (!expanded) return
    for ((key, value) in props) {
        PropRow(key) { StringField(value, Modifier.weight(1f)) { commit(key, it) } }
    }
}

// ---------------------------------------------------------------------------
// Color rows
// ---------------------------------------------------------------------------

@Composable
private fun ColorRows(props: Map<String, String>, commit: (String, String) -> Unit) {
    val cv = props["color"] ?: ""
    val parts = cv.trim().split(Regex("\\s+"))
    var r by remember(cv) { mutableStateOf(parts.getOrNull(0) ?: "0") }
    var g by remember(cv) { mutableStateOf(parts.getOrNull(1) ?: "0") }
    var b by remember(cv) { mutableStateOf(parts.getOrNull(2) ?: "0") }
    var a by remember(cv) { mutableStateOf(parts.getOrNull(3) ?: "1") }
    fun save() = commit("color", "$r $g $b $a")

    val preview = remember(r, g, b, a) {
        Color(r.toFloatOrNull() ?: 0f, g.toFloatOrNull() ?: 0f, b.toFloatOrNull() ?: 0f, a.toFloatOrNull() ?: 1f)
    }

    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("color", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(LABEL_W), maxLines = 1)
        Box(Modifier.size(14.dp).background(preview).border(1.dp, DarkenedBorder))
        FloatInput("R", r, Modifier.weight(1f)) { v -> r = v; save() }
        FloatInput("G", g, Modifier.weight(1f)) { v -> g = v; save() }
        FloatInput("B", b, Modifier.weight(1f)) { v -> b = v; save() }
        FloatInput("A", a, Modifier.weight(1f)) { v -> a = v; save() }
    }
    HLine()
    SubRow("  r") { FloatInput("", r, Modifier.weight(1f)) { v -> r = v; save() } }
    SubRow("  g") { FloatInput("", g, Modifier.weight(1f)) { v -> g = v; save() } }
    SubRow("  b") { FloatInput("", b, Modifier.weight(1f)) { v -> b = v; save() } }
    SubRow("  a") { FloatInput("", a, Modifier.weight(1f)) { v -> a = v; save() } }
}

// ---------------------------------------------------------------------------
// Slider row
// ---------------------------------------------------------------------------

@Composable
private fun SliderRow(label: String, raw: String?, min: Float, max: Float, onCommit: (String) -> Unit) {
    val current = raw?.toFloatOrNull()?.coerceIn(min, max) ?: min
    var text by remember(raw) { mutableStateOf(raw ?: fmtF(min)) }
    Row(
        Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(LABEL_W), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(
            value = current,
            onValueChange = { v -> text = fmtF(v); onCommit(fmtF(v)) },
            valueRange = min..max,
            modifier = Modifier.weight(1f).height(ROW_H),
            colors = SliderDefaults.colors(thumbColor = SelectColor, activeTrackColor = SelectColor, inactiveTrackColor = DarkenedBorder)
        )
        var focused by remember { mutableStateOf(false) }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(TextForeground),
            decorationBox = { inner ->
                Box(Modifier.width(40.dp).background(FrameBackground).border(1.dp, DarkenedBorder).padding(horizontal = 3.dp, vertical = 2.dp)) { inner() }
            },
            modifier = Modifier
                .onFocusChanged { fs -> if (focused && !fs.isFocused) onCommit(text); focused = fs.isFocused }
                .onKeyEvent { ev -> if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown) { onCommit(text); true } else false }
        )
    }
    HLine()
}

// ---------------------------------------------------------------------------
// Row primitives
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(18.dp).background(Color(0xFF2A2C30))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onToggle)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (expanded) "▾ $title" else "▸ $title", color = GhostlyText, fontSize = 9.sp)
    }
}

@Composable
private fun PropRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_H).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(LABEL_W), maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
    HLine()
}

@Composable
private fun SubRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW_H).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.width(LABEL_W), maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
    HLine()
}

@Composable
private fun CheckRow(key: String, value: String?, commit: (String, String) -> Unit) {
    val checked = value?.trim() == "1"
    Row(
        Modifier.fillMaxWidth().height(ROW_H)
            .clickable(remember { MutableInteractionSource() }, null) { commit(key, if (checked) "0" else "1") }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, color = GhostlyText, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.size(14.dp)
                .background(if (checked) SelectColor.copy(alpha = 0.25f) else Color.Transparent)
                .border(1.dp, if (checked) SelectColor else DarkenedBorder),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", color = SelectColor, fontSize = 9.sp, lineHeight = 9.sp)
        }
    }
    HLine()
}

@Composable
private fun HLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
}

// ---------------------------------------------------------------------------
// SpinInput
// ---------------------------------------------------------------------------

@Composable
private fun SpinLabel(text: String) {
    Box(Modifier.width(10.dp).padding(top = 9.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = GhostlyText, fontSize = 9.sp)
    }
}

@Composable
private fun SpinInput(
    value: String,
    isPx: Boolean,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }

    fun step(delta: Float) {
        val v = text.toFloatOrNull() ?: 0f
        val result = fmtF(v + delta)
        text = result
        onCommit(result)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SpinButton(up = true)  { isShift -> step( if (isShift) 10f else 1f) }

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(
                color = TextForeground, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End
            ),
            cursorBrush = SolidColor(TextForeground),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().background(FrameBackground).border(1.dp, DarkenedBorder)
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    Text(if (isPx) "px" else "%", color = GhostlyText, fontSize = 8.sp)
                }
            },
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.type != PointerEventType.Scroll) continue
                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy == 0f) continue
                            val delta = if (event.keyboardModifiers.isShiftPressed) 10f else 1f
                            step(if (dy > 0f) -delta else delta)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
                .onFocusChanged { fs ->
                    if (focused && !fs.isFocused && text != value) onCommit(text)
                    focused = fs.isFocused
                }
                .onKeyEvent { ev ->
                    when {
                        ev.key == Key.Enter && ev.type == KeyEventType.KeyDown -> {
                            if (text != value) onCommit(text); true
                        }
                        ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyDown -> {
                            step(if (ev.isShiftPressed) 10f else 1f); true
                        }
                        ev.key == Key.DirectionDown && ev.type == KeyEventType.KeyDown -> {
                            step(if (ev.isShiftPressed) -10f else -1f); true
                        }
                        else -> false
                    }
                }
        )

        SpinButton(up = false) { isShift -> step(if (isShift) -10f else -1f) }
    }
}

@Composable
private fun SpinButton(up: Boolean, onClick: (isShift: Boolean) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(9.dp)
            .background(Color(0xFF2A2C30))
            .border(1.dp, DarkenedBorder)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                            onClick(event.keyboardModifiers.isShiftPressed)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(if (up) "▲" else "▼", color = GhostlyText, fontSize = 7.sp, lineHeight = 7.sp)
    }
}

// ---------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------

@Composable
private fun TypeDropdown(current: WidgetType, onSelect: (WidgetType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.background(FrameBackground).border(1.dp, DarkenedBorder)
                .clickable(remember { MutableInteractionSource() }, null) { expanded = true }
                .padding(horizontal = 5.dp, vertical = 2.dp).widthIn(min = 60.dp)
        ) {
            Text(current.className.removeSuffix("WidgetClass"), color = TextForeground, fontSize = 10.sp, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(ElementsBackground)) {
            WidgetType.entries.filter { it != WidgetType.UNKNOWN }.forEach { wt ->
                DropdownMenuItem(
                    text = { Text(wt.className.removeSuffix("WidgetClass"), color = TextForeground, fontSize = 11.sp) },
                    onClick = { onSelect(wt); expanded = false },
                    modifier = Modifier.background(if (wt == current) SelectColor else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun CompactDropdown(value: String, options: List<String>, onCommit: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.background(FrameBackground).border(1.dp, DarkenedBorder)
                .clickable(remember { MutableInteractionSource() }, null) { expanded = true }
                .padding(horizontal = 5.dp, vertical = 2.dp).widthIn(min = 60.dp)
        ) {
            Text(value.ifEmpty { "—" }, color = TextForeground, fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(ElementsBackground)) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.ifEmpty { "—" }, color = TextForeground, fontSize = 11.sp) },
                    onClick = { onCommit(opt); expanded = false },
                    modifier = Modifier.background(if (opt == value) SelectColor else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.background(ElementsBackground).border(1.dp, DarkenedBorder)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GhostlyText, fontSize = 10.sp)
    }
}

@Composable
private fun StringField(value: String, modifier: Modifier = Modifier, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(color = TextForeground, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(TextForeground),
        decorationBox = { inner ->
            Box(Modifier.background(FrameBackground).border(1.dp, DarkenedBorder).padding(horizontal = 4.dp, vertical = 2.dp)) { inner() }
        },
        modifier = modifier
            .onFocusChanged { fs -> if (focused && !fs.isFocused && text != value) onCommit(text); focused = fs.isFocused }
            .onKeyEvent { ev ->
                if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown && text != value) { onCommit(text); true } else false
            }
    )
}

@Composable
private fun FloatInput(label: String, value: String, modifier: Modifier = Modifier, onCommit: (String) -> Unit) {
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
                Box(Modifier.fillMaxWidth().background(FrameBackground).border(1.dp, DarkenedBorder).padding(horizontal = 3.dp, vertical = 2.dp)) { inner() }
            },
            modifier = Modifier.weight(1f)
                .onFocusChanged { fs -> if (focused && !fs.isFocused && text != value) onCommit(text); focused = fs.isFocused }
                .onKeyEvent { ev ->
                    if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown && text != value) { onCommit(text); true } else false
                }
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