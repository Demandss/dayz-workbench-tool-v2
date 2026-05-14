package su.demands.dayz_workbench_tool.state

import androidx.compose.runtime.*
import su.demands.dayz_workbench_tool.config.AppConfig
import su.demands.dayz_workbench_tool.config.ConfigManager
import su.demands.dayz_workbench_tool.config.ModEntry
import su.demands.dayz_workbench_tool.model.Modification
import su.demands.dayz_workbench_tool.model.ModSide
import su.demands.dayz_workbench_tool.model.ModType

enum class Screen { MAIN, SETTINGS, MODS }

class AppState {
    var config by mutableStateOf(ConfigManager.load())
        private set

    val mods = mutableStateListOf<Modification>()

    var currentScreen by mutableStateOf(Screen.MAIN)

    init { loadModsFromConfig() }

    fun navigate(screen: Screen) { currentScreen = screen }

    fun updateConfig(newConfig: AppConfig) {
        config = newConfig
        ConfigManager.save(newConfig)
        loadModsFromConfig()
    }

    fun addMod(mod: Modification) {
        mods += mod
        persistMods()
    }

    fun removeMod(mod: Modification) {
        mods -= mod
        persistMods()
    }

    fun toggleEnabled(mod: Modification) {
        val idx = mods.indexOf(mod)
        if (idx >= 0) {
            mods[idx] = mod.copy(enabled = !mod.enabled)
            persistMods()
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
        ConfigManager.save(config)
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