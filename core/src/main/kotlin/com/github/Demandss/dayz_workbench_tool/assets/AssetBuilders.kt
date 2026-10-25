package com.github.demandss.dayz_workbench_tool.assets

import com.github.demandss.dayz_workbench_tool.imageset.ImageDef
import com.github.demandss.dayz_workbench_tool.imageset.ImageSet
import com.github.demandss.dayz_workbench_tool.imageset.ImageSetTexture
import com.github.demandss.dayz_workbench_tool.styles.StyleDef
import com.github.demandss.dayz_workbench_tool.styles.StyleItem
import com.github.demandss.dayz_workbench_tool.styles.StyleSheet
import com.github.demandss.dayz_workbench_tool.styles.StyleState
import com.github.demandss.dayz_workbench_tool.styles.WidgetStyles
import java.awt.image.BufferedImage

/** Packs separate pictures into one atlas and describes them as an imageset. */
object ImageSetBuilder {
    class Packed(
        val atlas: BufferedImage,
        val images: List<ImageDef>,
    )

    private fun powerOfTwo(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    /**
     * Puts the pictures in rows (the highest first) at most [maxSize] wide, [padding] pixels apart, on a transparent atlas whose
     * sides are powers of two. Fails when they do not fit in [maxSize] by [maxSize].
     */
    fun pack(
        pictures: List<Pair<String, BufferedImage>>,
        padding: Int = 2,
        maxSize: Int = 4096,
    ): Packed {
        require(pictures.isNotEmpty()) { "no pictures" }
        require(pictures.map { it.first }.distinct().size == pictures.size) { "names of the pictures repeat" }
        val sorted = pictures.sortedWith(compareByDescending<Pair<String, BufferedImage>> { it.second.height }.thenBy { it.first })
        val widest = sorted.maxOf { it.second.width } + padding * 2
        require(widest <= maxSize) { "a picture is wider than the atlas allows ($maxSize)" }
        // The width: the smallest power of two that is not much narrower than the square root of the area
        val area = sorted.sumOf { (it.second.width + padding).toLong() * (it.second.height + padding) }
        val width = powerOfTwo(maxOf(widest, Math.sqrt(area.toDouble()).toInt())).coerceAtMost(maxSize)
        val positions = LinkedHashMap<String, Pair<Int, Int>>()
        var x = padding
        var y = padding
        var rowHeight = 0
        for ((name, picture) in sorted) {
            if (x + picture.width + padding > width) {
                x = padding
                y += rowHeight + padding
                rowHeight = 0
            }
            positions[name] = x to y
            x += picture.width + padding
            rowHeight = maxOf(rowHeight, picture.height)
        }
        val height = powerOfTwo(y + rowHeight + padding)
        require(height <= maxSize) { "the pictures do not fit in an atlas of $maxSize by $maxSize" }
        val atlas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = atlas.createGraphics()
        for ((name, picture) in sorted) {
            val (px, py) = positions.getValue(name)
            g.drawImage(picture, px, py, null)
        }
        g.dispose()
        val defs =
            pictures.map { (name, picture) ->
                val (px, py) = positions.getValue(name)
                ImageDef(name, px, py, picture.width, picture.height)
            }
        return Packed(atlas, defs)
    }

    /** The imageset of a packed atlas; [texturePath] is the path of the `.edds` as the game reads it, from the mod folder. */
    fun imageSet(
        name: String,
        texturePath: String,
        packed: Packed,
    ) = ImageSet(
        name = name,
        refWidth = packed.atlas.width,
        refHeight = packed.atlas.height,
        textures = listOf(ImageSetTexture(1, texturePath)),
        images = packed.images,
        groups = emptyList(),
    )
}

/** Builds the styles of widgets: the nine slots of every state, each from an image of an imageset. */
object StyleBuilder {
    /** The slots of a style, as the game names them. */
    val SLOTS = listOf("LeftTop", "Top", "RightTop", "Right", "RightBottom", "Bottom", "LeftBottom", "Left", "Center")

    /** The states of a style of a widget type: a button has five, other widgets two. */
    fun defaultStates(widget: String): List<String> =
        if (widget == "ButtonWidget") listOf("Normal", "Highlight", "Pushed", "Disabled", "Focus") else listOf("Normal", "Disabled")

    /**
     * A style whose every state is the [slots] given for it, or the slots of `Normal` when it has none.
     */
    fun style(
        name: String,
        imageSet: String,
        slots: Map<String, Map<String, String>>,
        widget: String,
        font: String = "",
        color: String = "4294967295",
    ): StyleDef {
        val normal = slots["Normal"] ?: slots.values.firstOrNull() ?: error("no images for the style")
        val states =
            (defaultStates(widget) + slots.keys)
                .distinct()
                .map { state ->
                    val map = slots[state] ?: normal
                    StyleState(state, SLOTS.mapNotNull { slot -> map[slot]?.let { StyleItem(slot, it) } })
                }
        return StyleDef(name, font, imageSet, color, states)
    }

    /** Puts [style] into [sheet] under [widget], replacing a style of the same name. The other styles stay as they are. */
    fun withStyle(
        sheet: StyleSheet?,
        widget: String,
        style: StyleDef,
    ): StyleSheet {
        val widgets = sheet?.widgets.orEmpty()
        val group = widgets.firstOrNull { it.widget == widget }
        val updated =
            if (group == null) {
                widgets + WidgetStyles(widget, listOf(style))
            } else {
                widgets.map {
                    if (it !== group) it else WidgetStyles(widget, it.styles.filterNot { s -> s.name == style.name } + style)
                }
            }
        return StyleSheet(updated)
    }

