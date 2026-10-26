package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The numbers come from the container tests in the dayz-ui skill (Workbench 1.29, Live Preview). */
class SpacerLayoutTest {
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

    private fun square(
        name: String,
        side: Int = 40,
        vararg extra: Pair<String, String>,
    ) = widget(
        name,
        WidgetType.PANEL,
        "size" to "$side $side",
        "hexactsize" to "1",
        "vexactsize" to "1",
        *extra,
    )

    private fun wrap(
        w: Int,
        h: Int,
        children: List<EditorWidgetNode>,
        vararg props: Pair<String, String>,
    ) = widget(
        "wrap",
        WidgetType.WRAP_SPACER,
        "position" to "0 0",
        "size" to "$w $h",
        "hexactpos" to "1",
        "vexactpos" to "1",
        "hexactsize" to "1",
        "vexactsize" to "1",
        *props,
        children = children,
    )

    private fun layoutOf(container: EditorWidgetNode): TreeLayout =
        layoutTree(widget("root", WidgetType.FRAME, children = listOf(container)), screen)

    private fun TreeLayout.of(node: EditorWidgetNode) = rects.getValue(node.id)

    @Test
    fun `padding is the gap between children, rows are padding apart`() {
        // 130 px wide, 40 px children, Padding 8: two fit (40 + 8 + 40 = 88), the third does not
        val a = square("a")
        val b = square("b")
        val c = square("c")
        val container = wrap(130, 200, listOf(a, b, c), "Padding" to "8", "Margin" to "0")

        val layout = layoutOf(container)

        assertEquals(Rect(0f, 0f, 40f, 40f), layout.of(a))
        assertEquals(Rect(48f, 0f, 88f, 40f), layout.of(b))
        assertEquals(Rect(0f, 48f, 40f, 88f), layout.of(c))
    }

    @Test
    fun `margin is a frame around the children and narrows the available width`() {
        // Margin 10 leaves 110 px, which still fits two children and not three
        val a = square("a")
        val b = square("b")
        val c = square("c")
        val container = wrap(130, 200, listOf(a, b, c), "Padding" to "8", "Margin" to "10")

        val layout = layoutOf(container)

        assertEquals(Rect(10f, 10f, 50f, 50f), layout.of(a))
        assertEquals(Rect(58f, 10f, 98f, 50f), layout.of(b))
        assertEquals(Rect(10f, 58f, 50f, 98f), layout.of(c))
    }

    @Test
    fun `padding and margin are 2 when not written`() {
        val a = square("a")
        val b = square("b")
        val c = square("c")
        val container = wrap(100, 200, listOf(a, b, c))

        val layout = layoutOf(container)

        assertEquals(Rect(2f, 2f, 42f, 42f), layout.of(a))
        assertEquals(Rect(44f, 2f, 84f, 42f), layout.of(b))
        assertEquals(Rect(2f, 44f, 42f, 84f), layout.of(c))
    }

    @Test
    fun `a container is placed by its own position and the children follow it`() {
        val a = square("a")
        val container =
            widget(
                "wrap",
                WidgetType.WRAP_SPACER,
                "position" to "300 200",
                "size" to "100 100",
                "hexactpos" to "1",
                "vexactpos" to "1",
                "hexactsize" to "1",
                "vexactsize" to "1",
                "Padding" to "0",
                "Margin" to "0",
                children = listOf(a),
            )

        val layout = layoutOf(container)

        assertEquals(Rect(300f, 200f, 340f, 240f), layout.of(a))
    }

    @Test
    fun `size to content V grows the container to its rows and keeps the width`() {
        // Two rows of 40 with a gap of 8 are 96 high in Workbench: every row is followed by the gap, the last one too
        val container =
            wrap(
                130,
                10,
                listOf(square("a"), square("b"), square("c")),
                "Padding" to "8",
                "Margin" to "0",
                "Size To Content V" to "1",
            )

        val layout = layoutOf(container)

        assertEquals(Rect(0f, 0f, 130f, 96f), layout.of(container))
    }

    @Test
    fun `centred content sits in the middle on both axes`() {
        val a = square("a")
        val b = square("b")
        val container =
            wrap(200, 100, listOf(a, b), "Padding" to "8", "Margin" to "0", "content_halign" to "center", "content_valign" to "center")

        val layout = layoutOf(container)

        // the row is 88 wide, so 56 from the left. Vertically Workbench centres the row together with the gap after it
        // (48), which puts it at 26 and not at 30; measured at (436, 46) for a container at (380, 20)
        assertEquals(Rect(56f, 26f, 96f, 66f), layout.of(a))
        assertEquals(Rect(104f, 26f, 144f, 66f), layout.of(b))
    }

