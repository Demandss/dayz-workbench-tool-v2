package com.github.demandss.dayz_workbench_tool.layout_editor.behavior

import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpawnersTest {
    private fun frame(
        name: String,
        scriptClass: String? = null,
        params: Map<String, String>? = null,
        vararg children: EditorWidgetNode,
    ) = EditorWidgetNode(
        UUID.randomUUID(),
        WidgetNode(
            WidgetType.FRAME,
            WidgetType.FRAME.className,
            name,
            linkedMapOf<String, String>().also { if (scriptClass != null) it["scriptclass"] = "\"$scriptClass\"" },
            mutableListOf(),
            params?.let { LinkedHashMap(it) },
        ),
        children.toMutableList(),
    )

    @Test
    fun `every fragment is a layout the parser reads, with only game widgets and styles`() {
        for (spawner in Spawners.all) {
            val text = spawner.fragment(SpawnContext(emptyMap(), emptyMap()))
            val root = LayoutParser.parse(text)
            assertTrue(root.children.isNotEmpty(), spawner.name)

            fun check(node: WidgetNode) {
                node.properties["style"]?.let { assertEquals("Empty", it.trim(), spawner.name) }
                node.children.forEach { check(it) }
            }
            check(root)
        }
    }

    @Test
    fun `a select group builds one item per text and colours the chosen one`() {
        val group = frame("Group", "SelectGroup", mapOf("Items" to "A|B|C|D"))
        val shown = expandSpawners(group, emptyMap())
        val root = shown.children.single()
        assertEquals(8, root.children.size)
        val back0 =
            root.children
                .first { it.node.name == "Back0" }
                .node.properties["color"]
        val back1 =
            root.children
                .first { it.node.name == "Back1" }
                .node.properties["color"]
        assertTrue(back0 != back1)
    }

    @Test
    fun `a click on an item changes the state and the colours follow`() {
        val group = frame("Group", "SelectGroup", mapOf("Items" to "A|B|C"))
        val next = assertNotNull(spawnerClick(group, "Item2", emptyMap()))
        assertEquals("2", next["selected"])
        assertNull(spawnerClick(group, "Item2", next))
        assertNull(spawnerClick(group, "Back1", emptyMap()))
        val shown = expandSpawners(group, mapOf(group.id to next)).children.single()
        val chosen =
            shown.children
                .first { it.node.name == "Back2" }
                .node.properties["color"]
        val other =
            shown.children
                .first { it.node.name == "Back0" }
                .node.properties["color"]
        assertTrue(chosen != other)
    }

    @Test
    fun `ids of the built widgets stay the same when the fragment is built again`() {
        val group = frame("Group", "SelectGroup")
        val first =
            expandSpawners(group, emptyMap())
                .children
                .single()
                .children
                .map { it.id }
        val second =
            expandSpawners(group, mapOf(group.id to mapOf("selected" to "1")))
                .children
                .single()
                .children
                .map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun `a collapse header hides and shows the body it names`() {
        val body = frame("Body")
        val header = frame("Header", "Collapse", mapOf("Body" to "Body", "Title" to "Details"))
        val root = frame("Root", children = arrayOf(header, body))
        assertEquals(true, previewEffects(root, emptySet(), emptySet())[body.id]?.visible)
        val closed = spawnerClick(header, "Header", emptyMap())
        assertEquals(mapOf("open" to "0"), closed)
        assertEquals(false, previewEffects(root, emptySet(), emptySet(), states = mapOf(header.id to closed!!))[body.id]?.visible)
    }

    @Test
    fun `a layout without spawners is returned as it is`() {
        val root = frame("Root", children = arrayOf(frame("A")))
        assertTrue(expandSpawners(root, emptyMap()) === root)
    }
}
