package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.Tip
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.fileIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** What the viewport shows when nothing is open: a hint, a button and the files used last. */
@Composable
fun EmptyState(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
    onOpenFile: () -> Unit,
) {
    Box(modifier.fillMaxSize().background(FrameBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIconView(AppIcon.LAYOUT, size = 44.dp)
            Text(stringResource(Res.string.open_a_layout_or_a_texture), color = TextForeground, fontSize = 14.sp)
            Text(stringResource(Res.string.double_click_a_file_in_the_tree_or_use), color = GhostlyText, fontSize = 11.sp)
            DarkButton(
                stringResource(Res.string.open_file),
                onClick = onOpenFile,
                modifier = Modifier.width(140.dp),
                tooltip = stringResource(Res.string.choose_a_layout_edds_or_an_image),
            )

            val recent = state.recentFiles.take(5)
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.recent), color = GhostlyText, fontSize = 10.sp)
                Column(Modifier.width(300.dp)) {
                    recent.forEach { path -> RecentRow(path) { state.openAny(path) } }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    path: String,
    onClick: () -> Unit,
) {
    val file = File(path)
    Tip(path) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconView(fileIcon(file.name), size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(file.name, color = TextForeground, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(file.parentFile?.name.orEmpty(), color = GhostlyText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
