package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Frames per second and frame time over the last half second, from the real frame clock of the window. It asks for a
 * frame all the time while it is shown, so the number is what the window can do at most, not what an idle editor draws.
 */
@Composable
fun FpsOverlay(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        var frames = 0
        var worst = 0L
        var windowStart = last
        while (true) {
            val now = withFrameNanos { it }
            frames++
            worst = maxOf(worst, now - last)
            last = now
            if (now - windowStart >= 500_000_000L) {
                val seconds = (now - windowStart) / 1e9
                text = "%.0f FPS  ·  %.1f ms  ·  max %.1f ms".format(frames / seconds, seconds * 1000 / frames, worst / 1e6)
                frames = 0
                worst = 0
                windowStart = now
            }
        }
    }
    Column(modifier.background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text.ifEmpty { "…" }, color = Color(0xFF7CFC7C), fontSize = 11.sp)
    }
}
