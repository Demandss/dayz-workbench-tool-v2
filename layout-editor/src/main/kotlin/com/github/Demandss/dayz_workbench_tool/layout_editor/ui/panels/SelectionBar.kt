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
import com.github.demandss.dayz_workbench_tool.ui.components.DarkIconButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground

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
        DarkIconButton(AppIcon.UI_COPY, "Дублировать (Ctrl+D)", onClick = { state.duplicateSelected() })
        DarkIconButton(AppIcon.UI_TRASH, "Удалить (Delete)", onClick = { state.deleteSelected() })
        DarkIconButton(AppIcon.UI_LAYERS, "Сгруппировать в Frame (Ctrl+G), в WrapSpacer: Shift+A", onClick = { state.groupSelected() })
        if (ids.size >= 2) {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(16.dp)
                    .background(DarkenedBorder),
            )
            DarkIconButton(AppIcon.UI_ALIGNL, "По левому краю", onClick = { state.alignSelected(AlignMode.LEFT) })
            DarkIconButton(AppIcon.UI_ALIGNCH, "По центру по горизонтали", onClick = { state.alignSelected(AlignMode.CENTER_H) })
            DarkIconButton(AppIcon.UI_ALIGNR, "По правому краю", onClick = { state.alignSelected(AlignMode.RIGHT) })
            DarkIconButton(AppIcon.UI_ALIGNT, "По верхнему краю", onClick = { state.alignSelected(AlignMode.TOP) })
            DarkIconButton(AppIcon.UI_ALIGNMV, "По центру по вертикали", onClick = { state.alignSelected(AlignMode.CENTER_V) })
            DarkIconButton(AppIcon.UI_ALIGNB, "По нижнему краю", onClick = { state.alignSelected(AlignMode.BOTTOM) })
            DarkToolButton("⇔", { state.distributeSelected(true) }, tooltip = "Равные интервалы по горизонтали (от трёх виджетов)")
            DarkToolButton("⇕", { state.distributeSelected(false) }, tooltip = "Равные интервалы по вертикали (от трёх виджетов)")
        }
    }
}
