package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.model.Modification
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

object ModificationManager {

    fun createSymlink(modPath: Path, gameDirPath: Path) {
        val link = gameDirPath.resolve(modPath.fileName)
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) return
        ProcessBuilder("cmd", "/c", """mklink /J "$link" "$modPath"""")
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    fun removeSymlink(modPath: Path, gameDirPath: Path) {
        val link = gameDirPath.resolve(modPath.fileName)
        if (!Files.exists(link, LinkOption.NOFOLLOW_LINKS)) return
        ProcessBuilder("cmd", "/c", "rmdir", link.toString())
            .start()
            .waitFor()
    }

    fun createAll(mods: List<Modification>, gameDirPath: String) {
        if (gameDirPath.isBlank()) return
        val gameDir = File(gameDirPath).let { if (it.isFile) it.parentFile else it }.toPath()
        mods.filter { it.enabled }
            .sortedBy { it.priority }
            .forEach { mod -> runCatching { createSymlink(Path.of(mod.path), gameDir) } }
    }

    fun removeAll(mods: List<Modification>, gameDirPath: String) {
        if (gameDirPath.isBlank()) return
        val gameDir = File(gameDirPath).let { if (it.isFile) it.parentFile else it }.toPath()
        mods.forEach { mod -> runCatching { removeSymlink(Path.of(mod.path), gameDir) } }
    }

    fun createScriptsSymlink(scriptsPath: String, gameDirPath: String) {
        if (scriptsPath.isBlank() || gameDirPath.isBlank()) return
        val gameDir = File(gameDirPath).let { if (it.isFile) it.parentFile else it }.toPath()
        runCatching { createSymlink(Path.of(scriptsPath), gameDir) }
    }

    fun removeScriptsSymlink(scriptsPath: String, gameDirPath: String) {
        if (scriptsPath.isBlank() || gameDirPath.isBlank()) return
        val gameDir = File(gameDirPath).let { if (it.isFile) it.parentFile else it }.toPath()
        runCatching { removeSymlink(Path.of(scriptsPath), gameDir) }
    }

}