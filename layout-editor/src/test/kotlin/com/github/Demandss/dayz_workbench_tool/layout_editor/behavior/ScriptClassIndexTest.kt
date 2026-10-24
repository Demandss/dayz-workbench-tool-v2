package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptClassIndexTest {
    private fun decls(text: String) = ScriptClassIndex.parse(text, "Mod", "x.c")

    @Test
    fun `declarations are read with both ways of naming the parent and modded classes are skipped`() {
        val found =
            decls(
                """
                class A : ScriptedWidgetEventHandler
                {
                }
                class B extends A { }
                modded class C : A { }
                  class D:B
                """.trimIndent(),
            )
        assertEquals(listOf("A" to "ScriptedWidgetEventHandler", "B" to "A", "D" to "B"), found.map { it.name to it.parent })
    }

    @Test
    fun `only classes that reach ScriptedWidgetEventHandler are listed, however far up`() {
        val all =
            decls(
                "class A : ScriptedWidgetEventHandler {}\nclass B : A {}\nclass C : B {}\nclass Other : Managed {}\nclass Loose : Missing {}",
            )
        val names = ScriptClassIndex.build(all).filter { it.source == "Mod" }.map { it.name }
        assertEquals(listOf("A", "B", "C"), names)
    }

    @Test
    fun `the catalogue of the editor comes first and is not repeated`() {
        val all = decls("class HoverScale : ScriptedWidgetEventHandler {}\nclass Mine : ScriptedWidgetEventHandler {}")
        val list = ScriptClassIndex.build(all)
        assertEquals(EDITOR, list.first().source)
        assertEquals(1, list.count { it.name == "HoverScale" })
        assertTrue(list.any { it.name == "Mine" })
    }

    @Test
    fun `a cycle of parents does not hang`() {
        assertTrue(ScriptClassIndex.build(decls("class A : B {}\nclass B : A {}")).none { it.source == "Mod" })
    }

    @Test
    fun `the text of one class is cut out with its braces`() {
        val text = "// head\nclass A : X\n{\n    void F() { if (1) { } }\n}\n\nclass B : A\n{\n}\n"
        val a = assertNotNull(ScriptClassIndex.extractClass(text, "A"))
        assertTrue(a.startsWith("class A : X") && a.trimEnd().endsWith("}") && "class B" !in a)
        assertNull(ScriptClassIndex.extractClass(text, "Missing"))
    }

    @Test
    fun `the tooltip of a class says what it does, what it takes and where it is from`() {
        val own = ScriptClassIndex.editorClasses.first { it.name == "SelectGroup" }
        val ownTip = ScriptClassIndex.tooltip(own)
        assertTrue("Параметры:" in ownTip && "Items = Easy|Normal|Hard" in ownTip && "пустой фрейм" in ownTip)
        val vanilla =
            ScriptClassInfo("SizeToChild", "Игра", "ScriptedWidgetEventHandler", "pbo:scripts/3_game/gui/containers/sizetochild.c")
        val vanillaTip = ScriptClassIndex.tooltip(vanilla)
        assertTrue("Live Preview" in vanillaTip && "Источник: Игра · sizetochild.c" in vanillaTip)
        val modTip = ScriptClassIndex.tooltip(ScriptClassInfo("MF_Thing", "MF_CLS_Client", "SpacerBase", "K:/m/Thing.c"))
        assertTrue("Наследует SpacerBase" in modTip && "MF_CLS_Client" in modTip)
    }

    @Test
    fun `the scripts of the game give the classes the vanilla layouts use`() {
        val game =
            listOf("F:/steam/steamapps/common/DayZ", "C:/Program Files (x86)/Steam/steamapps/common/DayZ").firstOrNull {
                File(it, "dta/scripts.pbo").isFile
            }
        if (game == null) return
        val names = ScriptClassIndex.build(ScriptClassIndex.readGame(game)).map { it.name }.toSet()
        for (expected in listOf("SizeToChild", "TabberUI", "AutoHeightSpacer", "HoverEffect", "HorizontalSpacer")) {
            assertTrue(expected in names, expected)
        }
        val info = ScriptClassIndex.build(ScriptClassIndex.readGame(game)).first { it.name == "SizeToChild" }
        assertTrue(ScriptClassIndex.sourceOf(info, game)!!.contains("ResizeParentToChild"))
    }
}
