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
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource

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
    Heading(stringResource(Res.string.general))

    Caption(stringResource(Res.string.opening_a_layout))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.zoom_on_open), color = TextForeground, fontSize = 12.sp)
        DropdownField(
            config.zoomOnOpen,
            listOf("actual", "fit"),
            { if (it == "fit") str(Res.string.fit_to_window) else str(Res.string.n_100) },
        ) { onChange(config.copy(zoomOnOpen = it)) }
    }

    Caption(stringResource(Res.string.window))
    Check(stringResource(Res.string.remember_the_window_size), config.rememberWindow) { onChange(config.copy(rememberWindow = it)) }
    Text(stringResource(Res.string.the_size_is_saved_when_it_changes_and), color = GhostlyText, fontSize = 10.sp)

    Caption(stringResource(Res.string.edit))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.steps_in_the_history_undo_and_redo), color = TextForeground, fontSize = 12.sp)
        NumberField(config.undoLimit, 1..200) { onChange(config.copy(undoLimit = it)) }
    }
    val resolution = Resolution.entries.firstOrNull { it.label == config.defaultResolution } ?: Resolution.R1920x1080
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.resolution_at_start), color = TextForeground, fontSize = 12.sp)
        DropdownField(resolution, Resolution.entries.toList(), {
            if (it ==
                Resolution.FIT
            ) {
                str(Res.string.fit_to_window)
            } else {
                it.label
            }
        }) {
            onChange(config.copy(defaultResolution = it.label))
        }
    }
    Text(stringResource(Res.string.the_resolution_is_applied_the_next_time), color = GhostlyText, fontSize = 10.sp)
}

/** Colours, the names on the canvas and the grid. */
@Composable
fun LayoutEditorViewSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
) {
    Heading(stringResource(Res.string.view))
    Caption(stringResource(Res.string.colours))
    Check(
        stringResource(Res.string.colours_as_in_workbench_1_30_f6),
        config.workbenchColors,
    ) { onChange(config.copy(workbenchColors = it)) }
    Text(
        stringResource(Res.string.off_the_numbers_of_color_are_read_as),
        color = GhostlyText,
        fontSize = 10.sp,
    )
    Caption(stringResource(Res.string.canvas))
    Check(stringResource(Res.string.widget_labels_f7_2), config.showWidgetNames) { onChange(config.copy(showWidgetNames = it)) }
    Caption(stringResource(Res.string.grid))
    Check(stringResource(Res.string.show_the_grid_2), config.showGrid) { onChange(config.copy(showGrid = it)) }
    Check(stringResource(Res.string.snap_the_widgets_being_moved_to_the_grid), config.snapToGrid) { onChange(config.copy(snapToGrid = it)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.grid_step_pixels), color = TextForeground, fontSize = 12.sp)
        NumberField(config.gridSize, 1..512) { onChange(config.copy(gridSize = it)) }
    }
}

/** The switch and the port of the MCP server. */
@Composable
fun LayoutEditorMcpSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
) {
    Heading(stringResource(Res.string.connecting_an_assistant_mcp))
    Check(stringResource(Res.string.enable_the_server), config.mcpEnabled) { onChange(config.copy(mcpEnabled = it)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.port), color = TextForeground, fontSize = 12.sp)
        NumberField(config.mcpPort, 1024..65000) { onChange(config.copy(mcpPort = it)) }
        Text(stringResource(Res.string.if_the_port_is_busy_the_next_free_one_is), color = GhostlyText, fontSize = 10.sp)
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
