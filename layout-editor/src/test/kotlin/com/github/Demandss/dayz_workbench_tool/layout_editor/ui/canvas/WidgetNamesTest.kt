package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetNamesTest {
    @Test
    fun `cyrillic is written in latin letters`() {
        assertEquals("Zh", sanitizeWidgetName("\u0416"))
        assertEquals("zh_zh", sanitizeWidgetName("\u0436 \u0436"))
    }

    @Test
    fun `other characters become underscores`() {
        assertEquals("my_widget_1", sanitizeWidgetName("my widget-1"))
        assertEquals("widget", sanitizeWidgetName("   "))
        assertEquals("btn_ok", sanitizeWidgetName("btn_ok"))
    }

    @Test
    fun `invalid characters are found`() {
        assertTrue(hasInvalidNameChars("\u0436"))
        assertTrue(hasInvalidNameChars("a b"))
        assertFalse(hasInvalidNameChars("Group_1"))
    }
}
