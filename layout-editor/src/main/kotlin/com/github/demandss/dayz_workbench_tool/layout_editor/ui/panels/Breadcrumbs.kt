package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.pathTo
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.widgetIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource

/** Where the selected widget sits in the tree. A click on a step selects that ancestor. Shown for one selected widget. */
@Composable
fun Breadcrumbs(state: LayoutEditorState) {
    val doc = state.activeFile ?: return
    // The bar always keeps its height, so the canvas under it does not jump when a widget gets selected
    val id = state.selectedIds.singleOrNull()
    val path = id?.let { doc.root.pathTo(it) }.orEmpty()

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(FrameBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            path.forEachIndexed { index, node ->
                if (index > 0) AppIconView(AppIcon.UI_RIGHT, size = 12.dp, tint = GhostlyText)
                val current = index == path.lastIndex
                Tip(
                    if (current) {
                        stringResource(
                            Res.string.selected_widget,
                        )
                    } else {
                        stringResource(Res.string.select_x, node.node.name.ifEmpty { "(root)" })
                    },
                ) {
                    Row(
                        Modifier
                            .background(if (current) SelectColor else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                state.selectedIds.clear()
                                state.selectedIds.add(node.id)
                            }.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconView(widgetIcon(node.node.type), size = 14.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(node.node.name.ifEmpty { "(root)" }, color = TextForeground, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DarkenedBorder))
    }
}
