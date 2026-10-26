package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.Color
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetColorTest {
    // The numbers were measured on the Workbench of 1.30, which reads them as linear light
    @BeforeTest
    fun workbench130() {
        ColorSpaceMode.workbench130 = true
    }

    @AfterTest
    fun game() {
        ColorSpaceMode.workbench130 = false
    }

    private fun panel(vararg props: Pair<String, String>) =
        EditorWidgetNode(node = WidgetNode(WidgetType.PANEL, WidgetType.PANEL.className, "p", LinkedHashMap(props.toMap())))

    private fun assertRgb(
        expected: Triple<Int, Int, Int>,
        actual: Color,
    ) {
        val got = Triple((actual.red * 255).toInt(), (actual.green * 255).toInt(), (actual.blue * 255).toInt())
        // The screenshot is 8 bit, so one step either way is rounding
        assertTrue(
            abs(got.first - expected.first) <= 1 && abs(got.second - expected.second) <= 1 && abs(got.third - expected.third) <= 1,
            "expected $expected, got $got",
        )
    }

    @Test
    fun `color is read as linear light and shown in sRGB, as measured in Workbench`() {
        // Pixel values read from Workbench screenshots of filled panels
        assertRgb(Triple(244, 148, 148), parseWidgetColor("0.9 0.3 0.3 1"))
        assertRgb(Triple(137, 142, 148), parseWidgetColor("0.25 0.27 0.3 1"))
        assertRgb(Triple(90, 93, 98), parseWidgetColor("0.1 0.11 0.12 1"))
    }

    @Test
    fun `black, white and alpha are unchanged`() {
        assertEquals(Color(0f, 0f, 0f, 1f), parseWidgetColor("0 0 0 1"))
        assertEquals(Color(1f, 1f, 1f, 0.5f), parseWidgetColor("1 1 1 0.5"))
    }

    @Test
    fun `a missing or short colour is transparent or has defaults`() {
        assertEquals(Color.Transparent, parseWidgetColor(null))
        assertEquals(1f, parseWidgetColor("0.5 0.5 0.5").alpha)
        assertEquals(Color(0f, 0f, 0f, 1f), parseWidgetColor(""))
    }

    @Test
    fun `values outside 0 to 1 are clamped`() {
        assertEquals(Color(1f, 0f, 1f, 1f), parseWidgetColor("2 -1 1 5"))
    }

    @Test
    fun `a panel without a style draws nothing`() {
        assertNull(panel("color" to "1 0 0 1").fillColor())
    }

    @Test
    fun `styles that paint nothing give no fill`() {
        for (style in listOf("blank", "Empty", "default")) {
            assertNull(panel("style" to style, "color" to "1 0 0 1").fillColor(), style)
        }
    }

    @Test
    fun `a colorable style fills with the colour and needs one`() {
        assertNotNull(panel("style" to "rover_sim_colorable", "color" to "0.9 0.3 0.3 1").fillColor())
        assertNotNull(panel("style" to "Colorable", "color" to "0.9 0.3 0.3 1").fillColor())
        // the Colorable style has a transparent colour of its own
        assertNull(panel("style" to "Colorable").fillColor())
        // rover_sim_colorable is white
        assertEquals(Color.White, panel("style" to "rover_sim_colorable").fillColor())
    }

    @Test
    fun `an unknown style is approximated by a flat fill of the colour`() {
        assertRgb(Triple(244, 148, 148), assertNotNull(panel("style" to "rover_sim_black", "color" to "0.9 0.3 0.3 1").fillColor()))
    }
}
