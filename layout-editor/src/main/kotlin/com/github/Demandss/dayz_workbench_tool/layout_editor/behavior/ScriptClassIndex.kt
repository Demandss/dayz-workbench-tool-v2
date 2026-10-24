package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.pbo.PboArchive
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
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

/** Where a class comes from, as the list marks it: made for the editor, a class of the game, or a class of a connected mod. */
internal enum class ScriptKind(
    val label: String,
    /** What the icon in the list says, for its tooltip. */
    val meaning: String,
    val icon: AppIcon,
) {
    CUSTOM("кастомный", "Кастомный класс редактора", AppIcon.UI_ANCHOR),
    VANILLA("ванильный", "Ванильный класс игры (файлы игры только для чтения)", AppIcon.UI_LOCK),
    MOD("мод", "Класс подключённого мода", AppIcon.FOLDER),
}

internal fun ScriptClassInfo.kind(): ScriptKind =
    when (source) {
        EDITOR -> ScriptKind.CUSTOM
        "Игра" -> ScriptKind.VANILLA
        else -> ScriptKind.MOD
    }

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

    // What the classes do, for the tooltip of the list: the editor's own classes, then those of the game
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
            "SizeToChild" to
                "Подгоняет размер виджета под вложенный виджет m_ChildName, с отступами m_HorizontalOffset и m_VerticalOffset " +
                "по осям m_ResizeHorizontal и m_ResizeVertical.",
            "SizeToParent" to "Снимает точный размер по осям m_ResizeHorizontal и m_ResizeVertical: размер становится долей родителя.",
            "AutoHeightSpacer" to "Выстраивает детей рядами и берёт высоту стопки. Параметры AlignChilds, Gap, Top, MinHeight, MaxHeight.",
            "NoticeSpacer" to "AutoHeightSpacer, который пересчитывается ещё и по событиям виджета.",
            "SpacerBase" to
                "Основа HorizontalSpacer и VerticalSpacer: считает детей и ставит им точную позицию и размер. Ячейки задают наследники.",
            "HorizontalSpacer" to "Делит ширину поровну между детьми, с отступом Border от краёв и промежутком Gap.",
            "VerticalSpacer" to "Делит высоту поровну между детьми, с отступом border от краёв и промежутком gap.",
            "HorizontalSpacerWithFixedAspect" to
                "Первый ребёнок задаёт размер ячейки, остальные ставятся правее, на ширину ячейки плюс coef от неё. " +
                "Параметры border и gap в коде не используются.",
            "RightGap" to "Делает виджет шириной родителя без gap и высотой родителя, в пикселях.",
            "Tabber" to "Простые вкладки: дети ButtonsPanel переключают дочерние панели ContentPanel, видна одна (SelectedTab).",
            "TabberUI" to "Вкладки: кнопки Tab_Control_N в Tab_Control_Container и панели Tab_N. Выбранная вкладка красная на чёрном, " +
                "при наведении так же, остальные белые.",
            "HoverEffect" to
                "По замыслу при наведении плавно увеличивает виджет на долю amount от размера (рост от левого верхнего угла) " +
                "и красит в красноватый (1, 1, 0.5, 0.5), скорость speed. Цвет не настраивается и не возвращается к прежнему, " +
                "а доходит до белого. По исходнику его Update не подключён к очереди обновлений и таймер не тикает, " +
                "поэтому в игре он, судя по коду, ничего не меняет.",
            "Bouncer" to
                "По замыслу покачивает виджет: ширина растёт, высота падает по кругу (amount, speed). Update не подключён и таймер " +
                "не тикает, по коду ничего не меняет.",
            "Rotator" to "По замыслу вращает виджет со скоростью speed. Update не подключён и таймер не тикает, по коду ничего не меняет.",
            "MainMenuButtonEffect" to
                "Кнопка в фокусе сдвигает текст вправо на amount * 4 пикселя и красит его красным, остальное время текст " +
                "белый. Реагирует на фокус кнопки, не на наведение.",
            "EmbededButtonScript" to "Ставит текст Caption на кнопку, лежащую первой внутри виджета.",
            "RadialProgressBar" to
                "Круговой прогресс из двух половин BarHider_<имя> и BarPart2_<имя>, угол задаёт скрипт через SetProgress. " +
                "В начале обе половины скрыты.",
            "ItemsCounter" to "При создании считает вложенные виджеты в NumberOfItems, ничего не рисует.",
            "ScrollBarContainer" to
                "Прокрутка: дети Content, ScrollBar и Scroller, колесо мыши и перетаскивание ползунка. Параметр Invert.",
            "InventoryGrid" to "Сетка ячеек инвентаря: предметы, перетаскивание и события приходят из игры.",
            "ItemActionsWidget" to "Подсказки действий над предметом в руках игрока, берёт предмет и ввод из игры.",
            "ActionTargetsCursor" to
                "Курсор действий у объекта, на который смотрит игрок, с иконками доступных действий. Нужны объект и действия игры.",
            "ContinuousActionProgress" to "Прогресс длительного действия игрока (поднять, ремонтировать). Нужны игрок и его действие.",
            "CrossHairSelector" to "Выбирает и показывает прицел по оружию и настройкам профиля. Нужны игрок и профиль.",
            "ProjectedCrosshair" to "Прицел, который проецируется туда, куда направлено оружие. Позицию даёт игра.",
            "RadialMenu" to "Радиальное меню выбора по секциям: мышь и геймпад, секции регистрируют свои классы. Управляется из игры.",
            "ObjectFollower" to "Ведёт виджет за экранной позицией объекта мира (SetTarget). Нужен объект игры.",
            "PasswordMenuToolbarHandler" to "Подсказки кнопок геймпада в меню ввода пароля.",
            "ConsoleToolbarWidgetHandlerBase" to "Основа подсказок кнопок геймпада внизу консольных меню.",
            "ScriptConsoleToolTipEventHandler" to "Подсказки в отладочной консоли скриптов.",
        )

    // Which classes of the game Live Preview plays: those that size and place widgets or answer the pointer, those that do nothing
    // in the source, and the rest, which need the game
    private val played =
        setOf(
            "SizeToChild",
            "SizeToParent",
            "AutoHeightSpacer",
            "NoticeSpacer",
            "HorizontalSpacer",
            "VerticalSpacer",
            "HorizontalSpacerWithFixedAspect",
            "RightGap",
            "Tabber",
            "TabberUI",
            "MainMenuButtonEffect",
            "EmbededButtonScript",
            "RadialProgressBar",
        )
    private val idle = setOf("HoverEffect", "Bouncer", "Rotator", "ItemsCounter")

    /** True when the tooltip has a description of the class [name]. */
    fun describes(name: String): Boolean = about.containsKey(name)

    /** What Live Preview does with a class of the game: plays it, shows nothing because the class does nothing, or cannot (needs the game). */
    private fun previewStatus(name: String): String? =
        when {
            name in played -> "Live Preview: играется"
            name in idle -> "Live Preview: ничего не меняется"
            about.containsKey(name) -> "Live Preview: не играется, нужны данные или управление игры"
            else -> null
        }

    /** The text of the tooltip of [info]: what the class does, what it takes and where it is from. */
    fun tooltip(info: ScriptClassInfo): String {
        val lines = mutableListOf(info.name)
        // What the icon of the row means; the icon has no tooltip of its own
        info.kind().let { lines += if (it == ScriptKind.MOD) "${it.meaning}: ${info.source}" else it.meaning }
        about[info.name]?.let { lines += it }
        BehaviorCatalogue.find(info.name)?.takeIf { info.source == EDITOR }?.let { entry ->
            lines += "Параметры: " + entry.params.joinToString(", ") { "${it.name} = ${it.default.ifEmpty { "\"\"" }}" }
            lines += if (entry.spawner != null) "Ставится на пустой фрейм, виджеты строит сам" else "Ставится на готовый виджет"
        }
        if (info.source == "Игра") previewStatus(info.name)?.let { lines += it }
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
