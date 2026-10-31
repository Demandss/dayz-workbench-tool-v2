package com.github.demandss.dayz_workbench_tool.texture

/**
 * BC7 (BPTC) block decoder. The partition and anchor tables and the decoding steps follow the public BPTC description as
 * implemented in bgfx/bimg (BSD 2-clause, Branimir Karadzic) and the Texture2DDecoder port of it (MIT, K0lb3).
 */
internal object Bc7 {
    private val P2 =
        intArrayOf(
            0xcccc,
            0x8888,
            0xeeee,
            0xecc8,
            0xc880,
            0xfeec,
            0xfec8,
            0xec80,
            0xc800,
            0xffec,
            0xfe80,
            0xe800,
            0xffe8,
            0xff00,
            0xfff0,
            0xf000,
            0xf710,
            0x008e,
            0x7100,
            0x08ce,
            0x008c,
            0x7310,
            0x3100,
            0x8cce,
            0x088c,
            0x3110,
            0x6666,
            0x366c,
            0x17e8,
            0x0ff0,
            0x718e,
            0x399c,
            0xaaaa,
            0xf0f0,
            0x5a5a,
            0x33cc,
            0x3c3c,
            0x55aa,
            0x9696,
            0xa55a,
            0x73ce,
            0x13c8,
            0x324c,
            0x3bdc,
            0x6996,
            0xc33c,
            0x9966,
            0x0660,
            0x0272,
            0x04e4,
            0x4e40,
            0x2720,
            0xc936,
            0x936c,
            0x39c6,
            0x639c,
            0x9336,
            0x9cc6,
            0x817e,
            0xe718,
            0xccf0,
            0x0fcc,
            0x7744,
            0xee22,
        )
    private val A2 =
        intArrayOf(
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            15,
            2,
            8,
            2,
            2,
            8,
            8,
            15,
            2,
            8,
            2,
            2,
            8,
            8,
            2,
            2,
            15,
            15,
            6,
            8,
            2,
            8,
            15,
            15,
            2,
            8,
            2,
            2,
            2,
            15,
            15,
            6,
            6,
            2,
            6,
            8,
            15,
            15,
            2,
            2,
            15,
            15,
            15,
            15,
            15,
            2,
            2,
            15,
        )
    private val P3 =
        longArrayOf(
            0xaa685050L,
            0x6a5a5040L,
            0x5a5a4200L,
            0x5450a0a8L,
            0xa5a50000L,
            0xa0a05050L,
            0x5555a0a0L,
            0x5a5a5050L,
            0xaa550000L,
            0xaa555500L,
            0xaaaa5500L,
            0x90909090L,
            0x94949494L,
            0xa4a4a4a4L,
            0xa9a59450L,
            0x2a0a4250L,
            0xa5945040L,
            0x0a425054L,
            0xa5a5a500L,
            0x55a0a0a0L,
            0xa8a85454L,
            0x6a6a4040L,
            0xa4a45000L,
            0x1a1a0500L,
            0x0050a4a4L,
            0xaaa59090L,
            0x14696914L,
            0x69691400L,
            0xa08585a0L,
            0xaa821414L,
            0x50a4a450L,
            0x6a5a0200L,
            0xa9a58000L,
            0x5090a0a8L,
            0xa8a09050L,
            0x24242424L,
            0x00aa5500L,
            0x24924924L,
            0x24499224L,
            0x50a50a50L,
            0x500aa550L,
            0xaaaa4444L,
            0x66660000L,
            0xa5a0a5a0L,
            0x50a050a0L,
            0x69286928L,
            0x44aaaa44L,
            0x66666600L,
            0xaa444444L,
            0x54a854a8L,
            0x95809580L,
            0x96969600L,
            0xa85454a8L,
            0x80959580L,
            0xaa141414L,
            0x96960000L,
            0xaaaa1414L,
            0xa05050a0L,
            0xa0a5a5a0L,
            0x96000000L,
            0x40804080L,
            0xa9a8a9a8L,
            0xaaaaaa44L,
            0x2a4a5254L,
        )
    private val A3 =
        arrayOf(
            intArrayOf(
                3,
                3,
                15,
                15,
                8,
                3,
                15,
                15,
                8,
                8,
                6,
                6,
                6,
                5,
                3,
                3,
                3,
                3,
                8,
                15,
                3,
                3,
                6,
                10,
                5,
                8,
                8,
                6,
                8,
                5,
                15,
                15,
                8,
                15,
                3,
                5,
                6,
                10,
                8,
                15,
                15,
                3,
                15,
                5,
                15,
                15,
                15,
                15,
                3,
                15,
                5,
                5,
                5,
                8,
                5,
                10,
                5,
                10,
                8,
                13,
                15,
                12,
                3,
                3,
            ),
            intArrayOf(
                15,
                8,
                8,
                3,
                15,
                15,
                3,
                8,
                15,
                15,
                15,
                15,
                15,
                15,
                15,
                8,
                15,
                8,
                15,
                3,
                15,
                8,
                15,
                8,
                3,
                15,
                6,
                10,
                15,
                15,
                10,
                8,
                15,
                3,
                15,
                10,
                10,
                8,
                9,
                10,
                6,
                15,
                8,
                15,
                3,
                6,
                6,
                8,
                15,
                3,
                15,
                15,
                15,
                15,
                15,
                15,
                15,
                15,
                15,
                15,
                3,
                15,
                15,
                8,
            ),
        )
    private val FACTORS =
        arrayOf(
            intArrayOf(0, 21, 43, 64),
            intArrayOf(0, 9, 18, 27, 37, 46, 55, 64),
            intArrayOf(0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64),
        )

