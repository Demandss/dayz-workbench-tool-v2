package com.github.demandss.dayz_workbench_tool.layout_editor

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.application
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState

fun main() = application {
    val scope = rememberCoroutineScope()
    val workDrivePath = remember { ConfigManager.App.load().workDrivePath }
    val state = remember { LayoutEditorState(workDrivePath, scope) }

    LayoutEditorWindow(
        state = state,
        onCloseRequest = ::exitApplication
    )
}