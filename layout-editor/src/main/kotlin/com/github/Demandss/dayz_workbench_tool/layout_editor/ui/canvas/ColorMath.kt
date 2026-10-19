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

/** The `color` value of a layout for a colour that was chosen on the screen. */
internal fun Color.toFileColor(): String =
    listOf(srgbToLinear(red), srgbToLinear(green), srgbToLinear(blue), alpha).joinToString(" ") { formatNumber(it) }
