package com.github.demandss.dayz_workbench_tool.ui.icons

import androidx.compose.ui.graphics.vector.PathParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppIconTest {
    @Test
    fun `every icon has shapes whose path data parses`() {
        // Only the parsing is checked: building a Path needs the native Skia library, which unit tests do not load
        for (icon in AppIcon.entries) {
            assertTrue(icon.shapes.isNotEmpty(), "${icon.name} has no shapes")
            for (shape in icon.shapes) {
                val nodes = PathParser().parsePathString(shape.d).toNodes()
                assertTrue(nodes.size > 1, "${icon.name} has an empty path: ${shape.d}")
            }
        }
    }

    @Test
    fun `icons are chosen by extension`() {
        assertEquals(AppIcon.LAYOUT, fileIcon("menu.layout"))
        assertEquals(AppIcon.LAYOUT, fileIcon("MENU.LAYOUT"))
        assertEquals(AppIcon.EDDS, fileIcon("a.edds"))
        assertEquals(AppIcon.IMAGE, fileIcon("a.png"))
        assertEquals(AppIcon.IMAGE, fileIcon("a.jpeg"))
        assertEquals(AppIcon.IMAGESET, fileIcon("a.imageset"))
        assertEquals(AppIcon.STYLES, fileIcon("a.styles"))
        assertEquals(AppIcon.SCRIPT, fileIcon("MenuScript.c"))
        assertEquals(AppIcon.CONFIG, fileIcon("config.cpp"))
        assertEquals(AppIcon.META, fileIcon("orientation.edds.meta"))
        assertEquals(AppIcon.FILE, fileIcon("notes.txt"))
        assertEquals(AppIcon.FILE, fileIcon("no_extension"))
    }

    @Test
    fun `folders show open or closed`() {
        assertEquals(AppIcon.FOLDER, fileIcon("gui", isDir = true))
        assertEquals(AppIcon.FOLDER_OPEN, fileIcon("gui", isDir = true, expanded = true))
    }
}
