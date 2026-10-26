package com.github.demandss.dayz_workbench_tool.styles

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StyleEditTest {
    private val sheet =
        StyleSheet(
            listOf(
                WidgetStyles(
                    "PanelWidget",
                    listOf(
                        StyleDef("blank", "", "dayz_gui", "4294967295", listOf(StyleState("Normal", listOf(StyleItem("Center", ""))))),
                        StyleDef("box", "", "dayz_gui", "4294967295", listOf(StyleState("Normal", emptyList()))),
                    ),
                ),
            ),
        )

    @Test
    fun `an added style gets a unique name`() {
        val (next, index) = sheet.withAddedStyle(0, "box")!!
        assertEquals("box_2", next.widgets[0].styles[index].name)
        assertEquals(3, next.widgets[0].styles.size)
        assertNull(sheet.withAddedStyle(5, "x"))
    }

    @Test
    fun `a duplicate follows the original`() {
        val (next, index) = sheet.withDuplicatedStyle(0, 0)!!
        assertEquals(1, index)
        assertEquals(listOf("blank", "blank_copy", "box"), next.widgets[0].styles.map { it.name })
    }

    @Test
    fun `items are set added and removed by slot`() {
        val edited = sheet.withStyleAt(0, 0) { it.withItem(0, "Center", "pix").withItem(0, "Top", "top") }
        assertEquals(
            listOf("Center" to "pix", "Top" to "top"),
            edited.widgets[0]
                .styles[0]
                .states[0]
                .items
                .map { it.name to it.image },
        )
        val removed = edited.withStyleAt(0, 0) { it.withoutItem(0, "Center") }
        assertEquals(
            listOf("Top"),
            removed.widgets[0]
                .styles[0]
                .states[0]
                .items
                .map { it.name },
        )
    }

    @Test
    fun `a state is added once`() {
        val style =
            sheet.widgets[0]
                .styles[0]
                .withAddedState("Disabled")
                .withAddedState("Disabled")
        assertEquals(listOf("Normal", "Disabled"), style.states.map { it.name })
        assertEquals(STYLE_SLOTS.size, style.states[1].items.size)
    }

    @Test
    fun `colour converts to hex and back`() {
        assertEquals("FFFFFFFF", styleColorHex("4294967295"))
        assertEquals("00000000", styleColorHex("0"))
        assertEquals("4294967295", styleColorFromHex("#ffffffff"))
        assertNull(styleColorFromHex("zz"))
    }
}
