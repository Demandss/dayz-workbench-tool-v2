package com.github.demandss.dayz_workbench_tool.texture

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class Lz4Test {
    // token 0x40: four literals, no match; the block ends there
    private val good = byteArrayOf(0x40, 1, 2, 3, 4)

    @Test
    fun decodesALiteralBlock() {
        val out = ByteArray(4)
        Lz4.decodeBlock(good, 0, good.size, out, 0)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), out)
    }

    @Test
    fun aBlockLongerThanItsDataIsCorruptNotAnIndexError() {
        assertFailsWith<IllegalArgumentException> { Lz4.decodeBlock(good, 0, 500, ByteArray(4), 0) }
    }

    @Test
    fun aCutOffLengthOrOffsetIsCorrupt() {
        // token 0xF0: the literal length goes on in the next byte, which is not there
        assertFailsWith<IllegalArgumentException> { Lz4.decodeBlock(byteArrayOf(0xF0.toByte()), 0, 1, ByteArray(300), 0) }
        // one literal, then a match offset of which only one byte is left
        assertFailsWith<IllegalArgumentException> { Lz4.decodeBlock(byteArrayOf(0x11, 5, 1), 0, 3, ByteArray(20), 0) }
        // a match length that never ends
        assertFailsWith<IllegalArgumentException> {
            Lz4.decodeBlock(byteArrayOf(0x1F, 5, 1, 0, 255.toByte()), 0, 5, ByteArray(2000), 0)
        }
    }

    @Test
    fun anEddsWithATruncatedLz4BlockIsAnEddsException() {
        val bytes = EddsCodec.encode(4, 4, IntArray(16) { 0xFF336699.toInt() }, false)
        // The same file with the data cut: the codec has to say so with its own exception
        assertFailsWith<EddsException> { EddsCodec.decode(bytes.copyOf(bytes.size - 20)) }
    }
}
