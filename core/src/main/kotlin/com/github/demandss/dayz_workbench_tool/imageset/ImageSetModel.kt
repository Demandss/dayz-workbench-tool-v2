package com.github.demandss.dayz_workbench_tool.imageset

import kotlin.math.roundToInt

/** Bits of the `Flags` of an image: tiling along each axis. */
object ImageFlags {
    const val H_TILE = 1
    const val V_TILE = 2

    /** `3`, `ISVerticalTile` and `ISHorizontalTile|ISVerticalTile` all come back as the bits they mean. */
    fun parse(raw: String): Int {
        raw.trim().toIntOrNull()?.let { return it }
        var bits = 0
        for (part in raw.split('|', ' ', ',').filter { it.isNotBlank() }) {
            bits =
                bits or
                when (part.trim()) {
                    "ISHorizontalTile" -> H_TILE
                    "ISVerticalTile" -> V_TILE
                    else -> part.trim().toIntOrNull() ?: 0
                }
        }
        return bits
    }
}

/** A named rectangle on the texture, in pixels of [ImageSet.refWidth] by [ImageSet.refHeight]. */
data class ImageDef(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    // Written back as it stood in the file while the flags are not edited
    val flagsText: String = "0",
    // What the editor does not know about, kept so that saving loses nothing
    val extra: Map<String, String> = emptyMap(),
) {
    val flags: Int get() = ImageFlags.parse(flagsText)

    fun withFlags(bits: Int) = copy(flagsText = bits.toString())

    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

data class ImageSetTexture(
    val mpix: Int,
    val path: String,
)

data class ImageGroup(
    val name: String,
    val images: List<ImageDef>,
)

data class ImageSet(
    val name: String,
    val refWidth: Int,
    val refHeight: Int,
    val textures: List<ImageSetTexture>,
    val images: List<ImageDef>,
    val groups: List<ImageGroup>,
    val extra: Map<String, String> = emptyMap(),
) {
    /** Every image, the ones outside groups first. */
    val all: List<ImageDef> get() = images + groups.flatMap { it.images }
}

object ImageSetFormat {
    private fun unquote(v: String) = v.trim().removeSurrounding("\"")

    private fun ints(v: String?): List<Int> =
        v
            .orEmpty()
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { it.toFloatOrNull()?.roundToInt() }

    private fun image(n: EnfusionNode): ImageDef {
        val pos = ints(n.props["Pos"])
        val size = ints(n.props["Size"])
        val known = setOf("Name", "Pos", "Size", "Flags")
        return ImageDef(
            name = n.props["Name"]?.let(::unquote) ?: n.instanceName.orEmpty(),
            x = pos.getOrElse(0) { 0 },
            y = pos.getOrElse(1) { 0 },
            width = size.getOrElse(0) { 0 },
            height = size.getOrElse(1) { 0 },
            flagsText = n.props["Flags"]?.trim()?.ifEmpty { null } ?: "0",
            extra = n.props.filterKeys { it !in known },
        )
    }

    /** Reads an `.imageset`. Returns null when the text is not an imageset. */
    fun parse(text: String): ImageSet? {
        val root = EnfusionText.parse(text) ?: return null
        if (root.className != "ImageSetClass") return null
        val ref = ints(root.props["RefSize"])
        val textures =
            root
                .child("Textures")
                ?.childrenOf("ImageSetTextureClass")
                ?.map {
                    ImageSetTexture(it.props["mpix"]?.trim()?.toIntOrNull() ?: 0, unquote(it.props["path"].orEmpty()))
                }.orEmpty()
        val images =
            root
                .child("Images")
                ?.childrenOf("ImageSetDefClass")
                ?.map(::image)
                .orEmpty()
        val groups =
            root
                .child("Groups")
                ?.childrenOf("ImageSetGroupClass")
                ?.map { g ->
                    ImageGroup(
                        g.props["Name"]?.let(::unquote) ?: g.instanceName.orEmpty(),
                        g
                            .child("Images")
                            ?.childrenOf("ImageSetDefClass")
                            ?.map(::image)
                            .orEmpty(),
                    )
                }.orEmpty()
        return ImageSet(
            name = root.props["Name"]?.let(::unquote).orEmpty(),
            refWidth = ref.getOrElse(0) { 0 },
            refHeight = ref.getOrElse(1) { 0 },
            textures = textures,
            images = images,
            groups = groups,
            extra = root.props.filterKeys { it !in setOf("Name", "RefSize") },
        )
    }

    private fun imageNode(i: ImageDef): EnfusionNode {
        val props = LinkedHashMap<String, String>()
        props["Name"] = "\"${i.name}\""
        props["Pos"] = "${i.x} ${i.y}"
        props["Size"] = "${i.width} ${i.height}"
        props["Flags"] = i.flagsText
        props.putAll(i.extra)
        return EnfusionNode("ImageSetDefClass", i.name, props)
    }

    /** The imageset as text, in the layout Workbench writes. */
    fun serialize(set: ImageSet): String {
        val root = EnfusionNode("ImageSetClass")
        root.props["Name"] = "\"${set.name}\""
        root.props["RefSize"] = "${set.refWidth} ${set.refHeight}"
        root.props.putAll(set.extra)
        val textures = EnfusionNode("Textures")
        for (t in set.textures) {
            textures.children +=
                EnfusionNode(
                    "ImageSetTextureClass",
                    null,
                    linkedMapOf("mpix" to t.mpix.toString(), "path" to "\"${t.path}\""),
                )
        }
        root.children += textures
        val images = EnfusionNode("Images")
        set.images.forEach { images.children += imageNode(it) }
        root.children += images
        if (set.groups.isNotEmpty()) {
            val groups = EnfusionNode("Groups")
            for (g in set.groups) {
                val group = EnfusionNode("ImageSetGroupClass", g.name, linkedMapOf("Name" to "\"${g.name}\""))
                val inner = EnfusionNode("Images")
                g.images.forEach { inner.children += imageNode(it) }
                group.children += inner
                groups.children += group
            }
            root.children += groups
        }
        return EnfusionText.serialize(root)
    }
}
