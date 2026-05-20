package com.github.demandss.dayz_workbench_tool.state

import androidx.compose.runtime.*
import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.ModEntry
import com.github.demandss.dayz_workbench_tool.manager.ModificationManager
import com.github.demandss.dayz_workbench_tool.manager.ToolsManager
import com.github.demandss.dayz_workbench_tool.model.Modification
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import java.nio.file.Path

enum class Screen { MAIN, SETTINGS, MODS }

class AppState {
    var config by mutableStateOf(ConfigManager.App.load())
        private set

    val mods = mutableStateListOf<Modification>()

    var currentScreen by mutableStateOf(Screen.MAIN)
    var showLayoutEditor by mutableStateOf(false)

    init { loadModsFromConfig() }

    fun navigate(screen: Screen) { currentScreen = screen }

    fun updateConfig(newConfig: AppConfig) {
        if (config.scriptsPath != newConfig.scriptsPath || config.gamePath != newConfig.gamePath) {
            ModificationManager.removeScriptsSymlink(config.scriptsPath, config.gamePath)
            ModificationManager.createScriptsSymlink(newConfig.scriptsPath, newConfig.gamePath)
        }
        config = newConfig
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

    private fun symlinkOp(mod: Modification, create: Boolean) {
        val gameDir = config.gamePath
        if (gameDir.isBlank()) return
        runCatching {
            val modPath = Path.of(mod.path)
            val gameDirPath = Path.of(gameDir)
            if (create) ModificationManager.createSymlink(modPath, gameDirPath)
            else ModificationManager.removeSymlink(modPath, gameDirPath)
        }
    }

    private fun ModEntry.toModification(side: ModSide) = Modification(
        path = path,
        side = side,
        type = if ("@" in path) ModType.WORKSHOP else ModType.LOCAL,
        enabled = enabled,
        priority = priority
    )

    private fun Modification.toEntry() = ModEntry(path, enabled, priority)
}