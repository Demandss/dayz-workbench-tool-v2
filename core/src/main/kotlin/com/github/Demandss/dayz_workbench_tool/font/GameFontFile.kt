package com.github.demandss.dayz_workbench_tool.font

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Which characters a font of the game has. The `GLPS` chunk lists runs of code points; the records of the `TCRD` chunk follow
 * the runs one after another, so the record of a character is the number of characters in the runs before it plus its place
 * in its own run.
 */
class GlyphRanges(
    /** The runs as (first code point, how many), in the order of the records. */
    val runs: List<Pair<Int, Int>>,
) {
    private val starts = IntArray(runs.size)
    private val firstIndex = IntArray(runs.size)

    /** The number of glyphs, which is the number of records. */
    val count: Int

    init {
        var index = 0
        runs.forEachIndexed { i, (start, n) ->
            starts[i] = start
            firstIndex[i] = index
            index += n
        }
        count = index
    }

    /** The index of the record of [codePoint], or -1 when the font has no glyph for it. */
    fun indexOf(codePoint: Int): Int {
        for (i in runs.indices) {
            val offset = codePoint - starts[i]
            if (offset >= 0 && offset < runs[i].second) return firstIndex[i] + offset
        }
        return -1
    }

    fun has(codePoint: Int) = indexOf(codePoint) >= 0

    /** The code points of all glyphs, in the order of the records. */
    fun codePoints(): List<Int> = runs.flatMap { (start, n) -> (start until start + n).toList() }

    /** True when every character of [text] has a glyph (line breaks and tabs do not need one). */
    fun covers(text: String): Boolean = text.all { it == '\n' || it == '\r' || has(it.code) }

    companion object {
        /** The plain printable ASCII, for a font whose table cannot be read. */
        val ASCII = GlyphRanges(listOf(32 to 95))
    }
}

/** What the header of a `.fnt` says. */
class GameFontHeader(
    /** `FNT3` is a bitmap font, `FNT5` a distance field one. */
    val kind: String,
    val name: String,
    /** The size the font was made for: the height of a line in pixels. */
    val size: Int,
    val ranges: GlyphRanges,
    /** The number of records of the `TCRD` chunk. */
    val records: Int,
)

/** Reads the parts of a `.fnt` that tell which characters a font has and at what size it was made. */
object GameFontFile {
    fun header(bytes: ByteArray): GameFontHeader? {
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "FORM") return null
        val kind = String(bytes, 8, 4, Charsets.US_ASCII)
        if (kind != "FNT3" && kind != "FNT5") return null
        val buffer = ByteBuffer.wrap(bytes)
        var pos = 12
        var name = ""
        var size = 0
        var ranges: GlyphRanges? = null
        var records = 0
        val record = if (kind == "FNT3") 12 else 14
        while (pos + 8 <= bytes.size) {
            val tag = String(bytes, pos, 4, Charsets.US_ASCII)
            val length = buffer.order(ByteOrder.BIG_ENDIAN).getInt(pos + 4)
            val body = pos + 8
            if (length < 0 || body + length > bytes.size) break
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            when (tag) {
                "HEAD" -> {
                    val nameLength = buffer.getInt(body)
                    name = String(bytes, body + 4, (nameLength - 1).coerceAtLeast(0), Charsets.US_ASCII)
                    size = buffer.getInt(body + 4 + nameLength)
                }

                "GLPS" -> {
                    ranges = readRanges(buffer, body, length, kind)
                }

                "TCRD" -> {
                    records = length / record
                }
            }
            pos = body + length
        }
        return GameFontHeader(kind, name, size, ranges ?: GlyphRanges.ASCII, records)
    }

    /**
     * FNT3: eight bytes of header, then the number of glyphs, the number of runs and pairs of 16 bit numbers.
     * FNT5: the text RICH in 16 bit characters, the same two counts and pairs of 32 bit numbers.
     */
    private fun readRanges(
        buffer: ByteBuffer,
        body: Int,
        length: Int,
        kind: String,
    ): GlyphRanges? {
        val wide = kind == "FNT5"
        val runCount = buffer.getInt(body + 12)
        val pair = if (wide) 8 else 4
        if (runCount < 0 || 16 + runCount * pair > length) return null
        val runs =
            (0 until runCount).map { i ->
                val at = body + 16 + i * pair
                if (wide) {
                    buffer.getInt(at) to buffer.getInt(at + 4)
                } else {
                    (buffer.getShort(at).toInt() and 0xFFFF) to (buffer.getShort(at + 2).toInt() and 0xFFFF)
                }
            }
        return GlyphRanges(runs)
    }
}
