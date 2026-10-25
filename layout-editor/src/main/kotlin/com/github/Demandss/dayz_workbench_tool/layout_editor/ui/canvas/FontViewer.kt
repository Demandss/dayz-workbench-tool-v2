package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.font.GameFontFile
import com.github.demandss.dayz_workbench_tool.font.GameFontHeader
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.TextureDocument
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ColorPickerPopup
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import java.io.File
import java.util.UUID

/** The grey the Workbench shows a font on. */
private val FONT_PREVIEW_GREY = Color(0xFF828282)

/** What the viewer shows about a font file. */
private class FontFacts(
    val header: GameFontHeader?,
    val fontBytes: Long,
    val atlasBytes: Long,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val lineHeight: Int,
) {
    val sdf get() = header?.kind == "FNT5"
}

private fun readFacts(file: File): FontFacts {
    val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
    val header = GameFontFile.header(bytes)
    val edds = File(file.parentFile, file.nameWithoutExtension + ".edds")
    var w = 0
    var h = 0
    if (edds.isFile) {
        runCatching {
            edds.inputStream().use { input ->
                val head = ByteArray(32)
                input.read(head)
                val buffer =
                    java.nio.ByteBuffer
                        .wrap(head)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                h = buffer.getInt(12)
                w = buffer.getInt(16)
            }
        }
    }
    val line =
        BitmapFont
            .readGlyphs(file)
            ?.second
            ?.groupingBy { it.height }
            ?.eachCount()
            ?.maxByOrNull { it.value }
            ?.key ?: header?.size ?: 0
    return FontFacts(header, file.length(), if (edds.isFile) edds.length() else 0, w, h, line)
}

private fun hexColor(
    text: String,
    fallback: Color,
): Color {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return fallback
    val v = clean.toLongOrNull(16) ?: return fallback
    return if (clean.length == 6) Color((0xFF000000L or v).toInt()) else Color(v.toInt())
}

private fun asWidgetColor(c: Color) = "${c.red} ${c.green} ${c.blue} ${c.alpha}"

/**
 * A font of the game drawn the way the Workbench shows it: a sample text on grey that follows the size, the colour, bold and
 * italic, the outline and the shadow, with the facts about the file on the right. The text is drawn by the same code as the text
 * of a widget, so what is seen here is what a widget with these properties gets.
 */
