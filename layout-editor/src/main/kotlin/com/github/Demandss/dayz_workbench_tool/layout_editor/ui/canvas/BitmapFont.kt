package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.demandss.dayz_workbench_tool.font.GlyphRanges
import com.github.demandss.dayz_workbench_tool.pbo.readBytesOf
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One glyph of a bitmap font: its cell in the atlas (as high as the line) and the bearings left and right of it. */
internal class BitmapGlyph(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val leftBearing: Int,
    val rightBearing: Int,
)

/**
 * A bitmap font of the game (`gui/fonts/Metron22.fnt` with its `.edds` atlas): white letters with their coverage in the alpha
 * channel, every glyph as high as the line of the font size. The pen steps by the left bearing, the width and the right
 * bearing. Drawn at the size it was made for the letters are exactly the ones of the atlas; at another size they are scaled.
 */
internal class BitmapFont(
    val name: String,
    /** The size the font was made for: the height of a line in pixels. */
    val size: Int,
    private val glyphs: List<BitmapGlyph>,
    private val atlas: BufferedImage,
    /** The characters the font has, in the order of the glyph records. */
    private val ranges: GlyphRanges = GlyphRanges.ASCII,
) : GameFont() {
    private val cache = HashMap<Pair<Int, Int>, GlyphBitmap?>()

    override fun covers(text: String) = ranges.covers(text)

    override fun advance(
        c: Char,
        pixels: Float,
        widthScale: Float,
    ): Float {
        val g = glyphs[ranges.indexOf(c.code).takeIf { it in glyphs.indices } ?: 0]
        return (g.leftBearing + g.width + g.rightBearing) * pixels / size * widthScale
    }

    override fun glyph(
        c: Char,
        pixels: Float,
        look: GlyphLook,
        widthScale: Float,
    ): GlyphBitmap? {
        val index = ranges.indexOf(c.code)
        if (index !in glyphs.indices) return null
        return cache.getOrPut(index to (pixels * 4f).toInt()) {
            val g = glyphs[index]
            if (g.width <= 0 || g.height <= 0 || g.x + g.width > atlas.width || g.y + g.height > atlas.height) {
                null
            } else {
                val s = pixels / size
                GlyphBitmap(
                    atlas.getSubimage(g.x, g.y, g.width, g.height).toComposeImageBitmap(),
                    g.leftBearing * s * widthScale,
                    // The cell starts at the top of the line
                    -BASELINE * pixels,
                    s,
                    widthScale,
                )
            }
        }
    }

    companion object {
        const val FIRST = 32
        const val LAST = 126

        // Where the baseline lies in a line, in shares of the letter size (the same as for the SDF fonts)
        private const val BASELINE = 0.92f

        /** Reads the glyph table of a bitmap `.fnt` (`FNT3`): 12 byte records of x, y, width, height and two signed bearings. */
        fun readGlyphs(file: File): Pair<Int, List<BitmapGlyph>>? {
            val bytes = runCatching { readBytesOf(file) }.getOrNull() ?: return null
            if (bytes.size < 12 || String(bytes, 0, 4) != "FORM" || String(bytes, 8, 4) != "FNT3") return null
            val buffer = ByteBuffer.wrap(bytes)
            var pos = 12
            var size = 0
            var records: List<BitmapGlyph> = emptyList()
            while (pos + 8 <= bytes.size) {
                val tag = String(bytes, pos, 4)
                val length = buffer.order(ByteOrder.BIG_ENDIAN).getInt(pos + 4)
                val body = pos + 8
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                if (tag == "HEAD") {
                    val nameLength = buffer.getInt(body)
                    size = buffer.getInt(body + 4 + nameLength)
                } else if (tag == "TCRD") {
                    records =
                        (0 until length / RECORD).map { i ->
                            val at = body + i * RECORD

                            fun u(n: Int) = buffer.getShort(at + 2 * n).toInt() and 0xFFFF
                            BitmapGlyph(u(0), u(1), u(2), u(3), buffer.getShort(at + 8).toInt(), buffer.getShort(at + 10).toInt())
                        }
                }
                pos = body + length
            }
            return if (size > 0 && records.size > LAST - FIRST) size to records else null
        }

        private const val RECORD = 12
    }
}
