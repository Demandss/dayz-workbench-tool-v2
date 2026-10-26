package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toEditorNode
import java.util.UUID

/** What a spawner sees: the fields of its `ScriptParamsClass` and what the user has done to it in Live Preview. */
internal class SpawnContext(
    private val params: Map<String, String>,
    val state: Map<String, String>,
) {
    fun text(param: BehaviorParam): String = paramText(params, param)

    fun int(param: BehaviorParam): Int = text(param).toIntOrNull() ?: param.default.toInt()

    fun color(param: BehaviorParam): Color = parseHexColor(text(param)) ?: parseHexColor(param.default) ?: Color.White
}

/**
 * A script class that sits on an empty frame and builds its widgets inside it, from a layout fragment the user takes along with
 * the class. Only the widgets and styles the game has are used, so that it works in any mod. In Live Preview the editor puts the
 * same fragment into the frame and plays the clicks with [onClick].
 */
internal class Spawner(
    val name: String,
    val summary: String,
    val params: List<BehaviorParam>,
    /** The layout fragment with the current [SpawnContext.state]; the default state gives the one that is exported. */
    val fragment: (SpawnContext) -> String,
    /** A click on the widget called `clicked` inside the fragment: changes [state]. */
    val onClick: (clicked: String, state: MutableMap<String, String>, context: SpawnContext) -> Unit,
    /** What the spawner does to widgets of the layout that are already there, by their names. */
    val effects: (SpawnContext) -> Map<String, BehaviorEffect> = { emptyMap() },
)

private val ITEMS = BehaviorParam("Items", "Easy|Normal|Hard", "Texts of the choices, separated by |")
private val SELECTED = BehaviorParam("Selected", "0", "Number of the choice that is on at the start, from 0")
private val COLOR_ON = BehaviorParam("OnColor", "#E8622A", "Colour of the chosen item, #RRGGBB or #RRGGBBAA")
private val COLOR_OFF = BehaviorParam("OffColor", "#3A3D4A", "Colour of the other items, #RRGGBB or #RRGGBBAA")
private val TITLE = BehaviorParam("Title", "Section", "Text of the header")
private val BODY = BehaviorParam("Body", "", "Name of the widget the header opens and closes")
private val OPEN = BehaviorParam("Open", "1", "1 when the body is shown at the start, 0 when it is closed")
private val COLOR_HEADER = BehaviorParam("HeaderColor", "#3A3D4A", "Colour of the header, #RRGGBB or #RRGGBBAA", listOf("Color"))

private val VALUE = BehaviorParam("Value", "5", "Number at the start")
private val MIN = BehaviorParam("Min", "0", "Lowest number")
private val MAX = BehaviorParam("Max", "10", "Highest number")
private val INCREMENT = BehaviorParam("Increment", "1", "How much a click adds or takes", listOf("Step"))
private val COLOR_STEPPER = BehaviorParam("FieldColor", "#3A3D4A", "Colour of the field, #RRGGBB or #RRGGBBAA", listOf("Color"))

private fun layoutColor(color: Color) = "${color.red} ${color.green} ${color.blue} ${color.alpha}"

private fun share(value: Float) = "%.5f".format(java.util.Locale.ROOT, value)

