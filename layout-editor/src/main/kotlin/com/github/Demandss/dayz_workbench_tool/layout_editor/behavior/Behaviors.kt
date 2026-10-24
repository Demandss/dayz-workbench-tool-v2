package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val SCALE_HOVER = BehaviorParam("Scale", "1.05", "Size while the pointer is over the widget (1 is unchanged)", listOf("HoverScale"))
private val SCALE_PRESS = BehaviorParam("Scale", "0.95", "Size while the button is held (1 is unchanged)", listOf("PressScale"))
private val COLOR_HOVER =
    BehaviorParam("Color", "#FFFFFF", "Colour while the pointer is over the widget, #RRGGBB or #RRGGBBAA", listOf("HoverColor"))
private val SHOW_TARGET = BehaviorParam("Target", "", "Name of the widget that appears on hover (hidden until then)")
private val IMAGE_HOVER =
    BehaviorParam("Image", "", "Picture on hover: set:NAME image:IMAGE or the path of a texture", listOf("HoverImage"))
private val COLOR_HOVER_TEXT =
    BehaviorParam("Color", "#FFFFFF", "Colour of the text on hover, #RRGGBB or #RRGGBBAA", listOf("HoverColor", "TextColor"))
private val PERIOD = BehaviorParam("Period", "1.2", "Seconds for one full fade out and in")
private val MIN_ALPHA = BehaviorParam("MinAlpha", "0.2", "Opacity at the dimmest moment, 0 to 1")
private val DURATION_FADE = BehaviorParam("Duration", "0.5", "Seconds the fade takes")
private val DELAY = BehaviorParam("Delay", "0", "Seconds to wait before it starts")
private val AMPLITUDE = BehaviorParam("Amplitude", "8", "Pixels the widget swings at the start")
private val DURATION_SHAKE = BehaviorParam("Duration", "0.4", "Seconds the shake lasts")
private val VALUE_PROGRESS = BehaviorParam("Value", "75", "Value the bar fills up to, on the scale of its `current`")
private val DURATION_TWEEN = BehaviorParam("Duration", "1.2", "Seconds the fill takes")
private val VALUE_COUNT = BehaviorParam("Value", "100", "Number the text counts up to")
private val DURATION_COUNT = BehaviorParam("Duration", "1.5", "Seconds the count takes")
private const val SHAKE_HERTZ = 7f

/** How far a value that is played again and again has come [time] seconds in: it eases out, then waits a second and starts over. */
private fun tweenShare(
    time: Float,
    duration: Float,
): Float {
    val length = duration.coerceAtLeast(0.05f)
    val phase = time % (length + 1f)
    val linear = (phase / length).coerceIn(0f, 1f)
    return 1f - (1f - linear) * (1f - linear)
}