    // subsets, partition bits, rotation bits, index selection bits, colour bits, alpha bits, endpoint P-bits, shared P-bits, index bits 0 and 1
    private val MODES =
        arrayOf(
            intArrayOf(3, 4, 0, 0, 4, 0, 1, 0, 3, 0),
            intArrayOf(2, 6, 0, 0, 6, 0, 0, 1, 3, 0),
            intArrayOf(3, 6, 0, 0, 5, 0, 0, 0, 2, 0),
            intArrayOf(2, 6, 0, 0, 7, 0, 1, 0, 2, 0),
            intArrayOf(1, 0, 2, 1, 5, 6, 0, 0, 2, 3),
            intArrayOf(1, 0, 2, 0, 7, 8, 0, 0, 2, 2),
            intArrayOf(1, 0, 0, 0, 7, 7, 1, 0, 4, 0),
            intArrayOf(2, 6, 0, 0, 5, 5, 1, 0, 2, 0),
        )

    private class Bits(
        val data: ByteArray,
        val base: Int,
    ) {
        var pos = 0

        private fun at(
            bitPos: Int,
            n: Int,
        ): Int {
            var v = 0L
            val byte = bitPos / 8
            for (i in 0 until 4) if (byte + i < 16) v = v or ((data[base + byte + i].toLong() and 0xFF) shl (8 * i))
            return ((v ushr (bitPos and 7)) and ((1L shl n) - 1)).toInt()
        }

        fun read(n: Int): Int {
            val r = at(pos, n)
            pos += n
            return r
        }

        fun peek(
            offset: Int,
            n: Int,
        ): Int = at(pos + offset, n)
    }

    private fun expand(
        v: Int,
        bits: Int,
    ): Int {
        val s = (v shl (8 - bits)) and 0xFF
        return s or (s shr bits)
    }

