package su.demands.dayz_workbench_tool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.demands.dayz_workbench_tool.ui.theme.ElementsBackground
import su.demands.dayz_workbench_tool.ui.theme.FrameBackground
import su.demands.dayz_workbench_tool.ui.theme.TextForeground

@Composable
fun TitledPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .border(1.dp, ElementsBackground)
                .padding(top = 9.dp, start = 5.dp, end = 5.dp, bottom = 5.dp)
        ) {
            content()
        }
        Text(
            text = title,
            color = TextForeground,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            modifier = Modifier
                .padding(start = 8.dp)
                .background(FrameBackground)
                .padding(horizontal = 2.dp)
        )
    }
}