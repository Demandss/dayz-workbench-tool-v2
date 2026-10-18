package com.github.demandss.dayz_workbench_tool.texture

/** Pixel format of the DDS payload inside an EDDS file. */
internal enum class PixelFormat(
    val blockBytes: Int,
    val blockSize: Int,
) {
    BGRA32(4, 1),
    RGBA32(4, 1),
    L8(1, 1),
    BC1(8, 4),
    BC2(16, 4),
    BC3(16, 4),
    BC4(8, 4),
    BC5(16, 4),
    ;

    fun mipBytes(
        width: Int,
        height: Int,
    ): Int = ((width + blockSize - 1) / blockSize) * ((height + blockSize - 1) / blockSize) * blockBytes
}

/** Decoders from raw mip data to packed ARGB (0xAARRGGBB). */
internal object PixelDecoders {
    fun decode(
        format: PixelFormat,
        data: ByteArray,
        width: Int,
        height: Int,
    ): IntArray {
        val out = IntArray(width * height)
        when (format) {
            PixelFormat.BGRA32 -> {
                for (i in out.indices) {
                    val p = i * 4
                    out[i] = argb(data[p + 3].u(), data[p + 2].u(), data[p + 1].u(), data[p].u())
                }
            }

            PixelFormat.RGBA32 -> {
                for (i in out.indices) {
                    val p = i * 4
                    out[i] = argb(data[p + 3].u(), data[p].u(), data[p + 1].u(), data[p + 2].u())
                }
            }

            PixelFormat.L8 -> {
                for (i in out.indices) {
                    val l = data[i].u()
                    out[i] = argb(255, l, l, l)
                }
            }

            else -> {
                decodeBlocks(format, data, width, height, out)
            }
        }
        return out
    }

    private fun decodeBlocks(
        format: PixelFormat,
        data: ByteArray,
        width: Int,
        height: Int,
        out: IntArray,
    ) {
        val bw = (width + 3) / 4
        val bh = (height + 3) / 4
        val block = IntArray(16)
        var p = 0
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                when (format) {
                    PixelFormat.BC1 -> {
                        bc1(data, p, block, opaqueOnly = false)
                    }

                    PixelFormat.BC2 -> {
                        bc1(data, p + 8, block, opaqueOnly = true)
                        for (i in 0 until 16) {
                            val nibble = (data[p + i / 2].u() shr ((i and 1) * 4)) and 0xF
                            block[i] = (block[i] and 0x00FFFFFF) or ((nibble * 17) shl 24)
                        }
                    }

                    PixelFormat.BC3 -> {
                        bc1(data, p + 8, block, opaqueOnly = true)
                        val alpha = bc4Channel(data, p)
                        for (i in 0 until 16) block[i] = (block[i] and 0x00FFFFFF) or (alpha[i] shl 24)
                    }

                    PixelFormat.BC4 -> {
                        val r = bc4Channel(data, p)
                        for (i in 0 until 16) block[i] = argb(255, r[i], r[i], r[i])
                    }

                    PixelFormat.BC5 -> {
                        val r = bc4Channel(data, p)
                        val g = bc4Channel(data, p + 8)
                        for (i in 0 until 16) block[i] = argb(255, r[i], g[i], 0)
                    }

                    else -> {
                        error("not a block format: $format")
                    }
                }
                p += format.blockBytes
                for (i in 0 until 16) {
                    val x = bx * 4 + (i and 3)
                    val y = by * 4 + (i shr 2)
                    if (x < width && y < height) out[y * width + x] = block[i]
                }
            }
        }
    }

    private fun bc1(
        data: ByteArray,
        pos: Int,
        out: IntArray,
        opaqueOnly: Boolean,
    ) {
        val c0 = data[pos].u() or (data[pos + 1].u() shl 8)
        val c1 = data[pos + 2].u() or (data[pos + 3].u() shl 8)
        val r0 = expand5(c0 shr 11)
        val g0 = expand6((c0 shr 5) and 0x3F)
        val b0 = expand5(c0 and 0x1F)
        val r1 = expand5(c1 shr 11)
        val g1 = expand6((c1 shr 5) and 0x3F)
        val b1 = expand5(c1 and 0x1F)
        val palette = IntArray(4)
        palette[0] = argb(255, r0, g0, b0)
        palette[1] = argb(255, r1, g1, b1)
        if (c0 > c1 || opaqueOnly) {
            palette[2] = argb(255, (2 * r0 + r1) / 3, (2 * g0 + g1) / 3, (2 * b0 + b1) / 3)
            palette[3] = argb(255, (r0 + 2 * r1) / 3, (g0 + 2 * g1) / 3, (b0 + 2 * b1) / 3)
        } else {
            palette[2] = argb(255, (r0 + r1) / 2, (g0 + g1) / 2, (b0 + b1) / 2)
            palette[3] = 0
        }
        val bits = data[pos + 4].u() or (data[pos + 5].u() shl 8) or (data[pos + 6].u() shl 16) or (data[pos + 7].u() shl 24)
        for (i in 0 until 16) out[i] = palette[(bits ushr (i * 2)) and 3]
    }

    /** One BC4-style channel block (8 bytes) as 16 values 0..255. */
    private fun bc4Channel(
        data: ByteArray,
        pos: Int,
    ): IntArray {
        val a0 = data[pos].u()
        val a1 = data[pos + 1].u()
        val table = IntArray(8)
        table[0] = a0
        table[1] = a1
        if (a0 > a1) {
            for (i in 1..6) table[i + 1] = ((7 - i) * a0 + i * a1) / 7
        } else {
            for (i in 1..4) table[i + 1] = ((5 - i) * a0 + i * a1) / 5
            table[6] = 0
            table[7] = 255
        }
        var bits = 0L
        for (i in 0 until 6) bits = bits or (data[pos + 2 + i].u().toLong() shl (8 * i))
        return IntArray(16) { table[((bits ushr (3 * it)) and 7).toInt()] }
    }

    private fun expand5(v: Int) = (v shl 3) or (v shr 2)

    private fun expand6(v: Int) = (v shl 2) or (v shr 4)

    private fun Byte.u() = toInt() and 0xFF

    private fun argb(
        a: Int,
        r: Int,
        g: Int,
        b: Int,
    ) = (a shl 24) or (r shl 16) or (g shl 8) or b
}
