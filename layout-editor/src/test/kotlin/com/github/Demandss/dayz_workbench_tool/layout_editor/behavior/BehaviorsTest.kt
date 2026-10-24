package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BehaviorsTest {
    private fun widget(
        name: String,
        scriptClass: String? = null,
        params: Map<String, String>? = null,
        vararg children: EditorWidgetNode,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(
            WidgetType.PANEL,
            WidgetType.PANEL.className,
            name,
            linkedMapOf<String, String>().also { if (scriptClass != null) it["scriptclass"] = "\"$scriptClass\"" },
            mutableListOf(),
            params?.let { LinkedHashMap(it) },
        ),
        children.toMutableList(),
    )

    @Test
    fun `a class of a mod is found by its plain name`() {
        assertEquals("HoverColor", Behaviors.find("MF_CLS_HoverColorHandler")?.name)
        assertEquals("PressScale", Behaviors.find("MF_CLS_PressScale")?.name)
        assertEquals("HoverScale", Behaviors.find("hoverscale")?.name)
        assertNull(Behaviors.find("MF_CLS_SearchTextInput"))
    }

    @Test
    fun `hover scales a widget and only while the pointer is over it`() {
        val button = widget("Btn", "HoverScale", mapOf("Scale" to "1.2"))
        val root = widget("Root", children = arrayOf(button))
        assertTrue(previewEffects(root, emptySet(), emptySet()).isEmpty())
        val effect = previewEffects(root, setOf(root.id, button.id), emptySet())[button.id]
        assertEquals(1.2f, effect?.scale)
    }

    @Test
    fun `old parameter names of a mod class still work`() {
        val button = widget("Btn", "MF_CLS_PressScale", mapOf("PressScale" to "0.9"))
        val effect = previewEffects(button, emptySet(), setOf(button.id))[button.id]
        assertEquals(0.9f, effect?.scale)
    }

    @Test
    fun `a missing parameter takes its default`() {
        val button = widget("Btn", "PressOffset")
        val effect = previewEffects(button, emptySet(), setOf(button.id))[button.id]
        assertEquals(Offset(0f, 2f), effect?.offset)
    }

    @Test
    fun `the target can be the parent or a named widget`() {
        val glow = widget("Glow")
        val handler = widget("H", "HoverColor", mapOf("Color" to "#E8622A", "ColorParent" to "1"))
        val card = widget("Card", children = arrayOf(handler))
        val root = widget("Root", children = arrayOf(card, glow))
        val effects = previewEffects(root, setOf(handler.id), emptySet())
        assertEquals(setOf(card.id), effects.keys)

        val named = widget("H", "HoverScale", mapOf("Target" to "Glow"))
        val root2 = widget("Root", children = arrayOf(named, glow))
        assertEquals(setOf(glow.id), previewEffects(root2, setOf(named.id), emptySet()).keys)
    }

    @Test
    fun `the colour of an effect replaces the colour of the widget in a copy`() {
        val handler = widget("H", "HoverColor", mapOf("Color" to "#FF0000"))
        val root = widget("Root", children = arrayOf(handler))
        val effects = previewEffects(root, setOf(handler.id), emptySet())
        val shown = root.withEffectProperties(effects)
        val color =
            assertNotNull(
                shown.children
                    .single()
                    .node.properties["color"],
            )
        assertEquals("1.0 0.0 0.0 1.0", color)
        assertNull(handler.node.properties["color"])
    }

    @Test
    fun `a widget named by HoverShow is hidden until the pointer is over the trigger`() {
        val tip = widget("Tip")
        val trigger = widget("Trigger", "HoverShow", mapOf("Target" to "Tip"))
        val root = widget("Root", children = arrayOf(trigger, tip))
        val idle = previewEffects(root, emptySet(), emptySet())
        assertEquals(false, idle[tip.id]?.visible)
        assertEquals(
            "0",
            root
                .withEffectProperties(idle)
                .children[1]
                .node.properties["visible"],
        )
        assertEquals(true, previewEffects(root, setOf(trigger.id), emptySet())[tip.id]?.visible)
    }

    @Test
    fun `HoverImage swaps image0 and HoverText colours the text`() {
        val icon = widget("Icon", "HoverImage", mapOf("Image" to "set:a image:b"))
        val label = widget("Label", "HoverText", mapOf("Color" to "#FF0000"))
        val root = widget("Root", children = arrayOf(icon, label))
        val shown = root.withEffectProperties(previewEffects(root, setOf(icon.id, label.id), emptySet()))
        assertEquals("\"set:a image:b\"", shown.children[0].node.properties["image0"])
        assertEquals("1.0 0.0 0.0 1.0", shown.children[1].node.properties["color"])
    }

    @Test
    fun `blink goes between full and the lowest opacity`() {
        val w = widget("W", "Blink", mapOf("Period" to "2", "MinAlpha" to "0.25"))
        assertEquals(1f, previewEffects(w, emptySet(), emptySet(), 0f)[w.id]?.alpha)
        assertEquals(0.25f, previewEffects(w, emptySet(), emptySet(), 1f)[w.id]?.alpha ?: 0f, 0.001f)
    }

    @Test
    fun `fade in is finished after its duration`() {
        val w = widget("W", "FadeIn", mapOf("Duration" to "1"))
        assertEquals(0.5f, previewEffects(w, emptySet(), emptySet(), 0.5f)[w.id]?.alpha)
        assertNull(previewEffects(w, emptySet(), emptySet(), 1.5f)[w.id])
    }

    @Test
    fun `shake only plays after a press and dies down`() {
        val w = widget("W", "Shake", mapOf("Amplitude" to "10", "Duration" to "0.4"))
        assertNull(previewEffects(w, emptySet(), emptySet(), 3f)[w.id])
        val during = previewEffects(w, emptySet(), emptySet(), 3.05f, mapOf(w.id to 3f))[w.id]
        assertNotNull(during)
        assertNull(previewEffects(w, emptySet(), emptySet(), 3.5f, mapOf(w.id to 3f))[w.id])
    }

    @Test
    fun `a progress bar and a counter reach their value and start over`() {
        val bar = widget("Bar", "ProgressTween", mapOf("Value" to "80", "Duration" to "1"))
        assertEquals(0f, previewEffects(bar, emptySet(), emptySet(), 0f)[bar.id]?.current)
        assertEquals(80f, previewEffects(bar, emptySet(), emptySet(), 1.5f)[bar.id]?.current)
        assertEquals(0f, previewEffects(bar, emptySet(), emptySet(), 2f)[bar.id]?.current)
        val count = widget("Count", "CountUp", mapOf("Value" to "250", "Duration" to "1"))
        assertEquals("250", previewEffects(count, emptySet(), emptySet(), 1.2f)[count.id]?.text)
    }

    @Test
    fun `only a layout with a timed class needs the clock`() {
        assertEquals(true, Behaviors.hasTimed(widget("R", children = arrayOf(widget("W", "Blink")))))
        assertEquals(false, Behaviors.hasTimed(widget("R", children = arrayOf(widget("W", "HoverScale")))))
    }

    @Test
    fun `hex colours`() {
        assertEquals(Color(0xFFE8622A), parseHexColor("#E8622A"))
        assertEquals(Color(0x80E8622A), parseHexColor("#E8622A80"))
        assertNull(parseHexColor("red"))
    }
}
