package com.github.demandss.dayz_workbench_tool.texture

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/** Decoded top mip of an EDDS texture. [pixels] are packed ARGB, row by row. */
class EddsImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val mipCount: Int,
    val format: String,
) {
    fun toBufferedImage(): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
            it.setRGB(0, 0, width, height, pixels, 0, width)
        }
}

class EddsException(
    message: String,
) : Exception(message)

/**
 * Reader and writer for Enfusion `.edds`.
 *
 * Layout: a normal 128-byte DDS header (plus 20 bytes of DX10 header when the fourCC is `DX10`),
 * then a table of 8-byte entries (`COPY` or `LZ4 ` tag + int32 size), then the data of those blocks in
 * table order. Each block holds one mip level and the blocks go from the smallest mip to the largest,
 * the reverse of a plain DDS. A `COPY` block is raw bytes. An `LZ4 ` block is `uint32` unpacked size
 * followed by sub-blocks, each `int32` length (top bit ignored) + LZ4 data, chained across sub-blocks.
 */
object EddsCodec {
    private const val DDS_HEADER = 128
    private const val DX10_HEADER = 20

    private val COPY = "COPY".toByteArray(Charsets.US_ASCII)
    private val LZ4 = "LZ4 ".toByteArray(Charsets.US_ASCII)

    private class Block(
        val lz4: Boolean,
        val size: Int,
    )

