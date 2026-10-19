package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.pickFile
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Floating controls of the Workbench reference: the screenshot is laid over the work area so that the canvas
 * can be compared with what Workbench draws. It is stretched to the selected resolution, so crop it to the screen first.
 */
@Composable
fun ReferencePanel(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    if (!state.showReference) return
    val scope = rememberCoroutineScope()
    val image = state.referenceImage
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    // The panel sits 12 dp inside the corner of the canvas, dragging may move it anywhere inside that frame
    val margin = with(LocalDensity.current) { 12.dp.toPx() }

    Column(
        modifier
            .offset { IntOffset(state.referencePanelOffset.x.roundToInt(), state.referencePanelOffset.y.roundToInt()) }
            .onSizeChanged { panelSize = it }
            .width(250.dp)
            .background(FrameBackground)
            .border(1.dp, DarkenedBorder),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(ElementsBackground)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        state.referencePanelOffset =
                            clampPanelOffset(state.referencePanelOffset + drag, state.lastCanvasSize, panelSize, margin)
                    }
                }.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconView(AppIcon.UI_COMPARE, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text("Эталон Workbench", color = TextForeground, fontSize = 11.sp, modifier = Modifier.weight(1f))
            DarkIconButton(AppIcon.UI_CLOSE, "Скрыть эталон", onClick = { state.showReference = false })
        }

        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (image == null) "Скриншот не загружен" else File(state.referencePath).name,
                color = if (image == null) GhostlyText else TextForeground,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkButton("Загрузить…", tooltip = "Выбрать скриншот из Workbench", onClick = {
                    scope.launch {
                        val start = File(state.referencePath).parent ?: state.workDrivePath.ifBlank { null }
                        pickFile("Скриншот Workbench", "*", start)?.let { state.loadReference(it) }
                    }
                })
                if (image !=
                    null
                ) {
                    DarkButton("Убрать", onClick = { state.clearReference() }, muted = true, tooltip = "Убрать скриншот с холста")
                }
            }
            if (image != null) {
                Text("Прозрачность ${(state.referenceOpacity * 100).roundToInt()}%", color = GhostlyText, fontSize = 11.sp)
                Slider(
                    value = state.referenceOpacity,
                    onValueChange = { state.referenceOpacity = it },
                    onValueChangeFinished = { state.saveReferenceSettings() },
                )
                Text(
                    "Снимок ${image.width}×${image.height} растянут на выбранное разрешение",
                    color = GhostlyText,
                    fontSize = 10.sp,
                )
            } else {
                Text(
                    "Сделайте снимок лайаута в Workbench при тех же разрешении и масштабе 100%, обрежьте до экрана и загрузите сюда.",
                    color = GhostlyText,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/**
 * Keeps a panel anchored to the top-right corner of a canvas inside it: it can move left and down by what is left
 * of the canvas after its own size and the [margin] on both sides, and not past the corner.
 */
internal fun clampPanelOffset(
    offset: Offset,
    canvas: Size,
    panel: IntSize,
    margin: Float,
): Offset {
    val maxLeft = (canvas.width - panel.width - 2 * margin).coerceAtLeast(0f)
    val maxDown = (canvas.height - panel.height - 2 * margin).coerceAtLeast(0f)
    // Not -maxLeft directly: a negated 0f is -0f, which is not equal to 0f
    val minX = if (maxLeft > 0f) -maxLeft else 0f
    return Offset(offset.x.coerceIn(minX, 0f), offset.y.coerceIn(0f, maxDown))
}
