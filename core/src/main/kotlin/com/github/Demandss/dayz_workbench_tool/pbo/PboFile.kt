package com.github.demandss.dayz_workbench_tool.pbo

import java.io.File
import java.io.FileFilter

/**
 * A file or folder inside a PBO that looks like a [File] to the code that walks folders: the path is the path of the archive
 * followed by the path inside it (`...\gui.pbo\layouts\menu.layout`). It exists for [isFile], [isDirectory], [listFiles] and
 * [getParentFile]; the bytes are read with [readBytesOf] and [readTextOf], because the reading functions of [File] open the
 * disk. Nothing of the archive is kept in memory or on disk.
 */
class PboFile(
    val archive: PboArchive,
    /** The path inside the archive with `/`; empty for the archive itself. */
    val inner: String,
) : File(archive.file.path + (if (inner.isEmpty()) "" else File.separator + inner.replace('/', File.separatorChar))) {
    override fun isFile(): Boolean = inner.isNotEmpty() && archive.entry(inner) != null

    override fun isDirectory(): Boolean = inner.isEmpty() || archive.list(inner).isNotEmpty()

    override fun exists(): Boolean = isFile || isDirectory

    override fun length(): Long = archive.entry(inner)?.size ?: 0L

    override fun getParentFile(): File? =
        if (inner.isEmpty()) archive.file.parentFile else PboFile(archive, inner.substringBeforeLast('/', ""))

    override fun getAbsoluteFile(): File = this

    override fun getCanonicalFile(): File = this

    override fun getCanonicalPath(): String = path

    override fun listFiles(): Array<File>? =
        if (isDirectory) archive.list(inner).map { child(it.trimEnd('/')) }.toTypedArray<File>() else null

    override fun listFiles(filter: FileFilter?): Array<File>? = listFiles()?.filter { filter == null || filter.accept(it) }?.toTypedArray()

    /** The file or folder [name] (one or several names with `/`) inside this folder. */
    fun child(name: String): PboFile = PboFile(archive, if (inner.isEmpty()) name else "$inner/$name")

    companion object {
        private val archives = java.util.concurrent.CopyOnWriteArrayList<PboArchive>()

        /** Lets [resolve] find files of [archive] by their path text. */
        fun register(archive: PboArchive) {
            if (archives.none { it.file == archive.file }) archives += archive
        }

        /** The file a path text names when it points into a registered archive, else null. */
        fun resolve(path: String): PboFile? {
            for (archive in archives) {
                val base = archive.file.path
                if (path.equals(base, ignoreCase = true)) return PboFile(archive, "")
                if (path.length > base.length + 1 && path.startsWith(base, ignoreCase = true) && path[base.length] in "\\/") {
                    return PboFile(archive, path.substring(base.length + 1).replace('\\', '/'))
                }
            }
            return null
        }
    }
}

/** The file [relative] inside [dir]; a path inside an archive stays inside it. */
fun childOf(
    dir: File,
    relative: String,
): File = if (dir is PboFile) dir.child(relative.replace('\\', '/')) else File(dir, relative)

/** The bytes of a file on disk or inside a PBO. */
fun readBytesOf(file: File): ByteArray =
    (file as? PboFile ?: PboFile.resolve(file.path))?.let { it.archive.read(it.inner) } ?: file.readBytes()

fun readTextOf(file: File): String = readBytesOf(file).toString(Charsets.UTF_8)

/** The file a path text names: inside a registered PBO or on disk. */
fun fileAt(path: String): File = PboFile.resolve(path) ?: File(path)

/**
 * The file a game path such as `gui/looknfeel/x.styles` names under [root]. The top folder of an archive stands for its
 * prefix (`gui`), so `gui/` is dropped there; a path that does not start with the prefix is not in the archive.
 */
fun inRoot(
    root: File,
    relative: String,
): File {
    if (root !is PboFile || root.inner.isNotEmpty()) return childOf(root, relative)
    val prefix = root.archive.prefix
    val clean = relative.replace('\\', '/')
    return if (prefix.isNotEmpty() && clean.startsWith("$prefix/", ignoreCase = true)) {
        root.child(clean.substring(prefix.length + 1))
    } else {
        root.child("?")
    }
}
