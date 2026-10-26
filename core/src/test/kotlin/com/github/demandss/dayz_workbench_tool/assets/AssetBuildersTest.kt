package com.github.demandss.dayz_workbench_tool.assets

import com.github.demandss.dayz_workbench_tool.imageset.ImageSetFormat
import com.github.demandss.dayz_workbench_tool.styles.StylesFormat
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetBuildersTest {
    private fun picture(
        w: Int,
        h: Int,
    ) = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun packedPicturesDoNotOverlapAndStayInsideAPowerOfTwoAtlas() {
        val pictures = (1..9).map { "icon$it" to picture(20 + it * 7, 16 + it * 5) }
        val packed = ImageSetBuilder.pack(pictures, padding = 2)
        val atlas = packed.atlas
        assertEquals(0, atlas.width and (atlas.width - 1))
        assertEquals(0, atlas.height and (atlas.height - 1))
        for (a in packed.images) {
            assertTrue(a.x >= 0 && a.y >= 0 && a.right <= atlas.width && a.bottom <= atlas.height)
            for (b in packed.images) {
                if (a === b) continue
                val apart = a.right <= b.x || b.right <= a.x || a.bottom <= b.y || b.bottom <= a.y
                assertTrue(apart, "${a.name} overlaps ${b.name}")
            }
        }
    }

    @Test
    fun imagesetTextIsReadBackWithTheSameImages() {
        val packed = ImageSetBuilder.pack(listOf("a" to picture(32, 32), "b" to picture(16, 48)))
        val text = ImageSetFormat.serialize(ImageSetBuilder.imageSet("demo", "Mod\\gui\\demo.edds", packed))
        val back = assertNotNull(ImageSetFormat.parse(text))
        assertEquals("demo", back.name)
        assertEquals(listOf("a", "b"), back.images.map { it.name })
        assertEquals("Mod\\gui\\demo.edds", back.textures.single().path)
    }

    @Test
    fun styleHasEveryStateAndTheNormalSlotsFillTheOthers() {
        val flat = StyleBuilder.SLOTS.associateWith { "WhitePixel" }
        val style = StyleBuilder.style("Flat", "rover_imageset", mapOf("Normal" to flat), "ButtonWidget")
        assertEquals(listOf("Normal", "Highlight", "Pushed", "Disabled", "Focus"), style.states.map { it.name })
        assertTrue(style.states.all { it.items.size == 9 })
        val sheet = StyleBuilder.withStyle(null, "ButtonWidget", style)
        val back = assertNotNull(StylesFormat.parse(StylesFormat.serialize(sheet)))
        assertEquals("Flat", back.stylesFor("ButtonWidget").single().name)
        // The same name is replaced, another one stays
        val two = StyleBuilder.withStyle(sheet, "ButtonWidget", StyleBuilder.style("Other", "x", mapOf("Normal" to flat), "ButtonWidget"))
        val three = StyleBuilder.withStyle(two, "ButtonWidget", style)
        assertEquals(listOf("Other", "Flat"), three.stylesFor("ButtonWidget").map { it.name })
    }

    @Test
    fun maskKeepsTheShapeAndDropsTheColour() {
        val red = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB)
        red.setRGB(0, 0, 0xFFFF0000.toInt())
        red.setRGB(1, 0, 0x00FF0000)
        assertTrue(ImageSetBuilder.hasColor(red))
        val mask = ImageSetBuilder.toMask(red)
        assertEquals(false, ImageSetBuilder.hasColor(mask))
        assertEquals(0xFF, mask.getRGB(0, 0) ushr 24)
        assertEquals(0, mask.getRGB(1, 0) ushr 24)
        val white = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        white.setRGB(0, 0, 0xFFFFFFFF.toInt())
        assertEquals(0xFFFFFFFF.toInt(), ImageSetBuilder.toMask(white).getRGB(0, 0))
    }

    @Test
    fun colourNumbersAreArgb() {
        assertEquals("4294967295", StyleBuilder.colorNumber("1 1 1 1"))
        assertEquals("4278190080", StyleBuilder.colorNumber("#000000"))
        assertEquals("4294901760", StyleBuilder.colorNumber("#FF0000"))
        assertEquals("4278190335", StyleBuilder.colorNumber("0 0 1"))
    }

    private val config =
        "class CfgMods\n{\n\tclass M\n\t{\n\t\tclass defs\n\t\t{\n\t\t\tclass imageSets\n\t\t\t{\n\t\t\t\tvalue = \"\";\n" +
            "\t\t\t\tfiles[]=\n\t\t\t\t{\n\t\t\t\t\t\"M/a.imageset\"\n\t\t\t\t};\n\t\t\t};\n\t\t\tclass gameScriptModule\n\t\t\t{\n" +
            "\t\t\t\tvalue = \"\";\n\t\t\t\tfiles[] = {\"M/scripts\"};\n\t\t\t};\n\t\t};\n\t};\n};\n"

    @Test
    fun configGetsNewFilesInTheExistingListAndANewClass() {
        val added = assertNotNull(ConfigPatcher.addFiles(config, "imageSets", listOf("M/b.imageset", "M/A.imageset")))
        assertEquals(listOf("M/b.imageset"), added.added)
        assertTrue("\"M/a.imageset\",\n\t\t\t\t\t\"M/b.imageset\"" in added.text, added.text)
        val styles = assertNotNull(ConfigPatcher.addFiles(config, "widgetStyles", listOf("M/s.styles")))
        assertTrue("class widgetStyles" in styles.text && "\"M/s.styles\"" in styles.text)
        // Braces still match, so the config stays whole
        assertEquals(styles.text.count { it == '{' }, styles.text.count { it == '}' })
        assertNull(ConfigPatcher.addFiles("class CfgPatches {};", "imageSets", listOf("x")))
    }

    @Test
    fun aConfigWithoutModsGetsAWholeClass() {
        val plain = "class CfgPatches {};" + System.lineSeparator()
        assertTrue(ConfigPatcher.hasNoMods(plain))
        val block = ConfigPatcher.newMods("Demo", listOf("Demo/a.imageset"), listOf("Demo/b.styles"))
        assertEquals(block.count { it == '{' }, block.count { it == '}' })
        assertTrue("class defs" in block && "\"Demo/a.imageset\"" in block && "\"Demo/b.styles\"" in block)
        // What it writes is read by the patcher again: nothing is added twice
        val again = assertNotNull(ConfigPatcher.addFiles(plain + block, "imageSets", listOf("Demo/a.imageset")))
        assertTrue(again.added.isEmpty())
    }
}
