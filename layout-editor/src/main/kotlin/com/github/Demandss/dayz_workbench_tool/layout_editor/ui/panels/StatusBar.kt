package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.pathTo
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import kotlin.math.roundToInt

private val OnAccent = Color.White

/** The line at the bottom of the window: what is selected on the left, the view and the session on the right. */
@Composable
fun StatusBar(state: LayoutEditorState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(SubSelectColor)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(leftText(state), color = OnAccent, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (state.activeTexture == null && state.activeFile != null) {
            Text(state.selectedResolution.label, color = OnAccent, fontSize = 11.sp)
            Text("${(state.zoom * 100).roundToInt()}%", color = OnAccent, fontSize = 11.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(AppIcon.UI_CLOCK, size = 14.dp, tint = OnAccent)
            Spacer(Modifier.width(5.dp))
            Text("Сессия сохранена", color = OnAccent, fontSize = 11.sp)
        }
    }
}

private fun leftText(state: LayoutEditorState): String {
    state.activeImageSet?.let { doc ->
        val picked = doc.selected.mapNotNull { doc.model.entry(it) }
        return when (picked.size) {
            0 -> {
                "${doc.model.entries.size} картинок · RefSize ${doc.model.refWidth} × ${doc.model.refHeight}"
            }

            1 -> {
                val d = picked[0].def
                "Выбрано: ${d.name} · ${d.x}, ${d.y} · ${d.width} × ${d.height}"
            }

            else -> {
                "Выбрано: ${picked.size}"
            }
        }
    }
    state.activeTexture?.let { texture ->
        val image = texture.source ?: return texture.fileName
        return "${image.width} × ${image.height} · ${texture.format.ifEmpty { "—" }}"
    }
    val doc = state.activeFile ?: return "Нет открытых файлов"
    return when (state.selectedIds.size) {
        0 -> {
            "Ничего не выбрано"
        }

        1 -> {
            val id = state.selectedIds.first()
            val node = findName(doc.root, id)
            val rect = layoutTree(doc.root, screenRect(state)).rects[id]
            if (rect == null) {
                node
            } else {
                "Выбрано: $node · ${rect.width.roundToInt()} × ${rect.height.roundToInt()}"
            }
        }

        else -> {
            "Выбрано: ${state.selectedIds.size}"
        }
    }
}

private fun findName(
    root: EditorWidgetNode,
    id: java.util.UUID,
): String =
    root
        .pathTo(id)
        ?.lastOrNull()
        ?.node
        ?.name
        ?.ifEmpty { "(root)" } ?: ""
