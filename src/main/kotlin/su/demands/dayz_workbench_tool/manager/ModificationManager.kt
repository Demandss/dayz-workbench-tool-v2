package su.demands.dayz_workbench_tool.manager

import su.demands.dayz_workbench_tool.model.Modification
import java.nio.file.Files
import java.nio.file.Path

object ModificationManager {

    fun createSymlink(modPath: Path, gameDirPath: Path) {
        val link = gameDirPath.resolve(modPath.fileName)
        if (!Files.exists(link) && !Files.isSymbolicLink(link)) {
            Files.createSymbolicLink(link, modPath)
        }
    }

    fun removeSymlink(modPath: Path, gameDirPath: Path) {
        val link = gameDirPath.resolve(modPath.fileName)
        if (Files.isSymbolicLink(link)) Files.delete(link)
    }

    fun createAll(mods: List<Modification>, gameDirPath: String) {
        if (gameDirPath.isBlank()) return
        val gameDir = Path.of(gameDirPath)
        mods.filter { it.enabled }
            .sortedBy { it.priority }
            .forEach { mod -> runCatching { createSymlink(Path.of(mod.path), gameDir) } }
    }

    fun removeAll(mods: List<Modification>, gameDirPath: String) {
        if (gameDirPath.isBlank()) return
        val gameDir = Path.of(gameDirPath)
        mods.forEach { mod -> runCatching { removeSymlink(Path.of(mod.path), gameDir) } }
    }
}