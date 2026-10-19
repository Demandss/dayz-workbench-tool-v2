package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetNamesTest {
    @Test
    fun `cyrillic is written in latin letters`() {
        assertEquals("Gruppa", sanitizeWidgetName("Группа"))
        assertEquals("knopka_ok", sanitizeWidgetName("кнопка ок"))
    }

    @Test
    fun `other characters become underscores`() {
        assertEquals("my_widget_1", sanitizeWidgetName("my widget-1"))
        assertEquals("widget", sanitizeWidgetName("   "))
        assertEquals("btn_ok", sanitizeWidgetName("btn_ok"))
    }

    @Test
    fun `invalid characters are found`() {
        assertTrue(hasInvalidNameChars("кнопка"))
        assertTrue(hasInvalidNameChars("a b"))
        assertFalse(hasInvalidNameChars("Group_1"))
    }
}
