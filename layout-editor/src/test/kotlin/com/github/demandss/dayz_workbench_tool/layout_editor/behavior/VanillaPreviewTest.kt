package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toEditorNode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VanillaPreviewTest {
    private fun widget(
        name: String,
        scriptClass: String? = null,
        params: Map<String, String>? = null,
        type: WidgetType = WidgetType.FRAME,
        vararg children: EditorWidgetNode,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(
            type,
            type.className,
            name,
            linkedMapOf<String, String>().also { if (scriptClass != null) it["scriptclass"] = "\"$scriptClass\"" },
            mutableListOf(),
            params?.let { LinkedHashMap(it) },
        ),
        children.toMutableList(),
    )

    private fun frame(
        name: String,
        vararg children: EditorWidgetNode,
    ) = widget(name, type = WidgetType.FRAME, children = children)

    private fun effects(
        root: EditorWidgetNode,
        states: Map<UUID, Map<String, String>> = emptyMap(),
        hovered: Set<UUID> = emptySet(),
        focus: UUID? = null,
    ) = VanillaPreview.effects(root, states, hovered, focus)

    private class Tabs(
        val owner: EditorWidgetNode,
        val container: EditorWidgetNode,
        val control0: EditorWidgetNode,
        val control1: EditorWidgetNode,
        val title0: EditorWidgetNode,
        val title1: EditorWidgetNode,
        val tab0: EditorWidgetNode,
        val tab1: EditorWidgetNode,
    )

    private fun tabberUi(): Tabs {
        val title0 = frame("Tab_Control_0_Title")
        val title1 = frame("Tab_Control_1_Title")
        val control0 = frame("Tab_Control_0", title0)
        val control1 = frame("Tab_Control_1", title1)
        val container = frame("Tab_Control_Container", control0, control1)
        val tab0 = frame("Tab_0")
        val tab1 = frame("Tab_1")
        val owner = widget("Tabs", "TabberUI", null, WidgetType.FRAME, container, tab0, tab1)
        return Tabs(owner, container, control0, control1, title0, title1, tab0, tab1)
    }

    @Test
    fun `TabberUI dresses the first tab at the start and leaves the panels as they are`() {
        val t = tabberUi()
        val out = effects(t.owner)
        assertEquals(Color(1f, 0f, 0f, 1f), out[t.title0.id]?.textColor)
        assertEquals(Color(0f, 0f, 0f, 1f), out[t.control0.id]?.color)
        assertNull(out[t.control1.id])
        assertNull(out[t.tab0.id])
        assertNull(out[t.tab1.id])
    }

    @Test
    fun `a click on a tab control switches the tab, hides the other panels and dresses the controls`() {
        val t = tabberUi()
        val state = assertNotNullState(VanillaPreview.click(t.owner, listOf(t.container, t.control1, t.title1), emptyMap()))
        assertEquals("1", state["tab"])
        val out = effects(t.owner, mapOf(t.owner.id to state))
        assertEquals(false, out[t.tab0.id]?.visible)
        assertEquals(true, out[t.tab1.id]?.visible)
        assertEquals(Color(1f, 1f, 1f, 1f), out[t.title0.id]?.textColor)
        assertEquals(Color(0f, 0f, 0f, 0f), out[t.control0.id]?.color)
        assertEquals(Color(0f, 0f, 0f, 1f), out[t.control1.id]?.color)
        // The tab that is on is not switched to again
        assertNull(VanillaPreview.click(t.owner, listOf(t.control1), state))
    }

    @Test
    fun `a hovered control that is not selected is dressed like the selected one`() {
        val t = tabberUi()
        val out = effects(t.owner, hovered = setOf(t.control1.id))
        assertEquals(Color(1f, 0f, 0f, 1f), out[t.title1.id]?.textColor)
    }

    @Test
    fun `Tabber shows the panel of the button that was clicked`() {
        val buttons = frame("ButtonsPanel", frame("B0"), frame("B1"), frame("B2"))
        val content = frame("ContentPanel", frame("P0"), frame("P1"), frame("P2"))
        val owner = widget("T", "Tabber", mapOf("SelectedTab" to "2"), WidgetType.FRAME, buttons, content)
        val start = effects(owner)
        assertEquals(listOf(false, false, true), content.children.map { start[it.id]?.visible })
        val state = assertNotNullState(VanillaPreview.click(owner, listOf(buttons, buttons.children[1]), emptyMap()))
        val out = effects(owner, mapOf(owner.id to state))
        assertEquals(listOf(false, true, false), content.children.map { out[it.id]?.visible })
    }

    @Test
    fun `MainMenuButtonEffect colours the text white, red and moved when the button has the focus`() {
        val button = widget("B", "MainMenuButtonEffect", mapOf("amount" to "5"), WidgetType.BUTTON)
        val root = frame("Root", button)
        val idle = effects(root)[button.id]
        assertEquals(Color(1f, 1f, 1f, 1f), idle?.textColor)
        assertEquals(0f, idle?.textOffset)
        val focused = effects(root, focus = button.id)[button.id]
        assertEquals(Color(1f, 0f, 0f, 1f), focused?.textColor)
        assertEquals(20f, focused?.textOffset)
    }

    @Test
    fun `EmbededButtonScript puts the caption on the first child button`() {
        val button = widget("B", type = WidgetType.BUTTON)
        val owner = widget("Holder", "EmbededButtonScript", mapOf("Caption" to "Play"), WidgetType.FRAME, button)
        assertEquals("Play", effects(owner)[button.id]?.text)
        val notAButton = widget("Holder", "EmbededButtonScript", mapOf("Caption" to "Play"), WidgetType.FRAME, frame("X"))
        assertTrue(effects(notAButton).isEmpty())
    }

    @Test
    fun `RadialProgressBar starts with both halves hidden`() {
        val inner = frame("Inner")
        val hider = frame("BarHider_Ring", inner)
        val part = frame("BarPart2_Ring")
        val owner = widget("Ring", "RadialProgressBar", null, WidgetType.FRAME, hider, part)
        val out = effects(owner)
        assertEquals(false, out[inner.id]?.visible)
        assertEquals(false, out[part.id]?.visible)
        assertNull(out[hider.id])
    }

    @Test
    fun `these classes also work through the effects of the whole layout`() {
        val button = widget("B", "MainMenuButtonEffect", mapOf("amount" to "1"), WidgetType.BUTTON)
        val root = frame("Root", button)
        assertEquals(Color(1f, 1f, 1f, 1f), previewEffects(root, emptySet(), emptySet())[button.id]?.textColor)
    }

    @Test
    fun `RightGap makes the widget as wide as its parent less the gap and as high`() {
        val root =
            LayoutParser
                .parse(
                    """
                    FrameWidgetClass Root {
                     size 1 1
                     {
                      FrameWidgetClass Parent {
                       size 400 300
                       hexactsize 1
                       vexactsize 1
                       {
                        PanelWidgetClass Strip {
                         size 10 10
                         scriptclass "RightGap"
                        }
                        PanelWidgetClass Gap {
                         size 1 1
                        }
                       }
                      }
                     }
                    }
                    """.trimIndent(),
                ).toEditorNode()
        val layout = VanillaScripts.layout(root) { layoutTree(it, Rect(0f, 0f, 1920f, 1080f)) }
        val strip = layout.rects.getValue(root.children[0].children[0].id)
        assertEquals(400f, strip.width, 0.01f) // no gap given: the whole parent
        assertEquals(300f, strip.height, 0.01f)
    }

    @Test
    fun `HorizontalSpacerWithFixedAspect puts the other children right of the first one`() {
        val root =
            LayoutParser
                .parse(
                    """
                    FrameWidgetClass Root {
                     size 1 1
                     {
                      FrameWidgetClass Row {
                       size 500 100
                       hexactsize 1
                       vexactsize 1
                       scriptclass "HorizontalSpacerWithFixedAspect"
                       {
                        PanelWidgetClass A {
                         size 100 100
                         hexactsize 1
                         vexactsize 1
                        }
                        PanelWidgetClass B {
                         size 100 100
                         hexactsize 1
                         vexactsize 1
                        }
                       }
                       {
                        ScriptParamsClass {
                         coef 0.5
                        }
                       }
                      }
                     }
                    }
                    """.trimIndent(),
                ).toEditorNode()
        val layout = VanillaScripts.layout(root) { layoutTree(it, Rect(0f, 0f, 1920f, 1080f)) }
        val row = root.children[0]
        val a = layout.rects.getValue(row.children[0].id)
        val b = layout.rects.getValue(row.children[1].id)
        assertEquals(150f, b.left - a.left, 0.01f)
    }

    private fun assertNotNullState(state: Map<String, String>?): Map<String, String> {
        assertTrue(state != null, "the click changed nothing")
        return state
    }
}
