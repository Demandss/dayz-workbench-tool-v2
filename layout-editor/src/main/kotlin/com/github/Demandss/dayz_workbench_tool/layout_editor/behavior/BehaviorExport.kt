package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ToastKind
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** One script class of the catalogue, whichever kind it is. */
internal class BehaviorEntry(
    val name: String,
    val group: String,
    val summary: String,
    val params: List<BehaviorParam>,
    val spawner: Spawner?,
)

/** Every script class the editor knows, in the order the menu shows them. */
internal object BehaviorCatalogue {
    private val groups =
        linkedMapOf(
            "Наведение и нажатие" to
                listOf(
                    "HoverScale",
                    "PressScale",
                    "PressOffset",
                    "HoverColor",
                    "HoverShow",
                    "HoverImage",
                    "HoverText",
                ),
            "Анимации" to listOf("Blink", "FadeIn", "Shake", "ProgressTween", "CountUp"),
            "Выбор и ввод" to listOf("SelectGroup", "Collapse", "NumberStepper", "EditFilter", "DragMove"),
        )

    val entries: List<BehaviorEntry> =
        groups.flatMap { (group, names) ->
            names.mapNotNull { name ->
                Behaviors.all.firstOrNull { it.name == name }?.let { BehaviorEntry(name, group, it.summary, it.params, null) }
                    ?: Spawners.all.firstOrNull { it.name == name }?.let { BehaviorEntry(name, group, it.summary, it.params, it) }
            }
        }

    fun find(name: String): BehaviorEntry? = entries.firstOrNull { it.name == name }
}

private fun valuesOf(
    entry: BehaviorEntry,
    given: Map<String, String>,
): LinkedHashMap<String, String> {
    val out = LinkedHashMap<String, String>()
    for (param in entry.params) out[param.name] = paramText(given, param)
    return out
}

/**
 * What goes onto the clipboard for the class [entry] named [className]: the script, the layout fragment for classes that build
 * widgets, and what to write in the layout. [params] are the fields the widget already has, the rest take the defaults.
 */
internal fun behaviorExportText(
    entry: BehaviorEntry,
    className: String,
    params: Map<String, String>,
    mod: String,
): String {
    val values = valuesOf(entry, params)
    val layoutPath = "$mod/GUI/layout/Behaviors/$className.layout"
    val script = ScriptSource.of(entry.name, className, layoutPath) ?: return ""
    return buildString {
        append("// ===== 1. Script class: $mod/scripts/3_Game/$mod/GUI/$className.c =====\n")
        entry.params.forEach { append("// ${it.name} (${it.default.ifEmpty { "none" }}): ${it.description}\n") }
        append('\n').append(script)
        entry.spawner?.let { spawner ->
            append("\n// ===== 2. Layout fragment the class builds its widgets from: $layoutPath =====\n")
            append("// Uses only widgets and the Empty style of the game, so it needs no styles or imagesets of the mod.\n\n")
            append(spawner.fragment(SpawnContext(values, emptyMap())))
        }
        append("\n// ===== ${if (entry.spawner == null) "2" else "3"}. In the layout, on the widget =====\n")
        if (entry.spawner != null) append("// An empty frame: the class builds everything that is inside it.\n")
        append("scriptclass \"$className\"\n{\n ScriptParamsClass {\n")
        values.forEach { (key, value) -> append("  $key \"$value\"\n") }
        append(" }\n}\n")
    }
}

/**
 * Puts the class [name] on the selected widget (with the default fields, or the ones it already has) and copies the script, the
 * layout fragment and the lines for the layout. With nothing selected it only copies.
 */
internal fun LayoutEditorState.useBehavior(name: String) {
    val entry = BehaviorCatalogue.find(name) ?: return
    val doc = activeFile ?: return
    val className = modClassPrefix(doc.path) + entry.name
    // The folder of the mod in the game's path of the layout; a layout outside of a mod has only its file name there
    val mod = gameLayoutPath(doc.path).takeIf { '/' in it }?.substringBefore('/') ?: "MyMod"
    val selected = selectedIds.singleOrNull()?.let { doc.root.findNode(it) }
    val widget = selected?.takeIf { it.id != doc.root.id }
    val existing =
        widget
            ?.takeIf { it.scriptClassName() == className }
            ?.node
            ?.scriptParams
            .orEmpty()

    if (widget != null) {
        pushUndo(activeFileIndex, doc.root, "scriptclass = $className")
        val fields = valuesOf(entry, existing)
        updateActiveRoot(
            doc.root.updateNode(widget.id) { n ->
                n.copy(
                    node =
                        n.node.copy(
                            properties = LinkedHashMap(n.node.properties).also { it["scriptclass"] = "\"$className\"" },
                            scriptParams = fields,
                        ),
                )
            },
        )
    }
    val text = behaviorExportText(entry, className, existing, mod)
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        .onFailure {
            notify("Не удалось скопировать: ${it.message ?: it.javaClass.simpleName}", ToastKind.ERROR)
            return
        }
    notify(
        if (widget != null) "$className поставлен на «${widget.node.name}», скрипт в буфере" else "Скрипт $className в буфере",
        ToastKind.SUCCESS,
    )
}
