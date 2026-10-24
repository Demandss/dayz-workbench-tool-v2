package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID

private val SCALE_HOVER = BehaviorParam("Scale", "1.05", "Size while the pointer is over the widget (1 is unchanged)", listOf("HoverScale"))
private val SCALE_PRESS = BehaviorParam("Scale", "0.95", "Size while the button is held (1 is unchanged)", listOf("PressScale"))
private val COLOR_HOVER =
    BehaviorParam("Color", "#FFFFFF", "Colour while the pointer is over the widget, #RRGGBB or #RRGGBBAA", listOf("HoverColor"))
private val SHOW_TARGET = BehaviorParam("Target", "", "Name of the widget that appears on hover (hidden until then)")
private val IMAGE_HOVER =
    BehaviorParam("Image", "", "Picture on hover: set:NAME image:IMAGE or the path of a texture", listOf("HoverImage"))
private val COLOR_HOVER_TEXT =
    BehaviorParam("Color", "#FFFFFF", "Colour of the text on hover, #RRGGBB or #RRGGBBAA", listOf("HoverColor", "TextColor"))
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
            WidgetBehavior(
                "HoverShow",
                BehaviorKind.ON_WIDGET,
                "Shows another widget while the pointer is over this one and hides it otherwise",
                listOf(SHOW_TARGET),
            ) { c ->
                BehaviorEffect(visible = c.hovered)
            },
            WidgetBehavior(
                "HoverImage",
                BehaviorKind.ON_WIDGET,
                "Swaps the picture of an image widget while the pointer is over it",
                listOf(IMAGE_HOVER, TARGET),
            ) { c ->
                if (c.hovered) c.text(IMAGE_HOVER).takeIf { it.isNotEmpty() }?.let { BehaviorEffect(image = it) } else null
            },
            WidgetBehavior(
                "HoverText",
                BehaviorKind.ON_WIDGET,
                "Changes the colour of the text while the pointer is over the widget",
                listOf(COLOR_HOVER_TEXT, TARGET),
            ) { c ->
                if (c.hovered) c.color(COLOR_HOVER_TEXT)?.let { BehaviorEffect(textColor = it) } else null
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
    val param = behavior.params.firstOrNull { it.name == TARGET.name } ?: return widget
    val raw = BehaviorContext(widget, widget, widget.paramValues(), hovered = false, pressed = false).text(param)
    return when {
        raw == "1" || raw.equals("parent", ignoreCase = true) -> parent ?: widget
        raw == "0" || raw.isEmpty() || raw.equals("self", ignoreCase = true) -> widget
        else -> findByName(root, raw) ?: widget
    }
}

/**
 * What the behaviours of the layout do right now, by widget. A widget counts as hovered or held when the pointer is on it or on
 * something inside it, which is what the events of a script class on a frame see.
 */
internal fun previewEffects(
    root: EditorWidgetNode,
    hovered: Set<UUID>,
    pressed: Set<UUID>,
): Map<UUID, BehaviorEffect> {
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

/** A copy of the tree in which the widgets with an effect on their properties have them changed; the others keep their objects. */
internal fun EditorWidgetNode.withEffectProperties(effects: Map<UUID, BehaviorEffect>): EditorWidgetNode {
    if (effects.values.none { it.changesProperties }) return this

    fun copyOf(node: EditorWidgetNode): EditorWidgetNode {
        val children = node.children.map { copyOf(it) }
        val effect = effects[node.id]?.takeIf { it.changesProperties }
        if (effect == null && children.indices.all { children[it] === node.children[it] }) return node
        val props = LinkedHashMap(node.node.properties)
        if (effect != null) {
            effect.color?.let { props["color"] = colorProperty(it) }
            effect.visible?.let { props["visible"] = if (it) "1" else "0" }
            effect.image?.let { props["image0"] = if (it.startsWith("set:")) "\"$it\"" else it }
            effect.textColor?.let { props[if (node.node.type == WidgetType.BUTTON) "text color" else "color"] = colorProperty(it) }
        }
        return node.copy(node = node.node.copy(properties = props), children = children.toMutableList())
    }
    return copyOf(this)
}

private fun colorProperty(color: Color): String = "${color.red} ${color.green} ${color.blue} ${color.alpha}"
