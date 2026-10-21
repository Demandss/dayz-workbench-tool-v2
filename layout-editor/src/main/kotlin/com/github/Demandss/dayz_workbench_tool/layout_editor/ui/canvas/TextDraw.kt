package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.math.roundToInt

/** The text a text widget shows: the `text` property without quotes and without the `#` of a string table key. */
internal fun EditorWidgetNode.shownText(): String =
    prop("text")
        ?.trim()
        ?.trim('"')
        ?.removePrefix("#")
        .orEmpty()

private fun EditorWidgetNode.flagProp(name: String): Boolean =
    prop(name)?.let { it != "0" && !it.equals("false", ignoreCase = true) } ?: false

/**
 * How high the letters are: `exact text size` pixels when `exact text` is on, otherwise the font follows the height of the widget
 * (the line of text fills the widget).
 */
internal fun EditorWidgetNode.textPixelSize(
    boxHeight: Float,
    fontName: String = "",
): Float {
    val exact = flagProp("exact text")
    val set = prop("exact text size")?.toFloatOrNull()
    if (isBitmapMetron(fontName)) {
        // A bitmap font has a few sizes: a name with a number (Metron16) is that size, `exact text size` is taken as it is, and
        // otherwise the biggest size that is not higher than the letters should be (measured on 16, 24 and 40 px boxes)
        fontName
            .substringAfterLast('/')
            .takeLastWhile { it.isDigit() }
            .toFloatOrNull()
            ?.let { return it }
        if (exact && set != null) return set
        val proportion = prop("text_proportion")?.toFloatOrNull() ?: if (node.type == WidgetType.RICH_TEXT) 0.5f else 1f
        val wanted = boxHeight * proportion
        return BITMAP_SIZES.lastOrNull { it <= wanted }?.toFloat() ?: BITMAP_SIZES.first().toFloat()
    }
    // A button ignores `exact text`: its text follows the height of the button (measured: 70 px high, 24 asked, 68 drawn)
    if (exact && set != null && node.type != WidgetType.BUTTON) return set
    // `text_proportion` is the share of the widget height the letters take. A text widget fills it by default, the rich and
    // multiline ones take half of it (measured in Workbench on 16, 24 and 40 px high widgets)
    val proportion =
        prop("text_proportion")?.toFloatOrNull()
            ?: if (node.type == WidgetType.TEXT || node.type == WidgetType.BUTTON) 1f else 0.5f
    return boxHeight * proportion
}

private val BITMAP_SIZES = intArrayOf(12, 14, 16, 22, 28, 48, 58)

/** The bitmap fonts of the Metron family (`gui/fonts/Metron`, `Metron16`, `Metron-Bold`): drawn from the SDF font of the family. */
internal fun isBitmapMetron(fontName: String): Boolean {
    val name = fontName.substringAfterLast('/').lowercase()
    return name.startsWith("metron") && !name.startsWith("sdf_")
}

// The letters of the bitmap Metron are narrower than those of the SDF MetronBook at the same size (measured: 0.875 times)
private const val BITMAP_WIDTH = 0.875f

internal fun widthScaleOf(fontName: String): Float = if (isBitmapMetron(fontName)) BITMAP_WIDTH else 1f

// Metron is wider than the font of the system: its text came out 1.09 times wider in Workbench at the same height of letters
private const val METRON_WIDTH = 1.09f

