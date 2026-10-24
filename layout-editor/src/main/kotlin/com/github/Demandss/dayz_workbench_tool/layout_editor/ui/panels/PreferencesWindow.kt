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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.i18n.trKey
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.McpHost
import com.github.demandss.dayz_workbench_tool.layout_editor.mcp.McpTools
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private enum class Page(
    val key: String,
) {
    GENERAL(trKey("Общие")),
    VIEW(trKey("Отображение")),
    MCP(trKey("Подключение (MCP)")),
    ;

    /** The name of the page in the language of the interface, looked up when it is shown so that a change of language reaches it. */
    val title: String get() = tr(key)
}

/** The preferences window, with the pages down the left side like Blender's: general settings, the view and the MCP server. */
@Composable
fun PreferencesWindow(state: LayoutEditorState) {
    if (!state.preferencesOpen) return
    DialogWindow(
        onCloseRequest = { state.preferencesOpen = false },
        state = rememberDialogState(size = DpSize(980.dp, 760.dp)),
        title = tr("Настройки"),
    ) {
        var page by remember { mutableStateOf(Page.GENERAL) }
        Row(Modifier.fillMaxSize().background(FrameBackground)) {
            Column(
                Modifier
                    .width(190.dp)
                    .fillMaxHeight()
                    .background(ElementsBackground)
                    .padding(vertical = 8.dp),
            ) {
                Page.entries.forEach { p ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(if (p == page) SelectColor else Color.Transparent)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { page = p }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(p.title, color = TextForeground, fontSize = 12.sp)
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (page) {
                    Page.GENERAL -> GeneralPage(state)
                    Page.VIEW -> ViewPage(state)
                    Page.MCP -> McpPage(state)
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = TextForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Check(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .background(if (checked) SelectColor else FrameBackground)
                .border(1.dp, DarkenedBorder),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Canvas(Modifier.size(16.dp)) {
                    val stroke = 1.6.dp.toPx()
                    drawLine(
                        Color.White,
                        Offset(size.width * 0.24f, size.height * 0.52f),
                        Offset(size.width * 0.43f, size.height * 0.72f),
                        stroke,
                    )
                    drawLine(
                        Color.White,
                        Offset(size.width * 0.43f, size.height * 0.72f),
                        Offset(size.width * 0.77f, size.height * 0.30f),
                        stroke,
                    )
                }
            }
        }
        Text(label, color = TextForeground, fontSize = 12.sp)
    }
}

/** A number typed into a small field; [onValue] gets it when it is a whole number inside the limits. */
@Composable
private fun NumberField(
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Box(
        Modifier
            .width(70.dp)
            .background(FrameBackground)
            .border(1.dp, DarkenedBorder)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter(Char::isDigit).take(5)
                text.toIntOrNull()?.takeIf { it in range }?.let(onValue)
            },
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 12.sp),
            cursorBrush = SolidColor(TextForeground),
        )
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
            DarkButton(tr("Копировать"), {
                java.awt.Toolkit
                    .getDefaultToolkit()
                    .systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(text), null)
                state.notify(tr("Скопировано"), ToastKind.SUCCESS)
            })
        }
    }
}