@Composable
fun FontViewer(
    state: LayoutEditorState,
    doc: TextureDocument,
    modifier: Modifier = Modifier,
) {
    val file = remember(doc.path) { File(doc.path) }
    val font = remember(doc.path, state.fontsVersion) { state.loadGameFontFile(file) }
    val facts = remember(doc.path) { readFacts(file) }
    val measurer = rememberTextMeasurer()

    var size by remember(doc.path) { mutableFloatStateOf((facts.lineHeight.takeIf { it > 0 } ?: 32).toFloat().coerceIn(8f, 120f)) }
    var colorHex by remember { mutableStateOf("FFFFFF") }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var outline by remember { mutableFloatStateOf(0f) }
    var outlineHex by remember { mutableStateOf("000000") }
    var shadow by remember { mutableFloatStateOf(0f) }
    var shadowHex by remember { mutableStateOf("000000") }
    var offsetX by remember { mutableStateOf("1") }
    var offsetY by remember { mutableStateOf("1") }
    var sample by remember {
        mutableStateOf("The quick brown fox jumps over the lazy dog. 1234567890\nСъешь же ещё этих мягких французских булок")
    }
    var allCharacters by remember { mutableStateOf(false) }

    val sdf = facts.sdf
    val shown =
        if (allCharacters) {
            facts.header
                ?.ranges
                ?.codePoints()
                ?.filter { it >= 32 }
                ?.joinToString("") { String(Character.toChars(it)) }
                .orEmpty()
        } else {
            sample
        }

    Row(modifier.fillMaxSize().background(FrameBackground)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // The controls, in a row over the preview as in the Workbench; a bitmap font does not have what is greyed out
            Row(
                Modifier.fillMaxWidth().background(FrameBackground).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Label(tr("Размер"))
                Slider(
                    value = size,
                    onValueChange = { size = it.coerceIn(6f, 200f) },
                    valueRange = 6f..200f,
                    modifier = Modifier.width(160.dp).height(22.dp),
                    colors = sliderColors(),
                )
                Label("${size.toInt()}")
                ColorBox(colorHex) { colorHex = it }
                Toggle(tr("Жирный"), bold, sdf) { bold = it }
                Toggle(tr("Курсив"), italic, sdf) { italic = it }
                Label(tr("Обводка"))
                Slider(
                    value = outline,
                    onValueChange = { outline = it },
                    valueRange = 0f..8f,
                    enabled = sdf,
                    modifier = Modifier.width(110.dp).height(22.dp),
                    colors = sliderColors(),
                )
                ColorBox(outlineHex, enabled = sdf) { outlineHex = it }
                Label(tr("Тень"))
                Slider(
                    value = shadow,
                    onValueChange = { shadow = it },
                    valueRange = 0f..8f,
                    modifier = Modifier.width(110.dp).height(22.dp),
                    colors = sliderColors(),
                )
                ColorBox(shadowHex, enabled = sdf) { shadowHex = it }
                Label(tr("Смещение"))
                SmallField(offsetX, sdf) { offsetX = it }
                SmallField(offsetY, sdf) { offsetY = it }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DarkenedBorder))
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        // The wheel changes the size
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type != PointerEventType.Scroll) continue
                                val dy =
                                    event.changes
                                        .firstOrNull()
                                        ?.scrollDelta
                                        ?.y ?: 0f
                                if (dy != 0f) size = (size + if (dy > 0f) -2f else 2f).coerceIn(6f, 200f)
                            }
                        }
                    },
            ) {
                drawRect(FONT_PREVIEW_GREY)
                val boxWidth = if (allCharacters) this.size.width - 80f else (this.size.width * 0.5f).coerceAtLeast(240f)
                val boxHeight = if (allCharacters) this.size.height - 80f else this.size.height * 0.6f
                val node =
                    EditorWidgetNode(
                        UUID.randomUUID(),
                        WidgetNode(
                            WidgetType.TEXT,
                            WidgetType.TEXT.className,
                            "Preview",
                            linkedMapOf(
                                "text" to "\"${shown.replace("\"", "'")}\"",
                                "exact text" to "1",
                                "exact text size" to size.toString(),
                                "wrap" to "1",
                                "text halign" to if (allCharacters) "left" else "center",
                                "text valign" to "top",
                            ).also { props ->
                                if (bold && sdf) props["bold text"] = "1"
                                if (italic && sdf) props["italic text"] = "1"
                                if (outline > 0f && sdf) {
                                    props["outline size"] = outline.toString()
                                    props["outline color"] = asWidgetColor(hexColor(outlineHex, Color.Black))
                                }
                                if (shadow > 0f) {
                                    props["shadow size"] = shadow.toString()
                                    props["shadow color"] = asWidgetColor(hexColor(shadowHex, Color.Black))
                                    props["shadow offset"] = "${offsetX.toFloatOrNull() ?: 1f} ${offsetY.toFloatOrNull() ?: 1f}"
                                }
                            },
                        ),
                        mutableListOf(),
                    )
                if (font != null) {
                    drawWidgetText(
                        node,
                        Offset((this.size.width - boxWidth) / 2f, if (allCharacters) 40f else this.size.height * 0.2f),
                        Size(boxWidth, boxHeight),
                        1f,
                        measurer,
                        hexColor(colorHex, Color.White),
                        fontAt = { font },
                        fontName = file.nameWithoutExtension,
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))
        // The text to show and the facts about the file
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(FrameBackground)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = sample,
                onValueChange = { sample = it },
                textStyle = TextStyle(color = TextForeground, fontSize = 12.sp),
                cursorBrush = SolidColor(TextForeground),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(FrameBackground)
                            .border(1.dp, DarkenedBorder)
                            .padding(6.dp),
                    ) { inner() }
                },
            )
            Row(
                Modifier.clickable(remember { MutableInteractionSource() }, null) { allCharacters = !allCharacters },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(14.dp).background(if (allCharacters) SubSelectColor else DarkenedBorder).border(1.dp, Color(0xFF3A3C40)),
                    contentAlignment = Alignment.Center,
                ) { if (allCharacters) Text("✓", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp) }
                Text(tr("Показать все символы шрифта"), color = TextForeground, fontSize = 11.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DarkenedBorder))
            val h = facts.header
            val lines =
                listOf(
                    tr("Файл: {0} КБ", "%.2f".format(facts.fontBytes / 1024.0)),
                    tr("Версия: {0}", h?.kind ?: "?"),
                    tr("Тип: {0}", if (sdf) "sdf" else "bitmap"),
                    tr("Обводка: {0}", if (sdf) tr("да") else tr("нет")),
                    tr("Тень: {0}", if (sdf) tr("да") else tr("старая (без смещения и цвета)")),
                    tr("Жирный и курсив в игре: {0}", if (sdf) tr("да") else tr("нет")),
                    tr("Атлас: {0} КБ, {1} × {2}", "%.2f".format(facts.atlasBytes / 1024.0), facts.atlasWidth, facts.atlasHeight),
                    tr("Высота строки: {0} px", facts.lineHeight),
                    tr("Символов: {0}", h?.ranges?.count ?: 0),
                    tr("Кириллица: {0}", if (h?.ranges?.has(0x0416) == true) tr("да") else tr("нет")),
                )
            lines.forEach { Text(it, color = GhostlyText, fontSize = 11.sp) }
            if (font == null) Text(tr("Шрифт не удалось прочитать"), color = Color(0xFFD06A6A), fontSize = 11.sp)
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = GhostlyText, fontSize = 11.sp)