    @Test
    fun `content aligned right goes to the corner and the row is mirrored`() {
        val first = square("first")
        val second = square("second")
        val container =
            wrap(
                200,
                100,
                listOf(first, second),
                "Padding" to "8",
                "Margin" to "0",
                "content_halign" to "right",
                "content_valign" to "bottom",
            )

        val layout = layoutOf(container)

        // declared first, drawn rightmost
        assertEquals(Rect(160f, 60f, 200f, 100f), layout.of(first))
        assertEquals(Rect(112f, 60f, 152f, 100f), layout.of(second))
    }

    @Test
    fun `a hidden child gives up its slot unless ignore invisible is off`() {
        val a = square("a")
        val hidden = square("hidden", 40, "visible" to "0")
        val c = square("c")

        val skipped = layoutOf(wrap(300, 100, listOf(a, hidden, c), "Padding" to "10", "Margin" to "0"))
        val kept = layoutOf(wrap(300, 100, listOf(a, hidden, c), "Padding" to "10", "Margin" to "0", "Ignore invisible" to "0"))

        assertEquals(Rect(50f, 0f, 90f, 40f), skipped.of(c))
        assertEquals(Rect(100f, 0f, 140f, 40f), kept.of(c))
    }

    @Test
    fun `a child wider than the container stays alone in its row`() {
        val wide = square("wide", 200)
        val small = square("small")
        val container = wrap(100, 300, listOf(wide, small), "Padding" to "4", "Margin" to "0")

        val layout = layoutOf(container)

        assertEquals(Rect(0f, 0f, 200f, 200f), layout.of(wide))
        assertEquals(Rect(0f, 204f, 40f, 244f), layout.of(small))
    }

    @Test
    fun `grid cells are equal and relative children fill them`() {
        // 100x100, Padding 6, Margin 3, 2x2: a cell is (100 - 6 - 6) / 2 = 44
        val cells = (1..4).map { widget("c$it", WidgetType.PANEL, "size" to "1 1", "hexactsize" to "0", "vexactsize" to "0") }
        val container =
            widget(
                "grid",
                WidgetType.GRID_SPACER,
                "position" to "0 0",
                "size" to "100 100",
                "hexactpos" to "1",
                "vexactpos" to "1",
                "hexactsize" to "1",
                "vexactsize" to "1",
                "Columns" to "2",
                "Rows" to "2",
                "Padding" to "6",
                "Margin" to "3",
                children = cells,
            )

        val layout = layoutOf(container)

        assertEquals(Rect(3f, 3f, 47f, 47f), layout.of(cells[0]))
        assertEquals(Rect(53f, 3f, 97f, 47f), layout.of(cells[1]))
        assertEquals(Rect(3f, 53f, 47f, 97f), layout.of(cells[2]))
        assertEquals(Rect(53f, 53f, 97f, 97f), layout.of(cells[3]))
    }

    @Test
    fun `columns and rows decide the shape, six children make 3x2 or 2x3`() {
        fun grid(
            columns: Int,
            rows: Int,
        ): List<Rect> {
            val kids = (1..6).map { widget("c$it", WidgetType.PANEL, "size" to "1 1", "hexactsize" to "0", "vexactsize" to "0") }
            val container =
                widget(
                    "grid",
                    WidgetType.GRID_SPACER,
                    "position" to "0 0",
                    "size" to "300 200",
                    "hexactpos" to "1",
                    "vexactpos" to "1",
                    "hexactsize" to "1",
                    "vexactsize" to "1",
                    "Columns" to "$columns",
                    "Rows" to "$rows",
                    "Padding" to "0",
                    "Margin" to "0",
                    children = kids,
                )
            val layout = layoutOf(container)
            return kids.map { layout.of(it) }
        }

        val threeByTwo = grid(3, 2)
        val twoByThree = grid(2, 3)

        assertEquals(Rect(200f, 0f, 300f, 100f), threeByTwo[2])
        assertEquals(Rect(0f, 100f, 100f, 200f), threeByTwo[3])
        assertEquals(Rect(150f, 0f, 300f, 66.666664f), twoByThree[1])
        assertEquals(Rect(0f, 133.33333f, 150f, 200f), twoByThree[4])
    }

