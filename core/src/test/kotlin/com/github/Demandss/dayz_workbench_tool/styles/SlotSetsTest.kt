package com.github.demandss.dayz_workbench_tool.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlotSetsTest {
    @Test
    fun `images named after the slots form a set by prefix`() {
        val names = listOf("FrameLeftTop", "FrameTop", "FrameCenter", "FrameBottom", "Icon", "OtherTop")
        val sets = findSlotSets(names)
        assertEquals(1, sets.size)
        assertEquals("Frame", sets[0].label)
        assertEquals("FrameLeftTop", sets[0].images["LeftTop"])
        assertEquals("FrameCenter", sets[0].images["Center"])
    }

    @Test
    fun `the top left spelling is the left top slot`() {
        val sets = findSlotSets(listOf("quadTopLeft", "quadTopRight", "quadCenter", "quadLeft"))
        assertEquals("quadTopLeft", sets.single().images["LeftTop"])
        assertEquals("quadTopRight", sets.single().images["RightTop"])
    }

    @Test
    fun `a group of plain slot names is a set of its own`() {
        val groups = mapOf("Frame" to listOf("LeftTop", "Left", "Center"))
        val sets = findSlotSets(listOf("LeftTop", "Left", "Center"), groups)
        assertEquals(listOf("Группа Frame"), sets.map { it.label })
    }

    @Test
    fun `fewer than three slots is not a set`() {
        assertTrue(findSlotSets(listOf("aTop", "aLeft")).isEmpty())
    }
}
