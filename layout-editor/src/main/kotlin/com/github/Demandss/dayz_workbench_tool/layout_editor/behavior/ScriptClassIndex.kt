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
    /** The class has a method `OnWidgetScriptInit` of its own, which is what the game calls on a script class of a widget. */
    val hasInit: Boolean = false,
)

internal const val EDITOR = "Поведения редактора"

/**
 * The script classes a widget can carry: the classes of the game and of the connected mods that extend
 * `ScriptedWidgetEventHandler`, found by reading the declarations of every script file, and the catalogue of the editor.
 */
internal object ScriptClassIndex {
    // A class line, with or without a parent; a `modded` one is matched too so that its body is not taken for the previous class
    private val declaration = Regex("""^[ \t]*(modded[ \t]+)?class[ \t]+(\w+)(?:[ \t]*(?::|extends)[ \t]*(\w+))?""", RegexOption.MULTILINE)
    private val initMethod = Regex("""\bOnWidgetScriptInit\s*\(""")
    private const val ROOT = "ScriptedWidgetEventHandler"

    /** The classes of the catalogue of the editor, which come first in the list. */
    val editorClasses: List<ScriptClassInfo> =
        BehaviorCatalogue.entries.map { ScriptClassInfo(it.name, EDITOR, ROOT) }

    /**
     * Every `class A`, `class A : B` and `class A extends B` in [text], with whether the body up to the next class has an
     * `OnWidgetScriptInit` (a `modded class` adds to a class that is declared elsewhere, so it is not a declaration).
     */
    fun parse(
        text: String,
        source: String,
        location: String,
    ): List<ScriptDeclaration> {
        val found = declaration.findAll(text).toList()
        return found.mapIndexedNotNull { i, m ->
            if (m.groupValues[1].isNotEmpty()) return@mapIndexedNotNull null
            val body = text.substring(m.range.last + 1, found.getOrNull(i + 1)?.range?.first ?: text.length)
            ScriptDeclaration(m.groupValues[2], m.groupValues[3], source, location, initMethod.containsMatchIn(body))
        }
    }

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

    /**
     * The classes among [declarations] the game can start on a widget, which is those with an `OnWidgetScriptInit` of their own or
     * from a class above them, whatever they extend (the menus of single layouts have none); the catalogue of the editor first.
     */
    fun build(declarations: List<ScriptDeclaration>): List<ScriptClassInfo> {
        val parents = HashMap<String, String>()
        val inits = HashSet<String>()
        for (d in declarations) {
            parents.putIfAbsent(d.name, d.parent)
            if (d.hasInit) inits += d.name
        }

        fun runsInit(name: String): Boolean {
            var current: String? = name
            var steps = 0
            while (!current.isNullOrEmpty() && steps++ < 40) {
                if (current in inits) return true
                current = parents[current]
            }
            return false
        }
        val found =
            declarations
                .filter { runsInit(it.name) }
                .distinctBy { it.name }
                .sortedWith(compareBy({ it.source != "Игра" }, { it.source }, { it.name.lowercase() }))
                .map { ScriptClassInfo(it.name, it.source, it.parent, it.location) }
        val names = found.map { it.name }.toSet()
        return editorClasses.filter { it.name !in names } + found
    }

    // What the classes do, for the tooltip of the list; the editor's own classes first, then the vanilla ones a layout often uses
    private val about =
        mapOf(
            "HoverScale" to "Увеличивает виджет, пока курсор над ним.",
            "PressScale" to "Уменьшает виджет, пока кнопка мыши зажата.",
            "PressOffset" to "Сдвигает виджет, пока кнопка мыши зажата.",
            "HoverColor" to "Красит виджет в другой цвет при наведении.",
            "HoverShow" to "Показывает другой виджет при наведении, иначе он скрыт.",
            "HoverImage" to "Меняет картинку ImageWidget при наведении.",
            "HoverText" to "Меняет цвет текста при наведении.",
            "Blink" to "Плавно мигает: прозрачность ходит между полной и минимальной.",
            "FadeIn" to "Плавно проявляет виджет при показе лайаута.",
            "Shake" to "Трясёт виджет по клику.",
            "ProgressTween" to "Плавно заполняет прогресс-бар до значения.",
            "CountUp" to "Отсчитывает число в тексте до значения.",
            "SelectGroup" to "Ряд вариантов, из которых включён один. Строит виджеты в пустом фрейме.",
            "Collapse" to "Заголовок, который открывает и закрывает другой виджет. Строит виджеты в пустом фрейме.",
            "NumberStepper" to "Число с кнопками минус и плюс. Строит виджеты в пустом фрейме.",
            "EditFilter" to "Пропускает в поле ввода только нужные символы и ограничивает длину.",
            "DragMove" to "Двигает родителя (окно), когда тянут за этот виджет.",
            "SizeToChild" to "Подгоняет размер виджета под вложенный виджет m_ChildName, с отступами. Играется в Live Preview.",
            "SizeToParent" to "Делает размер виджета долей родителя по выбранным осям. Играется в Live Preview.",
            "AutoHeightSpacer" to
                "Выстраивает детей рядами и берёт высоту стопки (AlignChilds, Gap, MinHeight, MaxHeight). Играется в Live Preview.",
            "NoticeSpacer" to "AutoHeightSpacer, который пересчитывается по событиям. Играется в Live Preview.",
            "HorizontalSpacer" to "Делит ширину поровну между детьми, с Border и Gap. Играется в Live Preview.",
            "VerticalSpacer" to "Делит высоту поровну между детьми, с border и gap. Играется в Live Preview.",
            "TabberUI" to "Вкладки: кнопки сверху и панели под ними.",
            "Tabber" to "Простые вкладки: переключает панели по кнопкам.",
            "HoverEffect" to "Подсвечивает виджет красным и увеличивает при наведении.",
            "Bouncer" to "Покачивает виджет.",
            "Rotator" to "Вращает виджет.",
        )

    /** The text of the tooltip of [info]: what the class does, what it takes and where it is from. */
    fun tooltip(info: ScriptClassInfo): String {
        val lines = mutableListOf(info.name)
        about[info.name]?.let { lines += it }
        BehaviorCatalogue.find(info.name)?.takeIf { info.source == EDITOR }?.let { entry ->
            lines += "Параметры: " + entry.params.joinToString(", ") { "${it.name} = ${it.default.ifEmpty { "\"\"" }}" }
            lines += if (entry.spawner != null) "Ставится на пустой фрейм, виджеты строит сам" else "Ставится на готовый виджет"
        }
        if (info.source != EDITOR) {
            if (info.parent.isNotEmpty() && info.parent != ROOT) lines += "Наследует ${info.parent}"
            val file =
                info.location
                    .removePrefix("pbo:")
                    .takeIf { it.isNotEmpty() }
                    ?.let { " · " + File(it).name }
                    .orEmpty()
            lines += "Источник: ${info.source}$file"
        }
        return lines.joinToString(System.lineSeparator())
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
