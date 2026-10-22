package com.github.demandss.dayz_workbench_tool.pbo

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val SEP = 92.toChar()

class PboArchiveTest {
    private fun ByteArrayOutputStream.text(value: String) {
        write(value.toByteArray(Charsets.ISO_8859_1))
        write(0)
    }

    private fun ByteArrayOutputStream.numbers(vararg values: Int) {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putInt(it) }
        write(buffer.array())
    }

    private fun pbo(
        files: List<Pair<String, ByteArray>>,
        method: Int = 0,
    ): File {
        val out = ByteArrayOutputStream()
        out.text("")
        out.numbers(0x56657273, 0, 0, 0, 0)
        out.text("prefix")
        out.text("gui")
        out.text("version")
        out.text("127662")
        out.text("")
        files.forEach { (name, data) ->
            out.text(name)
            out.numbers(method, data.size, 0, 0, data.size)
        }
        out.text("")
        out.numbers(0, 0, 0, 0, 0)
        files.forEach { out.write(it.second) }
        return File.createTempFile("test", ".pbo").also {
            it.deleteOnExit()
            it.writeBytes(out.toByteArray())
        }
    }

    @Test
    fun readsHeaderAndFiles() {
        val archive =
            PboArchive.open(
                pbo(
                    listOf(
                        "looknfeel${SEP}a.styles" to byteArrayOf(1, 2, 3),
                        "layouts${SEP}b.layout" to byteArrayOf(9),
                    ),
                ),
            )!!
        assertEquals("127662", archive.version)
        assertEquals(listOf("looknfeel/a.styles", "layouts/b.layout"), archive.entries.map { it.path })
        assertContentEquals(byteArrayOf(9), archive.read("LAYOUTS/b.layout"))
        assertNull(archive.read("missing"))
    }

    @Test
    fun extractsKeepingFolders() {
        val archive = PboArchive.open(pbo(listOf("fonts${SEP}x.fnt" to byteArrayOf(5, 6))))!!
        val dir =
            kotlin.io.path
                .createTempDirectory()
                .toFile()
        assertEquals(1, archive.extractTo(dir))
        assertContentEquals(byteArrayOf(5, 6), File(dir, "fonts/x.fnt").readBytes())
        dir.deleteRecursively()
    }

    @Test
    fun packedEntriesAreNotSupported() {
        assertNull(PboArchive.open(pbo(listOf("a.txt" to byteArrayOf(1)), method = 0x43707273)))
    }
}
