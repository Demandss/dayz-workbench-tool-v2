package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProblemsTest {
    private fun widget(
        name: String,
        vararg props: Pair<String, String>,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(WidgetType.PANEL, WidgetType.PANEL.className, name, linkedMapOf(*props)),
        mutableListOf(),
    )

    private fun frame(
        name: String,
        vararg children: EditorWidgetNode,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(WidgetType.FRAME, WidgetType.FRAME.className, name, linkedMapOf()),
        children.toMutableList(),
    )

    @Test
    fun `a clean layout has no problems`() {
        val root = frame("Root", widget("a", "style" to "x"))
        assertTrue(findProblems(root).isEmpty())
    }

    @Test
    fun `bare center is reported`() {
        val root = frame("Root", widget("a", "style" to "x", "halign" to "center"))
        assertEquals(listOf("a"), findProblems(root).map { it.widgetName })
    }

    @Test
    fun `a panel without a style is reported`() {
        val root = frame("Root", widget("a"))
        assertEquals(1, findProblems(root).size)
    }

    @Test
    fun `a zero size is an error`() {
        val root = frame("Root", widget("a", "style" to "x", "size" to "0 20"))
        assertEquals(Severity.ERROR, findProblems(root).single().severity)
    }

    @Test
    fun `a repeated sibling name is reported once for each repeat`() {
        val root =
            frame("Root", widget("a", "style" to "x"), widget("a", "style" to "x"), widget("b", "style" to "x"))
        assertEquals(1, findProblems(root).size)
    }
}
