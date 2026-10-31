package com.github.demandss.dayz_workbench_tool.layout_editor.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRoundTripTest {
    @Test
    fun appliedSettingsComeBackAsTheSameConfig() {
        val state = LayoutEditorState("", CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val edited =
            state.settings().copy(
                gridSize = 24,
                showGrid = true,
                snapToGrid = true,
                showWidgetNames = false,
                workbenchColors = true,
                zoomOnOpen = "fit",
                rememberWindow = false,
                undoLimit = 33,
                defaultResolution = Resolution.R1280x720.label,
            )
        // save = false: the test must not write the settings of the person who runs it
        state.applySettings(edited, save = false)
        assertEquals(edited, state.settings())
        assertEquals(24, state.gridSize)
        assertEquals(33, state.historyDepth)
    }
}