/** The script classes that build their own widgets. */
internal object Spawners {
    val all: List<Spawner> =
        listOf(
            Spawner(
                "SelectGroup",
                "A row of choices where one is on: a radio group, a list of difficulties, a switch of views",
                listOf(ITEMS, SELECTED, COLOR_ON, COLOR_OFF),
                fragment = { c ->
                    val items =
                        c
                            .text(ITEMS)
                            .split('|')
                            .map { it.trim().replace("\"", "") }
                            .filter { it.isNotEmpty() }
                    val chosen = (c.state["selected"]?.toIntOrNull() ?: c.int(SELECTED)).coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    buildString {
                        append("FrameWidgetClass SelectGroupRoot {\n size 1 1\n {\n")
                        items.forEachIndexed { i, text ->
                            val at = "  position ${share(i / items.size.toFloat())} 0\n  size ${share(1f / items.size)} 1\n"
                            val color = if (i == chosen) c.color(COLOR_ON) else c.color(COLOR_OFF)
                            append(" ImageWidgetClass Back$i {\n$at  color ${layoutColor(color)}\n }\n")
                            append(" ButtonWidgetClass Item$i {\n$at  text \"$text\"\n  style Empty\n }\n")
                        }
                        append(" }\n}\n")
                    }
                },
                onClick = { clicked, state, _ ->
                    if (clicked.startsWith("Item")) clicked.removePrefix("Item").toIntOrNull()?.let { state["selected"] = it.toString() }
                },
            ),
            Spawner(
                "Collapse",
                "A header that opens and closes another widget when it is clicked",
                listOf(TITLE, BODY, OPEN, COLOR_HEADER),
                fragment = { c ->
                    val open = (c.state["open"] ?: c.text(OPEN)) != "0"
                    val mark = if (open) "v" else ">"
                    val title = c.text(TITLE).replace("\"", "")
                    "FrameWidgetClass CollapseRoot {\n size 1 1\n {\n" +
                        " ImageWidgetClass HeaderBack {\n  size 1 1\n  color ${layoutColor(c.color(COLOR_HEADER))}\n }\n" +
                        " ButtonWidgetClass Header {\n  size 1 1\n  text \"$mark  $title\"\n  style Empty\n }\n }\n}\n"
                },
                onClick = { clicked, state, c ->
                    if (clicked == "Header") state["open"] = if ((state["open"] ?: c.text(OPEN)) != "0") "0" else "1"
                },
                effects = { c ->
                    val body = c.text(BODY)
                    if (body.isEmpty()) emptyMap() else mapOf(body to BehaviorEffect(visible = (c.state["open"] ?: c.text(OPEN)) != "0"))
                },
            ),
            Spawner(
                "NumberStepper",
                "A number with a minus and a plus button: a quantity, a count of items, a page",
                listOf(VALUE, MIN, MAX, INCREMENT, COLOR_STEPPER),
                fragment = { c ->
                    val low = c.int(MIN)
                    val high = c.int(MAX).coerceAtLeast(low)
                    val value = (c.state["value"]?.toIntOrNull() ?: c.int(VALUE)).coerceIn(low, high)

                    // The three parts share the width of the frame: 25% for the minus, 50% for the number, 25% for the plus
                    fun part(
                        kind: String,
                        name: String,
                        left: String,
                        width: String,
                        vararg lines: String,
                    ) = listOf(" ${kind}WidgetClass $name {", "  position $left 0", "  size $width 1", *lines, " }")
                    listOf(
                        listOf("FrameWidgetClass NumberStepperRoot {", " size 1 1", " {"),
                        listOf(" ImageWidgetClass Back {", "  size 1 1", "  color ${layoutColor(c.color(COLOR_STEPPER))}", " }"),
                        part("Button", "Minus", "0", "0.25", "  text \"-\"", "  style Empty"),
                        part(
                            "Text",
                            "ValueText",
                            "0.25",
                            "0.5",
                            "  text \"$value\"",
                            "  \"text halign\" center",
                            "  \"text valign\" center",
                            "  ignorepointer 1",
                        ),
                        part("Button", "Plus", "0.75", "0.25", "  text \"+\"", "  style Empty"),
                        listOf(" }", "}"),
                    ).flatten().joinToString("\n", postfix = "\n")
                },
                onClick = { clicked, state, c ->
                    val low = c.int(MIN)
                    val high = c.int(MAX).coerceAtLeast(low)
                    val step = c.int(INCREMENT).coerceAtLeast(1)
                    val now = c.state["value"]?.toIntOrNull() ?: c.int(VALUE)
                    when (clicked) {
                        "Minus" -> state["value"] = (now - step).coerceIn(low, high).toString()
                        "Plus" -> state["value"] = (now + step).coerceIn(low, high).toString()
                    }
                },
            ),
        )

    fun find(scriptClass: String): Spawner? {
        val plain = scriptClass.substringAfterLast('_')
        return all.firstOrNull { it.name.equals(scriptClass, ignoreCase = true) }
            ?: all.firstOrNull { it.name.equals(plain, ignoreCase = true) }
    }
}

