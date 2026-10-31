package com.github.demandss.dayz_workbench_tool.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutFormatsTest {
    @Test
    fun `legacy names migrate and new identifiers survive editing`() {
        val source =
            """
            FrameWidgetClass Root {
             size 1 1
             {
              TextWidgetClass Caption {
               text "hello"
              }
             }
            }
            """.trimIndent()
        val root = LayoutParser.parse(source)
        val saved = LayoutSerializer.serialize(root)
        assertTrue(saved.contains("Name \"Root\""))
        assertFalse(saved.contains("FrameWidgetClass Root"))
        val again = LayoutParser.parse(saved)
        assertEquals(root, again)
        again.children.single().name = "Renamed"
        val edited = LayoutParser.parse(LayoutSerializer.serialize(again))
        assertEquals("Renamed", edited.children.single().name)
        assertEquals(again.children.single().resourceId, edited.children.single().resourceId)
    }

    @Test
    fun `new Name property and quoted identifier are separate`() {
        val root =
            LayoutParser.parse(
                """
                FrameWidgetClass {
                 Name "Hud"
                 {
                  TextWidgetClass "{00000000E4E67D3A}" {
                   Name "Action"
                   text "hello"
                  }
                 }
                }
                """.trimIndent(),
            )
        assertEquals("Hud", root.name)
        assertEquals("Action", root.children.single().name)
        assertEquals("{00000000E4E67D3A}", root.children.single().resourceId)
        assertFalse(root.properties.containsKey("Name"))
        assertEquals(root, LayoutParser.parse(LayoutSerializer.serialize(root)))
    }
}
