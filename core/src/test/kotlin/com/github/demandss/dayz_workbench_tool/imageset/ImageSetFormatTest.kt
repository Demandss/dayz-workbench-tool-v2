package com.github.demandss.dayz_workbench_tool.imageset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageSetFormatTest {
    private val workbench =
        """
        ImageSetClass {
         Name "dayz_gui"
         RefSize 1024 1024
         Textures {
          ImageSetTextureClass {
           mpix 0
           path "{534691EE0479871C}Gui/imagesets/dayz_gui.edds"
          }
          ImageSetTextureClass {
           mpix 1
           path "{C139E49FD0ECAF9E}Gui/imagesets/dayz_gui@2x.edds"
          }
         }
         Images {
          ImageSetDefClass Gradient {
           Name "Gradient"
           Pos 0 317
           Size 75 5
           Flags ISVerticalTile
          }
          ImageSetDefClass Expand {
           Name "Expand"
           Pos 121 257
           Size 20 20
           Flags 0
          }
         }
         Groups {
          ImageSetGroupClass Checkbox {
           Name "Checkbox"
           Images {
            ImageSetDefClass CheckboxHover {
             Name "CheckboxHover"
             Pos 35 102
             Size 26 26
             Flags 3
            }
           }
          }
         }
        }
        """.trimIndent()

    private val dabs =
        "ImageSetClass  {\n\tName \"solid\"\n\tRefSize 3648 3712\n\tTextures {\n\t\tImageSetTextureClass  {\n\t\t\tmpix 1\n" +
            "\t\t\tpath \"DabsFramework\\GUI\\icons\\solid.edds\"\n\t\t}\n\t}\n\tImages {\n\t\tImageSetDefClass 0 {\n\t\t\tName \"0\"\n" +
            "\t\t\tPos 0 0\n\t\t\tSize 64 64\n\t\t\tFlags 0\n\t\t}\n\t}\n}\n"

    @Test
    fun `a Workbench imageset is read`() {
        val set = assertNotNull(ImageSetFormat.parse(workbench))
        assertEquals("dayz_gui", set.name)
        assertEquals(1024, set.refWidth)
        assertEquals(2, set.textures.size)
        assertEquals("{C139E49FD0ECAF9E}Gui/imagesets/dayz_gui@2x.edds", set.textures[1].path)
        assertEquals(listOf("Gradient", "Expand", "CheckboxHover"), set.all.map { it.name })
        val gradient = set.images[0]
        assertEquals(0, gradient.x)
        assertEquals(317, gradient.y)
        assertEquals(ImageFlags.V_TILE, gradient.flags)
        assertEquals(3, set.groups[0].images[0].flags)
    }

    @Test
    fun `an imageset with tabs, numeric names and backslashes is read`() {
        val set = assertNotNull(ImageSetFormat.parse(dabs))
        assertEquals("solid", set.name)
        assertEquals(3712, set.refHeight)
        assertEquals("0", set.images.single().name)
        assertEquals("DabsFramework\\GUI\\icons\\solid.edds", set.textures.single().path)
    }

    @Test
    fun `writing and reading again gives the same imageset`() {
        val set = assertNotNull(ImageSetFormat.parse(workbench))
        val again = assertNotNull(ImageSetFormat.parse(ImageSetFormat.serialize(set)))
        assertEquals(set, again)
    }

    @Test
    fun `the flags text stays as it was until it is edited`() {
        val set = assertNotNull(ImageSetFormat.parse(workbench))
        assertEquals("ISVerticalTile", set.images[0].flagsText)
        val text = ImageSetFormat.serialize(set)
        assertEquals(true, text.contains("Flags ISVerticalTile"))
        assertEquals("3", set.images[0].withFlags(3).flagsText)
    }

    @Test
    fun `flags in every spelling are the same bits`() {
        assertEquals(3, ImageFlags.parse("ISHorizontalTile|ISVerticalTile"))
        assertEquals(1, ImageFlags.parse("ISHorizontalTile"))
        assertEquals(0, ImageFlags.parse("0"))
    }

    @Test
    fun `text that is not an imageset is refused`() {
        assertNull(ImageSetFormat.parse("FrameWidgetClass Root {\n}\n"))
        assertNull(ImageSetFormat.parse("ImageSetClass {\n Name \"x\"\n"))
        assertNull(ImageSetFormat.parse(""))
    }

    @Test
    fun `unknown properties are kept`() {
        val text =
            "ImageSetClass {\n Name \"a\"\n RefSize 8 8\n Custom 5\n Images {\n" +
                "  ImageSetDefClass b {\n   Name \"b\"\n   Pos 0 0\n   Size 1 1\n   Flags 0\n   Note hi\n  }\n }\n}\n"
        val set = assertNotNull(ImageSetFormat.parse(text))
        val out = ImageSetFormat.serialize(set)
        assertEquals(true, out.contains("Custom 5"))
        assertEquals(true, out.contains("Note hi"))
    }
}