    fun decode(bytes: ByteArray): EddsImage {
        if (bytes.size < DDS_HEADER || String(bytes, 0, 4, Charsets.US_ASCII) != "DDS ") {
            throw EddsException("Not a DDS/EDDS file")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val height = buf.getInt(12)
        val width = buf.getInt(16)
        val mipCount = buf.getInt(28).coerceAtLeast(1)
        if (width <= 0 || height <= 0 || width > 16384 || height > 16384) throw EddsException("Bad size ${width}x$height")

        val fourCc = String(bytes, 84, 4, Charsets.US_ASCII)
        var pos = DDS_HEADER
        val dxgi =
            if (fourCc == "DX10") {
                buf.getInt(DDS_HEADER).also { pos += DX10_HEADER }
            } else {
                0
            }
        val format = detectFormat(buf, fourCc, dxgi)

        val blocks = mutableListOf<Block>()
        while (pos + 8 <= bytes.size) {
            val lz4 =
                when {
                    tagAt(bytes, pos, COPY) -> false
                    tagAt(bytes, pos, LZ4) -> true
                    else -> break
                }
            blocks += Block(lz4, buf.getInt(pos + 4))
            pos += 8
        }
        if (blocks.isEmpty()) throw EddsException("No COPY/LZ4 block table")

        // The table lists mips smallest first; the largest is the last block
        val top = blocks.last()
        var dataPos = pos
        for (i in 0 until blocks.lastIndex) dataPos += blocks[i].size
        if (dataPos + top.size > bytes.size) throw EddsException("Truncated file")

        val need = format.mipBytes(width, height)
        val raw = if (top.lz4) unpackLz4(bytes, dataPos, top.size) else bytes.copyOfRange(dataPos, dataPos + top.size)
        if (raw.size < need) throw EddsException("Top mip has ${raw.size} bytes, expected $need")

        return EddsImage(width, height, PixelDecoders.decode(format, raw, width, height), mipCount, format.name)
    }

    fun decode(file: File): EddsImage = decode(file.readBytes())

    /**
     * Builds an uncompressed BGRA32 EDDS with a full mip chain, stored as `COPY` blocks.
     * This is the layout Workbench itself writes for plain RGBA textures.
     */
    fun encode(
        width: Int,
        height: Int,
        pixels: IntArray,
        generateMips: Boolean = true,
    ): ByteArray {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels do not match ${width}x$height" }

        val mips = mutableListOf(pixels)
        val dims = mutableListOf(width to height)
        if (generateMips) {
            var w = width
            var h = height
            while (w > 1 || h > 1) {
                val nw = maxOf(1, w / 2)
                val nh = maxOf(1, h / 2)
                mips += downsample(mips.last(), w, h, nw, nh)
                dims += nw to nh
                w = nw
                h = nh
            }
        }

        val payload = mips.sumOf { it.size * 4 }
        val out = ByteBuffer.allocate(DDS_HEADER + 8 * mips.size + payload).order(ByteOrder.LITTLE_ENDIAN)
        writeHeader(out, width, height, mips.size)
        for (i in mips.indices.reversed()) {
            out.put(COPY)
            out.putInt(mips[i].size * 4)
        }
        for (i in mips.indices.reversed()) {
            for (argb in mips[i]) {
                out.put(argb.toByte())
                out.put((argb shr 8).toByte())
                out.put((argb shr 16).toByte())
                out.put((argb shr 24).toByte())
            }
        }
        return out.array()
    }

    fun encode(
        image: BufferedImage,
        generateMips: Boolean = true,
    ): ByteArray {
        val w = image.width
        val h = image.height
        val px = IntArray(w * h)
        image.getRGB(0, 0, w, h, px, 0, w)
        return encode(w, h, px, generateMips)
    }

    fun edds2png(
        edds: File,
        png: File = File(edds.parentFile, edds.nameWithoutExtension + ".png"),
    ): File {
        ImageIO.write(decode(edds).toBufferedImage(), "png", png)
        return png
    }

    fun png2edds(
        png: File,
        edds: File = File(png.parentFile, png.nameWithoutExtension + ".edds"),
        generateMips: Boolean = true,
    ): File {
        val image = ImageIO.read(png) ?: throw EddsException("Cannot read image ${png.name}")
        edds.writeBytes(encode(image, generateMips))
        return edds
    }

    fun decodePng(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw EddsException("Cannot read image")

    // ------------------------------------------------------------------

    private fun tagAt(
        bytes: ByteArray,
        pos: Int,
        tag: ByteArray,
    ): Boolean = tag.indices.all { bytes[pos + it] == tag[it] }

    private fun detectFormat(
        buf: ByteBuffer,
        fourCc: String,
        dxgi: Int,
    ): PixelFormat =
        when (fourCc) {
            "DXT1" -> {
                PixelFormat.BC1
            }

            "DXT3" -> {
                PixelFormat.BC2
            }

            "DXT5" -> {
                PixelFormat.BC3
            }

            "ATI1", "BC4U" -> {
                PixelFormat.BC4
            }

            "ATI2", "BC5U" -> {
                PixelFormat.BC5
            }

            "DX10" -> {
                when (dxgi) {
                    71 -> PixelFormat.BC1
                    74 -> PixelFormat.BC2
                    77 -> PixelFormat.BC3
                    80 -> PixelFormat.BC4
                    83 -> PixelFormat.BC5
                    98 -> PixelFormat.BC7
                    87 -> PixelFormat.BGRA32
                    28 -> PixelFormat.RGBA32
                    61 -> PixelFormat.L8
                    else -> throw EddsException("Unsupported DXGI format $dxgi")
                }
            }

            else -> {
                val bpp = buf.getInt(88)
                val redMask = buf.getInt(92)
                when {
                    bpp == 32 && redMask == 0x00FF0000 -> PixelFormat.BGRA32
                    bpp == 32 && redMask == 0x000000FF -> PixelFormat.RGBA32
                    bpp == 8 -> PixelFormat.L8
                    else -> throw EddsException("Unsupported pixel format (fourCC '$fourCc', $bpp bpp)")
                }
            }
        }

    private fun unpackLz4(
        bytes: ByteArray,
        start: Int,
        size: Int,
    ): ByteArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val unpacked = buf.getInt(start)
        if (unpacked < 0 || unpacked > 1 shl 28) throw EddsException("Bad LZ4 size $unpacked")
        val target = ByteArray(unpacked)
        var written = 0
        var consumed = 4
        try {
            while (consumed < size) {
                val length = buf.getInt(start + consumed) and Int.MAX_VALUE
                written = Lz4.decodeBlock(bytes, start + consumed + 4, length, target, written)
                consumed += 4 + length
            }
        } catch (e: IllegalArgumentException) {
            throw EddsException("Corrupt LZ4 data: ${e.message}")
        }
        return target
    }

    private fun downsample(
        src: IntArray,
        w: Int,
        h: Int,
        nw: Int,
        nh: Int,
    ): IntArray {
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            for (x in 0 until nw) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val sx = minOf(x * 2 + dx, w - 1)
                        val sy = minOf(y * 2 + dy, h - 1)
                        val p = src[sy * w + sx]
                        a += p ushr 24
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                }
                out[y * nw + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out
    }

    private fun writeHeader(
        out: ByteBuffer,
        width: Int,
        height: Int,
        mips: Int,
    ) {
        out.put("DDS ".toByteArray(Charsets.US_ASCII))
        out.putInt(124) // header size
        out.putInt(0x0002100F) // CAPS | HEIGHT | WIDTH | PITCH | PIXELFORMAT | MIPMAPCOUNT
        out.putInt(height)
        out.putInt(width)
        out.putInt(width * 4) // pitch
        out.putInt(0) // depth
        out.putInt(mips)
        out.putInt(0) // reserved
        out.put("ENF1".toByteArray(Charsets.US_ASCII)) // Enfusion marker in the DDS reserved area
        repeat(9) { out.putInt(0) } // reserved
        out.putInt(32) // pixel format size
        out.putInt(0x41) // RGB | ALPHAPIXELS
        out.putInt(0) // fourCC
        out.putInt(32) // bits per pixel
        out.putInt(0x00FF0000) // R
        out.putInt(0x0000FF00) // G
        out.putInt(0x000000FF) // B
        out.putInt(-0x1000000) // A (0xFF000000)
        out.putInt(0x00401008) // COMPLEX | MIPMAP | TEXTURE
        repeat(4) { out.putInt(0) } // caps2, caps3, caps4, reserved
    }
}