    /** The colour of a style as the number the file writes: `r g b a` from 0 to 1, `#RRGGBB` / `#AARRGGBB`, or the number itself. */
    fun colorNumber(text: String): String {
        val t = text.trim()
        t.toLongOrNull()?.let { return it.toString() }
        if (t.startsWith("#")) {
            val hex = t.removePrefix("#")
            val value = hex.toLongOrNull(16) ?: error("not a colour: $text")
            return (if (hex.length <= 6) value or 0xFF000000L else value).toString()
        }
        val parts = t.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
        require(parts.size in 3..4) { "not a colour: $text" }

        fun channel(v: Double) = (v.coerceIn(0.0, 1.0) * 255 + 0.5).toLong()
        val a = if (parts.size == 4) channel(parts[3]) else 255L
        return ((a shl 24) or (channel(parts[0]) shl 16) or (channel(parts[1]) shl 8) or channel(parts[2])).toString()
    }
}

/** Adds the files of a mod to `config.cpp`: the `imageSets` and `widgetStyles` lists of `CfgMods > <mod> > defs`. */
object ConfigPatcher {
    class Outcome(
        val text: String,
        val added: List<String>,
    )

    private fun matchingBrace(
        text: String,
        open: Int,
    ): Int {
        var depth = 0
        var i = open
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (c == '"') inString = !inString
            if (!inString) {
                if (c == '{') depth++
                if (c == '}') {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** True when the config has no `CfgMods` at all, so that a whole one can be added. */
    fun hasNoMods(config: String) = !Regex("class\\s+CfgMods\\b").containsMatchIn(config)

    /** A `CfgMods` class for [mod] with the given imagesets and styles, to append to a config that has none. */
    fun newMods(
        mod: String,
        imageSets: List<String>,
        styles: List<String>,
        eol: String = "\n",
    ): String {
        fun block(
            kind: String,
            paths: List<String>,
        ): String {
            if (paths.isEmpty()) return ""
            val list = paths.joinToString(",$eol") { "\t\t\t\t\t\"$it\"" }
            return "\t\t\tclass $kind$eol\t\t\t{$eol\t\t\t\tvalue = \"\";$eol\t\t\t\tfiles[] =$eol\t\t\t\t{$eol" +
                "$list$eol\t\t\t\t};$eol\t\t\t};$eol"
        }
        return "${eol}class CfgMods$eol{$eol\tclass $mod$eol\t{$eol\t\ttype = \"mod\";$eol" +
            "\t\tdependencies[] = {\"Game\", \"World\", \"Mission\"};$eol\t\tclass defs$eol\t\t{$eol" +
            block("imageSets", imageSets) + block("widgetStyles", styles) + "\t\t};$eol\t};$eol};$eol"
    }

    /**
     * Adds [paths] to the `files[]` of class [kind] (`imageSets` or `widgetStyles`) inside `class defs`. Returns null when the
     * config has no `defs` class to put them in; a class [kind] that is missing is made. Paths that are in the list are skipped.
     */
    fun addFiles(
        config: String,
        kind: String,
        paths: List<String>,
    ): Outcome? {
        val defs = Regex("class\\s+defs\\s*\\{").find(config) ?: return null
        val defsOpen = defs.range.last
        val defsClose = matchingBrace(config, defsOpen)
        if (defsClose < 0) return null
        val body = config.substring(defsOpen, defsClose + 1)
        val eol = if ("\r\n" in config) "\r\n" else "\n"
        val kindMatch = Regex("class\\s+$kind\\s*\\{").find(body)
        if (kindMatch == null) {
            val list = paths.joinToString(",$eol") { "\t\t\t\t\t\"$it\"" }
            val block =
                "\t\t\tclass $kind$eol\t\t\t{$eol\t\t\t\tvalue = \"\";$eol\t\t\t\tfiles[] =$eol\t\t\t\t{$eol$list$eol\t\t\t\t};$eol\t\t\t};$eol\t\t"
            return Outcome(config.substring(0, defsClose).trimEnd('\t', ' ') + block + config.substring(defsClose), paths)
        }
        val classOpen = defsOpen + kindMatch.range.last
        val classClose = matchingBrace(config, classOpen)
        if (classClose < 0) return null
        val classBody = config.substring(classOpen, classClose)
        val files = Regex("files\\s*\\[\\s*]\\s*=\\s*\\{").find(classBody)
        val have = Regex("\"([^\"]*)\"").findAll(classBody).map { it.groupValues[1].lowercase().replace('\\', '/') }.toSet()
        val fresh = paths.filter { it.lowercase().replace('\\', '/') !in have }
        if (fresh.isEmpty()) return Outcome(config, emptyList())
        if (files == null) {
            val list = fresh.joinToString(",$eol") { "\t\t\t\t\t\"$it\"" }
            val insert = "${eol}\t\t\t\tfiles[] =$eol\t\t\t\t{$eol$list$eol\t\t\t\t};$eol\t\t\t"
            return Outcome(config.substring(0, classClose) + insert + config.substring(classClose), fresh)
        }
        val filesOpen = classOpen + files.range.last
        val filesClose = matchingBrace(config, filesOpen)
        if (filesClose < 0) return null
        val inside = config.substring(filesOpen + 1, filesClose)
        val trimmed = inside.trimEnd()
        val needsComma = trimmed.isNotEmpty() && !trimmed.endsWith(",")
        val tail = inside.substring(trimmed.length)
        val added = fresh.joinToString(",$eol") { "\t\t\t\t\t\"$it\"" }
        val rebuilt =
            if (trimmed.isEmpty()) {
                "$eol$added$eol\t\t\t\t"
            } else {
                trimmed + (if (needsComma) "," else "") + eol + added + tail
            }
        return Outcome(config.substring(0, filesOpen + 1) + rebuilt + config.substring(filesClose), fresh)
    }
}