/** Draws the text of a text widget into its box: size, colour, alignment, wrapping and the shadow. */
internal fun DrawScope.drawWidgetText(
    node: EditorWidgetNode,
    topLeft: Offset,
    boxSize: Size,
    zoom: Float,
    measurer: TextMeasurer,
    color: Color,
    // A button centres its text unless it says otherwise, a text widget starts at the top left
    centered: Boolean = false,
    // The font the widget uses at a size in layout pixels; the letters are drawn from it when every character is in it
    fontAt: (Float) -> GameFont? = { null },
    // The name of the font the widget uses (its weight and slant are in the name)
    fontName: String = "",
) {
    val text = node.shownText()
    if (text.isEmpty() || boxSize.width < 1f || boxSize.height < 1f) return
    val layoutPixels = node.textPixelSize(boxSize.height / zoom, fontName)
    val pixels = layoutPixels * zoom
    if (pixels < 2f) return
    val font = fontAt(layoutPixels)
    val face = fontName.lowercase()
    val bold = node.flagProp("bold text") || "bold" in face
    val italic = node.flagProp("italic text") || "italic" in face
    val wrap = node.flagProp("wrap")
    val halign =
        when (node.prop("text halign")?.trim('"') ?: if (centered) "center" else "left") {
            "center" -> TextAlign.Center
            "right" -> TextAlign.Right
            else -> TextAlign.Left
        }
    // Only `center` is a vertical alignment of the text: `bottom` leaves it at the top (measured)
    val valign = node.prop("text valign")?.trim('"') ?: if (centered) "center" else "top"
    val offsetParts = node.prop("text offset")?.trim()?.split(Regex("\\s+"))
    val offset =
        Offset(
            (offsetParts?.getOrNull(0)?.toFloatOrNull() ?: 0f) * zoom,
            (offsetParts?.getOrNull(1)?.toFloatOrNull() ?: 0f) * zoom,
        )
    val shadowSize = node.prop("shadow size")?.toFloatOrNull() ?: 0f
    // A shadow and an outline show only when they have a colour of their own (measured: `shadow size 3` alone draws nothing)
    val shadowColor = node.prop("shadow color")?.let { parseWidgetColor(it) } ?: Color.Transparent
    val shadowParts = node.prop("shadow offset")?.trim()?.split(Regex("\\s+"))
    val shadowOffset =
        Offset(
            (shadowParts?.getOrNull(0)?.toFloatOrNull() ?: 0f) * zoom,
            (shadowParts?.getOrNull(1)?.toFloatOrNull() ?: 0f) * zoom,
        )
    val shadow =
        if (shadowSize > 0f) {
            Shadow(shadowColor.copy(alpha = shadowColor.alpha * 0.8f), shadowOffset, shadowSize * zoom)
        } else {
            null
        }
    val outlineSize = (node.prop("outline size")?.toFloatOrNull() ?: 0f) * zoom
    val outlineColor = node.prop("outline color")?.let { parseWidgetColor(it) } ?: Color.Transparent
    if (font != null && font.covers(text)) {
        val look =
            SdfLook(
                bold = bold,
                italic = italic,
                wrap = wrap,
                halign = halign,
                centerVertically = valign == "center",
                offset = offset + contentOffset(node, zoom),
                shadowColor = shadowColor.takeIf { shadowSize > 0f && it.alpha > 0f },
                shadowSize = shadowSize * zoom,
                shadowOffset = shadowOffset,
                outlineColor = outlineColor.takeIf { outlineSize > 0f && it.alpha > 0f },
                outlineSize = outlineSize,
            )
        drawSdfText(font, text, topLeft, boxSize, pixels, color, look, if (font is SdfFont) widthScaleOf(fontName) else 1f)
        return
    }
    val style =
        TextStyle(
            color = color,
            fontSize = TextUnit(pixels, TextUnitType.Sp),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = halign,
            shadow = shadow,
            textGeometricTransform = TextGeometricTransform(scaleX = METRON_WIDTH),
        )
    val width = boxSize.width.toInt().coerceAtLeast(1)
    val layout =
        measurer.measure(
            text,
            style,
            overflow = TextOverflow.Clip,
            softWrap = wrap,
            constraints =
                Constraints(
                    minWidth =
                        if (wrap ||
                            halign != TextAlign.Left
                        ) {
                            width
                        } else {
                            0
                        },
                    maxWidth = if (wrap) width else Constraints.Infinity,
                ),
        )
    val textW = layout.size.width.toFloat()
    val textH = layout.size.height.toFloat()
    val x =
        when (halign) {
            TextAlign.Center -> topLeft.x + (boxSize.width - textW) / 2f
            TextAlign.Right -> topLeft.x + boxSize.width - textW
            else -> topLeft.x
        } + offset.x
    val y =
        when (valign) {
            "center" -> topLeft.y + (boxSize.height - textH) / 2f
            else -> topLeft.y
        } + offset.y
    // The text is cut at the left and right edge of its widget; it may reach beyond the top and bottom (an underscore does)
    clipRect(topLeft.x, topLeft.y - boxSize.height, topLeft.x + boxSize.width, topLeft.y + 2 * boxSize.height) {
        if (outlineSize > 0f) {
            // The outline is the text drawn around the letters in its own colour
            val outlined = layout.layoutInput.style.copy(color = outlineColor, shadow = null)
            val ring =
                measurer.measure(
                    text,
                    outlined,
                    overflow = TextOverflow.Clip,
                    softWrap = wrap,
                    constraints = layout.layoutInput.constraints,
                )
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx != 0 || dy != 0) drawText(ring, topLeft = Offset(x + dx * outlineSize, y + dy * outlineSize))
                }
            }
        }
        drawText(layout, topLeft = Offset(x, y))
    }
}

