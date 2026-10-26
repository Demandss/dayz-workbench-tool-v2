package com.github.demandss.dayz_workbench_tool.texture

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EddsCodecTest {
    @Test
    fun `encode then decode returns the same pixels`() {
        val w = 37
        val h = 19
        val px = IntArray(w * h) { (0xFF shl 24) or ((it * 7) and 0xFF shl 16) or ((it * 3) and 0xFF shl 8) or (it and 0xFF) }
        px[5] = 0x80102030.toInt()

        val decoded = EddsCodec.decode(EddsCodec.encode(w, h, px))

        assertEquals(w, decoded.width)
        assertEquals(h, decoded.height)
        assertEquals(6, decoded.mipCount)
        assertContentEquals(px, decoded.pixels)
    }
}
