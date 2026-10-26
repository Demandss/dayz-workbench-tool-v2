package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.pbo.PboArchive
import com.github.demandss.dayz_workbench_tool.resources.*
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import org.jetbrains.compose.resources.StringResource
import java.io.File

/** One class a layout can name in `scriptclass`: where it comes from and where its source is. */
internal class ScriptClassInfo(
    val name: String,
    /** [GAME_SOURCE], the folder name of a mod, or [EDITOR] for the catalogue of the editor. */
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

internal const val EDITOR = "<editor>"

/** The source of the classes of the game. Not a name a folder can have, so no mod is taken for it. */
internal const val GAME_SOURCE = "<game>"

/** The source as the list and the tooltips show it: the editor and the game by name, a mod by the name of its folder. */
internal fun sourceTitle(source: String): String =
    when (source) {
        EDITOR -> str(Res.string.script_source_editor)
        GAME_SOURCE -> str(Res.string.script_source_game)
        else -> source
    }

/** Where a class comes from, as the list marks it: made for the editor, a class of the game, or a class of a connected mod. */
internal enum class ScriptKind(
    private val labelResource: StringResource,
    /** What the icon in the list says, for its tooltip. */
    private val meaningResource: StringResource,
    val icon: AppIcon,
) {
    CUSTOM(Res.string.script_kind_custom, Res.string.script_kind_custom_meaning, AppIcon.UI_ANCHOR),
    VANILLA(Res.string.script_kind_vanilla, Res.string.script_kind_vanilla_meaning, AppIcon.UI_LOCK),
    MOD(Res.string.script_kind_mod, Res.string.script_kind_mod_meaning, AppIcon.FOLDER),
    ;

    /** The word the list shows for the kind, in the language of the interface. */
    val label: String get() = str(labelResource)

    val meaning: String get() = str(meaningResource)
}

internal fun ScriptClassInfo.kind(): ScriptKind =
    when (source) {
        EDITOR -> ScriptKind.CUSTOM
        GAME_SOURCE -> ScriptKind.VANILLA
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
            .flatMap { entry ->
                archive.read(entry.path)?.let { parse(String(it, Charsets.UTF_8), GAME_SOURCE, "pbo:${entry.path}") }.orEmpty()
            }
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
                .sortedWith(compareBy({ it.source != GAME_SOURCE }, { it.source }, { it.name.lowercase() }))
                .map { ScriptClassInfo(it.name, it.source, it.parent, it.location) }
        val names = found.map { it.name }.toSet()
        return editorClasses.filter { it.name !in names } + found
    }

    // What the classes do, for the tooltip of the list: the editor's own classes, then those of the game
    private val about: Map<String, StringResource> =
        mapOf(
            "HoverScale" to Res.string.class_about_hoverscale,
            "PressScale" to Res.string.class_about_pressscale,
            "PressOffset" to Res.string.class_about_pressoffset,
            "HoverColor" to Res.string.class_about_hovercolor,
            "HoverShow" to Res.string.class_about_hovershow,
            "HoverImage" to Res.string.class_about_hoverimage,
            "HoverText" to Res.string.class_about_hovertext,
            "Blink" to Res.string.class_about_blink,
            "FadeIn" to Res.string.class_about_fadein,
            "Shake" to Res.string.class_about_shake,
            "ProgressTween" to Res.string.class_about_progresstween,
            "CountUp" to Res.string.class_about_countup,
            "SelectGroup" to Res.string.class_about_selectgroup,
            "Collapse" to Res.string.class_about_collapse,
            "NumberStepper" to Res.string.class_about_numberstepper,
            "EditFilter" to Res.string.class_about_editfilter,
            "DragMove" to Res.string.class_about_dragmove,
            "SizeToChild" to Res.string.class_about_sizetochild,
            "SizeToParent" to Res.string.class_about_sizetoparent,
            "AutoHeightSpacer" to Res.string.class_about_autoheightspacer,
            "NoticeSpacer" to Res.string.class_about_noticespacer,
            "SpacerBase" to Res.string.class_about_spacerbase,
            "HorizontalSpacer" to Res.string.class_about_horizontalspacer,
            "VerticalSpacer" to Res.string.class_about_verticalspacer,
            "HorizontalSpacerWithFixedAspect" to Res.string.class_about_horizontalspacerwithfixedaspect,
            "RightGap" to Res.string.class_about_rightgap,
            "Tabber" to Res.string.class_about_tabber,
            "TabberUI" to Res.string.class_about_tabberui,
            "HoverEffect" to Res.string.class_about_hovereffect,
            "Bouncer" to Res.string.class_about_bouncer,
            "Rotator" to Res.string.class_about_rotator,
            "MainMenuButtonEffect" to Res.string.class_about_mainmenubuttoneffect,
            "EmbededButtonScript" to Res.string.class_about_embededbuttonscript,
            "RadialProgressBar" to Res.string.class_about_radialprogressbar,
            "ItemsCounter" to Res.string.class_about_itemscounter,
            "ScrollBarContainer" to Res.string.class_about_scrollbarcontainer,
            "InventoryGrid" to Res.string.class_about_inventorygrid,
            "ItemActionsWidget" to Res.string.class_about_itemactionswidget,
            "ActionTargetsCursor" to Res.string.class_about_actiontargetscursor,
            "ContinuousActionProgress" to Res.string.class_about_continuousactionprogress,
            "CrossHairSelector" to Res.string.class_about_crosshairselector,
            "ProjectedCrosshair" to Res.string.class_about_projectedcrosshair,
            "RadialMenu" to Res.string.class_about_radialmenu,
            "ObjectFollower" to Res.string.class_about_objectfollower,
            "PasswordMenuToolbarHandler" to Res.string.class_about_passwordmenutoolbarhandler,
            "ConsoleToolbarWidgetHandlerBase" to Res.string.class_about_consoletoolbarwidgethandlerbase,
            "ScriptConsoleToolTipEventHandler" to Res.string.class_about_scriptconsoletooltipeventhandler,
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
            name in played -> str(Res.string.tip_live_preview_played)
            name in idle -> str(Res.string.tip_live_preview_idle)
            about.containsKey(name) -> str(Res.string.tip_live_preview_needs_game)
            else -> null
        }

    /** The text of the tooltip of [info]: what the class does, what it takes and where it is from. */
    fun tooltip(info: ScriptClassInfo): String {
        val lines = mutableListOf(info.name)
        // What the icon of the row means; the icon has no tooltip of its own
        info.kind().let { lines += if (it == ScriptKind.MOD) "${it.meaning}: ${info.source}" else it.meaning }
        about[info.name]?.let { lines += str(it) }
        BehaviorCatalogue.find(info.name)?.takeIf { info.source == EDITOR }?.let { entry ->
            lines += str(Res.string.tip_parameters, entry.params.joinToString(", ") { "${it.name} = ${it.default.ifEmpty { "\"\"" }}" })
            lines += if (entry.spawner != null) str(Res.string.tip_goes_on_empty_frame) else str(Res.string.tip_goes_on_ready_widget)
        }
        if (info.source == GAME_SOURCE) previewStatus(info.name)?.let { lines += it }
        if (info.source != EDITOR) {
            if (info.parent.isNotEmpty() && info.parent != ROOT) lines += str(Res.string.tip_inherits, info.parent)
            val file =
                info.location
                    .removePrefix("pbo:")
                    .takeIf { it.isNotEmpty() }
                    ?.let { " · " + File(it).name }
                    .orEmpty()
            lines += str(Res.string.tip_source, sourceTitle(info.source), file)
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
