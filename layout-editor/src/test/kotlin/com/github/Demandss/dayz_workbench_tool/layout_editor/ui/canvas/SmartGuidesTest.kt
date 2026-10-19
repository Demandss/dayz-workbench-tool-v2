package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartGuidesTest {
    private val other = Rect(100f, 100f, 200f, 150f)

    @Test
    fun `a rectangle close to an edge snaps to it`() {
        val moving = Rect(0f, 0f, 40f, 40f)
        // Moved so that its left edge is at 97: three pixels from the left edge of the other one
        val snap = snapMove(moving, Offset(97f, 300f), listOf(other), threshold = 6f)
        assertEquals(3f, snap.dx)
        assertTrue(snap.guides.any { it.vertical && it.at == 100f })
    }

    @Test
    fun `nothing snaps beyond the threshold`() {
        val snap = snapMove(Rect(0f, 0f, 40f, 40f), Offset(70f, 300f), listOf(other), threshold = 6f)
        assertEquals(0f, snap.dx)
        assertEquals(0f, snap.dy)
        assertTrue(snap.guides.isEmpty())
    }

    @Test
    fun `the centres line up`() {
        // The moving centre ends at x = 147, the other centre is at 150
        val snap = snapMove(Rect(0f, 0f, 40f, 40f), Offset(127f, 0f), listOf(other), threshold = 6f)
        assertEquals(3f, snap.dx)
    }

    @Test
    fun `the gap between two widgets is measured through the shared band`() {
        val a = Rect(0f, 0f, 50f, 50f)
        val b = Rect(80f, 20f, 130f, 90f)
        val gaps = distancesBetween(a, b)
        assertEquals(30f, gaps.first { it.from.y == it.to.y }.value)
        // They share the band y 20..50, so the line runs at y = 35
        assertEquals(35f, gaps.first { it.from.y == it.to.y }.from.y)
    }

    @Test
    fun `a widget inside another one is measured to the edges of the other`() {
        val inner = Rect(20f, 30f, 60f, 70f)
        val outer = Rect(0f, 0f, 100f, 100f)
        val values = distancesBetween(inner, outer).map { it.value }.sorted()
        assertEquals(listOf(20f, 30f, 30f, 40f), values)
    }
}
