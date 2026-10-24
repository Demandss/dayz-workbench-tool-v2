package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptParamsTest {
    @Test
    fun `reference fields are read with their type, several to a line and the comment next to them`() {
        val params =
            ScriptParams.parse(
                """
                class X : Y
                {
                    reference string Name;        // the name
                    reference float  A, B;
                    // the border in pixels
                    reference int Border;
                    protected float m_Hidden;
                    float Plain;
                }
                """.trimIndent(),
                "X",
            )
        assertEquals(listOf("Name", "A", "B", "Border"), params.map { it.name })
        assertEquals(listOf("string", "float", "float", "int"), params.map { it.type })
        assertEquals("the name", params[0].description)
        assertEquals("the border in pixels", params[3].description)
    }

    @Test
    fun `a text is written in quotes and a number or a flag is not, and an empty value removes the field`() {
        val text = ScriptParam("Name", "string")
        val number = ScriptParam("Gap", "int")
        assertEquals("\"Play\"", ScriptParams.formatted(text, null, "Play"))
        assertEquals("\"Play\"", ScriptParams.formatted(text, null, "\"Play\""))
        assertEquals("10", ScriptParams.formatted(number, null, "10"))
        assertNull(ScriptParams.formatted(number, "10", "  "))
        // A field the class does not know keeps the way it was written
        assertEquals("\"5\"", ScriptParams.formatted(null, "\"3\"", "5"))
        assertEquals("5", ScriptParams.formatted(null, "3", "5"))
    }

    @Test
    fun `a class of the editor has the parameters of the catalogue with their defaults`() {
        val params = ScriptParams.ofEditorClass(BehaviorCatalogue.find("HoverScale")!!)
        assertTrue(params.any { it.name == "Scale" && it.default == "1.05" })
        assertTrue(params.any { it.name == "Target" })
    }

    @Test
    fun `the parameters of the vanilla classes come from their source and from the classes they extend`() {
        val game =
            listOf("F:/steam/steamapps/common/DayZ", "C:/Program Files (x86)/Steam/steamapps/common/DayZ").firstOrNull {
                File(it, "dta/scripts.pbo").isFile
            }
        if (game == null) return
        val declarations = ScriptClassIndex.readGame(game).distinctBy { it.name }.associateBy { it.name }

        fun names(cls: String) = ScriptParams.collect(cls, declarations, game).map { it.name }.toSet()
        assertEquals(
            setOf("m_ChildName", "m_HorizontalOffset", "m_VerticalOffset", "m_ResizeHorizontal", "m_ResizeVertical"),
            names("SizeToChild"),
        )
        assertEquals(setOf("Border", "Gap"), names("HorizontalSpacer"))
        // NoticeSpacer declares no fields of its own: they come from AutoHeightSpacer
        assertEquals(setOf("AlignChilds", "MinHeight", "MaxHeight", "Gap", "Top"), names("NoticeSpacer"))
        val gap = ScriptParams.collect("RightGap", declarations, game).first { it.name == "gap" }
        assertEquals("На сколько пикселей уже родителя", gap.description)
        assertTrue(ScriptParams.collect("SizeToParent", declarations, game).all { it.isFlag })
    }
}
