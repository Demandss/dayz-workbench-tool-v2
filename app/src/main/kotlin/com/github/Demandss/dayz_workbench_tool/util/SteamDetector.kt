package com.github.demandss.dayz_workbench_tool.util

import java.io.File

object SteamDetector {

    data class Paths(val gamePath: String = "", val workbenchPath: String = "")

    fun detect(): Paths {
        val steamPath = findSteamPath() ?: return Paths()
        val libraries = findLibraryPaths(steamPath)

        var gamePath = ""
        var workbenchPath = ""

        for (lib in libraries) {
            if (gamePath.isEmpty()) {
                gamePath = File(lib, "steamapps/common/DayZ/DayZDiag_x64.exe")
                    .takeIf { it.exists() }?.absolutePath ?: ""
            }
            if (workbenchPath.isEmpty()) {
                workbenchPath = File(lib, "steamapps/common/DayZ Tools/Bin/Workbench/workbenchApp.exe")
                    .takeIf { it.exists() }?.absolutePath ?: ""
            }
            if (gamePath.isNotEmpty() && workbenchPath.isNotEmpty()) break
        }

        return Paths(gamePath, workbenchPath)
    }

    private fun findSteamPath(): String? =
        queryRegistry("HKCU\\Software\\Valve\\Steam", "SteamPath")
            ?: queryRegistry("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath")

    private fun queryRegistry(key: String, valueName: String): String? = runCatching {
        val proc = ProcessBuilder("reg", "query", key, "/v", valueName)
            .redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        Regex("""$valueName\s+REG_SZ\s+(.+)""").find(output)
            ?.groupValues?.get(1)?.trim()?.replace("/", "\\")
    }.getOrNull()

    private fun findLibraryPaths(steamPath: String): List<String> {
        val paths = mutableListOf(steamPath)
        runCatching {
            val vdf = File(steamPath, "steamapps/libraryfolders.vdf").readText()
            Regex(""""path"\s+"([^"]+)"""").findAll(vdf).forEach { match ->
                val path = match.groupValues[1].replace("\\\\", "\\")
                if (path != steamPath) paths.add(path)
            }
        }
        return paths
    }
}