package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.config.ModEntry
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.Modification
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How the programs are started and what the game is given: the command lines, the folders and the junctions of the mods. */
class ManagersTest {
    private val dir = createTempDirectory("dayz-managers").toFile()

    private fun folder(name: String) = File(dir, name).also { it.mkdirs() }

    // Paths of mods that need not exist: under the folder of the test, so that they are nobody's
    private fun mod(name: String) = File(dir, "mods/$name").path

    private fun workshop(name: String) = File(dir, "mods/!Workshop/$name").path

    // ---- the arguments of a process

    @Test
    fun `arguments are split by spaces and a quoted one stays whole`() {
        assertEquals(
            listOf("-window", "-name=My Name", "-port=2302"),
            ProcessManager.parseArgs("""  -window "-name=My Name" -port=2302 """),
        )
        assertEquals(emptyList(), ProcessManager.parseArgs("   "))
    }

    @Test
    fun `the arguments of the game are the program, the settings and the mods`() {
        val command = ProcessManager.buildArgs("DayZDiag_x64.exe", "-window -filePatching", listOf("modA", "@modB"))
        assertEquals(listOf("DayZDiag_x64.exe", "-window", "-filePatching", "-mod=modA;@modB"), command)
        assertEquals(listOf("x.exe", "-window"), ProcessManager.buildArgs("x.exe", "-window", emptyList()))
    }

    @Test
    fun `workbench gets the flags it needs once, and the enabled mods with the workshop ones last`() {
        val config =
            AppConfig(
                workbenchPath = File(dir, "workbench").path,
                workbenchArgs = "-dologs -custom",
                clientMods =
                    listOf(
                        ModEntry(workshop("@Ws")),
                        ModEntry(mod("Off"), enabled = false),
                        ModEntry(mod("Local")),
                    ),
                serverMods = listOf(ModEntry(mod("Server"))),
            )
        val command = ProcessManager.workbenchCommand(config)

        assertTrue(command.first().endsWith("workbenchApp.exe"))
        // -dologs was in the settings: it is not added a second time
        assertEquals(1, command.count { it.equals("-dologs", ignoreCase = true) })
        assertTrue("-custom" in command && "-filePatching" in command && "-resolveFilePatchingUsingEnfusion=1" in command)
        assertEquals("\"-mod=${mod("Local")};${mod("Server")};${workshop("@Ws")}\"", command.last())
    }

    @Test
    fun `a plugin mod that is an existing folder is loaded first`() {
        val plugin = folder("plugin")
        val command =
            ProcessManager.workbenchCommand(
                AppConfig(workbenchPluginPath = plugin.path, clientMods = listOf(ModEntry(mod("A")))),
            )
        assertEquals("\"-mod=${plugin.path};${mod("A")}\"", command.last())

        val missing =
            ProcessManager.workbenchCommand(
                AppConfig(workbenchPluginPath = File(dir, "no-such").path, clientMods = listOf(ModEntry(mod("A")))),
            )
        assertEquals("\"-mod=${mod("A")}\"", missing.last())
    }

    @Test
    fun `workbench without mods gets no mod argument`() {
        assertFalse(ProcessManager.workbenchCommand(AppConfig()).any { it.contains("-mod=") })
    }

    @Test
    fun `workbench starts in the game folder when there is one, and next to itself otherwise`() {
        val game = folder("game")
        assertEquals(game, ProcessManager.workbenchDirectory(AppConfig(gamePath = game.path, workbenchPath = File(dir, "wb").path)))
        val exe = File(game, "DayZDiag_x64.exe").also { it.writeText("") }
        assertEquals(game, ProcessManager.workbenchDirectory(AppConfig(gamePath = exe.path)), "a path to the program is its folder")
        assertEquals(File(dir, "wb"), ProcessManager.workbenchDirectory(AppConfig(gamePath = "", workbenchPath = File(dir, "wb").path)))
        assertEquals(
            File(dir, "wb"),
            ProcessManager.workbenchDirectory(AppConfig(gamePath = File(dir, "no-such").path, workbenchPath = File(dir, "wb").path)),
        )
    }

    // ---- the settings file of the tools

