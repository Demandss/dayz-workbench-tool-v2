package com.github.demandss.dayz_workbench_tool.font

import com.github.demandss.dayz_workbench_tool.texture.EddsCodec
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** A font made for the game: the bytes of the `.fnt` and of the `.edds` atlas, and what they hold. */
class GameFontOutput(
    val name: String,
    val size: Int,
    val fnt: ByteArray,
    val edds: ByteArray,
    val glyphs: Int,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val ranges: GlyphRanges,
)

/**
 * Makes bitmap fonts of the game (`FNT3` with an `.edds` atlas) from a TrueType or OpenType font, the way the font generator
 * of the game tools makes them: white letters with their coverage in the alpha channel, every glyph cell as high as the line,
 * the pen stepping by the left bearing, the width and the right bearing. The size of a font file is the height of a line, so
 * the letters are scaled until the ascent and the descent of the font together fill it.
 */
object FontImporter {
    /** The characters a font gets by default: ASCII, Latin-1, Latin Extended-A, Cyrillic and the usual punctuation and currency signs. */
    val DEFAULT_CHARSET: List<IntRange> =
        listOf(
            0x20..0x7E,
            0xA0..0x17F,
            0x401..0x45F,
            0x490..0x491,
            0x2010..0x2027,
            0x2030..0x203A,
            0x20AC..0x20AC,
            0x20B4..0x20B4,
            0x20BD..0x20BD,
            0x2116..0x2116,
            0x2122..0x2122,
            0x2190..0x2193,
        )

    /** Named sets of characters for the tools: `ascii`, `latin`, `cyrillic` (which includes Latin) and `default`. */
    fun charset(name: String?): List<IntRange> =
        when (name?.lowercase()) {
            "ascii" -> listOf(0x20..0x7E)
            "latin" -> listOf(0x20..0x7E, 0xA0..0x17F, 0x2010..0x2027, 0x20AC..0x20AC, 0x2122..0x2122)
            "cyrillic" -> listOf(0x20..0x7E, 0xA0..0xFF, 0x401..0x45F, 0x490..0x491, 0x2010..0x2027, 0x20BD..0x20BD, 0x2116..0x2116)
            else -> DEFAULT_CHARSET
        }

    fun loadFont(file: File): Font = Font.createFont(Font.TRUETYPE_FONT, file)

    /** One font file of [size] from [font], named [name] + size (the files are `<name><size>.fnt` and `.edds`). */
    fun make(
        font: Font,
        name: String,
        size: Int,
        charset: List<IntRange> = DEFAULT_CHARSET,
    ): GameFontOutput {
        require(size in 6..200) { "size must be from 6 to 200 pixels" }
        val context = FontRenderContext(null, true, true)
        // The size at which the line (ascent + descent) of the font is as high as the file asks
        val probe = font.deriveFont(100f)
        val probeMetrics = probe.getLineMetrics("Hxg", context)
        val line100 = probeMetrics.ascent + probeMetrics.descent
        val sized = font.deriveFont(100f * size / line100)
        val metrics = sized.getLineMetrics("Hxg", context)
        val baseline = ceil(metrics.ascent.toDouble()).toInt().coerceIn(1, size - 1)

        class Cell(
            val code: Int,
            val image: BufferedImage?,
            val left: Int,
            val right: Int,
        ) {
            val width get() = image?.width ?: 0
            var x = 0
            var y = 0
        }

        val codes =
            charset
                .flatMap { it.toList() }
                .distinct()
                .filter { sized.canDisplay(it) }
                .sorted()
        require(codes.isNotEmpty()) { "the font has none of the characters asked for" }
        val pad = size
        val cells =
            codes.map { code ->
                val text = String(Character.toChars(code))
                val advance =
                    sized
                        .getStringBounds(text, context)
                        .width
                        .roundToInt()
                        .coerceAtLeast(0)
                val canvas = BufferedImage(advance + 2 * pad, size, BufferedImage.TYPE_INT_ARGB)
                val g = canvas.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
                g.font = sized
                g.color = java.awt.Color.WHITE
                g.drawString(text, pad, baseline)
                g.dispose()
                // The ink columns: where any pixel has coverage
                var first = -1
                var last = -1
                for (x in 0 until canvas.width) {
                    var any = false
                    for (y in 0 until size) if ((canvas.getRGB(x, y) ushr 24) != 0) any = true
                    if (any) {
                        if (first < 0) first = x
                        last = x
                    }
                }
                if (first < 0) {
                    Cell(code, null, 0, advance)
                } else {
                    val inkWidth = last - first + 1
                    val left = first - pad
                    Cell(code, canvas.getSubimage(first, 0, inkWidth, size), left, advance - left - inkWidth)
                }
            }

        // Pack the cells into rows of an atlas whose sides are powers of two, one pixel apart
        var atlasWidth = 256
        while (atlasWidth < 4096 &&
            cells.sumOf { (it.width + 1).toLong() * (size + 1) } > atlasWidth.toLong() * atlasWidth * 0.8
        ) {
            atlasWidth *= 2
        }
        var x = 1
        var y = 1
        for (cell in cells) {
            if (x + cell.width + 1 > atlasWidth) {
                x = 1
                y += size + 1
            }
            cell.x = x
            cell.y = y
            x += cell.width + 1
        }
        var atlasHeight = 64
        while (atlasHeight < y + size + 1) atlasHeight *= 2
        require(atlasWidth <= 4096 && atlasHeight <= 4096) { "the glyphs do not fit one atlas: ask for fewer characters or a smaller size" }
        val atlas = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        for (cell in cells) {
            cell.image?.let {
                atlas.createGraphics().apply {
                    drawImage(it, cell.x, cell.y, null)
                    dispose()
                }
            }
        }

        val ranges = GlyphRanges(runsOf(codes))
        val full = "$name$size"
        return GameFontOutput(
            full,
            size,
            fnt(full, size, ranges, cells.map { intArrayOf(it.x, it.y, it.width, size, it.left, it.right) }),
            EddsCodec.encode(atlas, generateMips = false),
            cells.size,
            atlasWidth,
            atlasHeight,
            ranges,
        )
    }

