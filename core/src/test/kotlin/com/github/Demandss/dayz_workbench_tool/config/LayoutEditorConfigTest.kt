package com.github.demandss.dayz_workbench_tool.config

import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutEditorConfigTest {
    @Test
    fun theSettingsAreTakenButTheWindowAndTheSessionsStay() {
        val onDisk =
            LayoutEditorConfig(
                windowWidth = 1500,
                windowHeight = 900,
                windowMaximized = true,
                sessions = mapOf("k:/mods" to EditorSession(layouts = listOf("a.layout"))),
            )
        val edited =
            LayoutEditorConfig(
                mcpEnabled = false,
                mcpPort = 40000,
                gridSize = 16,
                undoLimit = 20,
                // The copy kept by the settings window has an old window size and no sessions
                windowWidth = 800,
                windowHeight = 600,
            )
        val merged = onDisk.withSettingsFrom(edited)
        assertEquals(false, merged.mcpEnabled)
        assertEquals(40000, merged.mcpPort)
        assertEquals(16, merged.gridSize)
        assertEquals(20, merged.undoLimit)
        assertEquals(1500, merged.windowWidth)
        assertEquals(900, merged.windowHeight)
        assertEquals(true, merged.windowMaximized)
        assertEquals(listOf("a.layout"), merged.sessions.getValue("k:/mods").layouts)
    }

    @Test
    fun onlyTheFieldsTheWindowChangedAreTaken() {
        val opened = LayoutEditorConfig(gridSize = 10, showGrid = false, mcpPort = 39871)
        // While the settings window was open, the editor toggled the grid on (a hotkey) and wrote it to the disk
        val onDisk = opened.copy(showGrid = true)
        // The person changed the port in the window and left the grid alone
        val edited = opened.copy(mcpPort = 40001)
        val merged = onDisk.withChanges(opened, edited)
        assertEquals(40001, merged.mcpPort)
        assertEquals(true, merged.showGrid)
        assertEquals(10, merged.gridSize)
    }
}