    @Test
    fun `every argument of the game is quoted and a percent sign is doubled`() {
        assertEquals("\"-window\" \"-name=A 100%%\"", ToolsManager.launchArgs("""-window "-name=A 100%"""", splitConnect = false))
    }

    @Test
    fun `a client connection is split into the address and the port, a server one is left`() {
        assertEquals("\"-connect=127.0.0.1\" \"-port=2302\"", ToolsManager.launchArgs("-connect=127.0.0.1:2302", splitConnect = true))
        assertEquals("\"-connect=127.0.0.1:2302\"", ToolsManager.launchArgs("-connect=127.0.0.1:2302", splitConnect = false))
        // A port that is given is not guessed again, and an address without a port is not split
        assertEquals(
            "\"-connect=host:1\" \"-port=2\"",
            ToolsManager.launchArgs("-connect=host:1 -port=2", splitConnect = true),
        )
        assertEquals("\"-connect=host\"", ToolsManager.launchArgs("-connect=host", splitConnect = true))
    }

    @Test
    fun `file patching asks for the resolve flag unless it is there`() {
        assertEquals(
            "\"-filePatching\" \"-resolveFilePatchingUsingEnfusion=1\"",
            ToolsManager.launchArgs("-filePatching", splitConnect = false),
        )
        assertEquals(
            "\"-filePatching\" \"-resolveFilePatchingUsingEnfusion=0\"",
            ToolsManager.launchArgs("-filePatching -resolveFilePatchingUsingEnfusion=0", splitConnect = false),
        )
        assertEquals("\"-window\"", ToolsManager.launchArgs("-window", splitConnect = false))
    }

    @Test
    fun `the settings of the tools hold the paths and the enabled mods of each side`() {
        val before = ToolsManager.toolsDir
        ToolsManager.toolsDir = folder("tools").toPath()
        try {
            val mods =
                listOf(
                    Modification(mod("C1"), ModSide.CLIENT),
                    Modification(mod("C2"), ModSide.CLIENT, enabled = false),
                    Modification(mod("S1"), ModSide.SERVER),
                )
            ToolsManager.writeUserSettings(AppConfig(gamePath = File(dir, "game").path, workDrivePath = "P:"), mods)
            val text = File(ToolsManager.toolsDir.toFile(), "UserSettings.bat").readText()
            assertTrue("GAME_PATH=${File(dir, "game").path}\"" in text && "WORKDRIVE_PATH=P:\"" in text, text)
            assertTrue("MODS_CLIENT=${mod("C1")}\"" in text, text)
            assertTrue("MODS_SERVER=${mod("S1")}\"" in text, text)
            assertFalse("C2" in text)
            assertFalse("&" in text.replace("&&", ""), "every blank of the template is filled in")
        } finally {
            ToolsManager.toolsDir = before
        }
    }

    // ---- the junctions of the mods in the game folder

    private fun onWindows() = assumeTrue(System.getProperty("os.name").startsWith("Windows"))

    private fun isLink(path: File) = Files.exists(path.toPath(), LinkOption.NOFOLLOW_LINKS)

    @Test
    fun `a mod gets a junction in the game folder, once, and loses it without losing its files`() {
        onWindows()
        val game = folder("game")
        val mod = folder("MyMod").also { File(it, "config.cpp").writeText("class CfgPatches {};") }

        ModificationManager.createSymlink(mod.toPath(), game.toPath())
        val link = File(game, "MyMod")
        assertTrue(isLink(link))
        assertEquals("class CfgPatches {};", File(link, "config.cpp").readText(), "the junction shows the files of the mod")
        ModificationManager.createSymlink(mod.toPath(), game.toPath())
        assertTrue(isLink(link), "a second call changes nothing")

        ModificationManager.removeSymlink(mod.toPath(), game.toPath())
        assertFalse(isLink(link))
        assertTrue(File(mod, "config.cpp").isFile, "the mod itself is not touched")
    }

    @Test
    fun `all the enabled mods get a junction, and a blank game folder is left alone`() {
        onWindows()
        val game = folder("game2")
        val a = folder("ModA")
        val b = folder("ModB")
        val off = folder("ModOff")
        val mods =
            listOf(
                Modification(a.path, ModSide.CLIENT, priority = 2),
                Modification(b.path, ModSide.CLIENT, priority = 1),
                Modification(off.path, ModSide.CLIENT, enabled = false),
            )
        ModificationManager.createAll(mods, "")
        assertFalse(isLink(File(game, "ModA")))

        ModificationManager.createAll(mods, game.path)
        assertTrue(isLink(File(game, "ModA")) && isLink(File(game, "ModB")))
        assertFalse(isLink(File(game, "ModOff")))

        ModificationManager.removeAll(mods, game.path)
        assertFalse(isLink(File(game, "ModA")) || isLink(File(game, "ModB")))
        assertTrue(a.isDirectory && b.isDirectory)
    }

    @Test
    fun `the game folder may be given as the path of the program in it`() {
        onWindows()
        val game = folder("game3")
        val exe = File(game, "DayZDiag_x64.exe").also { it.writeText("") }
        val mod = folder("ModC")
        ModificationManager.createAll(listOf(Modification(mod.path, ModSide.CLIENT)), exe.path)
        assertTrue(isLink(File(game, "ModC")))
        ModificationManager.removeAll(listOf(Modification(mod.path, ModSide.CLIENT)), exe.path)
        assertFalse(isLink(File(game, "ModC")))
    }
}
