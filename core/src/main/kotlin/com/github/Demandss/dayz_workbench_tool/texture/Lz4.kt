package com.github.demandss.dayz_workbench_tool.texture

/**
 * Minimal LZ4 block decoder. Decodes into one contiguous output array, so a match may
 * point back into data produced by earlier blocks: this is what EDDS "chained" blocks need.
 */
internal object Lz4 {
    /**
     * Decodes [length] bytes of one LZ4 block from [src] at [srcPos] into [dst] at [dstPos].
     * Returns the position in [dst] after the last written byte.
     */
    fun decodeBlock(
        src: ByteArray,
        srcPos: Int,
        length: Int,
        dst: ByteArray,
        dstPos: Int,
    ): Int {
        var s = srcPos
        var d = dstPos
        val srcEnd = srcPos + length
        while (s < srcEnd) {
            val token = src[s++].toInt() and 0xFF

            var literals = token ushr 4
            if (literals == 15) {
                var b: Int
                do {
                    b = src[s++].toInt() and 0xFF
                    literals += b
                } while (b == 255)
            }
            if (d + literals > dst.size || s + literals > srcEnd) throw IllegalArgumentException("LZ4 literal run out of range")
            System.arraycopy(src, s, dst, d, literals)
            s += literals
            d += literals
            if (s >= srcEnd) break

            val offset = (src[s].toInt() and 0xFF) or ((src[s + 1].toInt() and 0xFF) shl 8)
            s += 2
            if (offset == 0 || offset > d) throw IllegalArgumentException("LZ4 bad match offset")

            var matchLen = token and 0x0F
            if (matchLen == 15) {
                var b: Int
                do {
                    b = src[s++].toInt() and 0xFF
                    matchLen += b
                } while (b == 255)
            }
            matchLen += 4
            if (d + matchLen > dst.size) throw IllegalArgumentException("LZ4 match out of range")

            // Overlapping copy has to go byte by byte
            var from = d - offset
            repeat(matchLen) { dst[d++] = dst[from++] }
        }
        return d
    }
}
