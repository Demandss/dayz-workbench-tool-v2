package su.demands.dayz_workbench_tool.layout_editor

import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import su.demands.dayz_workbench_tool.config.ConfigManager

fun main() = application {
    val workDrivePath = remember { ConfigManager.App.load().workDrivePath }

    LayoutEditorWindow(
        workDrivePath = workDrivePath,
        onCloseRequest = ::exitApplication
    )
}