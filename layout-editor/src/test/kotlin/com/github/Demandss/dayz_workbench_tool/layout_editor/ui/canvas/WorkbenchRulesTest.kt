package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rules measured on probe layouts and on the vanilla layouts in Workbench 1.30 (grid2_probe, wrap2_probe, text_probe...). */
class WorkbenchRulesTest {
    private val screen = Rect(0f, 0f, 1920f, 1080f)

    private fun widget(
        name: String,
        type: WidgetType,
        vararg props: Pair<String, String>,
        children: List<EditorWidgetNode> = emptyList(),
    ) = EditorWidgetNode(
        node = WidgetNode(type, type.className, name, LinkedHashMap(props.toMap())),
        children = children.toMutableList(),
    )

    private fun pixelPanel(
        name: String,
        w: Int,
        h: Int,
    ) = widget(name, WidgetType.PANEL, "size" to "$w $h", "hexactsize" to "1", "vexactsize" to "1")

    private fun container(
        type: WidgetType,
        w: Int,
        h: Int,
        children: List<EditorWidgetNode>,
        vararg props: Pair<String, String>,
    ) = widget(
        "spacer",
        type,
        "position" to "0 0",
        "size" to "$w $h",
        "hexactpos" to "1",
        "vexactpos" to "1",
        "hexactsize" to "1",
        "vexactsize" to "1",
        *props,
        children = children,
    )

    private fun layoutOf(
        container: EditorWidgetNode,
        textSize: ((EditorWidgetNode, Float, Float) -> Size?)? = null,
    ): TreeLayout = layoutTree(widget("root", WidgetType.FRAME, children = listOf(container)), screen, textSize = textSize)

    private fun TreeLayout.of(node: EditorWidgetNode) = rects.getValue(node.id)

    @Test
    fun `a grid that sizes to its content has a row for every child and children as wide as the grid`() {
        val kids = (0 until 5).map { pixelPanel("k$it", 100, 40) }
        val grid =
            container(
                WidgetType.GRID_SPACER,
                300,
                240,
                kids,
                "Columns" to "1",
                "Size To Content V" to "1",
                "Padding" to "2",
                "Margin" to "2",
            )

        val layout = layoutOf(grid)

        assertEquals(Rect(2f, 2f, 298f, 42f), layout.of(kids[0]))
        assertEquals(Rect(2f, 170f, 298f, 210f), layout.of(kids[4]))
        assertEquals(212f, layout.of(grid).height)
        assertEquals(300f, layout.of(grid).width)
    }

    @Test
    fun `a grid that sizes to its content in both ways shrinks to the children`() {
        val kids = (0 until 5).map { pixelPanel("k$it", 100, 40) }
        val grid =
            container(
                WidgetType.GRID_SPACER,
                300,
                240,
                kids,
                "Columns" to "2",
                "Size To Content V" to "1",
                "Size To Content H" to "1",
                "Padding" to "2",
                "Margin" to "2",
            )

        val layout = layoutOf(grid)

        assertEquals(206f, layout.of(grid).width)
        assertEquals(128f, layout.of(grid).height)
    }

    @Test
    fun `a child that has no cell in a grid is not drawn`() {
        val kids = (0 until 5).map { pixelPanel("k$it", 100, 40) }
        val grid = container(WidgetType.GRID_SPACER, 300, 240, kids, "Columns" to "2", "Rows" to "2", "Padding" to "2", "Margin" to "2")

        val layout = layoutOf(grid)

        assertTrue(kids[3].id in layout.rects)
        assertFalse(kids[4].id in layout.rects)
        // a pixel sized child fills its cell
        assertEquals(Rect(151f, 2f, 298f, 119f), layout.of(kids[1]))
    }

    @Test
    fun `a wrap aligned to the bottom fills its rows from the bottom up`() {
        val kids = (0 until 5).map { pixelPanel("k$it", 100, 60) }
        val wrap = container(WidgetType.WRAP_SPACER, 260, 260, kids, "Padding" to "4", "Margin" to "4", "content_valign" to "bottom")

        val layout = layoutOf(wrap)

        // the first row (two children) is the lowest, the fifth child is alone in the top row
        assertEquals(196f, layout.of(kids[0]).top)
        assertEquals(196f, layout.of(kids[1]).top)
        assertEquals(132f, layout.of(kids[2]).top)
        assertEquals(68f, layout.of(kids[4]).top)
    }

    @Test
    fun `a root with a size and an alignment leaves a margin`() {
        val root =
            widget(
                "root",
                WidgetType.FRAME,
                "size" to "0.9 0.9",
                "halign" to "center_ref",
                "valign" to "center_ref",
            )

        val layout = layoutTree(root, screen)

        val rect = layout.of(root)
        assertEquals(96f, rect.left, 0.01f)
        assertEquals(54f, rect.top, 0.01f)
        assertEquals(1728f, rect.width, 0.01f)
    }

    @Test
    fun `a text that sizes to its text takes the size of the text and keeps its corner`() {
        val label =
            widget(
                "label",
                WidgetType.TEXT,
                "position" to "10 10",
                "size" to "1 1",
                "hexactpos" to "1",
                "vexactpos" to "1",
                "size to text h" to "1",
                "text" to "\"abc\"",
            )
        val frame = container(WidgetType.FRAME, 200, 24, listOf(label))

        val layout = layoutOf(frame) { _, _, _ -> Size(50f, 24f) }

        assertEquals(Rect(10f, 10f, 60f, 34f), layout.of(label))
    }

    private fun text(
        type: WidgetType,
        vararg props: Pair<String, String>,
    ) = widget("t", type, *props)

    @Test
    fun `the size of the letters follows the height unless the text is exact`() {
        assertEquals(24f, text(WidgetType.TEXT).textPixelSize(24f))
        assertEquals(12f, text(WidgetType.TEXT, "text_proportion" to "0.5").textPixelSize(24f))
        assertEquals(12f, text(WidgetType.RICH_TEXT).textPixelSize(24f))
        assertEquals(18f, text(WidgetType.TEXT, "exact text" to "1", "exact text size" to "18").textPixelSize(70f))
        // a button ignores the exact size
        assertEquals(70f, text(WidgetType.BUTTON, "exact text" to "1", "exact text size" to "24").textPixelSize(70f))
    }

    @Test
    fun `a bitmap Metron font takes the biggest size that fits`() {
        val text = text(WidgetType.TEXT)
        assertEquals(22f, text.textPixelSize(24f, "gui/fonts/MetronBook"))
        assertEquals(28f, text.textPixelSize(40f, "gui/fonts/Metron"))
        assertEquals(16f, text.textPixelSize(100f, "gui/fonts/Metron16"))
        assertEquals(24f, text.textPixelSize(24f, "gui/fonts/sdf_MetronBook24"))
    }
}
