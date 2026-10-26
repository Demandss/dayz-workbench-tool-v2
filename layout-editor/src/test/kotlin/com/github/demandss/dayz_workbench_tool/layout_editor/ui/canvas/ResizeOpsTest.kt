package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResizeOpsTest {
    private val rect = Rect(100f, 100f, 300f, 200f)

    @Test
    fun `the edges and corners are found`() {
        assertEquals(ResizeHandle.E, handleAt(rect, Offset(302f, 150f), 5f))
        assertEquals(ResizeHandle.W, handleAt(rect, Offset(98f, 150f), 5f))
        assertEquals(ResizeHandle.N, handleAt(rect, Offset(200f, 103f), 5f))
        assertEquals(ResizeHandle.SE, handleAt(rect, Offset(298f, 198f), 5f))
        assertEquals(ResizeHandle.NW, handleAt(rect, Offset(101f, 99f), 5f))
    }

    @Test
    fun `the middle of the widget is not a handle`() {
        assertNull(handleAt(rect, Offset(200f, 150f), 5f))
        assertNull(handleAt(rect, Offset(400f, 150f), 5f))
    }

    @Test
    fun `a small widget keeps room to be moved`() {
        val tiny = Rect(0f, 0f, 9f, 9f)
        assertNull(handleAt(tiny, Offset(4.5f, 4.5f), 6f))
    }

    @Test
    fun `dragging the right edge changes the width only`() {
        assertEquals(Rect(100f, 100f, 340f, 200f), resizedRect(rect, ResizeHandle.E, Offset(40f, 17f)))
    }

    @Test
    fun `dragging the left edge past the right one stops at the minimum`() {
        val r = resizedRect(rect, ResizeHandle.W, Offset(500f, 0f))
        assertEquals(1f, r.width)
        assertEquals(300f, r.right)
    }

    @Test
    fun `a corner with the aspect kept grows both ways`() {
        val r = resizedRect(rect, ResizeHandle.SE, Offset(40f, 3f), keepAspect = true)
        assertEquals(240f, r.width)
        assertEquals(120f, r.height)
    }

    @Test
    fun `from the centre both sides move`() {
        val r = resizedRect(rect, ResizeHandle.E, Offset(20f, 0f), fromCentre = true)
        assertEquals(Rect(80f, 100f, 320f, 200f), r)
    }

    @Test
    fun `a dragged side snaps to the edge of another widget`() {
        val other = Rect(0f, 0f, 340f, 50f)
        val (snapped, guides) = snapResized(Rect(100f, 100f, 337f, 200f), ResizeHandle.E, listOf(other), 6f)
        assertEquals(340f, snapped.right)
        assertEquals(1, guides.size)
    }

    @Test
    fun `the new rectangle is written back in the units of the widget`() {
        val parent = Rect(0f, 0f, 1000f, 500f)
        val node =
            EditorWidgetNode(
                UUID.randomUUID(),
                WidgetNode(
                    WidgetType.PANEL,
                    WidgetType.PANEL.className,
                    "a",
                    linkedMapOf(
                        "position" to "0.1 0.1",
                        "size" to "0.2 0.2",
                        "hexactpos" to "0",
                        "vexactpos" to "0",
                        "hexactsize" to "0",
                        "vexactsize" to "0",
                    ),
                ),
                mutableListOf(),
            )
        val root =
            EditorWidgetNode(
                UUID.randomUUID(),
                WidgetNode(WidgetType.FRAME, WidgetType.FRAME.className, "root", linkedMapOf()),
                mutableListOf(node),
            )
        val result = withRect(root, node.id, Rect(100f, 50f, 400f, 250f), parent)
        assertEquals("0.3 0.4", result.findNode(node.id)!!.node.properties["size"])
        assertEquals("0.1 0.1", result.findNode(node.id)!!.node.properties["position"])
    }
}
