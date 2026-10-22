package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptTemplateTest {
    private fun node(
        type: WidgetType,
        name: String,
        vararg children: EditorWidgetNode,
    ) = EditorWidgetNode(node = WidgetNode(type, type.className, name, LinkedHashMap()), children = children.toMutableList())

    private val root =
        node(
            WidgetType.PANEL,
            "rootFrame",
            node(WidgetType.PANEL, "Header"),
            node(WidgetType.BUTTON, "CloseBtn"),
            node(WidgetType.EDIT_BOX, "NameInput"),
            node(WidgetType.TEXT, "Label"),
            node(WidgetType.TEXT, "Label"),
            node(WidgetType.TEXT, "Имя"),
        )

    @Test
    fun menuHasMembersLookupAndEvents() {
        val text = scriptTemplate(root, "Mod/GUI/x.layout", "MF_X", TemplateKind.MENU)
        assertTrue("class MF_X : UIScriptedMenu" in text)
        assertTrue("LAYOUT_PATH = \"Mod/GUI/x.layout\"" in text)
        assertTrue("ref Widget m_Header;" in text)
        assertTrue("m_CloseBtn = ButtonWidget.Cast(layoutRoot.FindAnyWidget(\"CloseBtn\"));" in text)
        assertTrue("override bool OnClick" in text && "override bool OnChange" in text)
        assertTrue("MF_X_MENU_ID" in text || "MF_X" in text)
    }

    @Test
    fun repeatedAndInvalidNamesAreLeftOut() {
        val text = scriptTemplate(root, "x.layout", "A", TemplateKind.WIDGETS)
        assertTrue(text.split("ref TextWidget m_Label;").size == 2)
        assertFalse("Имя" in text)
        assertFalse("rootFrame" in text)
    }

    @Test
    fun componentTakesAParentAndSetsItsHandler() {
        val text = scriptTemplate(root, "x.layout", "B", TemplateKind.COMPONENT)
        assertTrue("void B(Widget parent)" in text)
        assertTrue("CreateWidgets(LAYOUT_PATH, parent)" in text)
        assertTrue("layoutRoot.SetHandler(this);" in text)
    }
}