    // Measured in Workbench 1.30 (grid2_probe, 100 x 40 children in a 2 x 2 grid of 300 x 240): the children fill their cells
    @Test
    fun `a pixel sized child fills its cell`() {
        val child = square("child", 30)
        val container =
            widget(
                "grid",
                WidgetType.GRID_SPACER,
                "position" to "0 0",
                "size" to "200 100",
                "hexactpos" to "1",
                "vexactpos" to "1",
                "hexactsize" to "1",
                "vexactsize" to "1",
                "Columns" to "2",
                "Rows" to "1",
                "Padding" to "0",
                "Margin" to "0",
                children = listOf(widget("first", WidgetType.PANEL, "size" to "1 1"), child),
            )

        val layout = layoutOf(container)

        assertEquals(Rect(100f, 0f, 200f, 100f), layout.of(child))
    }

    // Measured in Workbench 1.30 (nested_probe): a 260 x 300 wrap with Margin 4 and Padding 8

    @Test
    fun `a spacer inside a wrap that sizes to its content is as high as its content and as wide as the parent allows`() {
        val inner =
            widget(
                "inner",
                WidgetType.WRAP_SPACER,
                "size" to "120 20",
                "hexactsize" to "1",
                "vexactsize" to "1",
                "Padding" to "6",
                "Margin" to "6",
                "Size To Content V" to "1",
                children = listOf(square("a"), square("b")),
            )
        val after = square("after", 50)
        val layout = layoutOf(wrap(260, 300, listOf(inner, after), "Padding" to "8", "Margin" to "4"))
        // Left 4 inside the margin, cut off at the parent's right edge, 6 + 40 + 6 + a gap of 6
        assertEquals(Rect(4f, 4f, 260f, 62f), layout.of(inner))
        // The next row starts a gap under it
        assertEquals(70f, layout.of(after).top)
    }

    @Test
    fun `a spacer inside a wrap ignores its size and runs to the edge of the parent`() {
        val grid =
            widget(
                "grid",
                WidgetType.GRID_SPACER,
                "size" to "0.5 80",
                "hexactsize" to "0",
                "vexactsize" to "1",
                "Padding" to "4",
                "Margin" to "4",
                "Columns" to "2",
                "Rows" to "2",
            )
        val first = square("first", 40)
        val layout = layoutOf(wrap(260, 300, listOf(first, grid), "Padding" to "8", "Margin" to "4"))
        // It goes to a row of its own under the first child and is cut off at the parent's right and bottom edge
        assertEquals(Rect(4f, 52f, 260f, 300f), layout.of(grid))
    }

    @Test
    fun `the row after a spacer in a wrap starts a parent height further down`() {
        val grid = widget("grid", WidgetType.GRID_SPACER, "Columns" to "2", "Rows" to "2")
        val after = square("after", 30)
        val layout = layoutOf(wrap(260, 300, listOf(grid, after), "Padding" to "8", "Margin" to "4"))
        assertEquals(4f + 300f + 8f, layout.of(after).top)
    }

    @Test
    fun `size to content horizontally changes nothing`() {
        val container = wrap(300, 100, listOf(square("a", 50), square("b", 50)), "Size To Content H" to "1")
        assertEquals(300f, layoutOf(container).of(container).width)
    }

    @Test
    fun `a grid child with a relative size fills its cell even when the fraction is smaller`() {
        val cell = widget("cell", WidgetType.PANEL, "size" to "0.5 0.5", "hexactsize" to "0", "vexactsize" to "0")
        val grid =
            widget(
                "grid",
                WidgetType.GRID_SPACER,
                "position" to "0 0",
                "size" to "256 296",
                "hexactpos" to "1",
                "vexactpos" to "1",
                "hexactsize" to "1",
                "vexactsize" to "1",
                "Padding" to "4",
                "Margin" to "4",
                "Columns" to "2",
                "Rows" to "2",
                children = listOf(cell),
            )
        val rect = layoutOf(grid).of(cell)
        // (256 - 2 * 4 - 4) / 2 by (296 - 2 * 4 - 4) / 2, as measured in Workbench
        assertEquals(122f, rect.width)
        assertEquals(142f, rect.height)
    }
}