    /** Decodes the 16 byte block at [pos] into 16 ARGB pixels. */
    fun decodeBlock(
        data: ByteArray,
        pos: Int,
        out: IntArray,
    ) {
        val bit = Bits(data, pos)
        var mode = 0
        while (mode < 8 && bit.read(1) == 0) mode++
        if (mode == 8) {
            out.fill(0)
            return
        }
        val mi = MODES[mode]
        val subsets = mi[0]
        val colorBitsRaw = mi[4]
        val alphaBitsRaw = mi[5]
        val modePBits = if (mi[6] != 0) mi[6] else mi[7]
        val partition = bit.read(mi[1])
        val rotation = bit.read(mi[2])
        val indexSelection = bit.read(mi[3])
        val epR = IntArray(6)
        val epG = IntArray(6)
        val epB = IntArray(6)
        val epA = IntArray(6) { 0xFF }
        for (ep in listOf(epR, epG, epB)) {
            for (i in 0 until subsets) {
                ep[i * 2] = bit.read(colorBitsRaw) shl modePBits
                ep[i * 2 + 1] = bit.read(colorBitsRaw) shl modePBits
            }
        }
        if (alphaBitsRaw != 0) {
            for (i in 0 until subsets) {
                epA[i * 2] = bit.read(alphaBitsRaw) shl modePBits
                epA[i * 2 + 1] = bit.read(alphaBitsRaw) shl modePBits
            }
        }
        if (modePBits != 0) {
            for (i in 0 until subsets) {
                val pa = bit.read(modePBits)
                val pb = if (mi[7] == 0) bit.read(modePBits) else pa
                for (ep in listOf(epR, epG, epB, epA)) {
                    ep[i * 2] = ep[i * 2] or pa
                    ep[i * 2 + 1] = ep[i * 2 + 1] or pb
                }
            }
        }
        val colorBits = colorBitsRaw + modePBits
        for (i in 0 until subsets * 2) {
            epR[i] = expand(epR[i], colorBits)
            epG[i] = expand(epG[i], colorBits)
            epB[i] = expand(epB[i], colorBits)
        }
        if (alphaBitsRaw != 0) {
            val alphaBits = alphaBitsRaw + modePBits
            for (i in 0 until subsets * 2) epA[i] = expand(epA[i], alphaBits)
        }
        val hasIndexBits1 = mi[9] != 0
        val factors0 = FACTORS[mi[8] - 2]
        val factors1 = if (hasIndexBits1) FACTORS[mi[9] - 2] else factors0
        val factors = arrayOf(factors0, factors1)
        val offset = intArrayOf(0, subsets * (16 * mi[8] - 1))
        for (idx in 0 until 16) {
            var subset = 0
            var anchorIdx = 0
            when (subsets) {
                2 -> {
                    subset = (P2[partition] shr idx) and 1
                    anchorIdx = if (subset != 0) A2[partition] else 0
                }

                3 -> {
                    subset = ((P3[partition] shr (2 * idx)) and 3).toInt()
                    anchorIdx = if (subset != 0) A3[subset - 1][partition] else 0
                }
            }
            val anchor = if (idx == anchorIdx) 1 else 0
            val num0 = mi[8] - anchor
            val num1 = if (hasIndexBits1) mi[9] - anchor else 0
            val index0 = bit.peek(offset[0], num0) and 0xFF
            val index1 = if (hasIndexBits1) bit.peek(offset[1], num1) and 0xFF else index0
            offset[0] += num0
            offset[1] += num1
            val indices = intArrayOf(index0, index1)
            val fc = factors[indexSelection][indices[indexSelection]]
            val fa = factors[1 - indexSelection][indices[1 - indexSelection]]
            val s = subset * 2
            var r = ((epR[s] * (64 - fc) + epR[s + 1] * fc + 32) and 0xFFFF) shr 6
            var g = ((epG[s] * (64 - fc) + epG[s + 1] * fc + 32) and 0xFFFF) shr 6
            var b = ((epB[s] * (64 - fc) + epB[s + 1] * fc + 32) and 0xFFFF) shr 6
            var a = ((epA[s] * (64 - fa) + epA[s + 1] * fa + 32) and 0xFFFF) shr 6
            when (rotation) {
                1 -> a = r.also { r = a }
                2 -> a = g.also { g = a }
                3 -> a = b.also { b = a }
            }
            out[idx] = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        }
    }
}
