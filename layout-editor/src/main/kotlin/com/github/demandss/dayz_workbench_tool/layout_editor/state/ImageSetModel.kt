package com.github.demandss.dayz_workbench_tool.layout_editor.state

import com.github.demandss.dayz_workbench_tool.i18n.str
import com.github.demandss.dayz_workbench_tool.imageset.ImageDef
import com.github.demandss.dayz_workbench_tool.imageset.ImageGroup
import com.github.demandss.dayz_workbench_tool.imageset.ImageSet
import com.github.demandss.dayz_workbench_tool.imageset.ImageSetTexture
import com.github.demandss.dayz_workbench_tool.resources.*

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

    /**
     * What Workbench makes with "Add 3x3": a group named [base] with nine 30 x 30 images named after it and a slot
     * (`LeftTop`, `Left`, `LeftBottom`, `Top`, `Center`, `Bottom`, `RightTop`, `Right`, `RightBottom`), five pixels apart,
     * going down each column. Measured in Workbench 1.30. The block starts at 100, 100 as long as it fits.
     */
    fun withNineQuads(base: String): Pair<ImageSetModel, List<Int>> {
        val startX = if (refWidth >= 200) 100 else 0
        val startY = if (refHeight >= 200) 100 else (refHeight - 100).coerceAtLeast(0)
        var model = this
        val ids = mutableListOf<Int>()
        NINE_SLOTS.forEachIndexed { index, slot ->
            val column = index / 3
            val row = index % 3
            val (next, id) = model.withAdded(ImageDef(base + slot, startX + column * 35, startY + row * 35, 30, 30), base)
            model = next
            ids += id
        }
        return model to ids
    }

    /**
     * Cuts the image [id] into nine for a 9-slice with corners of [corner] pixels: four corners, four edges and the centre,
     * in a group named after the image. The image itself is replaced. Returns null when the image is too small.
     */
    fun withNineSlice(
        id: Int,
        corner: Int,
    ): Pair<ImageSetModel, List<Int>>? {
        val entry = entry(id) ?: return null
        val d = entry.def
        if (corner < 1 || d.width < corner * 2 + 1 || d.height < corner * 2 + 1) return null
        val midW = d.width - corner * 2
        val midH = d.height - corner * 2
        val xs = listOf(d.x, d.x + corner, d.x + corner + midW)
        val ws = listOf(corner, midW, corner)
        val ys = listOf(d.y, d.y + corner, d.y + corner + midH)
        val hs = listOf(corner, midH, corner)
        var model = withRemoved(setOf(id))
        val ids = mutableListOf<Int>()
        // column, row of each slot in the order of NINE_SLOTS
        val cells = listOf(0 to 0, 0 to 1, 0 to 2, 1 to 0, 1 to 1, 1 to 2, 2 to 0, 2 to 1, 2 to 2)
        NINE_SLOTS.forEachIndexed { index, slot ->
            val (c, r) = cells[index]
            val (next, newId) = model.withAdded(ImageDef(d.name + slot, xs[c], ys[r], ws[c], hs[r], d.flagsText), d.name)
            model = next
            ids += newId
        }
        return model to ids
    }

    /** Moves the images into [group], or out of every group when it is null. */
    fun withGroup(
        ids: Set<Int>,
        group: String?,
    ): ImageSetModel {
        val groupList = if (group != null && group !in groups) groups + group else groups
        return copy(entries = entries.map { if (it.id in ids) it.copy(group = group) else it }, groups = groupList)
    }

    /** [name] if no group has it, otherwise `name_2`, `name_3`... */
    fun uniqueGroupName(name: String): String {
        if (name !in groups) return name
        var n = 2
        while ("${name}_$n" in groups) n++
        return "${name}_$n"
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

/** The slots of a 9-slice in the order Workbench makes them, a column after a column. */
val NINE_SLOTS = listOf("LeftTop", "Left", "LeftBottom", "Top", "Center", "Bottom", "RightTop", "Right", "RightBottom")

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
            out += ImageProblem(ImageProblemKind.EMPTY, str(Res.string.imageset_problem_empty, d.name, d.width, d.height), listOf(e.id))
        }
        if (d.x < 0 || d.y < 0 || d.right > model.refWidth || d.bottom > model.refHeight) {
            out +=
                ImageProblem(
                    ImageProblemKind.OUT_OF_BOUNDS,
                    str(Res.string.imageset_problem_out_of_bounds, d.name, model.refWidth, model.refHeight),
                    listOf(e.id),
                )
        }
    }
    all.groupBy { it.def.name }.filterValues { it.size > 1 }.forEach { (name, same) ->
        out +=
            ImageProblem(ImageProblemKind.DUPLICATE_NAME, str(Res.string.imageset_problem_duplicate, name, same.size), same.map { it.id })
    }
    // Sweep along x: a region is only compared with the ones that start before it ends
    val byX = all.sortedBy { it.def.x }
    for (i in byX.indices) {
        val a = byX[i].def
        if (a.width <= 0 || a.height <= 0) continue
        for (j in i + 1 until byX.size) {
            val b = byX[j].def
            if (b.x >= a.right) break
            if (b.width > 0 && b.height > 0 && a.y < b.bottom && b.y < a.bottom) {
                out +=
                    ImageProblem(
                        ImageProblemKind.OVERLAP,
                        str(Res.string.imageset_problem_overlap, a.name, b.name),
                        listOf(byX[i].id, byX[j].id),
                    )
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
                    str(Res.string.imageset_problem_ref_ratio, model.refWidth, model.refHeight, textureWidth, textureHeight),
                    emptyList(),
                )
        }
    }
    return out
}
