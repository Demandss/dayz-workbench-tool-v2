package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Light as the file stores it (linear) to what the screen shows (sRGB), and back. */
internal fun linearToSrgb(c: Float): Float = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f

internal fun srgbToLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

/** Hue in degrees, saturation and value from 0 to 1. */
internal data class Hsv(
    val h: Float,
    val s: Float,
    val v: Float,
)

internal fun Color.toHsv(): Hsv {
    val mx = max(red, max(green, blue))
    val mn = min(red, min(green, blue))
    val d = mx - mn
    val h =
        when {
            d == 0f -> 0f
            mx == red -> 60f * (((green - blue) / d) % 6f)
            mx == green -> 60f * ((blue - red) / d + 2f)
            else -> 60f * ((red - green) / d + 4f)
        }
    return Hsv(if (h < 0f) h + 360f else h, if (mx == 0f) 0f else d / mx, mx)
}

internal fun hsvToColor(
    h: Float,
    s: Float,
    v: Float,
    alpha: Float = 1f,
): Color {
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) =
        when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    return Color(r + m, g + m, b + m, alpha)
}

/** `#RRGGBB` for the colour as it is shown. */
internal fun Color.toHex(): String =
    "#%02X%02X%02X".format((red * 255f).roundToInt(), (green * 255f).roundToInt(), (blue * 255f).roundToInt())

/** Reads `#RRGGBB` or `RRGGBB`, or null when it is not a colour. */
internal fun parseHex(text: String): Color? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || t.any { it !in "0123456789abcdefABCDEF" }) return null
    val n = t.toInt(16)
    return Color(((n shr 16) and 0xFF) / 255f, ((n shr 8) and 0xFF) / 255f, (n and 0xFF) / 255f)
}

private val NAMED =
    mapOf(
        "black" to 0x000000,
        "white" to 0xFFFFFF,
        "red" to 0xFF0000,
        "green" to 0x008000,
        "blue" to 0x0000FF,
        "yellow" to 0xFFFF00,
        "orange" to 0xFFA500,
        "purple" to 0x800080,
        "gray" to 0x808080,
        "grey" to 0x808080,
        "cyan" to 0x00FFFF,
        "magenta" to 0xFF00FF,
        "pink" to 0xFFC0CB,
        "brown" to 0xA52A2A,
        "lime" to 0x00FF00,
    )

/** A number that may end with a percent sign: `50%` is `0.5` of [full], `128` is `128`. */
private fun number(
    token: String,
    full: Float,
): Float? {
    val t = token.trim()
    return if (t.endsWith("%")) t.dropLast(1).toFloatOrNull()?.let { it / 100f * full } else t.toFloatOrNull()
}

/**
 * A colour as it is copied from a browser or a design tool: `#abc`, `#aabbcc`, `#aabbccdd`, `rgb(12, 34, 56)`,
 * `rgba(12 34 56 / 50%)`, `hsl(210, 50%, 40%)`, a plain `12, 34, 56` or `12 34 56`, or a basic colour name. Null when it is
 * none of those. A colour that has an alpha part brings it, any other has alpha 1.
 */
internal fun parseColorText(text: String): Color? {
    val t = text.trim().trimEnd(';').lowercase()
    if (t.isEmpty()) return null
    NAMED[t]?.let { return Color((it shr 16 and 0xFF) / 255f, (it shr 8 and 0xFF) / 255f, (it and 0xFF) / 255f) }

    // Hex, with 3, 4, 6 or 8 digits
    val hex = t.removePrefix("#")
    if ((t.startsWith("#") || hex.all { it in "0123456789abcdef" }) && hex.length in setOf(3, 4, 6, 8) &&
        hex.all { it in "0123456789abcdef" }
    ) {
        val full = if (hex.length <= 4) hex.map { "$it$it" }.joinToString("") else hex
        val n = full.toLong(16)
        return if (full.length == 6) {
            Color(((n shr 16) and 0xFF) / 255f, ((n shr 8) and 0xFF) / 255f, (n and 0xFF) / 255f)
        } else {
            Color(((n shr 24) and 0xFF) / 255f, ((n shr 16) and 0xFF) / 255f, ((n shr 8) and 0xFF) / 255f, (n and 0xFF) / 255f)
        }
    }

    val function = Regex("^(rgba?|hsla?)\\((.*)\\)$").find(t)
    val kind = function?.groupValues?.get(1)?.take(3)
    val body = function?.groupValues?.get(2) ?: t
    val parts = body.split(Regex("[\\s,/]+")).filter { it.isNotEmpty() }
    if (parts.size !in 3..4) return null
    if (kind == null && parts.size != 3) return null
    val alpha = parts.getOrNull(3)?.let { number(it, 1f) }?.coerceIn(0f, 1f) ?: 1f

    return if (kind == "hsl") {
        val h = parts[0].removeSuffix("deg").toFloatOrNull() ?: return null
        val sat = number(parts[1], 1f) ?: return null
        val light = number(parts[2], 1f) ?: return null
        hslToColor(((h % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f), alpha)
    } else {
        val r = number(parts[0], 255f) ?: return null
        val g = number(parts[1], 255f) ?: return null
        val b = number(parts[2], 255f) ?: return null
        Color((r / 255f).coerceIn(0f, 1f), (g / 255f).coerceIn(0f, 1f), (b / 255f).coerceIn(0f, 1f), alpha)
    }
}

private fun hslToColor(
    h: Float,
    s: Float,
    l: Float,
    alpha: Float,
): Color {
    val v = l + s * min(l, 1f - l)
    val sv = if (v == 0f) 0f else 2f * (1f - l / v)
    return hsvToColor(h, sv, v, alpha)
}

/** The `color` value of a layout for a colour that was chosen on the screen. */
internal fun Color.toFileColor(): String =
    listOf(srgbToLinear(red), srgbToLinear(green), srgbToLinear(blue), alpha).joinToString(" ") { formatNumber(it) }
