package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode

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
internal fun EditorWidgetNode.textPixelSize(boxHeight: Float): Float {
    val exact = flagProp("exact text")
    val set = prop("exact text size")?.toFloatOrNull()
    // A button ignores `exact text`: its text follows the height of the button (measured: 70 px high, 24 asked, 68 drawn)
    if (exact && set != null && node.type != WidgetType.BUTTON) return set
    // `text_proportion` is the share of the widget height the letters take. A text widget fills it by default, the rich and
    // multiline ones take half of it (measured in Workbench on 16, 24 and 40 px high widgets)
    val proportion =
        prop("text_proportion")?.toFloatOrNull()
            ?: if (node.type == WidgetType.TEXT || node.type == WidgetType.BUTTON) 1f else 0.5f
    return boxHeight * proportion
}

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
) {
    val text = node.shownText()
    if (text.isEmpty() || boxSize.width < 1f || boxSize.height < 1f) return
    val pixels = node.textPixelSize(boxSize.height / zoom) * zoom
    if (pixels < 2f) return
    val font =
        node
            .prop("font")
            ?.trim('"')
            .orEmpty()
            .lowercase()
    val bold = node.flagProp("bold text") || "bold" in font
    val italic = node.flagProp("italic text")
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
    val shadowColor = node.prop("shadow color")?.let { parseWidgetColor(it) } ?: Color.Black
    val shadowParts = node.prop("shadow offset")?.trim()?.split(Regex("\\s+"))
    val shadowOffset =
        Offset(
            (shadowParts?.getOrNull(0)?.toFloatOrNull() ?: 1f) * zoom,
            (shadowParts?.getOrNull(1)?.toFloatOrNull() ?: 1f) * zoom,
        )
    val shadow =
        if (shadowSize > 0f) {
            Shadow(shadowColor.copy(alpha = shadowColor.alpha * 0.8f), shadowOffset, shadowSize * zoom)
        } else {
            null
        }
    val outlineSize = (node.prop("outline size")?.toFloatOrNull() ?: 0f) * zoom
    val outlineColor = node.prop("outline color")?.let { parseWidgetColor(it) } ?: Color.Black
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