private val parsed = HashMap<Pair<UUID, String>, EditorWidgetNode>()

/** The tree as Live Preview shows it: every spawner has the widgets of its fragment inside it. The widgets of the file are untouched. */
internal fun expandSpawners(
    root: EditorWidgetNode,
    states: Map<UUID, Map<String, String>>,
): EditorWidgetNode {
    // The view is asked for on every frame while it is dragged, so the tree of the last call is given again when nothing changed
    lastExpanded?.let { if (it.root === root && it.states === states) return it.result }
    val result = expandAll(root, states)
    lastExpanded = Expanded(root, states, result)
    return result
}

private class Expanded(
    val root: EditorWidgetNode,
    val states: Map<UUID, Map<String, String>>,
    val result: EditorWidgetNode,
)

private var lastExpanded: Expanded? = null

private fun expandAll(
    root: EditorWidgetNode,
    states: Map<UUID, Map<String, String>>,
): EditorWidgetNode {
    fun expand(node: EditorWidgetNode): EditorWidgetNode {
        val children = node.children.map { expand(it) }
        val spawner = node.scriptClassName()?.let { Spawners.find(it) }
        if (spawner == null && children.indices.all { children[it] === node.children[it] }) return node
        val spawned =
            spawner?.let {
                spawnedWidgets(
                    node,
                    it.fragment(SpawnContext(node.node.scriptParams.orEmpty(), states[node.id].orEmpty())),
                )
            }
        return node.copy(children = (children + listOfNotNull(spawned)).toMutableList())
    }
    return expand(root)
}

/** The widgets of the fragment [text] of the spawner [owner], parsed once per text. */
private fun spawnedWidgets(
    owner: EditorWidgetNode,
    text: String,
): EditorWidgetNode? {
    val key = owner.id to text
    parsed[key]?.let { return it }
    if (parsed.size > 300) parsed.clear()
    val made = runCatching { LayoutParser.parse(text).toEditorNode().withStableIds(owner.id) }.getOrNull() ?: return null
    parsed[key] = made
    return made
}

/** Ids that depend only on the spawner and the place in the fragment, so that hover and press stay on a widget when the fragment is rebuilt. */
private fun EditorWidgetNode.withStableIds(seed: UUID): EditorWidgetNode {
    fun walk(
        node: EditorWidgetNode,
        path: String,
    ): EditorWidgetNode =
        node.copy(
            id = UUID.nameUUIDFromBytes("$seed/$path".toByteArray()),
            children = node.children.mapIndexed { i, child -> walk(child, "$path/$i") }.toMutableList(),
        )
    return walk(this, "")
}

/** What the spawners do to widgets that are already in the layout, by widget id. */
internal fun spawnerEffects(
    root: EditorWidgetNode,
    states: Map<UUID, Map<String, String>>,
): Map<UUID, BehaviorEffect> {
    val out = HashMap<UUID, BehaviorEffect>()

    fun byName(
        node: EditorWidgetNode,
        name: String,
    ): EditorWidgetNode? = if (node.node.name == name) node else node.children.firstNotNullOfOrNull { byName(it, name) }

    fun walk(node: EditorWidgetNode) {
        node.scriptClassName()?.let { Spawners.find(it) }?.let { spawner ->
            val context = SpawnContext(node.node.scriptParams.orEmpty(), states[node.id].orEmpty())
            for ((name, effect) in spawner.effects(context)) {
                byName(root, name)?.let { out[it.id] = (out[it.id] ?: BehaviorEffect()) + effect }
            }
        }
        node.children.forEach { walk(it) }
    }
    walk(root)
    return out
}

/** A click at the widget [clicked] (found by name) inside the spawner [node]: the new state of that spawner, or null if nothing changed. */
internal fun spawnerClick(
    node: EditorWidgetNode,
    clicked: String,
    state: Map<String, String>,
): Map<String, String>? {
    val spawner = node.scriptClassName()?.let { Spawners.find(it) } ?: return null
    val next = HashMap(state)
    spawner.onClick(clicked, next, SpawnContext(node.node.scriptParams.orEmpty(), state))
    return next.takeIf { it != state }
}
