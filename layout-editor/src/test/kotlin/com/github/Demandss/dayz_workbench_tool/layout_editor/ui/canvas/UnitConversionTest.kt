package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals

class UnitConversionTest {
    @Test
    fun `pixels become a share of the parent`() {
        assertEquals(0.25f, convertUnit(480f, 1920f, toPixels = false))
    }

    @Test
    fun `a share becomes pixels`() {
        assertEquals(540f, convertUnit(0.5f, 1080f, toPixels = true))
    }

    @Test
    fun `a parent without size keeps the value`() {
        assertEquals(12f, convertUnit(12f, 0f, toPixels = false))
    }

    @Test
    fun `numbers are written short`() {
        assertEquals("20", formatNumber(20f))
        assertEquals("0.123456", formatNumber(0.123456f))
        assertEquals("0", formatNumber(-0.0000001f))
        assertEquals("1234.5", formatNumber(1234.5f))
    }
}
