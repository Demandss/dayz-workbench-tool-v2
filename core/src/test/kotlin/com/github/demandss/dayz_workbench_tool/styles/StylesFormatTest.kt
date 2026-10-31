package com.github.demandss.dayz_workbench_tool.styles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StylesFormatTest {
    private val sample =
        """
        <WidgetStyles>
            <Widget Name="TextWidget">
                <Style Name="Normal" Font="gui/fonts/MetronBook" ImageSet="" Color="4294967295" />
                <Style Name="Bold" Font="gui/fonts/MetronBook-Bold" ImageSet="" Color="4294967295" />
            </Widget>
            <Widget Name="ButtonWidget">
                <Style Name="Default" Font="" ImageSet="dayz_gui" Color="4294967295">
                    <State Name="Normal">
                        <Item Name="Center" Image="ButtonCenter" />
                        <Item Name="Left" Image="" />
                    </State>
                    <State Name="Pushed">
                        <Item Name="Center" Image="ButtonPushed" />
                    </State>
                </Style>
            </Widget>
        </WidgetStyles>
        """.trimIndent()

    @Test
    fun `widget types and their styles are read`() {
        val sheet = assertNotNull(StylesFormat.parse(sample))
        assertEquals(listOf("Normal", "Bold"), sheet.stylesFor("TextWidgetClass").map { it.name })
        assertEquals(listOf("Default"), sheet.stylesFor("ButtonWidget").map { it.name })
        assertEquals(emptyList(), sheet.stylesFor("PanelWidgetClass"))
    }

    @Test
    fun `states and slots are read`() {
        val style = assertNotNull(StylesFormat.parse(sample)).stylesFor("ButtonWidget").single()
        assertEquals("dayz_gui", style.imageSet)
        assertEquals(listOf("Normal", "Pushed"), style.states.map { it.name })
        assertEquals("ButtonCenter", style.states[0].items[0].image)
        assertEquals("", style.states[0].items[1].image)
    }

    @Test
    fun `writing and reading again gives the same sheet`() {
        val sheet = assertNotNull(StylesFormat.parse(sample))
        assertEquals(sheet, StylesFormat.parse(StylesFormat.serialize(sheet)))
    }

    @Test
    fun `text that is not a styles file is refused`() {
        assertNull(StylesFormat.parse("ImageSetClass {\n}\n"))
        assertNull(StylesFormat.parse("<Other />"))
        assertNull(StylesFormat.parse(""))
    }
}
