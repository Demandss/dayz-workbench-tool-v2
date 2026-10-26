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
}
