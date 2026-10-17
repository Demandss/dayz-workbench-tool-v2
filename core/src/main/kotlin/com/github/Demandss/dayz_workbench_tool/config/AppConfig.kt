@file:Suppress("ktlint:standard:max-line-length")

package com.github.demandss.dayz_workbench_tool.config

import com.github.demandss.dayz_workbench_tool.ui.theme.ThemeDefinition
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AppConfig(
    val version: Int = 2,
    val setupDone: Boolean = false,
    val gamePath: String = "",
    val workbenchPath: String = "",
    val workDrivePath: String = "",
    val scriptsPath: String = "",
    val clientArgs: String = "-connect=127.0.0.1:2302 -profiles=DebugLogs\\Client -window -name=Survivor -filePatching -dologs",
    val serverArgs: String = "-server -freezecheck -DoLogs -noPause -port=2302 -config=serverDZ.cfg -filePatching -profiles=DebugLogs\\Server",
    val sandboxArgs: String = "-connect=127.0.0.1:2302 -profiles=DebugLogs\\Client -window -name=Sandbox -filePatching -dologs",
    val workbenchArgs: String = "",
    val workbenchPluginPath: String = "",
    val autoKillTasks: List<String> = listOf("workbenchApp.exe", "DayZDiag_x64.exe"),
    val customWorkbenchScripts: List<String> = emptyList(),
    val clientMods: List<ModEntry> = emptyList(),
    val serverMods: List<ModEntry> = emptyList(),
    val lockWindowSize: Boolean = false,
    val adaptiveScale: Boolean = true,
    val selectedTheme: String = "dark",
    val customThemes: List<ThemeDefinition> = emptyList(),
    val windowWidth: Float = 400f,
    val windowHeight: Float = 400f,
) {
    // Falls back to <work drive root>\scripts when no path is set
    fun effectiveScriptsPath(): String {
        if (scriptsPath.isNotBlank()) return scriptsPath
        val root = runCatching { File(workDrivePath).toPath().root?.toString() }.getOrNull() ?: return ""
        return root.trimEnd('\\', '/') + "\\scripts"
    }
}

@Serializable
data class ModEntry(
    val path: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
)
