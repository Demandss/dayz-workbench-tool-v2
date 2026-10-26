package com.github.demandss.dayz_workbench_tool.pbo

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A packed game folder (`dta/gui.pbo`): a header listing the files, then their bytes one after the other.
 *
 * ```
 * entry: path (zero ended), u32 packing method, u32 original size, u32 reserved, u32 time stamp, u32 data size
 * the first entry has no name and the packing method 'Vers': it is followed by zero ended key and value pairs
 *   (product, prefix, version) that end with an empty key
 * an entry without a name and without a size ends the header
 * ```
 *
 * Only unpacked entries (method 0) are read: that is how the game ships `gui.pbo`. Nothing is unpacked to disk: an entry is read
 * from the archive when it is asked for.
 */
class PboArchive private constructor(
    val file: File,
    val properties: Map<String, String>,
    val entries: List<Entry>,
) {
    class Entry(
        /** The path inside the archive with `/` as the separator. */
        val path: String,
        internal val offset: Long,
        val size: Long,
    )

    /** The game build the archive was made for (the `version` of its header), or an empty text. */
    val version: String get() = properties["version"].orEmpty()

    private val byPath: Map<String, Entry> = entries.associateBy { it.path.lowercase() }

    /** The folder the archive stands for in game paths (`gui` for gui.pbo), or an empty text. */
    val prefix: String get() = properties["prefix"].orEmpty().replace('\\', '/').trim('/')

    /** The entry at [path] (`/` separated, case does not matter), or null. */
    fun entry(path: String): Entry? = byPath[path.lowercase()]

    /** The names directly inside the folder [dir] (empty for the top), folders marked by a trailing `/`, in no order. */
    fun list(dir: String): List<String> {
        val start = if (dir.isEmpty()) "" else dir.trimEnd('/').lowercase() + "/"
        val names = LinkedHashSet<String>()
        for (entry in entries) {
            if (!entry.path.lowercase().startsWith(start)) continue
            val rest = entry.path.substring(start.length)
            val slash = rest.indexOf('/')
            names += if (slash < 0) rest else rest.substring(0, slash) + "/"
        }
        return names.toList()
    }

    /** The bytes of one entry, read from the archive when asked for and not kept; null when it is not in the archive. */
    fun read(path: String): ByteArray? {
        val entry = entry(path) ?: return null
        // A size or an offset that the file cannot hold is a broken archive: say so instead of returning zeros
        val tooBig = entry.size < 0 || entry.size > Int.MAX_VALUE - 8
        if (tooBig) {
            throw IOException("${file.name}: entry $path is too big (${entry.size} bytes)")
        }
        RandomAccessFile(file, "r").use { raf ->
            if (entry.offset < 0 || entry.offset + entry.size > raf.length()) {
                throw IOException("${file.name}: entry $path lies past the end of the file")
            }
            val buffer = ByteBuffer.allocate(entry.size.toInt())
            var position = entry.offset
            // One read may stop short: go on until the buffer is full
            while (buffer.hasRemaining()) {
                val n = raf.channel.read(buffer, position)
                if (n < 0) throw IOException("${file.name}: entry $path is cut off")
                position += n
            }
            return buffer.array()
        }
    }

    companion object {
        private const val VERS = 0x56657273 // "sreV" read as a little endian number

        /** Reads the header of [file]; null when it is not a PBO or uses a packing this reader does not know. */
        fun open(file: File): PboArchive? = runCatching { read(file) }.getOrNull()

        private fun read(file: File): PboArchive? {
            if (!file.isFile) return null
            val properties = LinkedHashMap<String, String>()
            val raw = ArrayList<Triple<String, Long, Long>>()
            DataInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16)).use { input ->
                var headerEnd = 0L

                fun text(): String {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (true) {
                        val b = input.read()
                        headerEnd++
                        if (b <= 0) break
                        bytes.write(b)
                    }
                    return bytes.toString(Charsets.ISO_8859_1)
                }

                fun number(): Long {
                    val buffer = ByteArray(4)
                    input.readFully(buffer)
                    headerEnd += 4
                    return ByteBuffer
                        .wrap(buffer)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .toLong() and 0xFFFFFFFFL
                }
                while (true) {
                    val name = text()
                    val method = number()
                    val original = number()
                    number()
                    number()
                    val size = number()
                    if (name.isEmpty() && method == VERS.toLong()) {
                        while (true) {
                            val key = text()
                            if (key.isEmpty()) break
                            properties[key] = text()
                        }
                        continue
                    }
                    if (name.isEmpty() && size == 0L && original == 0L) break
                    if (method != 0L) return null
                    raw += Triple(name.replace('\\', '/'), size, 0L)
                }
                var offset = headerEnd
                val entries =
                    raw.map { (path, size, _) ->
                        Entry(path, offset, size).also { offset += size }
                    }
                return PboArchive(file, properties, entries)
            }
        }
    }
}
