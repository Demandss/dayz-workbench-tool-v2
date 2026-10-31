package com.github.demandss.dayz_workbench_tool.layout_editor.state

import com.github.demandss.dayz_workbench_tool.imageset.ImageDef
import com.github.demandss.dayz_workbench_tool.imageset.ImageSet
import com.github.demandss.dayz_workbench_tool.imageset.ImageSetTexture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageSetModelTest {
    private fun def(
        name: String,
        x: Int,
        y: Int,
        w: Int = 10,
        h: Int = 10,
    ) = ImageDef(name, x, y, w, h)

    private fun model() =
        ImageSet(
            "set",
            100,
            100,
            listOf(ImageSetTexture(0, "a.edds")),
            listOf(def("a", 0, 0), def("b", 20, 0)),
            emptyList(),
        ).toModel()

    @Test
    fun `an imageset goes into the model and comes back the same`() {
        val set =
            ImageSet(
                "set",
                100,
                100,
                listOf(ImageSetTexture(0, "a.edds")),
                listOf(def("a", 0, 0)),
                listOf(
                    com.github.demandss.dayz_workbench_tool.imageset
                        .ImageGroup("g", listOf(def("b", 20, 0))),
                ),
            )
        assertEquals(set, set.toModel().toImageSet())
    }

    @Test
    fun `an added image gets a free name`() {
        val (next, id) = model().withAdded(def("a", 50, 50))
        assertEquals("a_2", next.entry(id)!!.def.name)
    }

    @Test
    fun `duplicates are shifted and named`() {
        val m = model()
        val (next, ids) = m.withDuplicated(setOf(0))
        assertEquals(1, ids.size)
        assertEquals("a_2", next.entry(ids[0])!!.def.name)
        assertEquals(8, next.entry(ids[0])!!.def.x)
    }

    @Test
    fun `removing and moving to a group`() {
        val m = model().withGroup(setOf(1), "Icons")
        assertEquals(listOf("Icons"), m.groups)
        assertEquals("Icons", m.entry(1)!!.group)
        assertEquals(1, m.withRemoved(setOf(0)).entries.size)
    }

    @Test
    fun `overlapping images are found`() {
        val m = model().withUpdated(1) { it.copy(x = 5) }
        val problems = checkImageSet(m)
        assertTrue(problems.any { it.kind == ImageProblemKind.OVERLAP })
    }

    @Test
    fun `touching images do not overlap`() {
        val m = model().withUpdated(1) { it.copy(x = 10) }
        assertTrue(checkImageSet(m).none { it.kind == ImageProblemKind.OVERLAP })
    }

    @Test
    fun `an image outside RefSize is found`() {
        val m = model().withUpdated(0) { it.copy(x = 95) }
        assertTrue(checkImageSet(m).any { it.kind == ImageProblemKind.OUT_OF_BOUNDS })
    }

    @Test
    fun `a repeated name and an empty image are found`() {
        val m = model().withUpdated(1) { it.copy(name = "a", width = 0) }
        val kinds = checkImageSet(m).map { it.kind }
        assertTrue(ImageProblemKind.DUPLICATE_NAME in kinds)
        assertTrue(ImageProblemKind.EMPTY in kinds)
    }

    @Test
    fun `a texture with other proportions is reported`() {
        assertTrue(checkImageSet(model(), 200, 100).any { it.kind == ImageProblemKind.REF_SIZE })
        assertTrue(checkImageSet(model(), 200, 200).none { it.kind == ImageProblemKind.REF_SIZE })
    }

    @Test
    fun `nine quads are made like Workbench makes them`() {
        val big = ImageSet("set", 512, 512, emptyList(), emptyList(), emptyList()).toModel()
        val (model, ids) = big.withNineQuads("Frame")
        assertEquals(9, ids.size)
        assertEquals(listOf("Frame"), model.groups)
        assertEquals(listOf(100, 100, 30, 30), model.entry(ids[0])!!.def.let { listOf(it.x, it.y, it.width, it.height) })
        assertEquals("FrameLeft", model.entry(ids[1])!!.def.name)
        assertEquals(135, model.entry(ids[1])!!.def.y)
        assertEquals("FrameTop", model.entry(ids[3])!!.def.name)
        assertEquals(135, model.entry(ids[3])!!.def.x)
        assertEquals("FrameRightBottom", model.entry(ids[8])!!.def.name)
        assertEquals(170, model.entry(ids[8])!!.def.x)
        assertEquals(170, model.entry(ids[8])!!.def.y)
    }

    @Test
    fun `an image is cut into nine without gaps or overlaps`() {
        val m = ImageSet("set", 100, 100, emptyList(), listOf(def("Btn", 10, 20, 40, 30)), emptyList()).toModel()
        val (cut, ids) = m.withNineSlice(0, 8)!!
        assertEquals(9, ids.size)
        assertEquals(40 * 30, ids.sumOf { cut.entry(it)!!.def.let { d -> d.width * d.height } })
        assertTrue(checkImageSet(cut).none { it.kind == ImageProblemKind.OVERLAP })
        val center = cut.entries.first { it.def.name == "BtnCenter" }.def
        assertEquals(listOf(18, 28, 24, 14), listOf(center.x, center.y, center.width, center.height))
        assertEquals("Btn", cut.entries[0].group)
    }

    @Test
    fun `an image that is too small is not cut`() {
        val m = ImageSet("set", 100, 100, emptyList(), listOf(def("Btn", 0, 0, 10, 10)), emptyList()).toModel()
        assertEquals(null, m.withNineSlice(0, 5))
    }
}
