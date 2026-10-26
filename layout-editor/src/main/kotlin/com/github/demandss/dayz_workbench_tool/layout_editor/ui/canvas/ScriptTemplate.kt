package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.resources.*
import org.jetbrains.compose.resources.StringResource

/** The kinds of Enforce Script class the editor can write for a layout. */
enum class TemplateKind(
    private val titleResource: StringResource,
) {
    MENU(Res.string.template_menu),
    COMPONENT(Res.string.template_component),
    WIDGETS(Res.string.template_widgets),
    ;

    /** The name of the kind in the language of the interface. */
    val title: String get() = str(titleResource)
}

private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** The widgets of a layout that a script can reach: named, with a name that is a valid identifier, the first one of each name. */
private fun reachable(root: EditorWidgetNode): List<EditorWidgetNode> {
    val seen = HashSet<String>()
    val out = mutableListOf<EditorWidgetNode>()

    fun visit(node: EditorWidgetNode) {
        val name = node.node.name
        if (node !== root && IDENTIFIER.matches(name) && seen.add(name)) out += node
        node.children.forEach(::visit)
    }
    visit(root)
    return out
}

/** Panels, frames and the like are kept as `Widget`, which is what a script does with them. */
private fun memberType(node: EditorWidgetNode): String =
    when (node.node.type) {
        WidgetType.PANEL, WidgetType.FRAME, WidgetType.SMART_PANEL, WidgetType.CONTENT, WidgetType.EMBEDDED -> "Widget"
        else -> scriptType(node)
    }

private fun lookup(node: EditorWidgetNode): String {
    val find = "layoutRoot.FindAnyWidget(\"${node.node.name}\")"
    return if (memberType(node) ==
        "Widget"
    ) {
        "${memberName(node.node.name)} = $find;"
    } else {
        "${memberName(node.node.name)} = ${memberType(node)}.Cast($find);"
    }
}

private fun isInput(type: WidgetType) =
    type in
        setOf(
            WidgetType.EDIT_BOX,
            WidgetType.MULTILINE_EDIT_BOX,
            WidgetType.PASSWORD_EDIT_BOX,
            WidgetType.CHECKBOX,
            WidgetType.THREE_STATE_CHECKBOX,
            WidgetType.SLIDER,
            WidgetType.XCOMBO_BOX,
        )

/**
 * The Enforce Script class for a layout, in the shape the mods of the project use: the members of the named widgets, the
 * lookup after the layout is created, the events of the buttons and inputs, and for a menu the lock of the controls, the way back
 * and the lines that register and open it. [layoutRef] is the path the game finds the layout by, [className] the new class.
 */
