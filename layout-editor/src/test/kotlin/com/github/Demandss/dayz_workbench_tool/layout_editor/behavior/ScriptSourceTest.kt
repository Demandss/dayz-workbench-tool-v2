package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScriptSourceTest {
    @Test
    fun `every behaviour of the catalogue has a script and the catalogue has them all`() {
        assertEquals(17, BehaviorCatalogue.entries.size)
        val known = Behaviors.all.map { it.name } + Spawners.all.map { it.name }
        assertEquals(known.toSet(), BehaviorCatalogue.entries.map { it.name }.toSet())
    }

    @Test
    fun `a script is a class of the given name with balanced braces and no placeholders left`() {
        for (entry in BehaviorCatalogue.entries) {
            val script = assertNotNull(ScriptSource.of(entry.name, "T_${entry.name}", "Mod/GUI/layout/x.layout"), entry.name)
            assertTrue(script.contains("class T_${entry.name} : ScriptedWidgetEventHandler"), entry.name)
            assertEquals(script.count { it == '{' }, script.count { it == '}' }, entry.name)
            assertEquals(script.count { it == '(' }, script.count { it == ')' }, entry.name)
            assertTrue("__" !in script && '$' !in script, entry.name)
            assertTrue("void OnWidgetScriptInit(Widget w)" in script, entry.name)
        }
    }

    @Test
    fun `the script reads every parameter the editor plays under its own name`() {
        for (entry in BehaviorCatalogue.entries) {
            val script = ScriptSource.of(entry.name, "T", "x")!!
            for (param in entry.params) {
                assertTrue("reference string ${param.name};" in script, "${entry.name}: ${param.name}")
            }
        }
    }

    @Test
    fun `the export has the script, the fragment of a spawner and the lines of the layout`() {
        val select = BehaviorCatalogue.find("SelectGroup")!!
        val text = behaviorExportText(select, "MF_CLS_SelectGroup", mapOf("Items" to "A|B"), "MF_CLS_Client")
        assertTrue("class MF_CLS_SelectGroup" in text)
        assertTrue("MF_CLS_Client/GUI/layout/Behaviors/MF_CLS_SelectGroup.layout" in text)
        assertTrue("scriptclass \"MF_CLS_SelectGroup\"" in text)
        assertTrue("Items \"A|B\"" in text)
        val fragment = text.substringAfter("=====\n// Uses only").substringAfter("\n\n").substringBefore("\n// =====")
        assertEquals(4, LayoutParser.parse(fragment).children.size)

        val hover = behaviorExportText(BehaviorCatalogue.find("HoverScale")!!, "HoverScale", emptyMap(), "Mod")
        assertTrue("Layout fragment" !in hover)
        assertTrue("Scale \"1.05\"" in hover)
    }
}
