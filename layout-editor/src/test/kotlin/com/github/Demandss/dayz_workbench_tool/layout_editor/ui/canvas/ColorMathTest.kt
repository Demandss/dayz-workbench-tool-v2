package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorMathTest {
    private fun near(
        a: Float,
        b: Float,
        eps: Float = 0.002f,
    ) = assertTrue(abs(a - b) < eps, "$a is not near $b")

    @Test
    fun `linear and sRGB are inverse`() {
        for (v in listOf(0f, 0.01f, 0.3f, 0.8f, 1f)) near(v, srgbToLinear(linearToSrgb(v)))
    }

    @Test
    fun `a layout colour turns into the colour on the screen and back`() {
        val shown = parseWidgetColor("0.9 0.3 0.3 1")
        val back = parseWidgetColor(shown.toFileColor())
        near(shown.red, back.red)
        near(shown.green, back.green)
    }

    @Test
    fun `hsv round trip`() {
        val c = Color(0.2f, 0.6f, 0.9f)
        val hsv = c.toHsv()
        val back = hsvToColor(hsv.h, hsv.s, hsv.v)
        near(c.red, back.red)
        near(c.green, back.green)
        near(c.blue, back.blue)
    }

    @Test
    fun `pure red has hue zero`() {
        assertEquals(0f, Color.Red.toHsv().h)
        assertEquals(120f, Color.Green.toHsv().h)
    }

    @Test
    fun `hex is read and written`() {
        assertEquals("#3366CC", parseHex("3366cc")!!.toHex())
        assertNull(parseHex("#12"))
        assertNull(parseHex("zzzzzz"))
    }
}