@Composable
private fun McpPage(state: LayoutEditorState) {
    val url = McpHost.url
    Heading(tr("Подключение ассистента (MCP)"))
    Text(
        "Редактор отдаёт ассистенту, например Claude Code, свои инструменты: читать и править открытый лейаут, " +
            "менять вид и снимать скриншоты. Сервер слушает только этот компьютер.",
        color = GhostlyText,
        fontSize = 11.sp,
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(9.dp).background(if (url != null) Color(0xFF5CB85C) else Color(0xFFD06A6A)))
        Text(
            when {
                url != null -> tr("Работает: {0}", url)
                McpHost.lastError != null -> tr("Не запущен: {0}", McpHost.lastError)
                else -> tr("Выключен")
            },
            color = TextForeground,
            fontSize = 12.sp,
        )
    }
    Check(tr("Включить сервер"), state.mcpEnabled) {
        state.mcpEnabled = it
        McpHost.apply(state)
        state.savePreferences()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Порт"), color = TextForeground, fontSize = 12.sp)
        NumberField(state.mcpPort, 1024..65000) { state.mcpPort = it }
        DarkButton(tr("Применить"), {
            McpHost.apply(state)
            state.savePreferences()
        })
        Text(tr("Если порт занят, берётся следующий свободный"), color = GhostlyText, fontSize = 10.sp)
    }

    if (url != null) {
        Caption(tr("Подключить"))
        CopyRow(state, tr("Адрес"), url)
        CopyRow(state, tr("Claude Code, одна команда в терминале"), "claude mcp add --transport http dayz-layout-editor $url")
        CopyRow(state, "Файл .mcp.json проекта", """{"mcpServers":{"dayz-layout-editor":{"type":"http","url":"$url"}}}""")
    }

    Caption(tr("Инструменты ({0})", McpTools.definitions().size))
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

    Caption(tr("Последние вызовы"))
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
            Text(tr("Пока не было"), color = GhostlyText, fontSize = 10.sp)
        } else {
            McpHost.calls.forEach { Text(it, color = TextForeground, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ViewPage(state: LayoutEditorState) {
    Heading(tr("Отображение"))
    Caption(tr("Цвета"))
    Check(tr("Цвета как в Workbench 1.30 (F6)"), state.workbenchColors) {
        state.workbenchColors = it
        state.savePreferences()
    }
    Text(
        tr("Выключено: числа color читаются как sRGB, как в игре. Включено: как линейный свет, как в Workbench 1.30."),
        color = GhostlyText,
        fontSize = 10.sp,
    )
    Caption(tr("Холст"))
    Check(tr("Подписи виджетов (F7)"), state.showWidgetNames) {
        state.showWidgetNames = it
        state.savePreferences()
    }
    Caption(tr("Сетка"))
    Check(tr("Показывать сетку"), state.showGrid) {
        state.showGrid = it
        state.savePreferences()
    }
    Check(tr("Привязывать перемещаемые виджеты к сетке"), state.snapToGrid) {
        state.snapToGrid = it
        state.savePreferences()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Шаг сетки, пикселей"), color = TextForeground, fontSize = 12.sp)
        NumberField(state.gridSize, 1..512) {
            state.gridSize = it
            state.savePreferences()
        }
    }
}

@Composable
private fun GeneralPage(state: LayoutEditorState) {
    Heading(tr("Общие"))

    Caption(tr("Язык"))
    val system = Language.ofSystem()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Язык интерфейса"), color = TextForeground, fontSize = 12.sp)
        DropdownField(
            I18n.setting,
            listOf(I18n.SYSTEM) + Language.entries.map { it.code },
            { code -> if (code == I18n.SYSTEM) tr("Как в системе: {0}", system.nativeName) else Language.byCode(code)?.nativeName ?: code },
        ) {
            I18n.setting = it
            state.savePreferences()
        }
    }
    Text(
        tr("По умолчанию берётся язык системы. Язык, которого нет в списке, заменяется английским."),
        color = GhostlyText,
        fontSize = 10.sp,
    )

    Caption(tr("Открытие лайаута"))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Масштаб при открытии"), color = TextForeground, fontSize = 12.sp)
        DropdownField(
            state.zoomOnOpen,
            listOf("actual", "fit"),
            { if (it == "fit") tr("Вписать в окно") else tr("100%") },
        ) {
            state.zoomOnOpen = it
            state.savePreferences()
        }
    }

    Caption(tr("Окно"))
    Check(tr("Запоминать размер окна"), state.rememberWindow) {
        state.rememberWindow = it
        state.savePreferences()
    }
    Text(tr("Размер сохраняется при изменении и возвращается при следующем запуске."), color = GhostlyText, fontSize = 10.sp)

    Caption(tr("Правка"))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Шагов в истории (отмена и возврат)"), color = TextForeground, fontSize = 12.sp)
        NumberField(state.historyDepth, 1..200) {
            state.historyDepth = it
            state.savePreferences()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Разрешение при запуске"), color = TextForeground, fontSize = 12.sp)
        DropdownField(state.defaultResolution, Resolution.entries.toList(), {
            if (it ==
                Resolution.FIT
            ) {
                tr("Вписать в окно")
            } else {
                it.label
            }
        }) {
            state.defaultResolution = it
            state.savePreferences()
        }
    }
    Text(tr("Разрешение применяется при следующем запуске редактора."), color = GhostlyText, fontSize = 10.sp)
}
