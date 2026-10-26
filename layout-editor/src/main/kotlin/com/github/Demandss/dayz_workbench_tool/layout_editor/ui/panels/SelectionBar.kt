package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.AlignMode
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import org.jetbrains.compose.resources.stringResource

/** Quick actions over the canvas: copy and delete for the selection, and aligning when several widgets are selected. */
@Composable
fun SelectionBar(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val doc = state.activeFile ?: return
    val ids = state.selectedIds.filter { it != doc.root.id }
    if (ids.isEmpty()) return

    Row(
        modifier.background(ElementsBackground).border(1.dp, DarkenedBorder).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DarkIconButton(AppIcon.UI_COPY, stringResource(Res.string.duplicate_ctrl_d), onClick = { state.duplicateSelected() })
        DarkIconButton(AppIcon.UI_TRASH, stringResource(Res.string.delete_delete), onClick = { state.deleteSelected() })
        DarkIconButton(
            AppIcon.UI_CMD,
            stringResource(Res.string.copy_the_access_code_enforce_script),
            onClick = { state.copyScriptSnippet() },
        )
        DarkIconButton(AppIcon.UI_LAYERS, stringResource(Res.string.group_into_a_frame_ctrl_g_into_a), onClick = { state.groupSelected() })
        if (ids.size >= 2) {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(16.dp)
                    .background(DarkenedBorder),
            )
            DarkIconButton(
                AppIcon.UI_ALIGNL,
                stringResource(Res.string.by_the_left_edge),
                onClick = { state.alignSelected(AlignMode.LEFT) },
            )
            DarkIconButton(
                AppIcon.UI_ALIGNCH,
                stringResource(Res.string.horizontal_centre),
                onClick = { state.alignSelected(AlignMode.CENTER_H) },
            )
            DarkIconButton(
                AppIcon.UI_ALIGNR,
                stringResource(Res.string.by_the_right_edge),
                onClick = { state.alignSelected(AlignMode.RIGHT) },
            )
            DarkIconButton(AppIcon.UI_ALIGNT, stringResource(Res.string.by_the_top_edge), onClick = { state.alignSelected(AlignMode.TOP) })
            DarkIconButton(
                AppIcon.UI_ALIGNMV,
                stringResource(Res.string.vertical_centre),
                onClick = { state.alignSelected(AlignMode.CENTER_V) },
            )
            DarkIconButton(
                AppIcon.UI_ALIGNB,
                stringResource(Res.string.by_the_bottom_edge),
                onClick = { state.alignSelected(AlignMode.BOTTOM) },
            )
            DarkToolButton(
                "⇔",
                { state.distributeSelected(true) },
                tooltip = stringResource(Res.string.equal_horizontal_spacing_from_three),
            )
            DarkToolButton("⇕", { state.distributeSelected(false) }, tooltip = stringResource(Res.string.equal_vertical_spacing_from_three))
        }
    }
}