/** The size the text of a widget takes: the longest line by the number of lines, for `size to text`. */
internal fun measureText(
    node: EditorWidgetNode,
    fontAt: (Float) -> GameFont?,
    boxWidth: Float,
    boxHeight: Float,
    fontName: String = "",
): Size {
    val text = node.shownText()
    val pixels = node.textPixelSize(boxHeight, fontName)
    val font = fontAt(pixels)
    val widthScale = if (font is SdfFont) widthScaleOf(fontName) else 1f
    if (text.isEmpty()) return Size(0f, 0f)
    val wrap = node.flagProp("wrap")
    if (font != null && font.covers(text)) {
        val lines = breakLines(font, text, pixels, boxWidth, wrap, widthScale)
        val width = lines.maxOf { line -> line.sumOf { font.advance(it, pixels, widthScale).toDouble() }.toFloat() }
        return Size(width, lines.size * pixels)
    }
    // Without the font only a guess is possible: the letters of Metron are about half as wide as they are high
    val lines = text.split('\n')
    return Size(lines.maxOf { it.length } * pixels * 0.52f, lines.size * pixels)
}

/** A rich text keeps its letters 2 pixels off the corner of its box unless `content offset` says another distance. */
private fun contentOffset(
    node: EditorWidgetNode,
    zoom: Float,
): Offset {
    if (node.node.type != WidgetType.RICH_TEXT) return Offset.Zero
    val distance = (node.prop("content offset")?.toFloatOrNull() ?: 2f) * zoom
    return Offset(distance, distance)
}

/** How the letters of an SDF text are put in their box. */
private class SdfLook(
    val bold: Boolean,
    val italic: Boolean,
    val wrap: Boolean,
    val halign: TextAlign,
    val centerVertically: Boolean,
    val offset: Offset,
    val shadowColor: Color?,
    val shadowSize: Float,
    val shadowOffset: Offset,
    val outlineColor: Color?,
    val outlineSize: Float,
)

// Measured in Workbench on 24 px letters: a line is as high as the letters are and the baseline lies 22 px under its top
private const val BASELINE = 0.92f
private const val BOLD_GROWTH = 0.35f

