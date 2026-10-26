package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class NineSliceTest {
    private val native = NINE_SLICE_SLOTS.associateWith { Size(4f, 4f) }

    @Test
    fun `corners keep their size and the centre takes the rest`() {
        val r = sliceRects(Rect(0f, 0f, 100f, 50f), native)
        assertEquals(Rect(0f, 0f, 4f, 4f), r["LeftTop"])
        assertEquals(Rect(96f, 46f, 100f, 50f), r["RightBottom"])
        assertEquals(Rect(4f, 4f, 96f, 46f), r["Center"])
        assertEquals(Rect(4f, 0f, 96f, 4f), r["Top"])
    }

    @Test
    fun `small target shrinks the sides without overlap`() {
        val r = sliceRects(Rect(0f, 0f, 4f, 4f), native)
        assertEquals(Rect(0f, 0f, 2f, 2f), r["LeftTop"])
        assertEquals(Rect(2f, 2f, 4f, 4f), r["RightBottom"])
        assertEquals(0f, r["Center"]!!.width)
    }

    @Test
    fun `missing slots are skipped`() {
        val r = sliceRects(Rect(0f, 0f, 10f, 10f), mapOf("Center" to Size(1f, 1f)))
        assertEquals(setOf("Center"), r.keys)
        assertEquals(Rect(0f, 0f, 10f, 10f), r["Center"])
    }

    @Test
    fun `a style with no corners has side edges and centre over the whole height`() {
        val sizes =
            mapOf(
                "Left" to Size(4f, 512f),
                "Right" to Size(4f, 512f),
                "Top" to Size(512f, 4f),
                "Bottom" to Size(512f, 4f),
                "Center" to Size(1f, 1f),
            )
        val r = sliceRects(Rect(0f, 0f, 100f, 50f), sizes)
        assertEquals(Rect(0f, 0f, 4f, 50f), r["Left"])
        assertEquals(Rect(96f, 0f, 100f, 50f), r["Right"])
        assertEquals(Rect(4f, 0f, 96f, 50f), r["Center"])
        assertEquals(Rect(4f, 0f, 96f, 4f), r["Top"])
    }

    @Test
    fun `a list box with only a scroll bar column keeps its Center over the whole widget`() {
        val sizes = mapOf("RightTop" to Size(24f, 4f), "Right" to Size(24f, 5f), "RightBottom" to Size(24f, 4f), "Center" to Size(1f, 5f))
        val r = sliceRects(Rect(0f, 0f, 140f, 64f), sizes)
        assertEquals(Rect(0f, 0f, 116f, 64f), r["Center"])
        assertEquals(Rect(116f, 4f, 140f, 60f), r["Right"])
    }
}
