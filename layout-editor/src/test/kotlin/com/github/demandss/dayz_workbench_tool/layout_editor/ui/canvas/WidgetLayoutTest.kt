package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetLayoutTest {
    private val screen = Rect(0f, 0f, 1920f, 1080f)

    private fun widget(
        name: String,
        vararg props: Pair<String, String>,
        type: WidgetType = WidgetType.PANEL,
        children: List<EditorWidgetNode> = emptyList(),
    ) = EditorWidgetNode(
        node = WidgetNode(type, type.className, name, LinkedHashMap(props.toMap())),
        children = children.toMutableList(),
    )

    private fun pixels(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) = arrayOf(
        "position" to "$x $y",
        "size" to "$w $h",
        "hexactpos" to "1",
        "vexactpos" to "1",
        "hexactsize" to "1",
        "vexactsize" to "1",
    )

    @Test
    fun `the root takes the whole screen and is its own parent`() {
        val root = widget("root")

        val layout = layoutTree(root, screen)

        assertEquals(screen, layout.rects[root.id])
        assertEquals(screen, layout.parentRects[root.id])
    }

    @Test
    fun `a child is placed by its own position inside its parent`() {
        val child = widget("child", *pixels(20, 30, 100, 50))
        val parent = widget("parent", *pixels(200, 100, 400, 300), children = listOf(child))
        val root = widget("root", children = listOf(parent))

        val layout = layoutTree(root, screen)

        assertEquals(Rect(200f, 100f, 600f, 400f), layout.rects[parent.id])
        assertEquals(Rect(220f, 130f, 320f, 180f), layout.rects[child.id])
        assertEquals(layout.rects[parent.id], layout.parentRects[child.id])
    }

    @Test
    fun `anchors and fractions follow the parent`() {
        val right = widget("right", *pixels(20, 20, 160, 60), "halign" to "right_ref", "valign" to "bottom_ref")
        val half = widget("half", "position" to "0.25 0.5", "size" to "0.5 0.25")
        val root = widget("root", children = listOf(right, half))

        val layout = layoutTree(root, screen)

        assertEquals(Rect(1740f, 1000f, 1900f, 1060f), layout.rects[right.id])
        assertEquals(Rect(480f, 540f, 1440f, 810f), layout.rects[half.id])
    }
}
