package com.github.demandss.dayz_workbench_tool.layout

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewFileKindTest {
    private val dir = createTempDirectory("newfile")

    @Test
    fun `extension is added once`() {
        assertEquals("a.layout", NewFileKind.LAYOUT.fileName("a"))
        assertEquals("a.layout", NewFileKind.LAYOUT.fileName("a.layout"))
        assertEquals("a.LAYOUT", NewFileKind.LAYOUT.fileName("a.LAYOUT"))
        assertEquals("a.imageset", NewFileKind.IMAGESET.fileName("a"))
        assertEquals("a.styles", NewFileKind.STYLES.fileName("a"))
        assertEquals("plain", NewFileKind.FOLDER.fileName("plain"))
    }

    @Test
    fun `a new layout can be parsed back`() {
        val path = assertNotNull(NewFileKind.LAYOUT.create(dir, "menu"))

        val root = LayoutParser.parse(path.readText())

        assertEquals(WidgetType.FRAME, root.type)
        assertEquals("RootFrame", root.name)
    }

    @Test
    fun `an imageset names itself after its file`() {
        val path = assertNotNull(NewFileKind.IMAGESET.create(dir, "icons"))

        val text = path.readText()

        assertEquals("icons.imageset", path.fileName.toString())
        assertTrue(text.contains("Name \"icons\""), text)
        assertTrue(text.startsWith("ImageSetClass {"), text)
    }

    @Test
    fun `styles start as an empty document`() {
        val path = assertNotNull(NewFileKind.STYLES.create(dir, "mine"))

        assertEquals("<WidgetStyles>\n</WidgetStyles>\n", path.readText())
    }

    @Test
    fun `a folder is created and missing parents too`() {
        val path = assertNotNull(NewFileKind.FOLDER.create(dir.resolve("deep/er"), "leaf"))

        assertTrue(Files.isDirectory(path))
    }

    @Test
    fun `an existing file is not overwritten`() {
        val first = assertNotNull(NewFileKind.LAYOUT.create(dir, "keep"))
        first.writeText("my content")

        val again = assertNotNull(NewFileKind.LAYOUT.create(dir, "keep"))

        assertEquals(first, again)
        assertEquals("my content", first.readText())
    }

    @Test
    fun `names that leave the folder are refused`() {
        for (bad in listOf("", "   ", "..", ".", "../x", "a/b", "a\\b", "c:x", "a*b")) {
            assertNull(NewFileKind.LAYOUT.create(dir, bad), "'$bad' must be refused")
        }
    }
}
