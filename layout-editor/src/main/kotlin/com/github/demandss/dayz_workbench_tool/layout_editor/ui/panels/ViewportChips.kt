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
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Small labels in the corner of the canvas: how it is zoomed, at what resolution, and whether Live Preview is on. */
@Composable
fun ViewportChips(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tip(stringResource(Res.string.canvas_zoom)) { Chip("${(state.zoom * 100).roundToInt()}%") }
        Tip(stringResource(Res.string.the_resolution_the_layout_is_drawn_at)) { Chip(state.selectedResolution.label) }
        if (state.livePreview) Tip(stringResource(Res.string.the_clean_view_is_on_it_is_turned_off)) { Chip("Live Preview", accent = true) }
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
