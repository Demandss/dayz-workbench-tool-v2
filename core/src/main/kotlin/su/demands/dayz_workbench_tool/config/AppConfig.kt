package su.demands.dayz_workbench_tool.config

import kotlinx.serialization.Serializable
import su.demands.dayz_workbench_tool.ui.theme.ThemeDefinition

@Serializable
data class AppConfig(
    val version: Int = 2,
    val setupDone: Boolean = false,
    val gamePath: String = "",
    val workbenchPath: String = "",
    val workDrivePath: String = "",
    val clientArgs: String = "-connect=127.0.0.1:2302 -profiles=DebugLogs\\Client -window -name=Survivor -filePatching -dologs -password=ok",
    val serverArgs: String = "-server -freezecheck -DoLogs -noPause -port=2302 -config=serverDZ.cfg -filePatching -profiles=DebugLogs\\Server",
    val sandboxArgs: String = "-connect=127.0.0.1:2302 -profiles=DebugLogs\\Client -window -name=Sandbox -filePatching -dologs -password=ok",
    val autoKillTasks: List<String> = listOf("workbenchApp.exe", "DayZDiag_x64.exe"),
    val clientMods: List<ModEntry> = emptyList(),
    val serverMods: List<ModEntry> = emptyList(),
    val lockWindowSize: Boolean = true,
    val selectedTheme: String = "dark",
    val customThemes: List<ThemeDefinition> = emptyList(),
    val windowWidth: Float = 400f,
    val windowHeight: Float = 400f,
)

@Serializable
data class ModEntry(
    val path: String,
    val enabled: Boolean = true,
    val priority: Int = 0
)