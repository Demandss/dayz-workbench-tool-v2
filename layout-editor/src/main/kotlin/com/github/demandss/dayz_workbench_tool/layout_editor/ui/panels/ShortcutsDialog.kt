package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import org.jetbrains.compose.resources.stringResource

/** The keys and what they do, in the language of the interface. */
@Composable
private fun shortcuts(): List<Pair<String, List<Pair<String, String>>>> =
    listOf(
        stringResource(Res.string.general) to
            listOf(
                "Ctrl+Shift+P" to stringResource(Res.string.sc_command_palette),
                "Ctrl+P" to stringResource(Res.string.sc_open_file_by_name),
                "Ctrl+," to stringResource(Res.string.settings),
                "Ctrl+F" to stringResource(Res.string.sc_find_widget_or_image),
                "Ctrl+B" to stringResource(Res.string.sc_toggle_file_browser),
                "Ctrl+Tab / Ctrl+Shift+Tab" to stringResource(Res.string.sc_next_previous_tab),
                "Ctrl+1…9" to stringResource(Res.string.sc_go_to_tab_by_number),
                "Ctrl+S" to stringResource(Res.string.save),
                "Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z" to stringResource(Res.string.sc_undo_redo),
                "Ctrl+W" to stringResource(Res.string.sc_close_tab),
                "F3" to stringResource(Res.string.sc_fps_counter),
                "F5" to "Live Preview",
                "F7" to stringResource(Res.string.sc_widget_labels),
                "F6" to stringResource(Res.string.sc_workbench_colors),
                "F1" to stringResource(Res.string.sc_this_help),
            ),
        "Layout" to
            listOf(
                "Delete" to stringResource(Res.string.delete_the_selection),
                stringResource(Res.string.sc_keys_arrows_large) to stringResource(Res.string.sc_move_selected),
                "F2" to stringResource(Res.string.rename),
                "Ctrl+C / X / V" to stringResource(Res.string.sc_copy_cut_paste),
                "Ctrl+D" to stringResource(Res.string.sc_duplicate),
                "Ctrl+Alt+C / Ctrl+Alt+V" to stringResource(Res.string.sc_copy_paste_style),
                "Ctrl+G / Ctrl+Shift+G" to stringResource(Res.string.sc_group_ungroup),
                "Shift+A" to stringResource(Res.string.sc_wrap_in_wrapspacer),
                "] / [" to stringResource(Res.string.sc_layer_forward_back),
                "Ctrl+] / Ctrl+[" to stringResource(Res.string.sc_to_front_back),
                "Shift+1 / Shift+2" to stringResource(Res.string.sc_fit_or_zoom_selected),
                "Ctrl+= / Ctrl+- / Ctrl+0" to stringResource(Res.string.sc_zoom_in_out_fit),
            ),
        "ImageSet" to
            listOf(
                "Delete" to stringResource(Res.string.sc_delete_region),
                stringResource(Res.string.sc_keys_arrows_8px) to stringResource(Res.string.sc_move_region),
                "Ctrl+D / Ctrl+A" to stringResource(Res.string.sc_duplicate_select_all),
                "F2" to stringResource(Res.string.rename),
                "Shift+1 / Shift+2" to stringResource(Res.string.sc_fit_or_selected),
                "Ctrl+= / Ctrl+- / Ctrl+0" to stringResource(Res.string.sc_zoom_in_out_fit),
                stringResource(Res.string.sc_keys_space_drag) to stringResource(Res.string.sc_move_canvas),
                stringResource(Res.string.sc_keys_ctrl_drag) to stringResource(Res.string.sc_draw_new_region),
            ),
    )

/** F1: every shortcut of the editors. */
@Composable
fun ShortcutsDialog(state: LayoutEditorState) {
    if (!state.shortcutsOpen) return
    FlatDialog(
        title = stringResource(Res.string.keyboard_shortcuts),
        onDismiss = { state.shortcutsOpen = false },
        onConfirm = { state.shortcutsOpen = false },
        width = 560.dp,
        buttons = {
            DarkButton(stringResource(Res.string.close), onClick = { state.shortcutsOpen = false }, modifier = Modifier.width(90.dp))
        },
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for ((section, rows) in shortcuts()) {
                Text(section.uppercase(), color = GhostlyText, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                for ((keys, what) in rows) {
                    Row {
                        Text(keys, color = TextForeground, fontSize = 12.sp, modifier = Modifier.width(240.dp))
                        Text(what, color = GhostlyText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
