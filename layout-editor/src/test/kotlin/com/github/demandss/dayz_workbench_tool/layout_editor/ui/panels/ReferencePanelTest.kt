package com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferencePanelTest {
    private val canvas = Size(900f, 700f)
    private val panel = IntSize(250, 240)

    @Test
    fun `a panel can not go past its corner`() {
        assertEquals(Offset.Zero, clampPanelOffset(Offset(40f, -30f), canvas, panel, margin = 12f))
    }

    @Test
    fun `a panel can go left and down by the free room`() {
        // 900 - 250 - 24 = 626 to the left, 700 - 240 - 24 = 436 down
        assertEquals(Offset(-626f, 436f), clampPanelOffset(Offset(-5000f, 5000f), canvas, panel, margin = 12f))
    }

    @Test
    fun `an offset inside the frame is kept`() {
        assertEquals(Offset(-100f, 80f), clampPanelOffset(Offset(-100f, 80f), canvas, panel, margin = 12f))
    }

    @Test
    fun `a canvas smaller than the panel leaves no room to move`() {
        assertEquals(Offset.Zero, clampPanelOffset(Offset(-50f, 50f), Size(200f, 100f), panel, margin = 12f))
    }
}
