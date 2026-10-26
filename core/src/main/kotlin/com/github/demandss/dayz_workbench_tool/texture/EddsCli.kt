package com.github.demandss.dayz_workbench_tool.texture

import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * Command line front end for the codec, used by the test stand.
 *
 *     edds2png <file.edds> [out.png]
 *     png2edds <file.png> [out.edds]
 *     convert <dir>                          every png in dir (recursive) -> .edds next to it
 *     verify <pngDir> <eddsDir> <outDir>
 */
fun main(args: Array<String>) {
    val code =
        when (args.firstOrNull()) {
            "edds2png" -> {
                val src = File(args.getOrNull(1) ?: usage())
                val dst = args.getOrNull(2)?.let(::File) ?: File(src.parentFile, src.nameWithoutExtension + ".png")
                EddsCodec.edds2png(src, dst)
                println("$src -> $dst")
                0
            }

            "png2edds" -> {
                val src = File(args.getOrNull(1) ?: usage())
                val dst = args.getOrNull(2)?.let(::File) ?: File(src.parentFile, src.nameWithoutExtension + ".edds")
                EddsCodec.png2edds(src, dst)
                println("$src -> $dst")
                0
            }

            "convert" -> {
                val dir = File(args.getOrNull(1) ?: usage())
                for (png in dir.walkTopDown().filter { it.isFile && it.extension == "png" }.sortedBy { it.name }) {
                    println("${png.name} -> ${EddsCodec.png2edds(png).name}")
                }
                0
            }

            "verify" -> {
                verify(File(args.getOrNull(1) ?: usage()), File(args.getOrNull(2) ?: usage()), File(args.getOrNull(3) ?: usage()))
            }

            else -> {
                usage()
            }
        }
    exitProcess(code)
}

private fun usage(): Nothing {
    System.err.println("usage: edds2png <in> [out] | png2edds <in> [out] | convert <dir> | verify <pngDir> <eddsDir> <outDir>")
    exitProcess(2)
}

/** Round trip of every PNG (must be pixel exact) and a decode of every EDDS (must not fail). */
private fun verify(
    pngDir: File,
    eddsDir: File,
    outDir: File,
): Int {
    outDir.mkdirs()
    var failed = 0

    println("== PNG -> EDDS -> pixels (round trip)")
    for (png in pngDir.walkTopDown().filter { it.isFile && it.extension == "png" }.sortedBy { it.name }) {
        val line =
            try {
                val src = ImageIO.read(png)
                val px = IntArray(src.width * src.height).also { src.getRGB(0, 0, src.width, src.height, it, 0, src.width) }
                val back = EddsCodec.decode(EddsCodec.encode(src.width, src.height, px))
                when {
                    back.width != src.width || back.height != src.height -> "FAIL size ${back.width}x${back.height}"
                    !back.pixels.contentEquals(px) -> "FAIL pixels differ"
                    else -> "ok   ${src.width}x${src.height}, ${back.mipCount} mips"
                }
            } catch (e: Exception) {
                "FAIL ${e.message}"
            }
        if (line.startsWith("FAIL")) failed++
        println("  ${png.name.padEnd(28)} $line")
    }

    println("== EDDS -> PNG (decode)")
    for (edds in eddsDir.walkTopDown().filter { it.isFile && it.extension == "edds" }.sortedBy { it.name }) {
        val line =
            try {
                val img = EddsCodec.decode(edds)
                ImageIO.write(img.toBufferedImage(), "png", File(outDir, edds.nameWithoutExtension + ".png"))
                "ok   ${img.width}x${img.height}, ${img.format}, ${img.mipCount} mips"
            } catch (e: Exception) {
                "FAIL ${e.message}"
            }
        if (line.startsWith("FAIL")) failed++
        println("  ${edds.name.padEnd(28)} $line")
    }

    println(if (failed == 0) "ALL OK" else "$failed FAILED")
    return if (failed == 0) 0 else 1
}
