package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
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
            "$type${if (default.isNotEmpty()) ", по умолчанию $default" else ""}",
            description.takeIf { it.isNotEmpty() },
        ).joinToString(System.lineSeparator())
}

internal object ScriptParams {
    private val reference = Regex("""^[ \t]*reference[ \t]+(\w+)[ \t]+([^;\r\n]+);[ \t]*(?://[ \t]*(.*))?""")

    // What the fields of the classes of the game that the editor plays are for, by class and field
    private val about =
        mapOf(
            "SizeToChild.m_ChildName" to "Имя вложенного виджета, под размер которого подгоняется этот",
            "SizeToChild.m_HorizontalOffset" to "Сколько пикселей добавить к ширине вложенного виджета",
            "SizeToChild.m_VerticalOffset" to "Сколько пикселей добавить к высоте вложенного виджета",
            "SizeToChild.m_ResizeHorizontal" to "1: менять ширину",
            "SizeToChild.m_ResizeVertical" to "1: менять высоту",
            "SizeToParent.m_ResizeHorizontal" to "1: ширина становится долей родителя",
            "SizeToParent.m_ResizeVertical" to "1: высота становится долей родителя",
            "AutoHeightSpacer.AlignChilds" to "1: выстроить детей рядами, иначе только измерить, где они стоят",
            "AutoHeightSpacer.MinHeight" to "Наименьшая высота виджета, пиксели",
            "AutoHeightSpacer.MaxHeight" to "Наибольшая высота, пиксели; 0 без ограничения",
            "AutoHeightSpacer.Gap" to "Промежуток между детьми, пиксели",
            "AutoHeightSpacer.Top" to "Отступ первого ряда сверху, пиксели",
            "HorizontalSpacer.Border" to "Отступ от краёв, пиксели",
            "HorizontalSpacer.Gap" to "Промежуток между ячейками, пиксели",
            "VerticalSpacer.border" to "Отступ от краёв, пиксели",
            "VerticalSpacer.gap" to "Промежуток между ячейками, пиксели",
            "HorizontalSpacerWithFixedAspect.coef" to "Во сколько ширин ячейки отодвигать остальных детей от первого",
            "RightGap.gap" to "На сколько пикселей уже родителя",
            "Tabber.SelectedTab" to "Номер вкладки, открытой в начале, с нуля",
            "MainMenuButtonEffect.speed" to "Скорость анимации в игре",
            "MainMenuButtonEffect.amount" to "Сдвиг текста: amount * 4 пикселя",
            "EmbededButtonScript.Caption" to "Текст кнопки внутри виджета",
            "RadialProgressBar.speed" to "Скорость анимации в игре",
            "RadialProgressBar.start_rotation" to "Угол, с которого начинается полоса",
            "HoverEffect.speed" to "Скорость анимации",
            "HoverEffect.amount" to "На какую долю размера увеличивается виджет",
            "Bouncer.amount" to "Размах покачивания, доля размера",
            "Bouncer.speed" to "Скорость, циклов в секунду",
            "Rotator.speed" to "Скорость вращения, циклов в секунду",
            "ScrollBarContainer.Invert" to "1: прокрутка в обратную сторону",
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
                out += ScriptParam(name, type, about["$owner.$name"] ?: comment, owner = owner)
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
