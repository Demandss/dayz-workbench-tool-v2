package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SubSelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The window that picks the icon of an image widget from the imagesets of the game and of the connected mods, with a search.
 * Opens while [LayoutEditorState.iconPicker] is set; a click on an icon hands `set:NAME image:IMAGE` to it and closes the window.
 */
@Composable
fun IconPickerWindow(state: LayoutEditorState) {
    val onPick = state.iconPicker ?: return
    val nearPath = state.activeFile?.path
    DialogWindow(
        onCloseRequest = { state.iconPicker = null },
        state = rememberDialogState(size = DpSize(780.dp, 600.dp)),
        title = "Выбор иконки",
    ) {
        var query by remember { mutableStateOf("") }
        val icons by produceState(emptyList<Pair<String, String>>(), nearPath) {
            value = withContext(Dispatchers.IO) { state.allIcons(nearPath) }
        }
        val words =
            query
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        val shown =
            remember(icons, query) {
                icons.filter { (set, image) -> words.all { it in "$set $image".lowercase() } }
            }
        Column(Modifier.fillMaxSize().background(FrameBackground)) {
            SearchField(query, "Поиск по названию иконки или набора…") { query = it }
            Text(
                if (icons.isEmpty()) "Загрузка…" else "Найдено: ${shown.size} из ${icons.size}",
                color = GhostlyText,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shown.take(ICON_LIMIT), key = { (set, image) -> "$set/$image" }) { (set, image) ->
                    IconCell(state, set, image, nearPath) {
                        onPick("set:$set image:$image")
                        state.iconPicker = null
                    }
                }
            }
        }
    }
}

@Composable
private fun IconCell(
    state: LayoutEditorState,
    set: String,
    image: String,
    nearPath: String?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val piece = state.imagePiece(set, image, nearPath)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (hovered) SubSelectColor else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(64.dp).border(1.dp, DarkenedBorder)) {
            val cell = 8.dp.toPx()
            for (y in 0..(size.height / cell).toInt()) {
                for (x in 0..(size.width / cell).toInt()) {
                    drawRect(if ((x + y) % 2 == 0) Color(0xFF3A3A3A) else Color(0xFF2C2C2C), Offset(x * cell, y * cell), Size(cell, cell))
                }
            }
            if (piece != null) {
                val scale = minOf(size.width / piece.width, size.height / piece.height)
                val w = (piece.width * scale).toInt().coerceAtLeast(1)
                val h = (piece.height * scale).toInt().coerceAtLeast(1)
                drawImage(
                    piece,
                    srcSize = IntSize(piece.width, piece.height),
                    dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
                    dstSize = IntSize(w, h),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        Text(image, color = TextForeground, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(set, color = GhostlyText, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

private const val ICON_LIMIT = 1500
