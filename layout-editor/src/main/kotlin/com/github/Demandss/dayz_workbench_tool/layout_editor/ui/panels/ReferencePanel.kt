package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

    Column(
        modifier
            .width(250.dp)
            .background(FrameBackground)
            .border(1.dp, DarkenedBorder),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(ElementsBackground)
                .padding(start = 8.dp),
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
                DarkButton("Загрузить…", onClick = {
                    scope.launch {
                        val start = File(state.referencePath).parent ?: state.workDrivePath.ifBlank { null }
                        pickFile("Скриншот Workbench", "*", start)?.let { state.loadReference(it) }
                    }
                })
                if (image != null) DarkButton("Убрать", onClick = { state.clearReference() }, muted = true)
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
