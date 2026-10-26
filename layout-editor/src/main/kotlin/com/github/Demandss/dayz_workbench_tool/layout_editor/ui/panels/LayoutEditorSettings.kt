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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

/*
 * The settings of the layout editor as forms of a [LayoutEditorConfig]. The same forms are in the preferences window of the editor
 * (each change is applied at once) and in the Layout Editor tab of the settings window of the app (a local copy that the Save of
 * the window stores), so there is one place for what a setting looks like and what it says.
 */

/** All the settings, one section under another: general, view and MCP. */
@Composable
fun LayoutEditorSettingsForm(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LayoutEditorGeneralSection(config, onChange)
        LayoutEditorViewSection(config, onChange)
        LayoutEditorMcpSection(config, onChange)
    }
}

/** How a layout opens, the window, the history and the resolution at start. */
@Composable
fun LayoutEditorGeneralSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
) {
    Heading(tr("Общие"))

    Caption(tr("Открытие лайаута"))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Масштаб при открытии"), color = TextForeground, fontSize = 12.sp)
        DropdownField(
            config.zoomOnOpen,
            listOf("actual", "fit"),
            { if (it == "fit") tr("Вписать в окно") else tr("100%") },
        ) { onChange(config.copy(zoomOnOpen = it)) }
    }

    Caption(tr("Окно"))
    Check(tr("Запоминать размер окна"), config.rememberWindow) { onChange(config.copy(rememberWindow = it)) }
    Text(tr("Размер сохраняется при изменении и возвращается при следующем запуске."), color = GhostlyText, fontSize = 10.sp)

    Caption(tr("Правка"))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Шагов в истории (отмена и возврат)"), color = TextForeground, fontSize = 12.sp)
        NumberField(config.undoLimit, 1..200) { onChange(config.copy(undoLimit = it)) }
    }
    val resolution = Resolution.entries.firstOrNull { it.label == config.defaultResolution } ?: Resolution.R1920x1080
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Разрешение при запуске"), color = TextForeground, fontSize = 12.sp)
        DropdownField(resolution, Resolution.entries.toList(), { if (it == Resolution.FIT) tr("Вписать в окно") else it.label }) {
            onChange(config.copy(defaultResolution = it.label))
        }
    }
    Text(tr("Разрешение применяется при следующем запуске редактора."), color = GhostlyText, fontSize = 10.sp)
}

/** Colours, the names on the canvas and the grid. */
@Composable
fun LayoutEditorViewSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
) {
    Heading(tr("Отображение"))
    Caption(tr("Цвета"))
    Check(tr("Цвета как в Workbench 1.30 (F6)"), config.workbenchColors) { onChange(config.copy(workbenchColors = it)) }
    Text(
        tr("Выключено: числа color читаются как sRGB, как в игре. Включено: как линейный свет, как в Workbench 1.30."),
        color = GhostlyText,
        fontSize = 10.sp,
    )
    Caption(tr("Холст"))
    Check(tr("Подписи виджетов (F7)"), config.showWidgetNames) { onChange(config.copy(showWidgetNames = it)) }
    Caption(tr("Сетка"))
    Check(tr("Показывать сетку"), config.showGrid) { onChange(config.copy(showGrid = it)) }
    Check(tr("Привязывать перемещаемые виджеты к сетке"), config.snapToGrid) { onChange(config.copy(snapToGrid = it)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Шаг сетки, пикселей"), color = TextForeground, fontSize = 12.sp)
        NumberField(config.gridSize, 1..512) { onChange(config.copy(gridSize = it)) }
    }
}

/** The switch and the port of the MCP server. */
@Composable
fun LayoutEditorMcpSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
) {
    Heading(tr("Подключение ассистента (MCP)"))
    Check(tr("Включить сервер"), config.mcpEnabled) { onChange(config.copy(mcpEnabled = it)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Порт"), color = TextForeground, fontSize = 12.sp)
        NumberField(config.mcpPort, 1024..65000) { onChange(config.copy(mcpPort = it)) }
        Text(tr("Если порт занят, берётся следующий свободный"), color = GhostlyText, fontSize = 10.sp)
    }
}

@Composable
internal fun Heading(text: String) {
    Text(text, color = TextForeground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun Caption(text: String) {
    Text(text.uppercase(), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun Check(
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

/** A number typed into a small field; [onValue] gets it on Enter or when the field loses the focus, held inside the limits. */
@Composable
internal fun NumberField(
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }

    fun apply() {
        val number = text.toIntOrNull()?.coerceIn(range)
        if (number == null) {
            text = value.toString()
        } else {
            text = number.toString()
            if (number != value) onValue(number)
        }
    }
    Box(
        Modifier
            .width(70.dp)
            .background(FrameBackground)
            .border(1.dp, DarkenedBorder)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { v -> text = v.filter(Char::isDigit).take(5) },
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 12.sp),
            cursorBrush = SolidColor(TextForeground),
            modifier =
                Modifier
                    .onFocusChanged { fs ->
                        if (focused && !fs.isFocused) apply()
                        focused = fs.isFocused
                    }.onKeyEvent { ev ->
                        if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown) {
                            apply()
                            true
                        } else {
                            false
                        }
                    },
        )
    }
}
