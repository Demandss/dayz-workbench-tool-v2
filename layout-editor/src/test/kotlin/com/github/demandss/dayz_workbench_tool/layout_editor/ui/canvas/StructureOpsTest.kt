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

    private fun holder(
        name: String,
        x: Int,
        y: Int,
        vararg kids: EditorWidgetNode,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(
            WidgetType.FRAME,
            WidgetType.FRAME.className,
            name,
            linkedMapOf("position" to "$x $y", "size" to "300 300", *exact),
        ),
        kids.toMutableList(),
    )

    @Test
    fun `widgets of different parents are wrapped in the nearest widget that holds them, and stay where they are`() {
        val inner = box("inner", 10, 10)
        val outer = holder("f", 100, 100, inner)
        val loose = box("loose", 500, 50)
        val root = frame(outer, loose)
        val layout = layoutTree(root, screen)
        val (newRoot, groupId) = wrapped(root, listOf(inner.id, loose.id), layout, WidgetType.FRAME, "g")!!
        val after = layoutTree(newRoot, screen)
        assertEquals(layout.rects.getValue(inner.id), after.rects.getValue(inner.id))
        assertEquals(layout.rects.getValue(loose.id), after.rects.getValue(loose.id))
        // Both are in the group, the group is in the root, and the old parent has lost the widget it gave
        val group = newRoot.findNode(groupId)!!
        assertEquals(setOf(inner.id, loose.id), group.children.map { it.id }.toSet())
        assertEquals(groupId, newRoot.parentOf(inner.id)!!.id)
        assertEquals(newRoot.id, newRoot.parentOf(groupId)!!.id)
        assertEquals(0, newRoot.findNode(outer.id)!!.children.size)
    }

    @Test
    fun `two widgets deep in different branches go into the widget that holds both branches`() {
        val a1 = box("a1", 10, 10)
        val b1 = box("b1", 20, 20)
        val a = holder("a", 50, 50, a1)
        val b = holder("b", 400, 100, b1)
        val middle = holder("middle", 0, 0, a, b)
        val root = frame(middle)
        val layout = layoutTree(root, screen)
        val (newRoot, groupId) = wrapped(root, listOf(a1.id, b1.id), layout, WidgetType.FRAME, "g")!!
        assertEquals(middle.id, newRoot.parentOf(groupId)!!.id)
        val after = layoutTree(newRoot, screen)
        assertEquals(layout.rects.getValue(a1.id), after.rects.getValue(a1.id))
        assertEquals(layout.rects.getValue(b1.id), after.rects.getValue(b1.id))
        // The group is drawn over the two branches it was taken from
        assertEquals(listOf("a", "b", "g"), newRoot.findNode(middle.id)!!.children.map { it.node.name })
    }

    @Test
    fun `a widget selected together with the widget it is in is carried by it`() {
        val inner = box("inner", 10, 10)
        val outer = holder("f", 100, 100, inner)
        val other = box("other", 500, 50)
        val root = frame(outer, other)
        val layout = layoutTree(root, screen)
        val (newRoot, groupId) = wrapped(root, listOf(outer.id, inner.id, other.id), layout, WidgetType.FRAME, "g")!!
        assertEquals(
            setOf(outer.id, other.id),
            newRoot
                .findNode(groupId)!!
                .children
                .map { it.id }
                .toSet(),
        )
        assertEquals(outer.id, newRoot.parentOf(inner.id)!!.id)
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
