package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import java.io.File
import java.nio.file.Path

object ProcessManager {

    fun launchWorkbench(config: AppConfig): Process {
        val exe = File(config.workbenchPath, "workbenchApp.exe").absolutePath
        val allMods = (config.clientMods + config.serverMods)
            .filter { it.enabled }
            .map { it.path }
            .sortedBy { if (it.contains("@")) 1 else 0 }
        val args = mutableListOf(exe)
        if (allMods.isNotEmpty()) args.add("-mod=${allMods.joinToString(";")}")
        return ProcessBuilder(args)
            .directory(File(config.workbenchPath))
            .inheritIO()
            .start()
    }

    fun launchDebugClient(config: AppConfig): Process {
        val modNames = config.clientMods.filter { it.enabled }.map { modName(it.path) }
        return ProcessBuilder(buildArgs(gameExe(config), config.clientArgs, modNames)).inheritIO().start()
    }

    fun launchDebugServer(config: AppConfig): Process {
        val modNames = config.serverMods.filter { it.enabled }.map { modName(it.path) }
        return ProcessBuilder(buildArgs(gameExe(config), config.serverArgs, modNames)).inheritIO().start()
    }

    fun launchDebugClientSandbox(config: AppConfig): Process {
        val modNames = config.clientMods.filter { it.enabled }.map { modName(it.path) }
        return ProcessBuilder(buildArgs(gameExe(config), "${config.clientArgs} ${config.sandboxArgs}", modNames)).inheritIO().start()
    }

    private fun gameExe(config: AppConfig) = File(config.gamePath, "DayZDiag_x64.exe").absolutePath

    fun launchDebugAll(config: AppConfig): Pair<Process, Process> =
        launchDebugClient(config) to launchDebugServer(config)

    fun killTasks(taskNames: List<String>) {
        taskNames.forEach { name ->
            runCatching { ProcessBuilder("taskkill", "/F", "/IM", name).start() }
        }
    }

    private fun modName(path: String): String =
        Path.of(path).fileName?.toString() ?: path

    private fun buildArgs(exePath: String, extraArgs: String, mods: List<String>): List<String> =
        buildList {
            add(exePath)
            parseArgs(extraArgs).forEach { add(it) }
            if (mods.isNotEmpty()) add("-mod=${mods.joinToString(";")}")
        }

    // Splits args by whitespace, respecting double-quoted tokens
    private fun parseArgs(args: String): List<String> {
        val result = mutableListOf<String>()
        Regex(""""[^"]*"|\S+""").findAll(args.trim()).forEach { match ->
            result.add(match.value.removeSurrounding("\""))
        }
        return result
    }
}