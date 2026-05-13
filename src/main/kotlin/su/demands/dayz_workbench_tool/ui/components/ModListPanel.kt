package su.demands.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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
    selected: Modification? = null,
    onSelect: ((Modification) -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, SubSelectColor)
            .background(FrameBackground)
    ) {
        items(mods, key = { "${it.path}_${it.side}" }) { mod ->
            ModItem(
                mod = mod,
                isSelected = mod == selected,
                onSelect = onSelect?.let { cb -> { cb(mod) } },
                onToggleEnabled = { onToggleEnabled(mod) },
                onSwitchSide = { onSwitchSide(mod) }
            )
            androidx.compose.material3.HorizontalDivider(color = DarkenedBorder)
        }
    }
}