package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetRulesTest {
    private fun widget(
        name: String,
        vararg props: Pair<String, String>,
        children: List<EditorWidgetNode> = emptyList(),
    ) = EditorWidgetNode(
        node = WidgetNode(WidgetType.PANEL, WidgetType.PANEL.className, name, LinkedHashMap(props.toMap())),
        children = children.toMutableList(),
    )

    @Test
    fun `a widget is visible and clips its children unless told otherwise`() {
        val w = widget("w")

        assertTrue(w.isVisible())
        assertTrue(w.clipsChildren())
    }

    @Test
    fun `visible 0 hides and clipchildren 0 lets children stick out`() {
        assertFalse(widget("w", "visible" to "0").isVisible())
        assertFalse(widget("w", "visible" to " false ").isVisible())
        assertTrue(widget("w", "visible" to "1").isVisible())
        assertFalse(widget("w", "clipchildren" to "0").clipsChildren())
        assertTrue(widget("w", "clipchildren" to "1").clipsChildren())
    }

    @Test
    fun `children keep the file order when priorities are equal`() {
        val parent = widget("p", children = listOf(widget("a"), widget("b"), widget("c")))

        assertEquals(listOf("a", "b", "c"), parent.drawOrder().map { it.node.name })
    }

    @Test
    fun `a higher priority is drawn later, on top of the others`() {
        val parent =
            widget(
                "p",
                children = listOf(widget("first", "priority" to "5"), widget("second"), widget("third", "priority" to "-1")),
            )

        assertEquals(listOf("third", "second", "first"), parent.drawOrder().map { it.node.name })
    }

    @Test
    fun `hidden children are not in the draw order`() {
        val parent = widget("p", children = listOf(widget("a"), widget("b", "visible" to "0"), widget("c")))

        assertEquals(listOf("a", "c"), parent.drawOrder().map { it.node.name })
    }

    @Test
    fun `an unreadable priority counts as zero`() {
        val parent = widget("p", children = listOf(widget("a", "priority" to "high"), widget("b", "priority" to "1")))

        assertEquals(listOf("a", "b"), parent.drawOrder().map { it.node.name })
    }

    @Test
    fun `dragging moves right and bottom anchored widgets the opposite way`() {
        assertEquals(-1f, anchorDirection("right_ref"))
        assertEquals(-1f, anchorDirection("bottom_ref"))
        assertEquals(1f, anchorDirection("center_ref"))
        assertEquals(1f, anchorDirection(null))
    }
}
