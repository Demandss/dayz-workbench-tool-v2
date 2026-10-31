package com.github.demandss.dayz_workbench_tool.config

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The settings files, in a folder of their own: the tests never touch the settings of the person who runs them. */
class ConfigManagerTest {
    private val base = createTempDirectory("dayz-config")

    private fun manager() = ConfigManager("app", AppConfig.serializer(), ::AppConfig, base)

    @Test
    fun `without a file the default settings are given`() {
        assertEquals(AppConfig(), manager().load())
    }

    @Test
    fun `settings that are saved come back the same`() {
        val config =
            AppConfig(
                setupDone = true,
                gamePath = "game-folder",
                language = "ru",
                clientMods = listOf(ModEntry("mods/A", enabled = false, priority = 3)),
                serverMods = listOf(ModEntry("mods/@B")),
            )
        manager().save(config)
        assertEquals(config, manager().load())
    }

    @Test
    fun `the file lies under the folder of its kind`() {
        manager().save(AppConfig(gamePath = "x"))
        assertTrue(base.resolve("app/settings.json").toFile().isFile)
    }

    @Test
    fun `a file that cannot be read gives the default settings`() {
        base.resolve("app").toFile().mkdirs()
        base.resolve("app/settings.json").writeText("{ this is not json")
        assertEquals(AppConfig(), manager().load())
    }

    @Test
    fun `a key that the program does not know is ignored`() {
        base.resolve("app").toFile().mkdirs()
        base.resolve("app/settings.json").writeText("""{"gamePath":"G","aKeyFromTheFuture":1}""")
        assertEquals("G", manager().load().gamePath)
    }

    @Test
    fun `a value of the file is read by its key, also when the class no longer has it`() {
        val editor = ConfigManager("layout-editor", LayoutEditorConfig.serializer(), ::LayoutEditorConfig, base)
        base.resolve("layout-editor").toFile().mkdirs()
        base.resolve("layout-editor/settings.json").writeText("""{"language":"ru","gridSize":12}""")
        assertEquals("ru", editor.readRawString("language"))
        // A number is not a text
        assertNull(editor.readRawString("gridSize"))
        assertNull(editor.readRawString("nothing"))
        assertNull(manager().readRawString("language"))
    }

    @Test
    fun `a file next to the settings is in the same folder`() {
        assertEquals(base.resolve("app").resolve("mcp.json"), manager().fileIn("mcp.json"))
    }

    @Test
    fun `saving twice keeps the last settings`() {
        manager().save(AppConfig(gamePath = "first"))
        manager().save(AppConfig(gamePath = "second"))
        assertEquals("second", manager().load().gamePath)
        assertTrue("second" in base.resolve("app/settings.json").readText())
    }

    @Test
    fun `the scripts path falls back to the scripts folder of the work drive`() {
        assertEquals("P:\\scripts", AppConfig(workDrivePath = "P:\\work").effectiveScriptsPath())
        assertEquals("D:\\mine", AppConfig(workDrivePath = "P:\\work", scriptsPath = "D:\\mine").effectiveScriptsPath())
    }
}