@Composable
private fun sliderColors() =
    SliderDefaults.colors(
        thumbColor = SelectColor,
        activeTrackColor = SelectColor,
        inactiveTrackColor = DarkenedBorder,
        disabledThumbColor = DarkenedBorder,
        disabledActiveTrackColor = DarkenedBorder,
        disabledInactiveTrackColor = DarkenedBorder,
    )

@Composable
private fun Toggle(
    label: String,
    on: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.clickable(remember { MutableInteractionSource() }, null, enabled = enabled) { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier.size(14.dp).background(if (on && enabled) SubSelectColor else DarkenedBorder).border(1.dp, Color(0xFF3A3C40)),
            contentAlignment = Alignment.Center,
        ) { if (on && enabled) Text("✓", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp) }
        Text(label, color = if (enabled) TextForeground else GhostlyText, fontSize = 11.sp)
    }
}

@Composable
private fun ColorBox(
    hex: String,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val anchorHeight = with(LocalDensity.current) { 22.dp.roundToPx() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            Box(
                Modifier
                    .size(22.dp)
                    .background(Color(0xFF2B2B30))
                    .background(if (enabled) hexColor(hex, Color.Black) else DarkenedBorder)
                    .border(1.dp, Color(0xFF3A3C40))
                    .clickable(remember { MutableInteractionSource() }, null, enabled = enabled) { picking = true },
            )
            if (picking) {
                // The same picker as for the colour of a widget: a square, the hue, the opacity, the hex value and the presets
                ColorPickerPopup(
                    initial = hexColor(hex, Color.Black),
                    anchorHeightPx = anchorHeight,
                    onDismiss = { picking = false },
                    onCommit = { onChange(colorToHex(it)) },
                )
            }
        }
        BasicTextField(
            value = hex,
            onValueChange = { if (enabled) onChange(it.filter { c -> c.isLetterOrDigit() }.take(8).uppercase()) },
            singleLine = true,
            textStyle = TextStyle(color = if (enabled) TextForeground else GhostlyText, fontSize = 10.sp),
            cursorBrush = SolidColor(TextForeground),
            modifier =
                Modifier
                    .width(
                        52.dp,
                    ).background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(horizontal = 3.dp, vertical = 3.dp),
        )
    }
}

private fun colorToHex(c: Color): String {
    val r = (c.red * 255f + 0.5f).toInt()
    val g = (c.green * 255f + 0.5f).toInt()
    val b = (c.blue * 255f + 0.5f).toInt()
    val a = (c.alpha * 255f + 0.5f).toInt()
    return if (a == 255) "%02X%02X%02X".format(r, g, b) else "%02X%02X%02X%02X".format(a, r, g, b)
}

@Composable
private fun SmallField(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = { if (enabled) onChange(it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(5)) },
        singleLine = true,
        textStyle = TextStyle(color = if (enabled) TextForeground else GhostlyText, fontSize = 10.sp),
        cursorBrush = SolidColor(TextForeground),
        modifier =
            Modifier
                .width(
                    30.dp,
                ).background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(horizontal = 3.dp, vertical = 3.dp),
    )
}
