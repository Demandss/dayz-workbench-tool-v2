package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgeDistanceTest {
    private val screen = Rect(0f, 0f, 1920f, 1080f)

    @Test
    fun `a left and top anchored widget is measured from the left and top edges`() {
        val rect = Rect(20f, 20f, 180f, 80f)

        val (h, v) = edgeDistances(rect, screen, null, null)

        assertEquals(20f, h.value)
        assertEquals(Offset(0f, 50f), h.from)
        assertEquals(Offset(20f, 50f), h.to)
        assertEquals(20f, v.value)
        assertEquals(Offset(100f, 0f), v.from)
        assertEquals(Offset(100f, 20f), v.to)
    }

    @Test
    fun `a right and bottom anchored widget is measured from the right and bottom edges`() {
        val rect = Rect(1740f, 980f, 1900f, 1040f)

        val (h, v) = edgeDistances(rect, screen, "right_ref", "bottom_ref")

        assertEquals(20f, h.value)
        assertEquals(Offset(1900f, 1010f), h.from)
        assertEquals(Offset(1920f, 1010f), h.to)
        assertEquals(40f, v.value)
        assertEquals(Offset(1820f, 1040f), v.from)
        assertEquals(Offset(1820f, 1080f), v.to)
    }

    @Test
    fun `a centred widget is measured from the centre and the sign tells the direction`() {
        // 60x60 widget, 40 right of and 30 above the centre
        val rect = Rect(1000f - 30f, 510f - 30f, 1000f + 30f, 510f + 30f)

        val (h, v) = edgeDistances(rect, screen, "center_ref", "center_ref")

        assertEquals(40f, h.value)
        assertEquals(Offset(960f, 510f), h.from)
        assertEquals(-30f, v.value)
        assertEquals(Offset(1000f, 540f), v.from)
    }

    @Test
    fun `a widget exactly in the centre has nothing to measure`() {
        val rect = Rect(840f, 480f, 1080f, 600f)

        assertTrue(edgeDistances(rect, screen, "center_ref", "center_ref").isEmpty())
    }

    @Test
    fun `a widget touching an edge skips that gap`() {
        val rect = Rect(0f, 30f, 100f, 90f)

        val result = edgeDistances(rect, screen, null, null)

        assertEquals(1, result.size)
        assertEquals(30f, result[0].value)
    }

    @Test
    fun `a bare center behaves like left because that is where the canvas puts it`() {
        val rect = Rect(10f, 10f, 90f, 90f)

        assertEquals(edgeDistances(rect, screen, null, null), edgeDistances(rect, screen, "center", null))
    }

    @Test
    fun `gaps are relative to the parent, not the screen`() {
        val parent = Rect(300f, 200f, 600f, 500f)
        val rect = Rect(320f, 230f, 420f, 290f)

        val (h, v) = edgeDistances(rect, parent, null, null)

        assertEquals(20f, h.value)
        assertEquals(30f, v.value)
    }
}
