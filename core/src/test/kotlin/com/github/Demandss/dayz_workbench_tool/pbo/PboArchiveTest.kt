package com.github.demandss.dayz_workbench_tool.pbo

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun listsFoldersAndReadsThroughVirtualFiles() {
        val archive =
            PboArchive.open(
                pbo(
                    listOf(
                        "fonts${SEP}x.fnt" to byteArrayOf(5, 6),
                        "fonts${SEP}y.fnt" to byteArrayOf(7),
                        "a.txt" to byteArrayOf(1),
                    ),
                ),
            )!!
        assertEquals(listOf("fonts/", "a.txt"), archive.list(""))
        assertEquals(listOf("x.fnt", "y.fnt"), archive.list("fonts"))
        assertEquals("gui", archive.prefix)
        PboFile.register(archive)
        val file = PboFile.resolve(archive.file.path + SEP + "fonts" + SEP + "x.fnt")!!
        assertEquals(true, file.isFile)
        assertEquals(true, file.parentFile!!.isDirectory)
        assertContentEquals(byteArrayOf(5, 6), readBytesOf(file))
        assertEquals("x.fnt", file.name)
        assertContentEquals(byteArrayOf(5, 6), readBytesOf(inRoot(PboFile(archive, ""), "gui/fonts/x.fnt")))
        assertEquals(false, inRoot(PboFile(archive, ""), "other/fonts/x.fnt").isFile)
    }

    @Test
    fun packedEntriesAreNotSupported() {
        assertNull(PboArchive.open(pbo(listOf("a.txt" to byteArrayOf(1)), method = 0x43707273)))
    }

    @Test
    fun aTruncatedArchiveFailsInsteadOfReturningZeros() {
        val file = pbo(listOf("a.txt" to ByteArray(100) { 7 }))
        val archive = PboArchive.open(file)!!
        // The data of the entry is cut in half
        file.writeBytes(file.readBytes().copyOf((file.length() - 50).toInt()))
        assertFailsWith<java.io.IOException> { archive.read("a.txt") }
    }
}
