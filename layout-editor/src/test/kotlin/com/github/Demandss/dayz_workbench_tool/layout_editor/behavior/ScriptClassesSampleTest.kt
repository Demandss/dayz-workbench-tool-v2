package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toEditorNode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The sample layout with every script class: it must read, name every class and show what the classes do. */
class ScriptClassesSampleTest {
    private val file =
        listOf("samples/script-classes.layout", "layout-editor/samples/script-classes.layout").map(::File).firstOrNull { it.isFile }

    private fun root(): EditorWidgetNode = LayoutParser.parse(file!!.readText()).toEditorNode()

    private fun all(node: WidgetNode): List<WidgetNode> = listOf(node) + node.children.flatMap { all(it) }

    private fun EditorWidgetNode.flat(): List<EditorWidgetNode> = listOf(this) + children.flatMap { it.flat() }

    private fun EditorWidgetNode.named(name: String) = flat().first { it.node.name == name }

    private val screen = Rect(0f, 0f, 1920f, 1080f)

    @Test
    fun `the sample names every custom class and every vanilla class of the preview`() {
        if (file == null) return
        val used = all(LayoutParser.parse(file.readText())).mapNotNull { it.properties["scriptclass"]?.trim('"') }.toSet()
        val custom = BehaviorCatalogue.entries.map { it.name }
        assertEquals(emptyList(), custom.filter { it !in used })
        val vanilla =
            listOf(
                "SizeToChild",
                "SizeToParent",
                "AutoHeightSpacer",
                "NoticeSpacer",
                "HorizontalSpacer",
                "VerticalSpacer",
                "HorizontalSpacerWithFixedAspect",
                "RightGap",
                "Tabber",
                "TabberUI",
                "MainMenuButtonEffect",
                "EmbededButtonScript",
                "RadialProgressBar",
                "HoverEffect",
                "Bouncer",
                "Rotator",
            )
        assertEquals(emptyList(), vanilla.filter { it !in used })
    }

    @Test
    fun `widget names are unique, so that every class finds its own widgets`() {
        if (file == null) return
        val names = all(LayoutParser.parse(file.readText())).map { it.name }
        assertEquals(
            emptyList(),
            names
                .groupBy { it }
                .filter { it.value.size > 1 }
                .keys
                .toList(),
        )
    }

    @Test
    fun `the classes of the game change the layout in Live Preview`() {
        if (file == null) return
        val tree = root()
        val layout = VanillaScripts.layout(tree) { layoutTree(it, screen) }

        fun rect(name: String) = layout.rects.getValue(tree.named(name).id)
        // SizeToChild: the content is 120 x 50, the offsets 24 and 16
        assertEquals(144f, rect("Box_v_SizeToChild").width, 0.01f)
        assertEquals(66f, rect("Box_v_SizeToChild").height, 0.01f)
        // SizeToParent: the child fills its parent
        assertEquals(rect("Parent_v_SizeToParent").width, rect("Fill_v_SizeToParent").width, 0.01f)
        assertEquals(rect("Parent_v_SizeToParent").height, rect("Fill_v_SizeToParent").height, 0.01f)
        // AutoHeightSpacer: four items of 80 x 24 in a row of 280 take two rows, 24 + 6 + 24
        assertEquals(54f, rect("List_v_AutoHeightSpacer").height, 0.01f)
        // RightGap: the parent is 280 wide, the gap 60
        assertEquals(220f, rect("Strip_v_RightGap").width, 0.01f)
        // HorizontalSpacer: three cells in 280 with the border and the gap of 8
        assertEquals((280f - 16f - 16f) / 3f, rect("Cell0_v_HorizontalSpacer").width, 0.01f)
        // VerticalSpacer: three cells in 100 with the border and the gap of 6
        assertEquals((100f - 12f - 12f) / 3f, rect("Cell0_v_VerticalSpacer").height, 0.01f)
    }

    @Test
    fun `the tabs start with the first panel and the main menu button text is white`() {
        if (file == null) return
        val tree = root()
        val effects = previewEffects(tree, emptySet(), emptySet())
        val panels = listOf(0, 1, 2).map { effects[tree.named("Panel${it}_v_Tabber").id]?.visible }
        assertEquals(listOf(true, false, false), panels)
        assertNotNull(effects[tree.named("Button_v_MainMenuButtonEffect").id]?.textColor)
        assertEquals("Играть", effects[tree.named("Button_v_EmbededButtonScript").id]?.text)
        assertEquals(false, effects[tree.named("BarPart2_Ring").id]?.visible)
        assertTrue(effects[tree.named("Card_v_HoverEffect").id] == null)
    }

    @Test
    fun `the cells of both halves stay inside their panels`() {
        if (file == null) return
        val tree = root()
        val layout = VanillaScripts.layout(tree) { layoutTree(it, screen) }
        for (name in listOf("PanelCustom", "PanelVanilla")) {
            val panel = layout.rects.getValue(tree.named(name).id)
            val half =
                tree.flat().filter { n ->
                    n.node.name.startsWith("T_") &&
                        n.node.name
                            .removePrefix("T_")
                            .startsWith(if (name == "PanelCustom") "c_" else "v_")
                }
            for (cell in half) {
                val r = layout.rects.getValue(cell.id)
                assertTrue(r.left >= panel.left && r.right <= panel.right && r.bottom <= panel.bottom, "${cell.node.name} is outside $name")
            }
        }
    }
}
