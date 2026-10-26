package com.github.demandss.dayz_workbench_tool.ui

import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.ModType
import com.github.demandss.dayz_workbench_tool.model.Modification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModFiltersTest {
    private val clientOn = Modification("mods/ClientOn", ModSide.CLIENT, ModType.LOCAL, enabled = true)
    private val clientOff = Modification("mods/ClientOff", ModSide.CLIENT, ModType.LOCAL, enabled = false)
    private val serverWorkshop = Modification("steam/@ServerWs", ModSide.SERVER, ModType.WORKSHOP, enabled = true)
    private val all = listOf(clientOn, clientOff, serverWorkshop)

    @Test
    fun `without a filter every mod is shown`() {
        assertFalse(ModFilters().isActive)
        assertEquals(all, filterMods(all, ModFilters(), "", emptySet()))
    }

    @Test
    fun `each filter narrows the list`() {
        assertEquals(listOf(clientOn, clientOff), filterMods(all, ModFilters(side = ModSide.CLIENT), "", emptySet()))
        assertEquals(listOf(clientOn, serverWorkshop), filterMods(all, ModFilters(enabled = true), "", emptySet()))
        assertEquals(listOf(clientOff), filterMods(all, ModFilters(enabled = false), "", emptySet()))
        assertEquals(listOf(serverWorkshop), filterMods(all, ModFilters(type = ModType.WORKSHOP), "", emptySet()))
        assertEquals(listOf(clientOff), filterMods(all, ModFilters(onlyMissing = true), "", setOf(clientOff.path)))
    }

    @Test
    fun `filters work together with each other and with the search`() {
        val filters = ModFilters(side = ModSide.CLIENT, enabled = true)
        assertEquals(listOf(clientOn), filterMods(all, filters, "", emptySet()))
        assertEquals(emptyList(), filterMods(all, filters, "server", emptySet()))
        assertEquals(listOf(clientOff), filterMods(all, ModFilters(side = ModSide.CLIENT), " clientoff ", emptySet()))
    }

    @Test
    fun `a chip that is lit again turns its filter off`() {
        fun ModFilters.everyChip() =
            toggleSide(ModSide.SERVER)
                .toggleEnabled(false)
                .toggleType(ModType.LOCAL)
                .toggleMissing()

        val set = ModFilters().everyChip()
        assertTrue(set.isActive)
        assertEquals(ModFilters(), set.everyChip())
        // The other side replaces the one that was set
        assertEquals(ModSide.CLIENT, ModFilters(side = ModSide.SERVER).toggleSide(ModSide.CLIENT).side)
    }
}
