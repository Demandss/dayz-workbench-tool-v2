package su.demands.dayz_workbench_tool.layout_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import su.demands.dayz_workbench_tool.ui.theme.AppTheme
import su.demands.dayz_workbench_tool.ui.theme.FrameBackground
import su.demands.dayz_workbench_tool.ui.theme.GhostlyText
import su.demands.dayz_workbench_tool.ui.theme.TextForeground
import javax.imageio.ImageIO

private val appIcon by lazy {
    BitmapPainter(
        ImageIO.read(
            Thread.currentThread().contextClassLoader!!
                .getResourceAsStream("assets/logo/logo.png")
        ).toComposeImageBitmap()
    )
}

@Composable
fun LayoutEditorWindow(workDrivePath: String, onCloseRequest: () -> Unit) {
    Window(
        onCloseRequest = onCloseRequest,
        title = "DayZ Layout Editor",
        icon = appIcon,
        state = rememberWindowState(width = 900.dp, height = 600.dp),
        resizable = true
    ) {
        AppTheme {
            Box(
                modifier = Modifier.fillMaxSize().background(FrameBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DayZ Layout Editor", color = TextForeground, fontSize = 18.sp)
                    Text(
                        text = "Work drive: ${workDrivePath.ifBlank { "not set" }}",
                        color = GhostlyText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}