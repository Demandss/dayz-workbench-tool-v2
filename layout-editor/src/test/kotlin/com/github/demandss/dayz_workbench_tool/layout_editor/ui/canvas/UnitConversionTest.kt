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
    fun `a number is written with a dot whatever the language of the interface is`() {
        val before = java.util.Locale.getDefault()
        try {
            // The interface sets this locale: in Russian the decimal separator is a comma, which a layout cannot read
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ru"))
            assertEquals("0.3333", formatShort(1f / 3f))
            assertEquals("20", formatShort(20f))
            assertEquals("-12", formatShort(-12f))
            assertEquals("0.123456", formatNumber(0.123456f))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun `numbers are written short`() {
        assertEquals("20", formatNumber(20f))
        assertEquals("0.123456", formatNumber(0.123456f))
        assertEquals("0", formatNumber(-0.0000001f))
        assertEquals("1234.5", formatNumber(1234.5f))
        assertEquals("722.5", formatNumber(722.5f))
        assertEquals("1079.5", formatNumber(1079.5f))
    }
}

class RulerStepTest {
    @Test
    fun `the step grows as the view zooms out`() {
        assertEquals(1, rulerStep(80f))
        assertEquals(100, rulerStep(1f))
        assertEquals(200, rulerStep(0.37f))
    }
}
