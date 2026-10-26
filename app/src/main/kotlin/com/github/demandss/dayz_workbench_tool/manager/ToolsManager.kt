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
    /** Where the scripts of the tools are put; a test points it at a folder of its own. */
    internal var toolsDir: Path = Path(System.getenv("APPDATA")).resolve(".dayzworkbenchtool/tools")

    private val toolFiles =
        listOf(
            "StartDebugAll.bat",
            "StartDebugClient.bat",
            "StartDebugServer.bat",
            "StopDebugAll.bat",
            "SkipWorkbenchAsserts.ps1",
        )

    private val cl = ToolsManager::class.java.classLoader

    fun toolPath(name: String): Path = toolsDir.resolve(name)

    fun deploy() {
        toolsDir.createDirectories()
        for (name in toolFiles) {
            cl?.getResourceAsStream("assets/tools/$name")?.use { src ->
                toolsDir.resolve(name).outputStream().use { dst -> src.copyTo(dst) }
            }
        }
    }

    fun writeUserSettings(
        config: AppConfig,
        mods: List<Modification>,
    ) {
        toolsDir.createDirectories()
        val template =
            cl
                ?.getResourceAsStream("assets/tools/UserSettings.bat")
                ?.reader()
                ?.readText() ?: return

        // Full paths: -mod/-servermod take physical folders, no junctions in the game folder needed
        val clientMods = mods.filter { it.side == ModSide.CLIENT && it.enabled }.map { it.path }
        val serverMods = mods.filter { it.side == ModSide.SERVER && it.enabled }.map { it.path }

        val result =
            template
                .replace("&GAME_PATH&", config.gamePath)
                .replace("&WORKDRIVE_PATH&", config.workDrivePath)
                .replace("&MODS_CLIENT&", clientMods.joinToString(";"))
                .replace("&MODS_SERVER&", serverMods.joinToString(";"))
                .replace("&SERVER_ARGS&", launchArgs(config.serverArgs, splitConnect = false))
                .replace("&CLIENT_ARGS&", launchArgs(config.clientArgs, splitConnect = true))
                .replace("&CLIENT_SANDBOX_ARGS&", launchArgs(config.sandboxArgs, splitConnect = true))

        toolsDir.resolve("UserSettings.bat").writeText(result)
    }

    // Quotes every argument, splits -connect=host:port into -connect/-port and enables Enfusion file patching resolve
    internal fun launchArgs(
        args: String,
        splitConnect: Boolean,
    ): String {
        val tokens = ProcessManager.parseArgs(args).toMutableList()
        val connectIndex = tokens.indexOfFirst { it.startsWith("-connect=", ignoreCase = true) }
        if (splitConnect && connectIndex >= 0 && tokens.none { it.startsWith("-port=", ignoreCase = true) }) {
            val value = tokens[connectIndex].substringAfter('=')
            val port = value.substringAfterLast(':', "")
            if (port.isNotEmpty() && port.all { it.isDigit() }) {
                tokens[connectIndex] = "-connect=" + value.substringBeforeLast(':')
                tokens.add(connectIndex + 1, "-port=$port")
            }
        }
        if (tokens.any { it.equals("-filePatching", ignoreCase = true) } &&
            tokens.none { with(ProcessManager) { it.hasFlag(ENFUSION_FS_FLAG) } }
        ) {
            tokens.add("${ProcessManager.ENFUSION_FS_FLAG}=1")
        }
        return tokens.joinToString(" ") { "\"" + it.replace("%", "%%") + "\"" }
    }
}
