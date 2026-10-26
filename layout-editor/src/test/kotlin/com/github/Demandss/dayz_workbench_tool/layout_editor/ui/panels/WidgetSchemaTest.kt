package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetSchemaTest {
    @Test
    fun `a property is listed once for a widget class`() {
        for (type in WidgetType.entries) {
            val keys = schemaFor(type).flatMap { section -> section.specs.map { it.key } }
            assertEquals(keys.distinct(), keys, "${type.className} lists a property twice")
        }
    }

    @Test
    fun `a button has the text, the focus and the colour properties of the Workbench`() {
        val keys = schemaKeys(WidgetType.BUTTON)
        for (key in listOf(
            "text",
            "font",
            "switch",
            "text_proportion",
            "text_halign",
            "text color",
            "disabled text color",
            "no focus",
            "next up",
        )) {
            assertTrue(key in keys, "ButtonWidget lacks $key")
        }
    }

    @Test
    fun `an enum property offers values and a check property is zero or one`() {
        for (type in WidgetType.entries) {
            for (spec in schemaFor(type).flatMap { it.specs }) {
                // The hint is a string resource now: that every property has one is the type, that it is not empty is StringResourcesTest
                if (spec.kind == PropKind.ENUM) assertTrue(spec.options.isNotEmpty(), "${spec.key} has no values")
            }
        }
    }
}
