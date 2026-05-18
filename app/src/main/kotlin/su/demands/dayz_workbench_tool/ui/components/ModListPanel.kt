package su.demands.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.demands.dayz_workbench_tool.model.Modification
import su.demands.dayz_workbench_tool.ui.theme.DarkenedBorder
import su.demands.dayz_workbench_tool.ui.theme.FrameBackground
import su.demands.dayz_workbench_tool.ui.theme.SubSelectColor

@Composable
fun ModListPanel(
    mods: List<Modification>,
    onToggleEnabled: (Modification) -> Unit,
    onSwitchSide: (Modification) -> Unit,
    modifier: Modifier = Modifier,
    selectedMods: Set<Modification> = emptySet(),
    onSelect: ((Modification, isShift: Boolean, isCtrl: Boolean) -> Unit)? = null,
    missingPaths: Set<String> = emptySet(),
    contextMenuContent: (@Composable (mod: Modification, dismiss: () -> Unit) -> Unit)? = null,
) {
    var contextMod by remember { mutableStateOf<Modification?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SubSelectColor)
            .background(FrameBackground)
    ) {
        items(mods, key = { "${it.path}_${it.side}" }) { mod ->
            Box {
                ModItem(
                    mod = mod,
                    isSelected = mod in selectedMods,
                    isMissing = mod.path in missingPaths,
                    onSelect = onSelect?.let { cb -> { isShift, isCtrl -> cb(mod, isShift, isCtrl) } },
                    onRightClick = contextMenuContent?.let { { contextMod = mod } },
                    onToggleEnabled = { onToggleEnabled(mod) },
                    onSwitchSide = { onSwitchSide(mod) },
                )
                if (contextMenuContent != null) {
                    DropdownMenu(
                        expanded = contextMod == mod,
                        onDismissRequest = { contextMod = null },
                        modifier = Modifier.background(FrameBackground).border(1.dp, DarkenedBorder)
                    ) {
                        contextMenuContent(mod) { contextMod = null }
                    }
                }
            }
            HorizontalDivider(color = DarkenedBorder)
        }
    }
}