package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.i18n.Language
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.McpHost
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.McpTools
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

/**
 * What the MCP server is doing, read-only: the status, the address and the token, the commands that connect an assistant, the
 * tools and the latest calls. The settings themselves (the switch, the port, the view, the history) are only in the settings
 * window of the app; the button here opens its Layout Editor tab.
 */
@Composable
fun McpConnectionWindow(state: LayoutEditorState) {
    if (!state.mcpInfoOpen) return
    DialogWindow(
        onCloseRequest = { state.mcpInfoOpen = false },
        state = rememberDialogState(size = DpSize(780.dp, 760.dp)),
        title = stringResource(Res.string.connection_mcp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(FrameBackground)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            McpPage(state)
        }
    }
}

@Composable
private fun CopyRow(
    state: LayoutEditorState,
    label: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = GhostlyText, fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .background(FrameBackground)
                    .border(1.dp, DarkenedBorder)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(text, color = TextForeground, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            DarkButton(stringResource(Res.string.copy), {
                java.awt.Toolkit
                    .getDefaultToolkit()
                    .systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(text), null)
                state.notify(str(Res.string.copied), ToastKind.SUCCESS)
            })
        }
    }
}

@Composable
private fun McpPage(state: LayoutEditorState) {
    val url = McpHost.url
    Heading(stringResource(Res.string.connecting_an_assistant_mcp))
    Text(
        "Редактор отдаёт ассистенту, например Claude Code, свои инструменты: читать и править открытый лейаут, " +
            "менять вид и снимать скриншоты. Сервер слушает только этот компьютер.",
        color = GhostlyText,
        fontSize = 11.sp,
    )

    val error = McpHost.lastError
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).background(if (url != null) Color(0xFF5CB85C) else Color(0xFFD06A6A)))
        Text(
            when {
                url != null -> stringResource(Res.string.running_x, url)
                error != null -> stringResource(Res.string.not_running_x, error)
                else -> stringResource(Res.string.disabled)
            },
            color = TextForeground,
            fontSize = 12.sp,
        )
    }
    state.openAppSettings?.let { open ->
        DarkButton(stringResource(Res.string.connection_settings_the_layout_editor), { open() })
    }

    if (url != null) {
        Caption(stringResource(Res.string.connect))
        val token = McpHost.token.orEmpty()
        CopyRow(state, stringResource(Res.string.address), url)
        CopyRow(state, stringResource(Res.string.token), token)
        CopyRow(
            state,
            stringResource(Res.string.claude_code_one_command_in_the_terminal),
            "claude mcp add --transport http dayz-layout-editor $url --header \"Authorization: Bearer $token\"",
        )
        CopyRow(
            state,
            "Файл .mcp.json проекта",
            """{"mcpServers":{"dayz-layout-editor":{"type":"http","url":"$url","headers":{"Authorization":"Bearer $token"}}}}""",
        )
    }

    Caption(stringResource(Res.string.tools_x, McpTools.definitions().size))
    Column(
        Modifier
            .fillMaxWidth()
            .background(FrameBackground)
            .border(1.dp, DarkenedBorder)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        McpTools.definitions().forEach { tool ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tool["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    color = TextForeground,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(150.dp),
                )
                Text(
                    tool["description"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        .orEmpty()
                        .substringBefore(". ")
                        .take(120),
                    color = GhostlyText,
                    fontSize = 10.sp,
                )
            }
        }
    }

    Caption(stringResource(Res.string.recent_calls))
    Column(
        Modifier
            .fillMaxWidth()
            .height(
                110.dp,
            ).background(FrameBackground)
            .border(1.dp, DarkenedBorder)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (McpHost.calls.isEmpty()) {
            Text(stringResource(Res.string.none_yet), color = GhostlyText, fontSize = 10.sp)
        } else {
            McpHost.calls.forEach { Text(it, color = TextForeground, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Heading(text: String) {
    Text(text, color = TextForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
}
