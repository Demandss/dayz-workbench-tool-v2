package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Rect
import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toEditorNode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.TreeLayout
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VanillaScriptsTest {
    private val screen = Rect(0f, 0f, 1920f, 1080f)

    private fun tree(text: String) = LayoutParser.parse(text).toEditorNode()

    private fun run(root: EditorWidgetNode): TreeLayout = VanillaScripts.layout(root) { layoutTree(it, screen) }

    private fun EditorWidgetNode.named(name: String): EditorWidgetNode =
        if (node.name == name) this else children.firstNotNullOf { c -> runCatching { c.named(name) }.getOrNull() }

    private fun TreeLayout.of(
        root: EditorWidgetNode,
        name: String,
    ) = rects.getValue(root.named(name).id)

    @Test
    fun `a layout without these script classes is left alone`() {
        assertFalse(VanillaScripts.present(tree("FrameWidgetClass A {\n size 1 1\n scriptclass \"HoverEffect\"\n}")))
        assertTrue(VanillaScripts.present(tree("FrameWidgetClass A {\n size 1 1\n scriptclass \"SizeToChild\"\n}")))
    }

    @Test
    fun `SizeToChild takes the size of the child and the offset`() {
        val root =
            tree(
                """
                FrameWidgetClass Root {
                 size 1 1
                 {
                  FrameWidgetClass Box {
                   size 100 10
                   hexactsize 1
                   vexactsize 1
                   scriptclass "SizeToChild"
                   {
                    PanelWidgetClass Content {
                     size 300 200
                     hexactsize 1
                     vexactsize 1
                    }
                   }
                   {
                    ScriptParamsClass {
                     m_ChildName "Content"
                     m_HorizontalOffset 10
                     m_VerticalOffset 5
                     m_ResizeHorizontal 1
                     m_ResizeVertical 1
                    }
                   }
                  }
                 }
                }
                """.trimIndent(),
            )
        val box = run(root).of(root, "Box")
        assertEquals(310f, box.width, 0.01f)
        assertEquals(205f, box.height, 0.01f)
    }

    @Test
    fun `SizeToChild only changes the axes that are switched on`() {
        val root =
            tree(
                """
                FrameWidgetClass Root {
                 size 1 1
                 {
                  FrameWidgetClass Box {
                   size 100 10
                   hexactsize 1
                   vexactsize 1
                   scriptclass "SizeToChild"
                   {
                    PanelWidgetClass Content {
                     size 300 200
                     hexactsize 1
                     vexactsize 1
                    }
                   }
                   {
                    ScriptParamsClass {
                     m_ChildName "Content"
                     m_ResizeVertical 1
                    }
                   }
                  }
                 }
                }
                """.trimIndent(),
            )
        val box = run(root).of(root, "Box")
        assertEquals(100f, box.width, 0.01f)
        assertEquals(200f, box.height, 0.01f)
    }

    @Test
    fun `SizeToParent follows the size of the parent`() {
        val root =
            tree(
                """
                FrameWidgetClass Root {
                 size 400 300
                 hexactsize 1
                 vexactsize 1
                 {
                  PanelWidgetClass Fill {
                   size 1 1
                   hexactsize 1
                   vexactsize 1
                   scriptclass "SizeToParent"
                   {
                    ScriptParamsClass {
                     m_ResizeHorizontal 1
                     m_ResizeVertical 1
                    }
                   }
                  }
                 }
                }
                """.trimIndent(),
            )
        // 1 px in exact size would be a speck; as a share of the parent it fills it
        val fill = run(root).of(root, "Fill")
        assertEquals(400f, fill.width, 0.01f)
        assertEquals(300f, fill.height, 0.01f)
    }

    @Test
    fun `AutoHeightSpacer lines the children up in rows and takes the height of the stack`() {
        val root =
            tree(
                """
                FrameWidgetClass Root {
                 size 1 1
                 {
                  FrameWidgetClass List {
                   size 250 1
                   hexactsize 1
                   vexactsize 1
                   scriptclass "AutoHeightSpacer"
                   {
                    PanelWidgetClass A {
                     size 100 50
                     hexactsize 1
                     vexactsize 1
                    }
                    PanelWidgetClass B {
                     size 100 50
                     hexactsize 1
                     vexactsize 1
                    }
                    PanelWidgetClass C {
                     size 100 50
                     hexactsize 1
                     vexactsize 1
                    }
                   }
                   {
                    ScriptParamsClass {
                     AlignChilds 1
                     Gap 10
                    }
                   }
                  }
                 }
                }
                """.trimIndent(),
            )
        val layout = run(root)
        val list = layout.of(root, "List")
        assertEquals(110f, list.height, 0.01f)
        assertEquals(0f, layout.of(root, "A").left - list.left, 0.01f)
        assertEquals(110f, layout.of(root, "B").left - list.left, 0.01f)
        assertEquals(60f, layout.of(root, "C").top - list.top, 0.01f)
        assertEquals(0f, layout.of(root, "C").left - list.left, 0.01f)
    }

    @Test
    fun `AutoHeightSpacer keeps within its minimum and maximum height`() {
        fun height(
            min: Int,
            max: Int,
        ): Float {
            val root =
                tree(
                    """
                    FrameWidgetClass Root {
                     size 1 1
                     {
                      FrameWidgetClass List {
                       size 250 1
                       hexactsize 1
                       vexactsize 1
                       scriptclass "AutoHeightSpacer"
                       {
                        PanelWidgetClass A {
                         size 100 50
                         hexactsize 1
                         vexactsize 1
                        }
                       }
                       {
                        ScriptParamsClass {
                         AlignChilds 1
                         MinHeight $min
                         MaxHeight $max
                        }
                       }
                      }
                     }
                    }
                    """.trimIndent(),
                )
            return run(root).of(root, "List").height
        }
        assertEquals(50f, height(0, 0), 0.01f)
        assertEquals(80f, height(80, 0), 0.01f)
        assertEquals(30f, height(0, 30), 0.01f)
    }

    @Test
    fun `HorizontalSpacer divides its width among the children`() {
        val root =
            tree(
                """
                FrameWidgetClass Root {
                 size 1 1
                 {
                  FrameWidgetClass Row {
                   size 300 100
                   hexactsize 1
                   vexactsize 1
                   scriptclass "HorizontalSpacer"
                   {
                    PanelWidgetClass A {
                     size 5 5
                    }
                    PanelWidgetClass B {
                     size 5 5
                    }
                    PanelWidgetClass C {
                     size 5 5
                    }
                   }
                   {
                    ScriptParamsClass {
                     Border 10
                     Gap 10
                    }
                   }
                  }
                 }
                }
                """.trimIndent(),
            )
        val layout = run(root)
        val row = layout.of(root, "Row")
        val a = layout.of(root, "A")
        val b = layout.of(root, "B")
        assertEquals(260f / 3f, a.width, 0.01f)
        assertEquals(80f, a.height, 0.01f)
        assertEquals(10f, a.left - row.left, 0.01f)
        assertEquals(10f + 260f / 3f + 10f, b.left - row.left, 0.01f)
    }

    @Test
    fun `VerticalSpacer divides its height among the children`() {
        val root =
            tree(
                """
                FrameWidgetClass Root {
                 size 1 1
                 {
                  FrameWidgetClass Column {
                   size 100 210
                   hexactsize 1
                   vexactsize 1
                   scriptclass "VerticalSpacer"
                   {
                    PanelWidgetClass A {
                     size 5 5
                    }
                    PanelWidgetClass B {
                     size 5 5
                    }
                   }
                   {
                    ScriptParamsClass {
                     border 5
                     gap 10
                    }
                   }
                  }
                 }
                }
                """.trimIndent(),
            )
        val layout = run(root)
        val column = layout.of(root, "Column")
        val b = layout.of(root, "B")
        assertEquals(90f, b.width, 0.01f)
        assertEquals(95f, b.height, 0.01f)
        assertEquals(5f + 95f + 10f, b.top - column.top, 0.01f)
    }
}
