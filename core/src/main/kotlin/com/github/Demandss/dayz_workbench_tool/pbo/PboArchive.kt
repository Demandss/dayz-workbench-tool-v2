package com.github.demandss.dayz_workbench_tool.pbo

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

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
 * Only unpacked entries (method 0) are read: that is how the game ships `gui.pbo`.
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

    /** Writes every entry for which [accept] is true under [directory], keeping its folders. Returns how many were written. */
    fun extractTo(
        directory: File,
        accept: (String) -> Boolean = { true },
    ): Int {
        var count = 0
        val root = directory.canonicalFile
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            for (entry in entries) {
                if (!accept(entry.path)) continue
                val target = File(root, entry.path).canonicalFile
                // A path that climbs out of the folder is not written
                if (!target.path.startsWith(root.path + File.separator)) continue
                target.parentFile.mkdirs()
                target.outputStream().channel.use { out -> copy(channel, entry.offset, entry.size, out) }
                count++
            }
        }
        return count
    }

    /** The bytes of one entry, or null when it is not in the archive. */
    fun read(path: String): ByteArray? {
        val entry = entries.firstOrNull { it.path.equals(path, ignoreCase = true) } ?: return null
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteBuffer.allocate(entry.size.toInt())
            raf.channel.read(buffer, entry.offset)
            return buffer.array()
        }
    }

    private fun copy(
        from: FileChannel,
        offset: Long,
        size: Long,
        to: FileChannel,
    ) {
        var done = 0L
        while (done < size) {
            val moved = from.transferTo(offset + done, size - done, to)
            if (moved <= 0) break
            done += moved
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
