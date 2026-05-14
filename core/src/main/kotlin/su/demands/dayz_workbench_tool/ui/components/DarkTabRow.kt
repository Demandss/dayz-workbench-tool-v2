package su.demands.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.demands.dayz_workbench_tool.ui.theme.DarkenedBorder
import su.demands.dayz_workbench_tool.ui.theme.FrameBackground
import su.demands.dayz_workbench_tool.ui.theme.SubSelectColor
import su.demands.dayz_workbench_tool.ui.theme.TextForeground

@Composable
fun DarkTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .background(if (isSelected) SubSelectColor else FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = TextForeground, fontSize = 12.sp)
            }
        }
    }
}