package com.github.demandss.dayz_workbench_tool.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/** Shows [text] in a small tooltip when the pointer rests on [content]. Without text it is only the content. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tip(
    text: String?,
    content: @Composable () -> Unit,
) {
    if (text.isNullOrBlank()) {
        content()
        return
    }
    TooltipArea(
        tooltip = {
            Text(
                text,
                color = TextForeground,
                fontSize = 11.sp,
                modifier =
                    Modifier
                        .widthIn(max = 260.dp)
                        .background(ElementsBackground)
                        .border(1.dp, DarkenedBorder)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        delayMillis = 500,
        content = content,
    )
}
