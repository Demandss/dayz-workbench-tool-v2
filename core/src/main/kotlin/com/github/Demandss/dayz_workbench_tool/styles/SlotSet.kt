package com.github.demandss.dayz_workbench_tool.styles

/** A set of images that together fill the slots of a style: [images] maps a slot (`LeftTop`, `Center`...) to an image name. */
data class SlotSet(
    val label: String,
    val images: Map<String, String>,
)

// Other ways the same corner is named: `editorQuadTopLeft` is the `LeftTop` slot
private val SLOT_SUFFIXES: Map<String, List<String>> =
    mapOf(
        "LeftTop" to listOf("LeftTop", "TopLeft"),
        "RightTop" to listOf("RightTop", "TopRight"),
        "LeftBottom" to listOf("LeftBottom", "BottomLeft"),
        "RightBottom" to listOf("RightBottom", "BottomRight"),
        "Top" to listOf("Top", "TopCenter"),
        "Bottom" to listOf("Bottom", "BottomCenter"),
        "Left" to listOf("Left", "LeftCenter"),
        "Right" to listOf("Right", "RightCenter"),
        "Center" to listOf("Center"),
    )

private const val MIN_SLOTS = 3

/**
 * Finds the sets of images in an imageset that are named after the slots of a style, such as `FrameLeftTop`, `FrameTop`,
 * `FrameCenter`. Every common prefix with at least three slots is a set; the images of a group are looked at on their own, with
 * the group name as the prefix, so that a group of plain `Left`, `Top`, `Center` images works too.
 * [groups] maps a group name to the names of its images.
 */
fun findSlotSets(
    imageNames: List<String>,
    groups: Map<String, List<String>> = emptyMap(),
): List<SlotSet> {
    val out = ArrayList<SlotSet>()
    val seen = HashSet<Map<String, String>>()

    fun add(
        label: String,
        names: List<String>,
        prefixOf: (String) -> String?,
    ) {
        val byPrefix = LinkedHashMap<String, LinkedHashMap<String, String>>()
        for (name in names) {
            // The longest ending wins: `FrameLeftTop` is the `LeftTop` of `Frame`, not the `Top` of `FrameLeft`
            val match =
                SLOT_SUFFIXES
                    .flatMap { (slot, suffixes) -> suffixes.map { slot to it } }
                    .filter { name.endsWith(it.second, ignoreCase = true) }
                    .maxByOrNull { it.second.length } ?: continue
            val prefix = prefixOf(name.dropLast(match.second.length)) ?: continue
            byPrefix.getOrPut(prefix) { LinkedHashMap() }.putIfAbsent(match.first, name)
        }
        for ((prefix, slots) in byPrefix) {
            if (slots.size < MIN_SLOTS || !seen.add(slots)) continue
            // A group is named once; the prefix is added only when it says something the group name does not
            val shown =
                if (label.isEmpty() || prefix.isEmpty() ||
                    prefix == label.removePrefix("Группа ")
                ) {
                    label.ifEmpty { prefix }
                } else {
                    "$label · $prefix"
                }
            out += SlotSet(shown, slots)
        }
    }

    for ((group, names) in groups) add("Группа $group", names) { it }
    add("", imageNames) { it.takeIf { prefix -> prefix.isNotEmpty() } }
    return out
}