fun scriptTemplate(
    root: EditorWidgetNode,
    layoutRef: String,
    className: String,
    kind: TemplateKind,
): String {
    val widgets = reachable(root)
    val buttons = widgets.filter { it.node.type == WidgetType.BUTTON }
    val inputs = widgets.filter { isInput(it.node.type) }
    val members = widgets.joinToString("\n") { "    ref ${memberType(it)} ${memberName(it.node.name)};" }
    val lookups = widgets.joinToString("\n") { "        ${lookup(it)}" }
    val clicks =
        buttons.joinToString("\n\n") {
            "        if (w == ${memberName(
                it.node.name,
            )} && button == MouseState.LEFT)\n        {\n            // TODO: ${it.node.name}\n            return true;\n        }"
        }
    val changes =
        inputs.joinToString("\n\n") {
            "        if (w == ${memberName(
                it.node.name,
            )})\n        {\n            // TODO: ${it.node.name}${if (it.node.type == WidgetType.EDIT_BOX) ", finished tells that Enter was pressed" else ""}\n            return true;\n        }"
        }

    fun events(superCall: Boolean): String {
        val out = StringBuilder()
        if (buttons.isNotEmpty()) {
            out.append("\n    override bool OnClick(Widget w, int x, int y, int button)\n    {\n")
            out.append(clicks).append("\n\n")
            out.append(if (superCall) "        return super.OnClick(w, x, y, button);\n" else "        return false;\n")
            out.append("    }\n")
        }
        if (inputs.isNotEmpty()) {
            out.append("\n    override bool OnChange(Widget w, int x, int y, bool finished)\n    {\n")
            out.append(changes).append("\n\n")
            out.append(if (superCall) "        return super.OnChange(w, x, y, finished);\n" else "        return false;\n")
            out.append("    }\n")
        }
        return out.toString()
    }

    val idName = className.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase() + "_MENU_ID"
    val menuId = 90000 + (className.hashCode().toLong() and 0x7fffffffL).toInt() % 9000
    return when (kind) {
        TemplateKind.MENU -> {
            """
class $className : UIScriptedMenu
{
    protected const string LAYOUT_PATH = "$layoutRef";

$members

    void $className()
    {
    }

    void ~$className()
    {
    }

    override Widget Init()
    {
        layoutRoot = g_Game.GetWorkspace().CreateWidgets(LAYOUT_PATH);
$lookups

        return layoutRoot;
    }

    override void OnShow()
    {
        super.OnShow();

        g_Game.GetMission().PlayerControlDisable(INPUT_EXCLUDE_ALL);
        g_Game.GetUIManager().ShowUICursor(true);
        g_Game.GetMission().GetHud().Show(false);
    }

    override void OnHide()
    {
        super.OnHide();

        g_Game.GetMission().PlayerControlEnable(true);
        g_Game.GetUIManager().ShowUICursor(false);
        g_Game.GetMission().GetHud().Show(true);
    }

    override void Update(float timeslice)
    {
        super.Update(timeslice);

        if (GetUApi().GetInputByID(UAUIBack).LocalPress())
            Close();
    }
${events(true)}}

/*
// 3_Game: the id of the menu
const int $idName = $menuId;

// 5_Mission: registration of the menu
modded class MissionBase
{
    override UIScriptedMenu CreateScriptedMenu(int id)
    {
        UIScriptedMenu menu = super.CreateScriptedMenu(id);

        if (!menu && id == $idName)
        {
            menu = new $className();
            menu.SetID(id);
        }

        return menu;
    }
}

// Opening, for example from an action (OnEndClient) or a key:
UIManager uiManager = g_Game.GetUIManager();
if (uiManager && !uiManager.IsMenuOpen($idName))
    uiManager.EnterScriptedMenu($idName, null);
*/
"""
        }

        TemplateKind.COMPONENT -> {
            """
class $className : ScriptedWidgetEventHandler
{
    protected const string LAYOUT_PATH = "$layoutRef";

    ref Widget layoutRoot;
$members

    // Events for the owner: Insert a function and it is called from here
    ref ScriptInvoker m_onSelect = new ScriptInvoker();

    void $className(Widget parent)
    {
        layoutRoot = g_Game.GetWorkspace().CreateWidgets(LAYOUT_PATH, parent);
$lookups

        layoutRoot.SetHandler(this);
    }

    void ~$className()
    {
        if (layoutRoot)
            layoutRoot.Unlink();
    }

    Widget GetRoot()
    {
        return layoutRoot;
    }

    void Show(bool show)
    {
        layoutRoot.Show(show);
    }
${events(false)}}
"""
        }

        TemplateKind.WIDGETS -> {
            """
protected const string LAYOUT_PATH = "$layoutRef";

ref Widget layoutRoot;
${widgets.joinToString("\n") { "ref ${memberType(it)} ${memberName(it.node.name)};" }}

layoutRoot = g_Game.GetWorkspace().CreateWidgets(LAYOUT_PATH);
${widgets.joinToString("\n") { lookup(it) }}
"""
        }
    }.trimStart('\n').replace(Regex("\n{3,}"), "\n\n")
}
