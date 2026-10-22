package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.demandss.dayz_workbench_tool.pbo.readBytesOf
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * One glyph of a DayZ SDF font (`gui/fonts/sdf_*.fnt` with its `.edds` atlas): where its cell is in the atlas and the size of
 * the shape, its left bearing and its height over the baseline, all in the pixels of the font size the atlas was made for.
 */
internal class SdfGlyph(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val bearingX: Int,
    val bearingY: Int,
    val advance: Int,
)

/**
 * A glyph cut out of the atlas at one size: [left] and [top] are where its bitmap starts, from the pen and the baseline.
 * The bitmap is drawn [scale] times as high and [scale] * [xScale] times as wide as it is.
 */
internal class GlyphBitmap(
    val image: ImageBitmap,
    val left: Float,
    val top: Float,
    val scale: Float = 1f,
    val xScale: Float = 1f,
)

/** A font of the game, bitmap or distance field: the pen steps and the letters. */
internal abstract class GameFont {
    /** Whether every character of [text] has a glyph here. */
    abstract fun covers(text: String): Boolean

    /** How far the pen moves after [c] at [pixels] size. */
    abstract fun advance(
        c: Char,
        pixels: Float,
        widthScale: Float = 1f,
    ): Float

    /** The bitmap of [c] for the font size [pixels], or null for a character without a shape (a space). */
    abstract fun glyph(
        c: Char,
        pixels: Float,
        look: GlyphLook = GlyphLook(),
        widthScale: Float = 1f,
    ): GlyphBitmap?
}

/** How a glyph is turned from a distance field into a bitmap: letters grown by [grow] pixels and edges softened by [soft]. */
internal data class GlyphLook(
    val grow: Float = 0f,
    val soft: Float = 0f,
)

/**
 * A multi channel signed distance field font. The glyphs sit in the cells of the atlas, centred; the median of the three channels
 * is the distance to the edge (127.5 at the edge, [slope] levels per atlas pixel, more inside). A glyph is drawn by sampling the
 * field for every pixel of the size asked for, so letters are sharp at any size.
 */
