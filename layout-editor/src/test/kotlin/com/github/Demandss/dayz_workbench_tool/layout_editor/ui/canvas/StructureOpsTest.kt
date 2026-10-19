package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StructureOpsTest {
    private val screen = Rect(0f, 0f, 1000f, 500f)
    private val exact = arrayOf("hexactpos" to "1", "vexactpos" to "1", "hexactsize" to "1", "vexactsize" to "1")

    private fun box(
        name: String,
        x: Int,
        y: Int,
        w: Int = 50,
        h: Int = 50,
        vararg more: Pair<String, String>,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(
            WidgetType.PANEL,
            WidgetType.PANEL.className,
            name,
            linkedMapOf("position" to "$x $y", "size" to "$w $h", *exact, *more),
        ),
        mutableListOf(),
    )

    private fun frame(vararg kids: EditorWidgetNode) =
        EditorWidgetNode(
            UUID.randomUUID(),
            WidgetNode(WidgetType.FRAME, WidgetType.FRAME.className, "root", linkedMapOf()),
            kids.toMutableList(),
        )

    @Test
    fun `wrapping keeps the widgets where they are on the screen`() {
        val a = box("a", 100, 100)
        val b = box("b", 200, 150)
        val root = frame(a, b)
        val layout = layoutTree(root, screen)
        val (newRoot, groupId) = wrapped(root, listOf(a.id, b.id), layout, WidgetType.FRAME, "group")!!
        val after = layoutTree(newRoot, screen)
        assertEquals(layout.rects.getValue(a.id), after.rects.getValue(a.id))
        assertEquals(layout.rects.getValue(b.id), after.rects.getValue(b.id))
        assertEquals(Rect(100f, 100f, 250f, 200f), after.rects.getValue(groupId))
    }

    @Test
    fun `ungrouping puts the widgets back and keeps their place`() {
        val a = box("a", 100, 100)
        val b = box("b", 200, 150)
        val root = frame(a, b)
        val layout = layoutTree(root, screen)
        val (grouped, groupId) = wrapped(root, listOf(a.id, b.id), layout, WidgetType.FRAME, "group")!!
        val (freed, ids) = ungrouped(grouped, groupId, layoutTree(grouped, screen))!!
        assertEquals(2, ids.size)
        assertEquals(layout.rects.getValue(a.id), layoutTree(freed, screen).rects.getValue(a.id))
    }

    @Test
    fun `widgets of different parents are not wrapped`() {
        val inner = box("inner", 10, 10)
        val outer =
            EditorWidgetNode(
                UUID.randomUUID(),
                WidgetNode(
                    WidgetType.FRAME,
                    WidgetType.FRAME.className,
                    "f",
                    linkedMapOf("size" to "400 400", *exact),
                ),
                mutableListOf(inner),
            )
        val loose = box("loose", 500, 50)
        val root = frame(outer, loose)
        assertNull(wrapped(root, listOf(inner.id, loose.id), layoutTree(root, screen), WidgetType.FRAME, "g"))
    }

    @Test
    fun `reordering moves a widget among its siblings`() {
        val a = box("a", 0, 0)
        val b = box("b", 0, 0)
        val c = box("c", 0, 0)
        val root = frame(a, b, c)
        assertEquals(listOf("b", "c", "a"), reordered(root, a.id, Reorder.FRONT)!!.children.map { it.node.name })
        assertEquals(listOf("a", "c", "b"), reordered(root, b.id, Reorder.FORWARD)!!.children.map { it.node.name })
        assertNull(reordered(root, a.id, Reorder.BACK))
    }

    @Test
    fun `distributing makes the gaps equal`() {
        val a = box("a", 0, 0, 50, 50)
        val b = box("b", 60, 0, 50, 50)
        val c = box("c", 300, 0, 50, 50)
        val root = frame(a, b, c)
        val result = distributed(root, listOf(a.id, b.id, c.id), layoutTree(root, screen), horizontal = true)
        // The gaps become (350 - 150) / 2 = 100, so b starts at 150
        assertEquals("150 0", result.findNode(b.id)!!.node.properties["position"])
    }

    @Test
    fun `changing the anchor resets the offset of the axis that changed`() {
        val a = box("a", 100, 40, 200, 100)
        val root = frame(a)
        val moved = reanchored(root, a.id, "right_ref", "top")
        val props = moved.findNode(a.id)!!.node.properties
        // The horizontal anchor changed, so x starts again from zero, the vertical one is the same and keeps its 40
        assertEquals("0 40", props["position"])
        assertEquals("right_ref", props["halign"])
        assertEquals(Rect(800f, 40f, 1000f, 140f), layoutTree(moved, screen).rects.getValue(a.id))
    }

    @Test
    fun `changing both anchors resets both offsets`() {
        val a = box("a", 100, 40, 200, 100)
        val moved = reanchored(frame(a), a.id, "center_ref", "bottom_ref")
        assertEquals("0 0", moved.findNode(a.id)!!.node.properties["position"])
    }

    @Test
    fun `appearance carries the look but not the place`() {
        val a = box("a", 5, 6, 10, 10, "color" to "1 0 0 1", "style" to "s")
        val copied = a.appearance()
        assertEquals(mapOf("style" to "s", "color" to "1 0 0 1"), copied)
        val b = box("b", 9, 9).withAppearance(copied)
        assertEquals("9 9", b.node.properties["position"])
        assertNotNull(b.node.properties["color"])
    }
}
