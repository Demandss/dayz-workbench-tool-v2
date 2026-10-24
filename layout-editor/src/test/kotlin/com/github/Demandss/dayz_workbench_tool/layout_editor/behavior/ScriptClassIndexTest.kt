package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptClassIndexTest {
    private fun decls(text: String) = ScriptClassIndex.parse(text, "Mod", "x.c")

    private fun names(text: String) = ScriptClassIndex.build(decls(text)).filter { it.source == "Mod" }.map { it.name }

    @Test
    fun `declarations are read with or without a parent and modded classes are skipped`() {
        val found =
            decls(
                """
                class A : ScriptedWidgetEventHandler
                {
                }
                class B extends A { }
                modded class C : A { }
                  class D:B
                class Loose
                {
                }
                """.trimIndent(),
            )
        assertEquals(listOf("A" to "ScriptedWidgetEventHandler", "B" to "A", "D" to "B", "Loose" to ""), found.map { it.name to it.parent })
    }

    @Test
    fun `a class is a script class of a widget when it has OnWidgetScriptInit, whatever it extends`() {
        val list =
            names(
                """
                class Plain
                {
                    void OnWidgetScriptInit(Widget w) { }
                }
                class Handler : ScriptedWidgetEventHandler
                {
                    void OnWidgetScriptInit( Widget w ) { }
                }
                class Menu : UIScriptedMenu
                {
                    void Init() { }
                }
                class Helper : ScriptedWidgetEventHandler
                {
                    void Update() { }
                }
                """.trimIndent(),
            )
        assertEquals(listOf("Handler", "Plain"), list)
    }

    @Test
    fun `a class gets the init of a class above it, and a modded class does not give its init to the one before it`() {
        val list =
            names(
                """
                class Base { void OnWidgetScriptInit(Widget w) { } }
                class Child : Base { }
                class Grand : Child { }
                class Other { }
                modded class Other { void OnWidgetScriptInit(Widget w) { } }
                """.trimIndent(),
            )
        assertEquals(listOf("Base", "Child", "Grand"), list)
    }

    @Test
    fun `the catalogue of the editor comes first and is not repeated`() {
        val all = decls("class HoverScale { void OnWidgetScriptInit(Widget w) { } }\nclass Mine { void OnWidgetScriptInit(Widget w) { } }")
        val list = ScriptClassIndex.build(all)
        assertEquals(EDITOR, list.first().source)
        assertEquals(1, list.count { it.name == "HoverScale" })
        assertTrue(list.any { it.name == "Mine" })
    }

    @Test
    fun `a cycle of parents does not hang`() {
        assertTrue(names("class A : B { }\nclass B : A { }").isEmpty())
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
    fun `the scripts of the game give the helper classes of the vanilla layouts and none of the menus`() {
        val game =
            listOf("F:/steam/steamapps/common/DayZ", "C:/Program Files (x86)/Steam/steamapps/common/DayZ").firstOrNull {
                File(it, "dta/scripts.pbo").isFile
            }
        if (game == null) return
        val list = ScriptClassIndex.build(ScriptClassIndex.readGame(game))
        val names = list.map { it.name }.toSet()
        // The classes the vanilla layouts name in `scriptclass`, with and without ScriptedWidgetEventHandler above them
        val used =
            listOf(
                "SizeToChild",
                "SizeToParent",
                "TabberUI",
                "Tabber",
                "AutoHeightSpacer",
                "NoticeSpacer",
                "HorizontalSpacer",
                "VerticalSpacer",
                "HoverEffect",
                "Bouncer",
                "Rotator",
                "RadialProgressBar",
                "EmbededButtonScript",
            )
        for (expected in used) assertTrue(expected in names, expected)
        for (menu in listOf("UIScriptedMenu", "MainMenu", "InGameMenu", "MissionGameplay", "ScriptedWidgetEventHandler")) {
            assertFalse(menu in names, menu)
        }
        val undescribed = list.filter { it.source != EDITOR && !ScriptClassIndex.describes(it.name) }.map { it.name }
        assertTrue(undescribed.isEmpty(), "classes of the game without a description: $undescribed")
        val ofGame = list.count { it.source == "Игра" }
        assertTrue(ofGame < 60, "too many classes: $ofGame")
        val info = list.first { it.name == "SizeToChild" }
        assertTrue(ScriptClassIndex.sourceOf(info, game)!!.contains("ResizeParentToChild"))
    }
}
