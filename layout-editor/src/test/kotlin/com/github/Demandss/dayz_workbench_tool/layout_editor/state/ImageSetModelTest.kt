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
}
