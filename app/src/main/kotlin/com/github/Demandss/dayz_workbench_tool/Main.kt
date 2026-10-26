package com.github.demandss.dayz_workbench_tool

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.application
import com.github.demandss.dayz_workbench_tool.i18n.I18n
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
import java.util.Locale

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

        // The strings of the resources follow the default locale: set it from the language of the app (MainWindow draws its
        // content again when the language changes)
        val language = I18n.language
        remember(language) { Locale.setDefault(Locale.forLanguageTag(language.code)) }
        MainWindow(state, onCloseRequest = ::exitApplication)

        if (state.showLayoutEditor) {
            val scope = rememberCoroutineScope()
            val enabledMods = (state.config.clientMods + state.config.serverMods).filter { it.enabled }.map { it.path }.distinct()
            val leState =
                remember {
                    LayoutEditorState(state.config.workDrivePath, scope, state.config.gamePath, enabledMods).also { s ->
                        s.openAppSettings = { state.openLayoutEditorSettings() }
                    }
                }
            // The window is kept while the game path or the enabled mods change in the main window: the editor takes them too
            // The same for the settings of the editor stored from the Layout Editor tab of the settings window
            LaunchedEffect(state.layoutEditorSettingsTick) {
                if (state.layoutEditorSettingsTick > 0) leState.reloadSettingsFromDisk()
            }
            LaunchedEffect(state.config.workDrivePath, state.config.gamePath, enabledMods) {
                leState.updateRoots(state.config.workDrivePath, state.config.gamePath, enabledMods)
            }
            val leTheme =
                remember(state.config.selectedTheme, state.config.customThemes) {
                    resolveTheme(state.config.selectedTheme, state.config.customThemes)
                }
            LayoutEditorWindow(state = leState, themeColors = leTheme, onCloseRequest = { state.showLayoutEditor = false })
        }
    }
