package com.github.demandss.dayz_workbench_tool.font

import com.github.demandss.dayz_workbench_tool.texture.EddsCodec
import java.awt.Font
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontImporterTest {
    private val font = Font(Font.SANS_SERIF, Font.PLAIN, 20)

    @Test
    fun `runs of characters give the index of a glyph`() {
        val ranges = GlyphRanges(listOf(0x20 to 95, 0x401 to 3))
        assertEquals(0, ranges.indexOf(0x20))
        assertEquals(94, ranges.indexOf('~'.code))
        assertEquals(95, ranges.indexOf(0x401))
        assertEquals(-1, ranges.indexOf(0x400))
        assertEquals(98, ranges.count)
        assertTrue(ranges.covers("Ab c\n"))
        assertTrue(!ranges.covers("Ж"))
    }

    @Test
    fun `a font made from a font file reads back with its characters and its atlas`() {
        val out = FontImporter.make(font, "TestFont", 22, FontImporter.charset("cyrillic"))
        val header = GameFontFile.header(out.fnt)!!
        assertEquals("FNT3", header.kind)
        assertEquals("TestFont22", header.name)
        assertEquals(22, header.size)
        assertEquals(out.glyphs, header.records)
        assertEquals(out.glyphs, header.ranges.count)
        assertTrue(header.ranges.has('A'.code) && header.ranges.has('Ж'.code) && header.ranges.has(0x20BD))
        val atlas = EddsCodec.decode(out.edds)
        assertEquals(out.atlasWidth, atlas.width)
        assertEquals(out.atlasHeight, atlas.height)
        // The letters are white with their coverage in the alpha channel
        val visible = atlas.pixels.count { (it ushr 24) != 0 }
        assertTrue(visible > 1000, "the atlas holds letters")
        assertTrue(atlas.pixels.filter { (it ushr 24) != 0 }.all { (it and 0xFFFFFF) == 0xFFFFFF })
    }

    @Test
    fun `files are written for every size and found by the catalog`() {
        val dir = Files.createTempDirectory("fonts").toFile()
        try {
            val written = FontImporter.write(font, "Probe", listOf(16, 12), dir, FontImporter.charset("ascii"))
            assertEquals(listOf(12, 16), written.map { it.size })
            val found = FontCatalog.scan(dir, dir.parentFile, "test")
            assertEquals(2, found.size)
            assertTrue(found.all { it.kind == "bitmap" && it.latin && !it.cyrillic && it.familyXml })
            assertEquals(setOf("Probe"), found.map { it.family.substringAfterLast('/') }.toSet())
        } finally {
            dir.deleteRecursively()
        }
    }
}
