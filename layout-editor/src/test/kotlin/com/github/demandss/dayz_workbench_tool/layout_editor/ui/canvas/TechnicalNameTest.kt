package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import kotlin.test.Test
import kotlin.test.assertEquals

class TechnicalNameTest {
    @Test
    fun `the first widget of a type gets the name of the type`() {
        assertEquals("TextWidget", technicalName(WidgetType.TEXT, emptySet()))
        assertEquals("ImageWidget", technicalName(WidgetType.IMAGE, setOf("TextWidget")))
    }

    @Test
    fun `the next ones get a number, counting from 0 and skipping the numbers that are taken`() {
        assertEquals("TextWidget0", technicalName(WidgetType.TEXT, setOf("TextWidget")))
        assertEquals("TextWidget1", technicalName(WidgetType.TEXT, setOf("TextWidget", "TextWidget0")))
        assertEquals("TextWidget0", technicalName(WidgetType.TEXT, setOf("TextWidget", "TextWidget1")))
    }
}
