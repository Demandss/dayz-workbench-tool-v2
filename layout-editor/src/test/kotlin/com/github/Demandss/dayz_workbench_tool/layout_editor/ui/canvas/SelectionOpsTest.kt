package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toEditorNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionOpsTest {
    private val ids = List(6) { UUID.randomUUID() }
    private val screen = Rect(0f, 0f, 1920f, 1080f)

    @Test
    fun `the keys give the mode of the click`() {
        assertEquals(SelectMode.REPLACE, selectMode(shift = false, ctrl = false))
        assertEquals(SelectMode.TOGGLE, selectMode(shift = false, ctrl = true))
        assertEquals(SelectMode.RANGE, selectMode(shift = true, ctrl = false))
        assertEquals(SelectMode.RANGE_ADD, selectMode(shift = true, ctrl = true))
    }

    @Test
    fun `a plain click selects one widget and makes it the anchor`() {
        val (selection, anchor) = applySelection(listOf(ids[0], ids[1]), ids[0], ids[3], ids, SelectMode.REPLACE)
        assertEquals(listOf(ids[3]), selection)
        assertEquals(ids[3], anchor)
    }

    @Test
    fun `Ctrl adds and removes one widget at a time`() {
        val (added, anchor) = applySelection(listOf(ids[1]), ids[1], ids[4], ids, SelectMode.TOGGLE)
        assertEquals(listOf(ids[1], ids[4]), added)
        assertEquals(ids[4], anchor)
        val (removed, next) = applySelection(added, anchor, ids[4], ids, SelectMode.TOGGLE)
        assertEquals(listOf(ids[1]), removed)
        assertEquals(ids[1], next)
    }

    @Test
    fun `Shift takes everything between the anchor and the click, up or down`() {
        val (down, anchor) = applySelection(listOf(ids[1]), ids[1], ids[4], ids, SelectMode.RANGE)
        assertEquals(ids.subList(1, 5), down)
        assertEquals(ids[1], anchor)
        val (up, _) = applySelection(down, anchor, ids[0], ids, SelectMode.RANGE)
        assertEquals(ids.subList(0, 2), up)
    }

    @Test
    fun `Shift with Ctrl adds the range to what is selected, and a range without an anchor is a plain click`() {
        val (added, _) = applySelection(listOf(ids[5]), ids[1], ids[3], ids, SelectMode.RANGE_ADD)
        assertEquals(listOf(ids[5], ids[1], ids[2], ids[3]), added)
        val (alone, anchor) = applySelection(emptyList(), null, ids[2], ids, SelectMode.RANGE)
        assertEquals(listOf(ids[2]), alone)
        assertEquals(ids[2], anchor)
    }

    private fun tree(): EditorWidgetNode =
        LayoutParser
            .parse(
                """
                FrameWidgetClass Root {
                 size 1 1
                 {
                  PanelWidgetClass A {
                   position 100 100
                   size 200 100
                   hexactpos 1
                   vexactpos 1
                   hexactsize 1
                   vexactsize 1
                   color 1 0 0 1
                   visible 1
                  }
                  PanelWidgetClass B {
                   position 400 300
                   size 100 100
                   hexactpos 1
                   vexactpos 1
                   hexactsize 1
                   vexactsize 1
                   color 0 1 0 1
                   visible 1
                  }
                  PanelWidgetClass C {
                   position 0 0
                   size 1 1
                   {
                    PanelWidgetClass Inner {
                     position 10 10
                     size 20 20
                     hexactpos 1
                     vexactpos 1
                     hexactsize 1
                     vexactsize 1
                    }
                   }
                  }
                 }
                }
                """.trimIndent(),
            ).toEditorNode()

    private fun EditorWidgetNode.named(name: String): EditorWidgetNode = flat().first { it.node.name == name }

    private fun EditorWidgetNode.flat(): List<EditorWidgetNode> = listOf(this) + children.flatMap { it.flat() }

    @Test
    fun `properties that agree are kept, differing or partial ones are Mixed, absent ones are left out`() {
        val root = tree()
        val merged = mergedProperties(listOf(root.named("A"), root.named("B")))
        assertEquals("1", merged["visible"])
        assertEquals(MIXED, merged["color"])
        assertEquals(MIXED, merged["size"])
        assertNull(merged["image0"])
        // Only one of the two has `color`
        assertEquals(MIXED, mergedProperties(listOf(root.named("A"), root.named("Inner")))["color"])
    }

    @Test
    fun `the rectangle around the selection and a move of its corner`() {
        val root = tree()
        val layout = layoutTree(root, screen)
        val ids = listOf(root.named("A").id, root.named("B").id)
        val bounds = selectionBounds(ids, layout)!!
        assertEquals(Rect(100f, 100f, 500f, 400f), bounds)
        val moved = movedSelection(root, ids, layout, screen, 50f, -20f)
        val after = layoutTree(moved, screen)
        assertEquals(Rect(150f, 80f, 550f, 380f), selectionBounds(ids, after)!!)
    }

    @Test
    fun `scaling the selection keeps the distances in proportion`() {
        val root = tree()
        val layout = layoutTree(root, screen)
        val ids = listOf(root.named("A").id, root.named("B").id)
        val scaled = scaledSelection(root, ids, layout, screen, 800f, null)
        val after = layoutTree(scaled, screen)
        val a = after.rects.getValue(ids[0])
        val b = after.rects.getValue(ids[1])
        assertEquals(Rect(100f, 100f, 100f + 400f, 200f), a)
        assertEquals(Rect(100f + 600f, 300f, 100f + 800f, 400f), b)
        assertEquals(800f, selectionBounds(ids, after)!!.width, 0.01f)
        assertEquals(300f, selectionBounds(ids, after)!!.height, 0.01f)
    }

    @Test
    fun `a widget inside another selected one is carried along, not moved twice`() {
        val root = tree()
        val layout = layoutTree(root, screen)
        val ids = listOf(root.named("C").id, root.named("Inner").id)
        assertEquals(listOf(root.named("C").id), topLevel(root, ids))
        val moved = movedSelection(root, ids, layout, screen, 30f, 0f)
        val after = layoutTree(moved, screen)
        assertEquals(30f, after.rects.getValue(root.named("Inner").id).left - layout.rects.getValue(root.named("Inner").id).left, 0.01f)
    }
}
