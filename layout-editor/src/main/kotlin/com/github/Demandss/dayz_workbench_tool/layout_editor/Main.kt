package com.github.demandss.dayz_workbench_tool.layout_editor

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.application
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import java.io.File

/**
 * An optional first argument overrides the work drive path from the app settings,
 * so a test stand can be opened without touching the saved config.
 * Further arguments are files (.layout, .edds, .png) to open on start; without them the previous session is restored.
 */
fun main(args: Array<String>) =
    application {
        val scope = rememberCoroutineScope()
        val workDrivePath =
            remember {
                args.firstOrNull()?.takeIf { File(it).isDirectory } ?: ConfigManager.App.load().workDrivePath
            }
        val state =
            remember {
                LayoutEditorState(workDrivePath, scope).also { s ->
                    val files = args.drop(1)
                    if (files.isEmpty()) {
                        s.restoreSession()
                    } else {
                        files.forEach { path ->
                            if (path.endsWith(".layout", ignoreCase = true)) s.openFile(path) else s.openTexture(path)
                        }
                    }
                }
            }

        LayoutEditorWindow(
            state = state,
            onCloseRequest = ::exitApplication,
        )
    }
