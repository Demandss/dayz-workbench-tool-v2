package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.demandss.dayz_workbench_tool.ui.theme.*

@Composable
fun ColorPickerDialog(title: String, initial: Color, onDismiss: () -> Unit, onSave: (Color) -> Unit) {
    val initialHsv = remember(initial) { colorToHsv(initial) }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var sat by remember { mutableStateOf(initialHsv[1]) }
    var bri by remember { mutableStateOf(initialHsv[2]) }

    val currentColor = hsvToColor(hue, sat, bri)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .background(FrameBackground)
                .border(1.dp, DarkenedBorder)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = TextForeground, fontSize = 12.sp)

            SvPicker(hue, sat, bri) { s, v -> sat = s; bri = v }

            HueStrip(hue) { hue = it }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(currentColor)
                        .border(1.dp, SubSelectColor)
                )
                Text(
                    "#%06X".format(currentColor.toArgb() and 0xFFFFFF),
                    color = TextForeground,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Save", onClick = { onSave(currentColor) }, modifier = Modifier.weight(1f))
                DarkButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SvPicker(hue: Float, sat: Float, bri: Float, onChange: (Float, Float) -> Unit) {
    var boxWidth by remember { mutableStateOf(1) }
    var boxHeight by remember { mutableStateOf(1) }
    val hueColor = hsvToColor(hue, 1f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .border(1.dp, SubSelectColor)
            .onSizeChanged { boxWidth = it.width; boxHeight = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(
                        (down.position.x / boxWidth).coerceIn(0f, 1f),
                        1f - (down.position.y / boxHeight).coerceIn(0f, 1f)
                    )
                    drag(down.id) { change ->
                        change.consume()
                        onChange(
                            (change.position.x / boxWidth).coerceIn(0f, 1f),
                            1f - (change.position.y / boxHeight).coerceIn(0f, 1f)
                        )
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = hueColor)
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * size.width
            val cy = (1f - bri) * size.height
            drawCircle(Color.Black, radius = 7.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

@Composable
private fun HueStrip(hue: Float, onChange: (Float) -> Unit) {
    var stripWidth by remember { mutableStateOf(1) }
    val hueColors = listOf(
        Color.Red, Color.Yellow, Color.Green,
        Color(0xFF00FFFF.toInt()), Color.Blue, Color.Magenta, Color.Red
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .border(1.dp, SubSelectColor)
            .onSizeChanged { stripWidth = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange((down.position.x / stripWidth).coerceIn(0f, 1f) * 360f)
                    drag(down.id) { change ->
                        change.consume()
                        onChange((change.position.x / stripWidth).coerceIn(0f, 1f) * 360f)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(hueColors))
            val x = (hue / 360f) * size.width
            drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
            drawLine(Color.White, Offset(x, 1f), Offset(x, size.height - 1f), strokeWidth = 1f)
        }
    }
}

fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = hue / 60f
    val i = h.toInt() % 6
    val f = h - h.toInt()
    val p = value * (1f - saturation)
    val q = value * (1f - f * saturation)
    val t = value * (1f - (1f - f) * saturation)
    val (r, g, b) = when (i) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }
    return Color(r, g, b)
}

fun colorToHsv(color: Color): FloatArray {
    val r = color.red; val g = color.green; val b = color.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
    val s = if (max == 0f) 0f else delta / max
    val h = when {
        delta == 0f -> 0f
        max == r -> (60f * ((g - b) / delta) + 360f) % 360f
        max == g -> 60f * ((b - r) / delta) + 120f
        else -> 60f * ((r - g) / delta) + 240f
    }
    return floatArrayOf(h, s, max)
}