package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class BulkEditTest {
    private fun panel(
        name: String,
        vararg props: Pair<String, String>,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(WidgetType.PANEL, WidgetType.PANEL.className, name, linkedMapOf(*props)),
        mutableListOf(),
    )

    private fun frame(vararg kids: EditorWidgetNode) =
        EditorWidgetNode(
            UUID.randomUUID(),
            WidgetNode(WidgetType.FRAME, WidgetType.FRAME.className, "root", linkedMapOf()),
            kids.toMutableList(),
        )

    @Test
    fun `names are replaced as text`() {
        val a = panel("btn_ok")
        val b = panel("btn_cancel")
        val c = panel("label")
        val (root, n) = bulkReplace(frame(a, b, c), null, BulkMode.NAME, "", "btn_", "button_")
        assertEquals(2, n)
        assertEquals(listOf("button_ok", "button_cancel", "label"), root.children.map { it.node.name })
    }

    @Test
    fun `a property value is replaced inside its text`() {
        val a = panel("a", "style" to "old_style")
        val b = panel("b", "style" to "other")
        val (root, n) = bulkReplace(frame(a, b), null, BulkMode.PROPERTY, "style", "old", "new")
        assertEquals(1, n)
        assertEquals("new_style", root.findNode(a.id)!!.node.properties["style"])
        assertEquals("other", root.findNode(b.id)!!.node.properties["style"])
    }

    @Test
    fun `an empty find sets the property wherever it exists`() {
        val a = panel("a", "priority" to "1")
        val b = panel("b")
        val (root, n) = bulkReplace(frame(a, b), null, BulkMode.PROPERTY, "priority", "", "5")
        assertEquals(1, n)
        assertEquals(null, root.findNode(b.id)!!.node.properties["priority"])
    }

    @Test
    fun `the scope limits what changes`() {
        val a = panel("x1")
        val b = panel("x2")
        val (root, n) = bulkReplace(frame(a, b), setOf(b.id), BulkMode.NAME, "", "x", "y")
        assertEquals(1, n)
        assertEquals(listOf("x1", "y2"), root.children.map { it.node.name })
    }

    @Test
    fun `the colours of a layout are listed once`() {
        val root = frame(panel("a", "color" to "1 0 0 1"), panel("b", "color" to "1 0 0 1"), panel("c", "color" to "0 1 0 1"))
        assertEquals(2, documentColors(root).size)
    }

    @Test
    fun `script lines are built from the widgets`() {
        val text = scriptSnippet(listOf(panel("px_box"), panel("label")))
        assertEquals(
            "PanelWidget m_PxBox;\nPanelWidget m_Label;\n\n" +
                "m_PxBox = PanelWidget.Cast(layoutRoot.FindAnyWidget(\"px_box\"));\n" +
                "m_Label = PanelWidget.Cast(layoutRoot.FindAnyWidget(\"label\"));",
            text,
        )
    }
}