    /** Writes `<name><size>.fnt` and `.edds` for every size into [dir]. Returns the files. */
    fun write(
        font: Font,
        name: String,
        sizes: List<Int>,
        dir: File,
        charset: List<IntRange> = DEFAULT_CHARSET,
    ): List<GameFontOutput> {
        dir.mkdirs()
        // The descriptor of the font generator: the sizes of the family, as it lies next to the fonts of the game
        File(dir, "$name.xml").writeText(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<fontGenerator>\n" +
                sizes.distinct().sorted().joinToString("") { "\t<font size=\"$it\" />\n" } + "</fontGenerator>\n",
        )
        return sizes.distinct().sorted().map { size ->
            make(font, name, size, charset).also {
                File(dir, "${it.name}.fnt").writeBytes(it.fnt)
                File(dir, "${it.name}.edds").writeBytes(it.edds)
            }
        }
    }

    private fun runsOf(codes: List<Int>): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        var start = codes.first()
        var previous = start
        for (c in codes.drop(1)) {
            if (c != previous + 1) {
                runs += start to previous - start + 1
                start = c
            }
            previous = c
        }
        runs += start to previous - start + 1
        return runs
    }

    /** The `FNT3` container: HEAD, GLPS, TCRD and an empty KERN, as the font generator of the game tools lays them out. */
    private fun fnt(
        name: String,
        size: Int,
        ranges: GlyphRanges,
        records: List<IntArray>,
    ): ByteArray {
        fun chunk(
            tag: String,
            body: ByteArray,
        ): ByteArray {
            val out = ByteBuffer.allocate(8 + body.size).order(ByteOrder.BIG_ENDIAN)
            out.put(tag.toByteArray(Charsets.US_ASCII))
            out.putInt(body.size)
            out.put(body)
            return out.array()
        }

        val nameBytes = name.toByteArray(Charsets.US_ASCII) + 0
        val head =
            ByteBuffer
                .allocate(4 + nameBytes.size + 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putInt(nameBytes.size)
                    put(nameBytes)
                    putInt(size)
                    putInt(0)
                }.array()
        val glps =
            ByteBuffer
                .allocate(16 + 4 * ranges.runs.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putInt(0x104)
                    putInt(0)
                    putInt(ranges.count)
                    putInt(ranges.runs.size)
                    for ((start, n) in ranges.runs) {
                        putShort(start.toShort())
                        putShort(n.toShort())
                    }
                }.array()
        val tcrd =
            ByteBuffer
                .allocate(12 * records.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    for (r in records) for (v in r) putShort(v.toShort())
                }.array()
        val body = ByteArrayOutputStream()
        body.write("FNT3".toByteArray(Charsets.US_ASCII))
        body.write(chunk("HEAD", head))
        body.write(chunk("GLPS", glps))
        body.write(chunk("TCRD", tcrd))
        body.write(chunk("KERN", ByteArray(0)))
        val form = ByteBuffer.allocate(8 + body.size()).order(ByteOrder.BIG_ENDIAN)
        form.put("FORM".toByteArray(Charsets.US_ASCII))
        form.putInt(body.size())
        form.put(body.toByteArray())
        return form.array()
    }

    /** The largest of the numbers, for a caller that wants the widest atlas of a set. */
    internal fun widest(outputs: List<GameFontOutput>) = outputs.fold(0) { m, o -> max(m, o.atlasWidth) }
}
