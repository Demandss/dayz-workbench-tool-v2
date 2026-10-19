package com.github.demandss.dayz_workbench_tool.layout_editor.state

import com.github.demandss.dayz_workbench_tool.imageset.ImageDef
import com.github.demandss.dayz_workbench_tool.imageset.ImageGroup
import com.github.demandss.dayz_workbench_tool.imageset.ImageSet
import com.github.demandss.dayz_workbench_tool.imageset.ImageSetTexture

/** An image of the set with a stable [id] for the selection and the group it is in, or null outside groups. */
data class ImageEntry(
    val id: Int,
    val group: String?,
    val def: ImageDef,
)

/**
 * The imageset as it is edited: a flat list of images that know their group. Every change makes a new model, which is
 * what the undo history keeps.
 */
data class ImageSetModel(
    val name: String,
    val refWidth: Int,
    val refHeight: Int,
    val textures: List<ImageSetTexture>,
    val extra: Map<String, String>,
    val groups: List<String>,
    val entries: List<ImageEntry>,
    val nextId: Int,
) {
    fun entry(id: Int): ImageEntry? = entries.firstOrNull { it.id == id }

    fun toImageSet(): ImageSet =
        ImageSet(
            name = name,
            refWidth = refWidth,
            refHeight = refHeight,
            textures = textures,
            images = entries.filter { it.group == null }.map { it.def },
            groups = groups.map { g -> ImageGroup(g, entries.filter { it.group == g }.map { it.def }) },
            extra = extra,
        )

    /** Adds an image and returns the new model and the id of the image. */
    fun withAdded(
        def: ImageDef,
        group: String? = null,
    ): Pair<ImageSetModel, Int> {
        val unique = def.copy(name = uniqueImageName(def.name))
        val groupList = if (group != null && group !in groups) groups + group else groups
        return copy(entries = entries + ImageEntry(nextId, group, unique), nextId = nextId + 1, groups = groupList) to nextId
    }

    fun withUpdated(
        id: Int,
        change: (ImageDef) -> ImageDef,
    ): ImageSetModel = copy(entries = entries.map { if (it.id == id) it.copy(def = change(it.def)) else it })

    fun withRemoved(ids: Set<Int>): ImageSetModel = copy(entries = entries.filter { it.id !in ids })

    /** Copies of the images [ids] next to the originals, shifted by [offset] pixels, with free names. Returns the new ids as well. */
    fun withDuplicated(
        ids: Set<Int>,
        offset: Int = 8,
    ): Pair<ImageSetModel, List<Int>> {
        var model = this
        val created = mutableListOf<Int>()
        for (entry in entries.filter { it.id in ids }) {
            val (next, newId) = model.withAdded(entry.def.copy(x = entry.def.x + offset, y = entry.def.y + offset), entry.group)
            model = next
            created += newId
        }
        return model to created
    }

    /** Moves the images into [group], or out of every group when it is null. */
    fun withGroup(
        ids: Set<Int>,
        group: String?,
    ): ImageSetModel {
        val groupList = if (group != null && group !in groups) groups + group else groups
        return copy(entries = entries.map { if (it.id in ids) it.copy(group = group) else it }, groups = groupList)
    }

    /** [name] if no image has it, otherwise `name_2`, `name_3`... */
    fun uniqueImageName(name: String): String {
        val taken = entries.map { it.def.name }.toSet()
        if (name !in taken) return name
        var n = 2
        while ("${name}_$n" in taken) n++
        return "${name}_$n"
    }
}

fun ImageSet.toModel(): ImageSetModel {
    var id = 0
    val entries =
        images.map { ImageEntry(id++, null, it) } +
            groups.flatMap { g -> g.images.map { ImageEntry(id++, g.name, it) } }
    return ImageSetModel(name, refWidth, refHeight, textures, extra, groups.map { it.name }, entries, id)
}

enum class ImageProblemKind { OVERLAP, OUT_OF_BOUNDS, DUPLICATE_NAME, EMPTY, REF_SIZE }

/** A check result. [ids] are the images it is about. */
data class ImageProblem(
    val kind: ImageProblemKind,
    val message: String,
    val ids: List<Int>,
)

/**
 * The checks of the atlas workflow of the team notes: no two images overlap, every image lies inside `RefSize`, names are
 * unique, no image is empty, and `RefSize` has the proportions of the texture ([textureWidth] by [textureHeight], 0 when unknown).
 */
fun checkImageSet(
    model: ImageSetModel,
    textureWidth: Int = 0,
    textureHeight: Int = 0,
): List<ImageProblem> {
    val out = mutableListOf<ImageProblem>()
    val all = model.entries
    for (e in all) {
        val d = e.def
        if (d.width <= 0 || d.height <= 0) {
            out += ImageProblem(ImageProblemKind.EMPTY, "«${d.name}»: размер ${d.width} × ${d.height}", listOf(e.id))
        }
        if (d.x < 0 || d.y < 0 || d.right > model.refWidth || d.bottom > model.refHeight) {
            out +=
                ImageProblem(
                    ImageProblemKind.OUT_OF_BOUNDS,
                    "«${d.name}» выходит за RefSize ${model.refWidth} × ${model.refHeight}",
                    listOf(e.id),
                )
        }
    }
    all.groupBy { it.def.name }.filterValues { it.size > 1 }.forEach { (name, same) ->
        out += ImageProblem(ImageProblemKind.DUPLICATE_NAME, "Имя «$name» встречается ${same.size} раза", same.map { it.id })
    }
    for (i in all.indices) {
        for (j in i + 1 until all.size) {
            val a = all[i].def
            val b = all[j].def
            val overlaps = a.x < b.right && b.x < a.right && a.y < b.bottom && b.y < a.bottom
            if (overlaps && a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0) {
                out += ImageProblem(ImageProblemKind.OVERLAP, "«${a.name}» и «${b.name}» пересекаются", listOf(all[i].id, all[j].id))
            }
        }
    }
    if (textureWidth > 0 && textureHeight > 0 && model.refWidth > 0 && model.refHeight > 0) {
        val textureRatio = textureWidth.toDouble() / textureHeight
        val refRatio = model.refWidth.toDouble() / model.refHeight
        if (kotlin.math.abs(textureRatio - refRatio) > 0.01) {
            out +=
                ImageProblem(
                    ImageProblemKind.REF_SIZE,
                    "RefSize ${model.refWidth} × ${model.refHeight} и текстура $textureWidth × $textureHeight имеют разные пропорции",
                    emptyList(),
                )
        }
    }
    return out
}
