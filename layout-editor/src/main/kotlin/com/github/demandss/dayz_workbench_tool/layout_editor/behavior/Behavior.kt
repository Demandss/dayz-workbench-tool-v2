package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.prop

/**
 * A script class that gives a widget a behaviour. [ON_WIDGET] classes sit on a widget that is already there and only change how
 * it is drawn or answers; [SPAWNER] classes sit on an empty frame and create the widgets they need inside it.
 */
internal enum class BehaviorKind { ON_WIDGET, SPAWNER }

/** One field of `ScriptParamsClass`. [aliases] are the names older script classes of the same kind use for it. */
internal class BehaviorParam(
    val name: String,
    val default: String,
    val description: String,
    val aliases: List<String> = emptyList(),
)

/** What a behaviour does to a widget while Live Preview runs: a scale and a shift around its centre and a colour in place of its own. */
internal data class BehaviorEffect(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
    val color: Color? = null,
    val visible: Boolean? = null,
    val image: String? = null,
    val textColor: Color? = null,
    // How opaque the widget and everything in it is drawn, 1 is as it is
    val alpha: Float = 1f,
    // The text of a text widget and the `current` value of a progress bar
    val text: String? = null,
    val current: Float? = null,
    // How far the text of a button is moved to the right, in pixels
    val textOffset: Float? = null,
) {
    /** True when the widget has to be drawn in a transformed or faded layer. */
    val moves: Boolean get() = scale != 1f || offset != Offset.Zero || alpha < 1f

    /** True when the widget has to be drawn from a changed copy of itself. */
    val changesProperties: Boolean
        get() =
            color != null || visible != null || image != null || textColor != null || text != null || current != null ||
                textOffset != null

    operator fun plus(other: BehaviorEffect) =
        BehaviorEffect(
            scale * other.scale,
            offset + other.offset,
            other.color ?: color,
            other.visible ?: visible,
            other.image ?: image,
            other.textColor ?: textColor,
            alpha * other.alpha,
            other.text ?: text,
            other.current ?: current,
            other.textOffset ?: textOffset,
        )
}

/** What a behaviour sees: the widget it sits on, the widget it works on, its parameters and the pointer. */
internal class BehaviorContext(
    val widget: EditorWidgetNode,
    val target: EditorWidgetNode,
    private val values: Map<String, String>,
    val hovered: Boolean,
    val pressed: Boolean,
    // Seconds since Live Preview started, and since the widget was last pressed (null if it has not been)
    val time: Float = 0f,
    val sincePress: Float? = null,
    // What the user did to this widget in Live Preview, such as how far it was dragged
    val state: Map<String, String> = emptyMap(),
) {
    fun text(param: BehaviorParam): String = paramText(values, param)

    fun float(param: BehaviorParam): Float = text(param).toFloatOrNull() ?: param.default.toFloat()

    fun color(param: BehaviorParam): Color? = parseHexColor(text(param))
}

/** The value of [param] in the `ScriptParamsClass` fields [values], under its own name or one of its aliases, or its default. */
internal fun paramText(
    values: Map<String, String>,
    param: BehaviorParam,
): String =
    (listOf(param.name) + param.aliases)
        .firstNotNullOfOrNull { key -> values.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value }
        ?.trim('"', ' ')
        ?: param.default

internal class WidgetBehavior(
    val name: String,
    val kind: BehaviorKind,
    val summary: String,
    val params: List<BehaviorParam>,
    // Plays by itself over time (or after a press), so that Live Preview has to keep a clock running
    val timed: Boolean = false,
    val effect: (BehaviorContext) -> BehaviorEffect?,
)

/** `#RRGGBB` or `#RRGGBBAA` (also `0x` and without a prefix); null when it is not a colour. */
internal fun parseHexColor(text: String): Color? {
    val hex =
        text
            .trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
    if (hex.length !in listOf(6, 8) || hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    val rgb = hex.substring(0, 6).toInt(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 255
    return Color((alpha shl 24) or rgb)
}

/** The name of the widget the behaviour works on: `self`, `parent` or the name of a widget in the layout. */
internal val TARGET =
    BehaviorParam(
        "Target",
        "self",
        "Widget it works on: self, parent or the name of a widget",
        aliases = listOf("ColorParent", "HoverParent"),
    )

/** A drag in Live Preview: which widget grabbed the pointer, where it was and how far the target had already been moved. */
internal data class PreviewDrag(
    val owner: java.util.UUID,
    val start: Offset,
    val startX: Float,
    val startY: Float,
)

internal fun EditorWidgetNode.scriptClassName(): String? = prop("scriptclass")?.trim('"', ' ')?.takeIf { it.isNotEmpty() }