private val DRAG_TARGET = BehaviorParam("Target", "parent", "Widget that moves with the pointer: parent, self or the name of a widget")
private val FILTER_MODE = BehaviorParam("Mode", "digits", "What may be typed: digits, letters, letters_digits or custom (only Allowed)")
private val FILTER_ALLOWED = BehaviorParam("Allowed", "", "Extra characters that may be typed, or all of them in the custom mode")
private val FILTER_MAX = BehaviorParam("MaxLength", "0", "Most characters the field takes, 0 for no limit")
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
            WidgetBehavior(
                "EditFilter",
                BehaviorKind.ON_WIDGET,
                "Lets an edit box take only some characters and no more than a number of them",
                listOf(FILTER_MODE, FILTER_ALLOWED, FILTER_MAX),
            ) { c ->
                c.state["text"]?.let { BehaviorEffect(text = it) }
            },
            WidgetBehavior(
                "DragMove",
                BehaviorKind.ON_WIDGET,
                "Moves a widget (by default the parent, such as a window) when this one is dragged",
                listOf(DRAG_TARGET),
            ) { c ->
                val x = c.state["dx"]?.toFloatOrNull()
                val y = c.state["dy"]?.toFloatOrNull()
                if (x == null || y == null) null else BehaviorEffect(offset = Offset(x, y))
            },
            WidgetBehavior(
                "Blink",
                BehaviorKind.ON_WIDGET,
                "Fades the widget in and out all the time",
                listOf(PERIOD, MIN_ALPHA, TARGET),
                timed = true,
            ) { c ->
                val period = c.float(PERIOD).coerceAtLeast(0.05f)
                val low = c.float(MIN_ALPHA).coerceIn(0f, 1f)
                BehaviorEffect(alpha = low + (1f - low) * (0.5f + 0.5f * cos(2f * PI.toFloat() * c.time / period)))
            },
            WidgetBehavior(
                "FadeIn",
                BehaviorKind.ON_WIDGET,
                "Makes the widget appear smoothly when the layout is shown",
                listOf(DURATION_FADE, DELAY, TARGET),
                timed = true,
            ) { c ->
                val done = ((c.time - c.float(DELAY)) / c.float(DURATION_FADE).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                if (done < 1f) BehaviorEffect(alpha = done) else null
            },
            WidgetBehavior(
                "Shake",
                BehaviorKind.ON_WIDGET,
                "Shakes the widget sideways after it is clicked",
                listOf(AMPLITUDE, DURATION_SHAKE, TARGET),
                timed = true,
            ) { c ->
                val since = c.sincePress
                val length = c.float(DURATION_SHAKE).coerceAtLeast(0.05f)
                if (since == null || since >= length) {
                    null
                } else {
                    val fade = 1f - since / length
                    BehaviorEffect(offset = Offset(c.float(AMPLITUDE) * fade * sin(2f * PI.toFloat() * SHAKE_HERTZ * since), 0f))
                }
            },
            WidgetBehavior(
                "ProgressTween",
                BehaviorKind.ON_WIDGET,
                "Fills a progress bar smoothly up to a value, again and again so that it can be watched",
                listOf(VALUE_PROGRESS, DURATION_TWEEN, TARGET),
                timed = true,
            ) { c ->
                BehaviorEffect(current = c.float(VALUE_PROGRESS) * tweenShare(c.time, c.float(DURATION_TWEEN)))
            },
            WidgetBehavior(
                "CountUp",
                BehaviorKind.ON_WIDGET,
                "Counts a number up to a value in a text widget, again and again so that it can be watched",
                listOf(VALUE_COUNT, DURATION_COUNT, TARGET),
                timed = true,
            ) { c ->
                BehaviorEffect(text = (c.float(VALUE_COUNT) * tweenShare(c.time, c.float(DURATION_COUNT))).roundToInt().toString())
            },
        )

    /** True when the layout has a behaviour that plays by itself, so that Live Preview has to run a clock. */
    fun hasTimed(root: EditorWidgetNode): Boolean {
        if (root.scriptClassName()?.let { find(it) }?.timed == true) return true
        return root.children.any { hasTimed(it) }
    }

    /** True when the edit box with the settings [params] takes the character [ch] after [typed]. */
    fun editFilterAccepts(
        params: Map<String, String>,
        typed: String,
        ch: Char,
    ): Boolean {
        if (ch.isISOControl()) return false
        val max = paramText(params, FILTER_MAX).toIntOrNull() ?: 0
        if (max > 0 && typed.length >= max) return false
        val allowed = paramText(params, FILTER_ALLOWED)
        return when (paramText(params, FILTER_MODE).lowercase()) {
            "digits" -> ch.isDigit() || ch in allowed
            "letters" -> ch.isLetter() || ch in allowed
            "letters_digits" -> ch.isLetterOrDigit() || ch in allowed
            else -> ch in allowed
        }
    }

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
    time: Float = 0f,
    pressTimes: Map<UUID, Float> = emptyMap(),
    states: Map<UUID, Map<String, String>> = emptyMap(),
): Map<UUID, BehaviorEffect> {
    val out = HashMap<UUID, BehaviorEffect>()

    fun walk(
        node: EditorWidgetNode,
        parent: EditorWidgetNode?,
    ) {
        val behavior = node.scriptClassName()?.let { Behaviors.find(it) }
        if (behavior != null && behavior.kind == BehaviorKind.ON_WIDGET) {
            val target = targetOf(root, node, parent, behavior)
            val context =
                BehaviorContext(
                    node,
                    target,
                    node.paramValues(),
                    node.id in hovered,
                    node.id in pressed,
                    time,
                    pressTimes[node.id]?.let { time - it },
                    states[node.id].orEmpty(),
                )
            behavior.effect(context)?.let { out[target.id] = (out[target.id] ?: BehaviorEffect()) + it }
        }
        for (child in node.children) walk(child, node)
    }
    walk(root, null)
    for ((id, effect) in spawnerEffects(root, states)) out[id] = (out[id] ?: BehaviorEffect()) + effect
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
            effect.text?.let { props["text"] = "\"$it\"" }
            effect.current?.let { props["current"] = it.toString() }
        }
        return node.copy(node = node.node.copy(properties = props), children = children.toMutableList())
    }
    return copyOf(this)
}

private fun colorProperty(color: Color): String = "${color.red} ${color.green} ${color.blue} ${color.alpha}"
