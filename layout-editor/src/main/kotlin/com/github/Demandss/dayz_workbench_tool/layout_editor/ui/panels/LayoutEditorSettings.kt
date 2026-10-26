package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.CheckRow
import com.github.demandss.dayz_workbench_tool.ui.components.NumberField
import com.github.demandss.dayz_workbench_tool.ui.components.SettingHint
import com.github.demandss.dayz_workbench_tool.ui.components.SettingItem
import com.github.demandss.dayz_workbench_tool.ui.components.SettingRow
import com.github.demandss.dayz_workbench_tool.ui.components.SettingsSection
import org.jetbrains.compose.resources.stringResource

/*
 * The settings of the layout editor as sections of a [LayoutEditorConfig], for the Layout Editor group of the settings window of
 * the app. Each setting is an item with the text the search looks for, and edits a copy of the config that Save stores.
 */

/** General, View and MCP: how a layout opens, the history, the canvas and the grid, the server for an assistant. */
@Composable
fun layoutEditorSettingsSections(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
): List<SettingsSection> =
    listOf(
        generalSection(config, onChange),
        viewSection(config, onChange),
        mcpSection(config, onChange),
    )

@Composable
private fun generalSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
): SettingsSection {
    val zoom = stringResource(Res.string.zoom_on_open)
    val rememberWindow = stringResource(Res.string.remember_the_window_size)
    val history = stringResource(Res.string.steps_in_the_history_undo_and_redo)
    val resolutionLabel = stringResource(Res.string.resolution_at_start)
    val fit = stringResource(Res.string.fit_to_window)
    val actual = stringResource(Res.string.n_100)
    val resolution = Resolution.entries.firstOrNull { it.label == config.defaultResolution } ?: Resolution.R1920x1080
    return SettingsSection(
        id = "general",
        title = stringResource(Res.string.general),
        items =
            listOf(
                SettingItem(Res.string.zoom_on_open, listOf(Res.string.opening_a_layout)) {
                    SettingRow(zoom) {
                        DropdownField(
                            config.zoomOnOpen,
                            listOf("actual", "fit"),
                            { if (it == "fit") fit else actual },
                        ) { onChange(config.copy(zoomOnOpen = it)) }
                    }
                },
                SettingItem(Res.string.remember_the_window_size, listOf(Res.string.window)) {
                    Column {
                        CheckRow(rememberWindow, config.rememberWindow) { onChange(config.copy(rememberWindow = it)) }
                        SettingHint(stringResource(Res.string.the_size_is_saved_when_it_changes_and))
                    }
                },
                SettingItem(Res.string.steps_in_the_history_undo_and_redo, listOf(Res.string.edit)) {
                    SettingRow(history) { NumberField(config.undoLimit, 1..200) { onChange(config.copy(undoLimit = it)) } }
                },
                SettingItem(Res.string.resolution_at_start, listOf(Res.string.edit)) {
                    Column {
                        SettingRow(resolutionLabel) {
                            DropdownField(resolution, Resolution.entries.toList(), { if (it == Resolution.FIT) fit else it.label }) {
                                onChange(config.copy(defaultResolution = it.label))
                            }
                        }
                        SettingHint(stringResource(Res.string.the_resolution_is_applied_the_next_time))
                    }
                },
            ),
    )
}

@Composable
private fun viewSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
): SettingsSection {
    val colours = stringResource(Res.string.colours_as_in_workbench_1_30_f6)
    val names = stringResource(Res.string.widget_labels_f7_2)
    val grid = stringResource(Res.string.show_the_grid_2)
    val snap = stringResource(Res.string.snap_the_widgets_being_moved_to_the_grid)
    val step = stringResource(Res.string.grid_step_pixels)
    return SettingsSection(
        id = "view",
        title = stringResource(Res.string.view),
        items =
            listOf(
                SettingItem(Res.string.colours_as_in_workbench_1_30_f6, listOf(Res.string.colours)) {
                    Column {
                        CheckRow(colours, config.workbenchColors) { onChange(config.copy(workbenchColors = it)) }
                        SettingHint(stringResource(Res.string.off_the_numbers_of_color_are_read_as))
                    }
                },
                SettingItem(Res.string.widget_labels_f7_2, listOf(Res.string.canvas)) {
                    CheckRow(names, config.showWidgetNames) { onChange(config.copy(showWidgetNames = it)) }
                },
                SettingItem(Res.string.show_the_grid_2, listOf(Res.string.grid)) {
                    CheckRow(grid, config.showGrid) { onChange(config.copy(showGrid = it)) }
                },
                SettingItem(Res.string.snap_the_widgets_being_moved_to_the_grid, listOf(Res.string.grid)) {
                    CheckRow(snap, config.snapToGrid) { onChange(config.copy(snapToGrid = it)) }
                },
                SettingItem(Res.string.grid_step_pixels, listOf(Res.string.grid)) {
                    SettingRow(step) { NumberField(config.gridSize, 1..512) { onChange(config.copy(gridSize = it)) } }
                },
            ),
    )
}

@Composable
private fun mcpSection(
    config: LayoutEditorConfig,
    onChange: (LayoutEditorConfig) -> Unit,
): SettingsSection {
    val enable = stringResource(Res.string.enable_the_server)
    val port = stringResource(Res.string.port)
    return SettingsSection(
        id = "mcp",
        title = "MCP",
        items =
            listOf(
                SettingItem(Res.string.enable_the_server, listOf(Res.string.connecting_an_assistant_mcp), words = listOf("MCP")) {
                    CheckRow(enable, config.mcpEnabled) { onChange(config.copy(mcpEnabled = it)) }
                },
                SettingItem(Res.string.port, words = listOf("MCP")) {
                    Column(Modifier.fillMaxWidth()) {
                        SettingRow(port) { NumberField(config.mcpPort, 1024..65000) { onChange(config.copy(mcpPort = it)) } }
                        SettingHint(stringResource(Res.string.if_the_port_is_busy_the_next_free_one_is))
                    }
                },
            ),
    )
}
