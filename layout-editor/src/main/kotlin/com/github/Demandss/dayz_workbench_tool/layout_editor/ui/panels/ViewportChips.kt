package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlin.math.roundToInt

/** Small labels in the corner of the canvas: how it is zoomed, at what resolution, and whether Live Preview is on. */
@Composable
fun ViewportChips(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tip(tr("Масштаб холста")) { Chip("${(state.zoom * 100).roundToInt()}%") }
        Tip(tr("Разрешение, на котором рисуется layout")) { Chip(state.selectedResolution.label) }
        if (state.livePreview) Tip(tr("Чистый вид включён. Выключается кнопкой Live Preview")) { Chip("Live Preview", accent = true) }
    }
}

@Composable
private fun Chip(
    text: String,
    accent: Boolean = false,
) {
    Text(
        text,
        color = if (accent) Color.White else TextForeground,
        fontSize = 11.sp,
        modifier =
            Modifier
                .background(if (accent) SubSelectColor else ElementsBackground)
                .border(1.dp, if (accent) SubSelectColor else DarkenedBorder)
                .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
