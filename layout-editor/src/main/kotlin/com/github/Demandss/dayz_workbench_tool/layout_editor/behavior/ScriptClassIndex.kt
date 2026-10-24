package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.pbo.PboArchive
import java.io.File

/** One class a layout can name in `scriptclass`: where it comes from and where its source is. */
internal class ScriptClassInfo(
    val name: String,
    /** `Игра`, the folder name of a mod, or [EDITOR] for the catalogue of the editor. */
    val source: String,
    /** The class it extends. */
    val parent: String = "",
    /** Where the declaration is: `pbo:<path in scripts.pbo>`, a file path, or empty for the catalogue of the editor. */
    val location: String = "",
)

/** A class declaration found in a script file. */
internal class ScriptDeclaration(
    val name: String,
    val parent: String,
    val source: String,
    val location: String,
)

internal const val EDITOR = "Поведения редактора"

/**
 * The script classes a widget can carry: the classes of the game and of the connected mods that extend
 * `ScriptedWidgetEventHandler`, found by reading the declarations of every script file, and the catalogue of the editor.
 */
internal object ScriptClassIndex {
    private val declaration = Regex("""^\s*class\s+(\w+)\s*(?::|extends)\s*(\w+)""", RegexOption.MULTILINE)
    private const val ROOT = "ScriptedWidgetEventHandler"

    /** The classes of the catalogue of the editor, which come first in the list. */
    val editorClasses: List<ScriptClassInfo> =
        BehaviorCatalogue.entries.map { ScriptClassInfo(it.name, EDITOR, ROOT) }

    /** Every `class A : B` or `class A extends B` in [text] (a `modded class` extends nothing new, so it is not one). */
    fun parse(
        text: String,
        source: String,
        location: String,
    ): List<ScriptDeclaration> =
        declaration
            .findAll(text)
            .map {
                ScriptDeclaration(it.groupValues[1], it.groupValues[2], source, location)
            }.toList()

    /** The declarations of every script of `dta/scripts.pbo` in the game folder, read from the archive without unpacking it. */
    fun readGame(gameFolder: String): List<ScriptDeclaration> {
        val pbo = File(gameFolder, "dta/scripts.pbo").takeIf { gameFolder.isNotBlank() && it.isFile } ?: return emptyList()
        val archive = PboArchive.open(pbo) ?: return emptyList()
        return archive.entries
            .filter { it.path.endsWith(".c", ignoreCase = true) }
            .flatMap { entry -> archive.read(entry.path)?.let { parse(String(it, Charsets.UTF_8), "Игра", "pbo:${entry.path}") }.orEmpty() }
    }

    /** The declarations of every `.c` file of the mod in [dir]. */
    fun readMod(dir: File): List<ScriptDeclaration> =
        dir
            .walkTopDown()
            .onEnter { it.name != ".git" }
            .filter { it.isFile && it.extension.equals("c", ignoreCase = true) }
            .flatMap { file -> runCatching { parse(file.readText(), dir.name, file.path) }.getOrDefault(emptyList()) }
            .toList()

    /** The classes among [declarations] that extend `ScriptedWidgetEventHandler`, however far up, by name; the catalogue of the editor first. */
    fun build(declarations: List<ScriptDeclaration>): List<ScriptClassInfo> {
        val parents = HashMap<String, String>()
        for (d in declarations) parents.putIfAbsent(d.name, d.parent)

        fun descends(name: String): Boolean {
            var current = parents[name]
            var steps = 0
            while (current != null && steps++ < 40) {
                if (current == ROOT) return true
                current = parents[current]
            }
            return false
        }
        val found =
            declarations
                .filter { descends(it.name) }
                .distinctBy { it.name }
                .sortedWith(compareBy({ it.source != "Игра" }, { it.source }, { it.name.lowercase() }))
                .map { ScriptClassInfo(it.name, it.source, it.parent, it.location) }
        val names = found.map { it.name }.toSet()
        return editorClasses.filter { it.name !in names } + found
    }

    /** The text of the class [name] in [text], from `class` to its closing brace; null when it is not declared there. */
    fun extractClass(
        text: String,
        name: String,
    ): String? {
        val start = Regex("""^\s*class\s+$name\b""", RegexOption.MULTILINE).find(text)?.range?.first ?: return null
        val open = text.indexOf('{', start)
        if (open < 0) return null
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> {
                    depth++
                }

                '}' -> {
                    if (--depth ==
                        0
                    ) {
                        return text.substring(text.lastIndexOf('\n', start).let { if (it < 0) 0 else it + 1 }, i + 1).trim('\n') + "\n"
                    }
                }
            }
        }
        return null
    }

    /** The source of [info]: the class of a file or of the game's archive, or null when it cannot be read. */
    fun sourceOf(
        info: ScriptClassInfo,
        gameFolder: String,
    ): String? {
        val text =
            when {
                info.location.startsWith("pbo:") -> {
                    val archive = PboArchive.open(File(gameFolder, "dta/scripts.pbo")) ?: return null
                    archive.read(info.location.removePrefix("pbo:"))?.let { String(it, Charsets.UTF_8) }
                }

                info.location.isNotEmpty() -> {
                    runCatching { File(info.location).readText() }.getOrNull()
                }

                else -> {
                    null
                }
            } ?: return null
        return extractClass(text, info.name)
    }
}
