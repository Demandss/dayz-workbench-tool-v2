package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.Modification
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

object ToolsManager {

    private val toolsDir: Path = Path(System.getenv("APPDATA")).resolve(".dayzworkbenchtool/tools")

    private val batFiles = listOf(
        "StartDebugAll.bat", "StartDebugClient.bat", "StartDebugServer.bat", "StopDebugAll.bat"
    )

    private val cl = ToolsManager::class.java.classLoader

    fun deploy() {
        toolsDir.createDirectories()
        for (name in batFiles) {
            cl?.getResourceAsStream("assets/tools/$name")?.use { src ->
                toolsDir.resolve(name).outputStream().use { dst -> src.copyTo(dst) }
            }
        }
    }

    fun writeUserSettings(config: AppConfig, mods: List<Modification>) {
        toolsDir.createDirectories()
        val template = cl?.getResourceAsStream("assets/tools/UserSettings.bat")
            ?.reader()?.readText() ?: return

        val clientMods = mods.filter { it.side == ModSide.CLIENT && it.enabled }.map { it.name }
        val serverMods = mods.filter { it.side == ModSide.SERVER && it.enabled }.map { it.name }

        val result = template
            .replace("&GAME_PATH&", config.gamePath)
            .replace("&WORKDRIVE_PATH&", config.workDrivePath)
            .replace("&MODS_CLIENT&", clientMods.joinToString(";"))
            .replace("&MODS_SERVER&", serverMods.joinToString(";"))
            .replace("&SERVER_ARGS&", config.serverArgs)
            .replace("&CLIENT_ARGS&", config.clientArgs)
            .replace("&CLIENT_SANDBOX_ARGS&", config.sandboxArgs)

        toolsDir.resolve("UserSettings.bat").writeText(result)
    }
}