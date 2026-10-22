package com.github.demandss.dayz_workbench_tool

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.application
import com.github.demandss.dayz_workbench_tool.layout_editor.LayoutEditorWindow
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.manager.ModificationManager
import com.github.demandss.dayz_workbench_tool.manager.ProcessManager
import com.github.demandss.dayz_workbench_tool.manager.ToolsManager
import com.github.demandss.dayz_workbench_tool.state.AppState
import com.github.demandss.dayz_workbench_tool.ui.MainWindow
import com.github.demandss.dayz_workbench_tool.ui.theme.resolveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main() =
    application {
        val state = remember { AppState() }

        remember {
            val hook =
                Thread {
                    runCatching {
                        ModificationManager.removeAll(state.mods.toList(), state.config.gamePath)
                        ModificationManager.removeScriptsSymlink(state.config.effectiveScriptsPath(), state.config.gamePath)
                        ProcessManager.killTasks(state.config.autoKillTasks)
                    }
                }
            Runtime.getRuntime().addShutdownHook(hook)
        }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                ToolsManager.deploy()
                if (state.config.setupDone) {
                    ToolsManager.writeUserSettings(state.config, state.mods.toList())
                    ModificationManager.createAll(state.mods.toList(), state.config.gamePath)
                    ModificationManager.createScriptsSymlink(state.config.effectiveScriptsPath(), state.config.gamePath)
                }
            }
        }

        MainWindow(state, onCloseRequest = ::exitApplication)

        if (state.showLayoutEditor) {
            val scope = rememberCoroutineScope()
            val leState =
                remember {
                    val mods = (state.config.clientMods + state.config.serverMods).filter { it.enabled }.map { it.path }.distinct()
                    LayoutEditorState(state.config.workDrivePath, scope, state.config.gamePath, mods)
                }
            val leTheme =
                remember(state.config.selectedTheme, state.config.customThemes) {
                    resolveTheme(state.config.selectedTheme, state.config.customThemes)
                }
            LayoutEditorWindow(state = leState, themeColors = leTheme, onCloseRequest = { state.showLayoutEditor = false })
        }
    }
