package com.github.demandss.dayz_workbench_tool.state

import androidx.compose.runtime.*
import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.LayoutEditorConfig
import com.github.demandss.dayz_workbench_tool.config.ModEntry
import com.github.demandss.dayz_workbench_tool.config.withChanges
import com.github.demandss.dayz_workbench_tool.i18n.I18n
import com.github.demandss.dayz_workbench_tool.manager.ModificationManager
import com.github.demandss.dayz_workbench_tool.manager.ToolsManager
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.model.Modification
import java.nio.file.Path

enum class Screen { MAIN, SETTINGS, MODS }

class AppState {
    var config by mutableStateOf(ConfigManager.App.load())
        private set

    val mods = mutableStateListOf<Modification>()

    var currentScreen by mutableStateOf(Screen.MAIN)
    var showLayoutEditor by mutableStateOf(false)

    init {
        // The language was a setting of the editor before: whoever chose one keeps it
        if (config.language == null) {
            val carried = ConfigManager.LayoutEditor.readRawString("language") ?: I18n.SYSTEM
            config = config.copy(language = carried)
            ConfigManager.App.save(config)
        }
        I18n.setting = config.language ?: I18n.SYSTEM
        loadModsFromConfig()
    }

    fun navigate(screen: Screen) {
        currentScreen = screen
    }

    /** The tab the settings window opens on: 0 Global, 3 Layout Editor (the editor asks for it). */
    var settingsTab by mutableStateOf(0)

    /** Brings the settings window of the app to the Layout Editor tab. */
    fun openLayoutEditorSettings() {
        settingsTab = 3
        navigate(Screen.SETTINGS)
    }

    /** Changes when the settings of the layout editor were stored from the settings window, so that an open editor reads them. */
    var layoutEditorSettingsTick by mutableStateOf(0)
        private set

    /**
     * Stores the settings of the layout editor that were edited in the settings window: only those that [edited] changed
     * against [base] (what the window showed when it was opened). The rest stays as it is on the disk, because the editor writes
     * the window size, the sessions and its toggles itself while the window is open.
     */
    fun saveLayoutEditorSettings(
        base: LayoutEditorConfig,
        edited: LayoutEditorConfig,
    ) {
        ConfigManager.LayoutEditor.save(ConfigManager.LayoutEditor.load().withChanges(base, edited))
        layoutEditorSettingsTick++
    }

    fun updateConfig(newConfig: AppConfig) {
        if (config.effectiveScriptsPath() != newConfig.effectiveScriptsPath() || config.gamePath != newConfig.gamePath) {
            ModificationManager.removeScriptsSymlink(config.effectiveScriptsPath(), config.gamePath)
            ModificationManager.createScriptsSymlink(newConfig.effectiveScriptsPath(), newConfig.gamePath)
        }
        config = newConfig
        I18n.setting = newConfig.language ?: I18n.SYSTEM
        ConfigManager.App.save(newConfig)
        loadModsFromConfig()
        ToolsManager.writeUserSettings(newConfig, mods.toList())
    }

    fun addMod(mod: Modification) {
        mods += mod
        persistMods()
        if (mod.enabled) symlinkOp(mod, create = true)
    }

    fun removeMod(mod: Modification) {
        mods -= mod
        persistMods()
        symlinkOp(mod, create = false)
    }

    fun toggleEnabled(mod: Modification) {
        val idx = mods.indexOf(mod)
        if (idx >= 0) {
            mods[idx] = mod.copy(enabled = !mod.enabled)
            persistMods()
            symlinkOp(mod, create = !mod.enabled)
        }
    }

    fun switchSide(mod: Modification) {
        val idx = mods.indexOf(mod)
        if (idx >= 0) {
            mods[idx] = mod.copy(side = if (mod.side == ModSide.CLIENT) ModSide.SERVER else ModSide.CLIENT)
            persistMods()
        }
    }

    private fun loadModsFromConfig() {
        mods.clear()
        config.clientMods.forEach { mods += it.toModification(ModSide.CLIENT) }
        config.serverMods.forEach { mods += it.toModification(ModSide.SERVER) }
    }

    private fun persistMods() {
        val clientEntries = mods.filter { it.side == ModSide.CLIENT }.map { it.toEntry() }
        val serverEntries = mods.filter { it.side == ModSide.SERVER }.map { it.toEntry() }
        config = config.copy(clientMods = clientEntries, serverMods = serverEntries)
        ConfigManager.App.save(config)
        ToolsManager.writeUserSettings(config, mods.toList())
    }

    private fun symlinkOp(
        mod: Modification,
        create: Boolean,
    ) {
        val gameDir = config.gamePath
        if (gameDir.isBlank()) return
        runCatching {
            val modPath = Path.of(mod.path)
            val gameDirPath = Path.of(gameDir)
            if (create) {
                ModificationManager.createSymlink(modPath, gameDirPath)
            } else {
                ModificationManager.removeSymlink(modPath, gameDirPath)
            }
        }
    }

    private fun ModEntry.toModification(side: ModSide) =
        Modification(
            path = path,
            side = side,
            type = if ("@" in path) ModType.WORKSHOP else ModType.LOCAL,
            enabled = enabled,
            priority = priority,
        )

    private fun Modification.toEntry() = ModEntry(path, enabled, priority)
}
