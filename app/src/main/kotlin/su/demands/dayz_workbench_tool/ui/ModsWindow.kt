package su.demands.dayz_workbench_tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import su.demands.dayz_workbench_tool.model.Modification
import su.demands.dayz_workbench_tool.model.ModSide
import su.demands.dayz_workbench_tool.state.AppState
import su.demands.dayz_workbench_tool.state.Screen
import su.demands.dayz_workbench_tool.ui.components.ModListPanel
import su.demands.dayz_workbench_tool.ui.components.PoweredByFooter
import su.demands.dayz_workbench_tool.ui.theme.*

private enum class ModFilter { ALL, CLIENT, SERVER }

@Composable
fun ModsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(ModFilter.ALL) }
    var selected by remember { mutableStateOf<Modification?>(null) }
    var path by remember { mutableStateOf("") }
    var filterMenuOpen by remember { mutableStateOf(false) }

    val visibleMods = remember(state.mods.toList(), filter) {
        when (filter) {
            ModFilter.ALL    -> state.mods.toList()
            ModFilter.CLIENT -> state.mods.filter { it.side == ModSide.CLIENT }
            ModFilter.SERVER -> state.mods.filter { it.side == ModSide.SERVER }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(FrameBackground)) {
        // Header: ← Back | Mods                     Filter ▾
        val borderColor = DarkenedBorder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(FrameBackground)
                .drawBehind {
                    drawLine(borderColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                }
        ) {
            val backSource = remember { MutableInteractionSource() }
            val isBackPressed by backSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(if (isBackPressed) SelectColor else FrameBackground)
                    .clickable(interactionSource = backSource, indication = null) { state.navigate(Screen.MAIN) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("← Back", color = TextForeground, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))

            // Filter dropdown button
            Box {
                val filterSource = remember { MutableInteractionSource() }
                val isFilterPressed by filterSource.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (isFilterPressed || filterMenuOpen) SelectColor else FrameBackground)
                        .clickable(interactionSource = filterSource, indication = null) { filterMenuOpen = true }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Filter: ${filter.name}", color = TextForeground, fontSize = 10.sp)
                }
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false },
                    modifier = Modifier.background(FrameBackground).border(1.dp, DarkenedBorder)
                ) {
                    ModFilter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.name, color = TextForeground, fontSize = 10.sp) },
                            onClick = { filter = f; selected = null; filterMenuOpen = false },
                            modifier = Modifier.background(FrameBackground).height(28.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = DarkenedBorder)

        // Control row: [path field "mod folder.."] [...] [+] [-]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, SubSelectColor)
                    .background(FrameBackground)
                    .padding(start = 5.dp),
                textStyle = TextStyle(color = TextForeground, fontSize = 10.sp),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (path.isEmpty()) Text("mod folder..", color = GhostlyText, fontSize = 10.sp)
                        innerTextField()
                    }
                }
            )
            Spacer(Modifier.width(3.dp))
            ModControlButton(onClick = {
                scope.launch {
                    val dir = path.takeIf { it.isNotBlank() }
                    pickDirectory("Select Mod Directory", dir)?.let { path = it }
                }
            }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextForeground, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(3.dp))
            ModControlButton(label = "+", onClick = {
                if (path.isNotBlank()) {
                    val side = if (filter == ModFilter.SERVER) ModSide.SERVER else ModSide.CLIENT
                    state.addMod(Modification(path = path.trim(), side = side))
                    path = ""
                    selected = null
                }
            })
            Spacer(Modifier.width(3.dp))
            ModControlButton(label = "-", onClick = {
                selected?.let { state.removeMod(it); selected = null; path = "" }
            })
        }
        HorizontalDivider(color = DarkenedBorder)

        ModListPanel(
            mods = visibleMods,
            onToggleEnabled = { state.toggleEnabled(it) },
            onSwitchSide = { state.switchSide(it) },
            modifier = Modifier.weight(1f).padding(horizontal = 5.dp, vertical = 3.dp),
            selected = selected,
            onSelect = { mod ->
                if (selected == mod) {
                    selected = null
                } else {
                    selected = mod
                    path = mod.path
                }
            },
        )

        PoweredByFooter()
    }
}

@Composable
private fun ModControlButton(label: String? = null, onClick: () -> Unit, content: (@Composable () -> Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(if (isPressed) SelectColor else ElementsBackground)
            .border(1.dp, SubSelectColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content()
        else Text(label ?: "", color = TextForeground, fontSize = 11.sp, lineHeight = 12.sp)
    }
}