/** Breaks [text] into lines: at a new line character, and at a space when [wrap] is on and the next word would pass [width]. */
private fun breakLines(
    font: GameFont,
    text: String,
    pixels: Float,
    width: Float,
    wrap: Boolean,
    widthScale: Float = 1f,
): List<String> {
    val lines = ArrayList<String>()
    for (paragraph in text.split('\n')) {
        if (!wrap) {
            lines += paragraph
            continue
        }
        var line = StringBuilder()
        var lineWidth = 0f
        for (word in paragraph.split(' ')) {
            val wordWidth = word.sumOf { font.advance(it, pixels, widthScale).toDouble() }.toFloat()
            val space = font.advance(' ', pixels, widthScale)
            if (line.isNotEmpty() && lineWidth + space + wordWidth > width) {
                lines += line.toString()
                line = StringBuilder()
                lineWidth = 0f
            }
            if (line.isNotEmpty()) {
                line.append(' ')
                lineWidth += space
            }
            line.append(word)
            lineWidth += wordWidth
        }
        lines += line.toString()
    }
    return lines
}

/** Draws [text] from the distance field of [font]: sharp at any size, with the shadow and the outline of the widget. */
private fun DrawScope.drawSdfText(
    font: GameFont,
    text: String,
    topLeft: Offset,
    boxSize: Size,
    pixels: Float,
    color: Color,
    look: SdfLook,
    widthScale: Float,
) {
    val lines = breakLines(font, text, pixels, boxSize.width, look.wrap, widthScale)
    val blockHeight = lines.size * pixels
    val top = topLeft.y + if (look.centerVertically) (boxSize.height - blockHeight) / 2f else 0f
    val glow = if (look.bold) BOLD_GROWTH * (pixels / 24f).coerceAtLeast(0.5f) else 0f

    fun pass(
        dx: Float,
        dy: Float,
        tint: Color,
        shape: GlyphLook,
    ) {
        lines.forEachIndexed { row, line ->
            val width = line.sumOf { font.advance(it, pixels, widthScale).toDouble() }.toFloat()
            var pen =
                topLeft.x +
                    when (look.halign) {
                        TextAlign.Center -> (boxSize.width - width) / 2f
                        TextAlign.Right -> boxSize.width - width
                        else -> 0f
                    } + look.offset.x + dx
            val baseline = top + row * pixels + BASELINE * pixels + look.offset.y + dy
            for (c in line) {
                font.glyph(c, pixels, shape, widthScale)?.let { g ->
                    val at = Offset(pen + g.left, baseline + g.top)

                    fun put() {
                        val filter = ColorFilter.tint(tint, BlendMode.Modulate)
                        if (g.scale == 1f && g.xScale == 1f) {
                            drawImage(g.image, topLeft = at, colorFilter = filter)
                        } else {
                            drawImage(
                                g.image,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(g.image.width, g.image.height),
                                dstOffset = IntOffset(at.x.roundToInt(), at.y.roundToInt()),
                                dstSize =
                                    IntSize(
                                        (g.image.width * g.scale * g.xScale).roundToInt().coerceAtLeast(1),
                                        (g.image.height * g.scale).roundToInt().coerceAtLeast(1),
                                    ),
                                colorFilter = filter,
                            )
                        }
                    }
                    if (look.italic) {
                        // Italics lean the letters by a fifth of their height around the baseline
                        val lean = Matrix()
                        lean[0, 1] = -0.2f
                        lean[0, 3] = 0.2f * baseline
                        withTransform({ transform(lean) }) { put() }
                    } else {
                        put()
                    }
                }
                pen += font.advance(c, pixels, widthScale)
            }
        }
    }
    clipRect(topLeft.x, topLeft.y - boxSize.height, topLeft.x + boxSize.width, topLeft.y + 2 * boxSize.height) {
        look.shadowColor?.let {
            pass(
                look.shadowOffset.x,
                look.shadowOffset.y,
                it.copy(alpha = it.alpha * color.alpha),
                GlyphLook(
                    glow,
                    (look.shadowSize / 6f).coerceIn(0f, 2f),
                ),
            )
        }
        look.outlineColor?.let { pass(0f, 0f, it.copy(alpha = it.alpha * color.alpha), GlyphLook(glow + look.outlineSize, 0f)) }
        pass(0f, 0f, color, GlyphLook(glow, 0f))
    }
}
