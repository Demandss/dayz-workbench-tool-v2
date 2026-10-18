package com.github.demandss.dayz_workbench_tool.texture

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EddsCodecTest {
    @Test
    fun `encode then decode returns the same pixels`() {
        val w = 37
        val h = 19
        val px = IntArray(w * h) { (0xFF shl 24) or ((it * 7) and 0xFF shl 16) or ((it * 3) and 0xFF shl 8) or (it and 0xFF) }
        px[5] = 0x80102030.toInt()

        val decoded = EddsCodec.decode(EddsCodec.encode(w, h, px))

        assertEquals(w, decoded.width)
        assertEquals(h, decoded.height)
        assertEquals(6, decoded.mipCount)
        assertContentEquals(px, decoded.pixels)
    }

    @Test
    fun `header and block table match a file written by Workbench`() {
        val sample = File("D:/CJ_Passport/passHud.edds")
        if (!sample.exists()) return
        val original = sample.readBytes()
        val decoded = EddsCodec.decode(original)

        val mine = EddsCodec.encode(decoded.width, decoded.height, decoded.pixels)

        // DDS header + 9 block entries
        assertContentEquals(original.copyOfRange(0, 200), mine.copyOfRange(0, 200))
        // Same size, and the top mip (last block) is identical. Smaller mips differ: Workbench uses its own filter.
        assertEquals(original.size, mine.size)
        val top = decoded.width * decoded.height * 4
        assertContentEquals(original.copyOfRange(original.size - top, original.size), mine.copyOfRange(mine.size - top, mine.size))
    }

    @Test
    fun `decodes every sample file found locally`() {
        val out = File("build/edds-out").also { it.mkdirs() }
        val roots = listOf("K:/@CJ_Anomalies", "D:/CJ_Passport", "K:/MF_Mods").map(::File).filter { it.exists() }
        var ok = 0
        val failures = mutableListOf<String>()
        for (root in roots) {
            for (f in root.walkTopDown().filter { it.isFile && it.extension == "edds" }.take(150)) {
                try {
                    val img = EddsCodec.decode(f)
                    if (ok < 12) ImageIO.write(img.toBufferedImage(), "png", File(out, f.nameWithoutExtension + ".png"))
                    ok++
                } catch (e: Exception) {
                    failures += "${f.path}: ${e.message}"
                }
            }
        }
        println("EDDS decoded=$ok failed=${failures.size}")
        failures.take(10).forEach(::println)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
