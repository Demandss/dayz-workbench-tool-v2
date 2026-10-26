package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import com.github.demandss.dayz_workbench_tool.resources.*
import org.jetbrains.compose.resources.StringResource
import java.util.UUID

/** A field of `ScriptParamsClass` that a script class reads: a `reference` field of the class or of one above it. */
internal class ScriptParam(
    val name: String,
    /** `string`, `bool`, `int`, `float` or another type as the class writes it. */
    val type: String,
    val description: String = "",
    val default: String = "",
    /** The class that declares the field. */
    val owner: String = "",
) {
    /** A text is written in quotes in the layout, a number or a flag is not. */
    val quoted: Boolean get() = type == "string"
    val isFlag: Boolean get() = type == "bool"

    /** The line for the tooltip: type, default and what the field is for. */
    fun hint(): String =
        listOfNotNull(
            if (default.isNotEmpty()) str(Res.string.script_param_default, type, default) else type,
            description.takeIf { it.isNotEmpty() },
        ).joinToString(System.lineSeparator())
}

internal object ScriptParams {
    private val reference = Regex("""^[ \t]*reference[ \t]+(\w+)[ \t]+([^;\r\n]+);[ \t]*(?://[ \t]*(.*))?""")

    // What the fields of the classes of the game that the editor plays are for, by class and field
    private val about: Map<String, StringResource> =
        mapOf(
            "SizeToChild.m_ChildName" to Res.string.param_about_sizetochild_m_childname,
            "SizeToChild.m_HorizontalOffset" to Res.string.param_about_sizetochild_m_horizontaloffset,
            "SizeToChild.m_VerticalOffset" to Res.string.param_about_sizetochild_m_verticaloffset,
            "SizeToChild.m_ResizeHorizontal" to Res.string.param_about_sizetochild_m_resizehorizontal,
            "SizeToChild.m_ResizeVertical" to Res.string.param_about_sizetochild_m_resizevertical,
            "SizeToParent.m_ResizeHorizontal" to Res.string.param_about_sizetoparent_m_resizehorizontal,
            "SizeToParent.m_ResizeVertical" to Res.string.param_about_sizetoparent_m_resizevertical,
            "AutoHeightSpacer.AlignChilds" to Res.string.param_about_autoheightspacer_alignchilds,
            "AutoHeightSpacer.MinHeight" to Res.string.param_about_autoheightspacer_minheight,
            "AutoHeightSpacer.MaxHeight" to Res.string.param_about_autoheightspacer_maxheight,
            "AutoHeightSpacer.Gap" to Res.string.param_about_autoheightspacer_gap,
            "AutoHeightSpacer.Top" to Res.string.param_about_autoheightspacer_top,
            "HorizontalSpacer.Border" to Res.string.param_about_horizontalspacer_border,
            "HorizontalSpacer.Gap" to Res.string.param_about_horizontalspacer_gap,
            "VerticalSpacer.border" to Res.string.param_about_horizontalspacer_border,
            "VerticalSpacer.gap" to Res.string.param_about_horizontalspacer_gap,
            "HorizontalSpacerWithFixedAspect.coef" to Res.string.param_about_horizontalspacerwithfixedaspect_coef,
            "RightGap.gap" to Res.string.param_about_rightgap_gap,
            "Tabber.SelectedTab" to Res.string.param_about_tabber_selectedtab,
            "MainMenuButtonEffect.speed" to Res.string.param_about_mainmenubuttoneffect_speed,
            "MainMenuButtonEffect.amount" to Res.string.param_about_mainmenubuttoneffect_amount,
            "EmbededButtonScript.Caption" to Res.string.param_about_embededbuttonscript_caption,
            "RadialProgressBar.speed" to Res.string.param_about_mainmenubuttoneffect_speed,
            "RadialProgressBar.start_rotation" to Res.string.param_about_radialprogressbar_start_rotation,
            "HoverEffect.speed" to Res.string.param_about_hovereffect_speed,
            "HoverEffect.amount" to Res.string.param_about_hovereffect_amount,
            "Bouncer.amount" to Res.string.param_about_bouncer_amount,
            "Bouncer.speed" to Res.string.param_about_bouncer_speed,
            "Rotator.speed" to Res.string.param_about_rotator_speed,
            "ScrollBarContainer.Invert" to Res.string.param_about_scrollbarcontainer_invert,
        )

    /** The `reference` fields declared in [classText] (the text of one class), with the comment next to each as its description. */
    fun parse(
        classText: String,
        owner: String,
    ): List<ScriptParam> {
        val lines = classText.lines()
        val out = mutableListOf<ScriptParam>()
        lines.forEachIndexed { i, line ->
            val m = reference.find(line) ?: return@forEachIndexed
            val before =
                lines
                    .getOrNull(i - 1)
                    ?.trim()
                    ?.takeIf { it.startsWith("//") }
                    ?.removePrefix("//")
                    ?.trim()
            val comment = m.groupValues[3].trim().ifEmpty { before.orEmpty() }
            val type = m.groupValues[1].lowercase()
            for (name in m.groupValues[2]
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }) {
                out += ScriptParam(name, type, about["$owner.$name"]?.let { str(it) } ?: comment, owner = owner)
            }
        }
        return out
    }

    /** The fields [className] reads: the `reference` fields of the class and of the classes it extends, the base classes first. */
    fun collect(
        className: String,
        declarations: Map<String, ScriptDeclaration>,
        gameFolder: String,
    ): List<ScriptParam> {
        val chain = mutableListOf<ScriptDeclaration>()
        var current = declarations[className]
        while (current != null && chain.size < 40) {
            chain.add(0, current)
            current = declarations[current.parent]
        }
        return chain
            .flatMap { d ->
                ScriptClassIndex
                    .sourceOf(ScriptClassInfo(d.name, d.source, d.parent, d.location), gameFolder)
                    ?.let { parse(it, d.name) }
                    .orEmpty()
            }.associateBy { it.name }
            .values
            .toList()
    }

    /** The fields of a class of the editor's catalogue, with their defaults and what they are for. */
    fun ofEditorClass(entry: BehaviorEntry): List<ScriptParam> =
        entry.params.map { ScriptParam(it.name, "string", it.description, it.default, entry.name) }

    /** [value] as it goes into the layout for [param]: a text in quotes, a number or a flag as it is; an empty value removes the field. */
    fun formatted(
        param: ScriptParam?,
        existing: String?,
        value: String,
    ): String? {
        val text = value.trim().trim('"')
        if (text.isEmpty()) return null
        val quoted = param?.quoted ?: existing?.trim()?.startsWith("\"") ?: !text.matches(Regex("-?\\d+(\\.\\d+)?"))
        return if (quoted) "\"$text\"" else text
    }
}

/** Sets or, with a null [value], removes the field [key] of `ScriptParamsClass` of the widget [id]; the change can be undone. */
internal fun LayoutEditorState.setScriptParam(
    id: UUID,
    key: String,
    value: String?,
) {
    val doc = activeFile ?: return
    pushUndo(activeFileIndex, doc.root, if (value == null) "$key removed" else "$key = $value")
    updateActiveRoot(
        doc.root.updateNode(id) { n: EditorWidgetNode ->
            val params = LinkedHashMap(n.node.scriptParams.orEmpty())
            if (value == null) params.remove(key) else params[key] = value
            n.copy(node = n.node.copy(scriptParams = params.takeIf { it.isNotEmpty() }))
        },
    )
}
