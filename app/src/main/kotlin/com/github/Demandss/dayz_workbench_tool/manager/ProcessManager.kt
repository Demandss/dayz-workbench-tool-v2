package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import java.io.File
import java.nio.file.Path

object ProcessManager {
    /** The command line of Workbench: the program, the arguments of the settings, the flags it always needs, and the mods. */
    internal fun workbenchCommand(config: AppConfig): List<String> {
        val exe = File(config.workbenchPath, "workbenchApp.exe").absolutePath
        val allMods =
            (config.clientMods + config.serverMods)
                .filter { it.enabled }
                .map { it.path }
                .sortedBy { if (it.contains("@")) 1 else 0 }
                .let { mods ->
                    // Workbench plugin mod is an unpacked folder, loaded first
                    if (config.workbenchPluginPath.isNotBlank() && File(config.workbenchPluginPath).isDirectory) {
                        listOf(config.workbenchPluginPath) + mods
                    } else {
                        mods
                    }
                }
        val args = mutableListOf(exe)
        args.addAll(parseArgs(config.workbenchArgs))
        // Unpacked mods are resolved through file patching since 1.30
        listOf("-filePatching", "-resolveFilePatchingUsingEnfusion=1", "-dologs").forEach { args.addIfMissing(it) }
        if (allMods.isNotEmpty()) args.add("\"-mod=${allMods.joinToString(";")}\"")
        return args
    }

    /** The folder Workbench starts in: dayz.gproj sets the project root to "./", so it is the game folder when there is one. */
    internal fun workbenchDirectory(config: AppConfig): File {
        val gameDir = File(config.gamePath).let { if (it.isFile) it.parentFile else it }
        return if (config.gamePath.isNotBlank() && gameDir.isDirectory) gameDir else File(config.workbenchPath)
    }

    fun launchWorkbench(config: AppConfig): Process {
        val workbench =
            ProcessBuilder(workbenchCommand(config))
                .directory(workbenchDirectory(config))
                .inheritIO()
                .start()
        if (config.skipWorkbenchAsserts) startAssertSkipper()
        return workbench
    }

    // Clicks "Skip" in Workbench assert dialogs for the first two minutes after launch
    private fun startAssertSkipper() {
        runCatching {
            val script = ToolsManager.toolPath("SkipWorkbenchAsserts.ps1").toString()
            ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
    }

    private fun MutableList<String>.addIfMissing(flag: String) {
        val key = flag.substringBefore('=')
        if (none { it.substringBefore('=').equals(key, ignoreCase = true) }) add(flag)
    }

    // Mods are passed by full path, -mod accepts physical folders since 1.30
    fun launchDebugClient(config: AppConfig): Process {
        val mods = config.clientMods.filter { it.enabled }.map { it.path }
        return ProcessBuilder(buildArgs(gameExe(config), config.clientArgs, mods)).inheritIO().start()
    }

    fun launchDebugServer(config: AppConfig): Process {
        val mods = config.serverMods.filter { it.enabled }.map { it.path }
        return ProcessBuilder(buildArgs(gameExe(config), config.serverArgs, mods)).inheritIO().start()
    }

    fun launchDebugClientSandbox(config: AppConfig): Process {
        val mods = config.clientMods.filter { it.enabled }.map { it.path }
        return ProcessBuilder(buildArgs(gameExe(config), "${config.clientArgs} ${config.sandboxArgs}", mods)).inheritIO().start()
    }

    private fun gameExe(config: AppConfig) = File(config.gamePath, "DayZDiag_x64.exe").absolutePath

    fun launchDebugAll(config: AppConfig): Pair<Process, Process> = launchDebugClient(config) to launchDebugServer(config)

    fun launchCustomScripts(paths: List<String>) {
        paths.filter { it.isNotBlank() }.forEach { path ->
            runCatching {
                val file = File(path)
                val builder =
                    if (path.endsWith(".bat", ignoreCase = true)) {
                        ProcessBuilder("cmd", "/c", path)
                    } else {
                        ProcessBuilder(path)
                    }
                builder.directory(file.parentFile).inheritIO().start()
            }
        }
    }

    fun killTasks(taskNames: List<String>) {
        taskNames.forEach { name ->
            runCatching { ProcessBuilder("taskkill", "/F", "/IM", name).start() }
        }
    }

    internal fun buildArgs(
        exePath: String,
        extraArgs: String,
        mods: List<String>,
    ): List<String> =
        buildList {
            add(exePath)
            parseArgs(extraArgs).forEach { add(it) }
            if (mods.isNotEmpty()) add("-mod=${mods.joinToString(";")}")
        }

    // Splits args by whitespace, respecting double-quoted tokens
    internal fun parseArgs(args: String): List<String> {
        val result = mutableListOf<String>()
        Regex(""""[^"]*"|\S+""").findAll(args.trim()).forEach { match ->
            result.add(match.value.removeSurrounding("\""))
        }
        return result
    }
}
