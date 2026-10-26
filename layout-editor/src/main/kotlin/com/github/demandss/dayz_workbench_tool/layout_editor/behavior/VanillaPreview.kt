package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID

/**
 * The script classes of the game that change how widgets look or answer to the pointer, as Live Preview plays them: tabs, the
 * effect of a main menu button, the caption of an embedded button and the starting look of a radial progress bar. Each one
 * is read from the source of the game; what a class does with a timer or with data of the game is left out.
 */
internal object VanillaPreview {
    private val classes = setOf("TabberUI", "Tabber", "MainMenuButtonEffect", "RadialProgressBar", "EmbededButtonScript")
    private val tabControl = Regex("""Tab_Control_(\d+)""")

    private val RED = Color(1f, 0f, 0f, 1f)
    private val WHITE = Color(1f, 1f, 1f, 1f)
    private val BLACK = Color(0f, 0f, 0f, 1f)
    private val CLEAR = Color(0f, 0f, 0f, 0f)

    private fun find(
        node: EditorWidgetNode,
        name: String,
    ): EditorWidgetNode? {
        for (child in node.children) {
            if (child.node.name == name) return child
            find(child, name)?.let { return it }
        }
        return null
    }

    private fun params(node: EditorWidgetNode): Map<String, String> =
        node.node.scriptParams
            .orEmpty()
            .mapValues { it.value.trim('"', ' ') }

    private fun add(
        out: MutableMap<UUID, BehaviorEffect>,
        node: EditorWidgetNode?,
        effect: BehaviorEffect,
    ) {
        if (node != null) out[node.id] = (out[node.id] ?: BehaviorEffect()) + effect
    }

    /** What the classes do to widgets right now, by widget id; [states] are the tabs the user has chosen, [focus] the button clicked last. */
    fun effects(
        root: EditorWidgetNode,
        states: Map<UUID, Map<String, String>>,
        hovered: Set<UUID>,
        focus: UUID?,
    ): Map<UUID, BehaviorEffect> {
        val out = HashMap<UUID, BehaviorEffect>()

        fun walk(node: EditorWidgetNode) {
            val name = node.scriptClassName()
            if (name in classes) {
                val state = states[node.id].orEmpty()
                when (name) {
                    "TabberUI" -> tabberUi(node, state, hovered, out)
                    "Tabber" -> tabber(node, state, out)
                    "MainMenuButtonEffect" -> mainMenuButton(node, node.id == focus, out)
                    "RadialProgressBar" -> radialProgress(node, out)
                    "EmbededButtonScript" -> embeddedButton(node, out)
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    /** The new state of the class on [owner] after a click on the last widget of [path] (the widgets below the owner), or null. */
    fun click(
        owner: EditorWidgetNode,
        path: List<EditorWidgetNode>,
        state: Map<String, String>,
    ): Map<String, String>? =
        when (owner.scriptClassName()) {
            "TabberUI" -> tabberUiClick(path, state)
            "Tabber" -> tabberClick(owner, path, state)
            else -> null
        }

    // ---- TabberUI: controls Tab_Control_N in Tab_Control_Container, panels Tab_N

    private fun tabberUi(
        owner: EditorWidgetNode,
        state: Map<String, String>,
        hovered: Set<UUID>,
        out: MutableMap<UUID, BehaviorEffect>,
    ) {
        val container = find(owner, "Tab_Control_Container") ?: return
        val selected = state["tab"]?.toIntOrNull() ?: 0
        val visited =
            state["visited"]
                .orEmpty()
                .split(',')
                .mapNotNull { it.toIntOrNull() }
                .toSet()
        val switched = state.containsKey("tab")
        for (i in container.children.indices) {
            val control = find(container, "Tab_Control_$i") ?: continue
            val title = find(control, "Tab_Control_${i}_Title")
            // At the start only the first control is dressed; the others are dressed when they are left or hovered
            val lit = i == selected || (i != selected && control.id in hovered)
            if (lit) {
                add(out, title, BehaviorEffect(textColor = RED))
                add(out, control, BehaviorEffect(color = BLACK))
            } else if (i in visited) {
                add(out, title, BehaviorEffect(textColor = WHITE))
                add(out, control, BehaviorEffect(color = CLEAR))
            }
            if (switched) find(owner, "Tab_$i")?.let { add(out, it, BehaviorEffect(visible = i == selected)) }
        }
    }

    private fun tabberUiClick(
        path: List<EditorWidgetNode>,
        state: Map<String, String>,
    ): Map<String, String>? {
        val index =
            path.firstNotNullOfOrNull {
                tabControl
                    .matchEntire(it.node.name)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            } ?: return null
        val old = state["tab"]?.toIntOrNull() ?: 0
        if (index == old) return null
        val visited = (state["visited"].orEmpty().split(',').filter { it.isNotEmpty() } + old.toString()).distinct().joinToString(",")
        return state + mapOf("tab" to index.toString(), "visited" to visited)
    }

    // ---- Tabber: the buttons are the children of ButtonsPanel, the panels the children of ContentPanel

    private fun tabber(
        owner: EditorWidgetNode,
        state: Map<String, String>,
        out: MutableMap<UUID, BehaviorEffect>,
    ) {
        val content = find(owner, "ContentPanel") ?: return
        val selected = state["tab"]?.toIntOrNull() ?: params(owner)["SelectedTab"]?.toIntOrNull() ?: 0
        content.children.forEachIndexed { i, panel -> add(out, panel, BehaviorEffect(visible = i == selected)) }
    }

    private fun tabberClick(
        owner: EditorWidgetNode,
        path: List<EditorWidgetNode>,
        state: Map<String, String>,
    ): Map<String, String>? {
        val buttons = find(owner, "ButtonsPanel") ?: return null
        val index =
            path.firstNotNullOfOrNull { node -> buttons.children.indexOfFirst { it.id == node.id }.takeIf { it >= 0 } } ?: return null
        return (state + ("tab" to index.toString())).takeIf { it != state }
    }

    // ---- MainMenuButtonEffect: the text of the focused button is red and moves, of the others white

    private fun mainMenuButton(
        button: EditorWidgetNode,
        focused: Boolean,
        out: MutableMap<UUID, BehaviorEffect>,
    ) {
        val amount = params(button)["amount"]?.toFloatOrNull() ?: 0f
        add(out, button, BehaviorEffect(textColor = if (focused) RED else WHITE, textOffset = if (focused) amount * 4f else 0f))
    }

    // ---- RadialProgressBar: at the start both halves are hidden (the progress is set by a script)

    private fun radialProgress(
        owner: EditorWidgetNode,
        out: MutableMap<UUID, BehaviorEffect>,
    ) {
        val name = owner.node.name
        find(owner, "BarPart2_$name")?.let { add(out, it, BehaviorEffect(visible = false)) }
        find(owner, "BarHider_$name")?.children?.firstOrNull()?.let { add(out, it, BehaviorEffect(visible = false)) }
    }

    // ---- EmbededButtonScript: the caption goes onto the button inside the widget

    private fun embeddedButton(
        owner: EditorWidgetNode,
        out: MutableMap<UUID, BehaviorEffect>,
    ) {
        val button = owner.children.firstOrNull()?.takeIf { it.node.type == WidgetType.BUTTON } ?: return
        add(out, button, BehaviorEffect(text = params(owner)["Caption"].orEmpty()))
    }
}
