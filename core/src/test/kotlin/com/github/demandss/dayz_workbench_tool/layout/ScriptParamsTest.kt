package com.github.demandss.dayz_workbench_tool.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScriptParamsTest {
    private val source =
        """
        FrameWidgetClass Root {
         size 1 1
         {
          PanelWidgetClass Spacer {
           size 10 10
           {
            TextWidgetClass A {
             text "a"
            }
           }
           {
            ScriptParamsClass {
             AlignChilds 1
             Gap 2
            }
           }
          }
          ButtonWidgetClass After {
           size 5 5
          }
         }
        }
        """.trimIndent()

    @Test
    fun `widgets after a script params block are kept`() {
        val root = LayoutParser.parse(source)
        assertEquals(listOf("Spacer", "After"), root.children.map { it.name })
        assertEquals(listOf("A"), root.children[0].children.map { it.name })
    }

    @Test
    fun `script params are read and written back`() {
        val spacer = LayoutParser.parse(source).children[0]
        assertEquals<Map<String, String>?>(mapOf("AlignChilds" to "1", "Gap" to "2"), spacer.scriptParams)
        assertNull(LayoutParser.parse(source).children[1].scriptParams)

        val again = LayoutParser.parse(LayoutSerializer.serialize(LayoutParser.parse(source)))
        assertNotNull(again.children[0].scriptParams)
        assertEquals(spacer.scriptParams, again.children[0].scriptParams)
        assertEquals(listOf("Spacer", "After"), again.children.map { it.name })
    }

    @Test
    fun `a widget with script params and no children`() {
        val root =
            LayoutParser.parse(
                """
                PanelWidgetClass P {
                 size 1 1
                 {
                  ScriptParamsClass {
                   Gap 4
                  }
                 }
                }
                """.trimIndent(),
            )
        assertEquals(0, root.children.size)
        assertEquals<Map<String, String>?>(mapOf("Gap" to "4"), root.scriptParams)
    }
}
