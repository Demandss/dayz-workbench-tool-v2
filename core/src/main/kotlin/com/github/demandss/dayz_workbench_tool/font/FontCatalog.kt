package com.github.demandss.dayz_workbench_tool.font

import java.io.File

/** A font file found in a folder: the path a layout writes into `font`, what kind it is and which characters it has. */
class FontInfo(
    /** `gui/fonts/Metron22`: the folder of the mod and the name of the file without its extension. */
    val path: String,
    /** `bitmap` (`FNT3` with an `.edds` atlas) or `sdf` (a distance field font). */
    val kind: String,
    /** The size the file was made for, in pixels. */
    val size: Int,
    /** Where it comes from: `game` or the name of the mod folder. */
    val origin: String,
    val glyphs: Int,
    val cyrillic: Boolean,
    val latin: Boolean,
    /** True when a `<family>.xml` (the descriptor of the font generator, with the sizes) lies next to the file. */
    val familyXml: Boolean = false,
) {
    /** The name without the size at its end: the family a layout can name to get the nearest size. */
    val family: String get() = path.trimEnd { it.isDigit() }

    /** The name of the file without its folder. */
    val name: String get() = path.substringAfterLast('/')
}

/** Looks for the fonts of the game and of mods: every `.fnt` that has its atlas next to it. */
object FontCatalog {
    /**
     * The fonts in [dir] and below it. [base] is the folder the paths start from (the game's data folder, or the folder that holds
     * the mod folder), and [origin] names where the fonts are from.
     */
    fun scan(
        dir: File,
        base: File,
        origin: String,
        maxDepth: Int = 12,
    ): List<FontInfo> {
        val found = mutableListOf<FontInfo>()

        fun visit(
            folder: File,
            depth: Int,
        ) {
            val files = runCatching { folder.listFiles() }.getOrNull() ?: return
            val names = files.map { it.name.lowercase() }.toSet()
            for (file in files) {
                if (file.isDirectory) {
                    if (depth < maxDepth && !file.name.startsWith(".")) visit(file, depth + 1)
                } else if (file.extension.equals("fnt", ignoreCase = true)) {
                    val stem = file.nameWithoutExtension.lowercase()
                    if ("$stem.edds" !in names) continue
                    val header = runCatching { GameFontFile.header(file.readBytes()) }.getOrNull() ?: continue
                    val relative = runCatching { file.relativeTo(base).path.replace('\\', '/') }.getOrNull() ?: continue
                    found +=
                        FontInfo(
                            path = relative.removeSuffix(".fnt").removeSuffix(".FNT"),
                            kind = if (header.kind == "FNT5") "sdf" else "bitmap",
                            size = header.size,
                            origin = origin,
                            glyphs = header.ranges.count,
                            cyrillic = header.ranges.has(0x0416),
                            latin = header.ranges.has('A'.code),
                            familyXml = "${stem.trimEnd { it.isDigit() }}.xml" in names,
                        )
                }
            }
        }
        visit(dir, 0)
        return found
    }
}
