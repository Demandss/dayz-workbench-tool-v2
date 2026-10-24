package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID

private val SCALE_HOVER = BehaviorParam("Scale", "1.05", "Size while the pointer is over the widget (1 is unchanged)", listOf("HoverScale"))
private val SCALE_PRESS = BehaviorParam("Scale", "0.95", "Size while the button is held (1 is unchanged)", listOf("PressScale"))
private val COLOR_HOVER =
    BehaviorParam("Color", "#FFFFFF", "Colour while the pointer is over the widget, #RRGGBB or #RRGGBBAA", listOf("HoverColor"))
private val OFFSET_X = BehaviorParam("OffsetX", "0", "Shift to the right in pixels while the button is held")
private val OFFSET_Y = BehaviorParam("OffsetY", "2", "Shift down in pixels while the button is held")

/** The script classes the editor can play in Live Preview. */
internal object Behaviors {
    val all: List<WidgetBehavior> =
        listOf(
            WidgetBehavior(
                "HoverScale",
                BehaviorKind.ON_WIDGET,
                "Grows the widget while the pointer is over it",
                listOf(SCALE_HOVER, TARGET),
            ) { c ->
                if (c.hovered) BehaviorEffect(scale = c.float(SCALE_HOVER)) else null
            },
            WidgetBehavior(
                "PressScale",
                BehaviorKind.ON_WIDGET,
                "Shrinks the widget while the button is held",
                listOf(SCALE_PRESS, TARGET),
            ) { c ->
                if (c.pressed) BehaviorEffect(scale = c.float(SCALE_PRESS)) else null
            },
            WidgetBehavior(
                "PressOffset",
                BehaviorKind.ON_WIDGET,
                "Moves the widget while the button is held",
                listOf(OFFSET_X, OFFSET_Y, TARGET),
            ) { c ->
                if (c.pressed) BehaviorEffect(offset = Offset(c.float(OFFSET_X), c.float(OFFSET_Y))) else null
            },
            WidgetBehavior(
                "HoverColor",
                BehaviorKind.ON_WIDGET,
                "Paints the widget in another colour while the pointer is over it",
                listOf(COLOR_HOVER, TARGET),
            ) { c ->
                if (c.hovered) c.color(COLOR_HOVER)?.let { BehaviorEffect(color = it) } else null
            },
        )

    /** The behaviour a `scriptclass` value stands for: its own name, or that of a class of a mod such as `MF_CLS_HoverColorHandler`. */
    fun find(scriptClass: String): WidgetBehavior? {
        val plain = scriptClass.substringAfterLast('_').removeSuffix("Handler")
        return all.firstOrNull { it.name.equals(scriptClass, ignoreCase = true) }
            ?: all.firstOrNull { it.name.equals(plain, ignoreCase = true) }
    }
}

private fun EditorWidgetNode.paramValues(): Map<String, String> = node.scriptParams.orEmpty()

private fun findByName(
    node: EditorWidgetNode,
    name: String,
): EditorWidgetNode? {
    if (node.node.name == name) return node
    for (child in node.children) findByName(child, name)?.let { return it }
    return null
}

private fun targetOf(
    root: EditorWidgetNode,
    widget: EditorWidgetNode,
    parent: EditorWidgetNode?,
    behavior: WidgetBehavior,
): EditorWidgetNode {
    if (TARGET !in behavior.params) return widget
    val raw = BehaviorContext(widget, widget, widget.paramValues(), hovered = false, pressed = false).text(TARGET)
    return when {
        raw == "1" || raw.equals("parent", ignoreCase = true) -> parent ?: widget
        raw == "0" || raw.isEmpty() || raw.equals("self", ignoreCase = true) -> widget
        else -> findByName(root, raw) ?: widget
    }
}

private operator fun List<BehaviorParam>.contains(param: BehaviorParam) = any { it === param }

/**
 * What the behaviours of the layout do right now, by widget. A widget counts as hovered or held when the pointer is on it or on
 * something inside it, which is what the events of a script class on a frame see.
 */
internal fun previewEffects(
    root: EditorWidgetNode,
    hovered: Set<UUID>,
    pressed: Set<UUID>,
): Map<UUID, BehaviorEffect> {
    if (hovered.isEmpty() && pressed.isEmpty()) return emptyMap()
    val out = HashMap<UUID, BehaviorEffect>()

    fun walk(
        node: EditorWidgetNode,
        parent: EditorWidgetNode?,
    ) {
        val behavior = node.scriptClassName()?.let { Behaviors.find(it) }
        if (behavior != null && behavior.kind == BehaviorKind.ON_WIDGET) {
            val target = targetOf(root, node, parent, behavior)
            val context = BehaviorContext(node, target, node.paramValues(), node.id in hovered, node.id in pressed)
            behavior.effect(context)?.let { out[target.id] = (out[target.id] ?: BehaviorEffect()) + it }
        }
        for (child in node.children) walk(child, node)
    }
    walk(root, null)
    return out
}

/** A copy of the tree in which the widgets with a colour effect have that colour; the widgets without keep their own objects. */
internal fun EditorWidgetNode.withEffectColors(effects: Map<UUID, BehaviorEffect>): EditorWidgetNode {
    if (effects.values.none { it.color != null }) return this

    fun copyOf(node: EditorWidgetNode): EditorWidgetNode {
        val children = node.children.map { copyOf(it) }
        val color = effects[node.id]?.color
        if (color == null && children.indices.all { children[it] === node.children[it] }) return node
        val props = LinkedHashMap(node.node.properties)
        if (color != null) props["color"] = colorProperty(color)
        return node.copy(node = node.node.copy(properties = props), children = children.toMutableList())
    }
    return copyOf(this)
}

private fun colorProperty(color: Color): String = "${color.red} ${color.green} ${color.blue} ${color.alpha}"
