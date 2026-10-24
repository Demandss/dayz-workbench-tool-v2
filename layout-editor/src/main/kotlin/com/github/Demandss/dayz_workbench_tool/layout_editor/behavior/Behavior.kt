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
) {
    val moves: Boolean get() = scale != 1f || offset != Offset.Zero

    operator fun plus(other: BehaviorEffect) = BehaviorEffect(scale * other.scale, offset + other.offset, other.color ?: color)
}

/** What a behaviour sees: the widget it sits on, the widget it works on, its parameters and the pointer. */
internal class BehaviorContext(
    val widget: EditorWidgetNode,
    val target: EditorWidgetNode,
    private val values: Map<String, String>,
    val hovered: Boolean,
    val pressed: Boolean,
) {
    fun text(param: BehaviorParam): String =
        (listOf(param.name) + param.aliases)
            .firstNotNullOfOrNull { key -> values.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value }
            ?.trim('"', ' ')
            ?: param.default

    fun float(param: BehaviorParam): Float = text(param).toFloatOrNull() ?: param.default.toFloat()

    fun color(param: BehaviorParam): Color? = parseHexColor(text(param))
}

internal class WidgetBehavior(
    val name: String,
    val kind: BehaviorKind,
    val summary: String,
    val params: List<BehaviorParam>,
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

internal fun EditorWidgetNode.scriptClassName(): String? = prop("scriptclass")?.trim('"', ' ')?.takeIf { it.isNotEmpty() }
