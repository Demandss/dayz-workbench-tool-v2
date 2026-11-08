package com.github.demandss.dayz_workbench_tool.manager

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** The legacy vanilla scripts link; mod links are owned and reconciled by [WorkbenchWorkspace]. */
object ModificationManager {
    private val createdScriptsLinks = mutableMapOf<Path, Path>()

    @Synchronized
    fun createScriptsSymlink(
        scriptsPath: String,
        gameDirPath: String,
    ) {
        if (scriptsPath.isBlank() || gameDirPath.isBlank()) return
        runCatching {
            val scripts = Path.of(scriptsPath).toAbsolutePath().normalize()
            if (!Files.isDirectory(scripts)) return
            val target = scripts.toRealPath()
            val link = gameDirectory(gameDirPath).resolve(scripts.fileName)
            if (WindowsJunctions.inspect(link).kind == JunctionKind.MISSING) {
                WindowsJunctions.create(link, target)
                createdScriptsLinks[link] = target
            }
        }
    }

    @Synchronized
    fun removeScriptsSymlink(
        scriptsPath: String,
        gameDirPath: String,
    ) {
        if (scriptsPath.isBlank() || gameDirPath.isBlank()) return
        runCatching {
            val link = gameDirectory(gameDirPath).resolve(Path.of(scriptsPath).fileName)
            val target = createdScriptsLinks[link] ?: return
            val actual = WindowsJunctions.inspect(link)
            val actualTarget =
                actual.target
                    ?.toAbsolutePath()
                    ?.normalize()
                    ?.toString()
            if (actual.kind == JunctionKind.JUNCTION && actualTarget.equals(target.toString(), ignoreCase = true)) {
                WindowsJunctions.remove(link)
            }
            createdScriptsLinks.remove(link)
        }
    }

    private fun gameDirectory(path: String): Path =
        File(path)
            .let { if (it.isFile) it.parentFile else it }
            .toPath()
            .toRealPath()
}
