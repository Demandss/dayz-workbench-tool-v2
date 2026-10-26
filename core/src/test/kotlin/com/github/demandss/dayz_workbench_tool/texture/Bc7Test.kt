package com.github.demandss.dayz_workbench_tool.texture

import kotlin.test.Test
import kotlin.test.assertEquals

class Bc7Test {
    @Test
    fun `mode 6 block with all bits zero is transparent black`() {
        val data = ByteArray(16)
        data[0] = 0x40
        val out = IntArray(16) { -1 }
        Bc7.decodeBlock(data, 0, out)
        assertEquals(List(16) { 0 }, out.toList())
    }

    @Test
    fun `reserved mode gives transparent pixels`() {
        val out = IntArray(16) { -1 }
        Bc7.decodeBlock(ByteArray(16), 0, out)
        assertEquals(List(16) { 0 }, out.toList())
    }

    @Test
    fun `mode 6 with equal endpoints is a flat colour`() {
        // mode 6: 7 colour bits per channel and alpha for both endpoints, one P-bit each, then 4 bit indices
        val bits = StringBuilder()
        bits.append("0000001") // mode 6 = six zeros and a one, read from the lowest bit

        fun field(
            v: Int,
            n: Int,
        ) = (0 until n).forEach { bits.append(((v shr it) and 1).toString()) }
        for (channel in 0 until 4) {
            field(0x7F, 7)
            field(0x7F, 7)
        }
        field(1, 1)
        field(1, 1)
        while (bits.length < 128) bits.append('0')
        val data = ByteArray(16)
        for (i in 0 until 128) if (bits[i] == '1') data[i / 8] = (data[i / 8].toInt() or (1 shl (i % 8))).toByte()
        val out = IntArray(16)
        Bc7.decodeBlock(data, 0, out)
        assertEquals(List(16) { 0xFFFFFFFF.toInt() }, out.toList())
    }
}