internal class SdfFont(
    val name: String,
    /** The font size the atlas was made for: glyph sizes and bearings are in pixels of this size. */
    val size: Int,
    private val glyphs: List<SdfGlyph>,
    private val atlas: BufferedImage,
    /** Advances of the printable ASCII characters in thousandths of an em. */
    private val advances: IntArray,
    private val cell: Int,
    private val slope: Float,
) : GameFont() {
    private val cache = HashMap<List<Any>, GlyphBitmap?>()

    /** Whether every character of [text] has a glyph here (the printable ASCII). */
    override fun covers(text: String) = text.all { it.code in FIRST..LAST || it == '\n' }

    /** How far the pen moves after [c] at [pixels] size. */
    override fun advance(
        c: Char,
        pixels: Float,
        widthScale: Float,
    ): Float = advances[(c.code - FIRST).coerceIn(0, advances.lastIndex)] * pixels * widthScale / 1000f

    /** The bitmap of [c] for the font size [pixels], or null for a character without a shape (a space). */
    override fun glyph(
        c: Char,
        pixels: Float,
        look: GlyphLook,
        widthScale: Float,
    ): GlyphBitmap? {
        if (c.code !in FIRST..LAST) return null
        // Sizes are told apart by a quarter of a pixel, so the cache stays small
        val key = listOf(c.code, (pixels * 4f).toInt(), look, (widthScale * 100f).toInt())
        return cache.getOrPut(key) { render(glyphs[c.code - FIRST], pixels, look, widthScale) }
    }

    private fun render(
        g: SdfGlyph,
        pixels: Float,
        look: GlyphLook,
        widthScale: Float,
    ): GlyphBitmap? {
        if (g.width <= 0 || g.height <= 0) return null
        val s = pixels / size
        val sx = s * widthScale
        val margin = ceil(2f + look.grow + look.soft * 2f).toInt()
        val outW = ceil(g.width * sx).toInt() + 2 * margin
        val outH = ceil(g.height * s).toInt() + 2 * margin
        if (outW <= 0 || outH <= 0 || outW > MAX_SIDE || outH > MAX_SIDE) return null
        // The shape is centred in its cell
        val shapeX = g.x + floor((cell - g.width) / 2f)
        val shapeY = g.y + floor((cell - g.height) / 2f)
        val image = BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB)
        val out = IntArray(outW * outH)
        val spread = 1f + look.soft * 2f
        for (oy in 0 until outH) {
            for (ox in 0 until outW) {
                val u = (ox + 0.5f - margin) / sx
                val v = (oy + 0.5f - margin) / s
                val d = (median(shapeX + u, shapeY + v) - 127.5f) / slope
                val coverage = (d * s / spread + 0.5f + look.grow / spread).coerceIn(0f, 1f)
                val a = (coverage * 255f + 0.5f).toInt()
                out[oy * outW + ox] = (a shl 24) or 0x00FFFFFF
            }
        }
        image.setRGB(0, 0, outW, outH, out, 0, outW)
        return GlyphBitmap(image.toComposeImageBitmap(), g.bearingX * sx - margin, -g.bearingY * s - margin)
    }

    /** The median of the three channels at the atlas position ([ax], [ay]), bilinear between the pixels. */
    private fun median(
        ax: Float,
        ay: Float,
    ): Float {
        val fx = ax - 0.5f
        val fy = ay - 0.5f
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val tx = fx - x0
        val ty = fy - y0
        var r = 0f
        var gr = 0f
        var b = 0f
        for (j in 0..1) {
            for (i in 0..1) {
                val w = (if (i == 0) 1f - tx else tx) * (if (j == 0) 1f - ty else ty)
                val px = (x0 + i).coerceIn(0, atlas.width - 1)
                val py = (y0 + j).coerceIn(0, atlas.height - 1)
                val rgb = atlas.getRGB(px, py)
                r += w * ((rgb shr 16) and 0xFF)
                gr += w * ((rgb shr 8) and 0xFF)
                b += w * (rgb and 0xFF)
            }
        }
        return max(minOf(r, gr), minOf(max(r, gr), b))
    }

    companion object {
        const val FIRST = 32
        const val LAST = 126
        private const val MAX_SIDE = 2400

        /** Reads the glyph table of a `.fnt` file: the records of the TCRD chunk, seven 16 bit numbers each. */
        fun readGlyphs(file: File): Pair<Int, List<SdfGlyph>>? {
            val bytes = runCatching { readBytesOf(file) }.getOrNull() ?: return null
            if (bytes.size < 12 || String(bytes, 0, 4) != "FORM" || String(bytes, 8, 4) != "FNT5") return null
            val buffer = ByteBuffer.wrap(bytes)
            var pos = 12
            var size = 0
            var records: List<SdfGlyph> = emptyList()
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

                            // The bearings are signed: an underscore lies under the baseline
                            fun signed(n: Int) = buffer.getShort(at + 2 * n).toInt()
                            SdfGlyph(u(0), u(1), u(2), u(3), signed(4), signed(5), u(6))
                        }
                }
                pos = body + length
            }
            return if (size > 0 && records.size > LAST - FIRST) size to records else null
        }

        private const val RECORD = 14

        /** The levels the median of the channels climbs per atlas pixel across the stem of a capital I. */
        fun estimateSlope(
            atlas: BufferedImage,
            glyph: SdfGlyph,
            cell: Int,
        ): Float {
            val row = glyph.y + cell / 2
            var best = 0f
            var previous = -1f
            for (x in glyph.x until glyph.x + cell) {
                val rgb = atlas.getRGB(x.coerceIn(0, atlas.width - 1), row.coerceIn(0, atlas.height - 1))
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val m = max(minOf(r, g), minOf(max(r, g), b)).toFloat()
                if (previous >= 0f && m in 20f..235f && previous in 20f..235f) best = max(best, kotlin.math.abs(m - previous))
                previous = m
            }
            return if (best > 4f) best else 21f
        }
    }
}
