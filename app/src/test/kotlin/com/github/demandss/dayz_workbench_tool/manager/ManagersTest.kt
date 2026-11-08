package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.config.ModEntry
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.Modification
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
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
    fun `workbench uses prepared mounts instead of rereading the raw source paths`() {
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
        val workspace = File(dir, ".Workbench").path
        val command = ProcessManager.workbenchCommand(config, PreparedWorkbench(listOf(workspace, workshop("@Ws"))))

        assertTrue(command.first().endsWith("workbenchApp.exe"))
        // -dologs was in the settings: it is not added a second time
        assertEquals(1, command.count { it.equals("-dologs", ignoreCase = true) })
        assertTrue("-custom" in command && "-filePatching" in command && "-loadRVFilesViaEnfusionFS=1" in command)
        assertEquals("\"-mod=$workspace;${workshop("@Ws")}\"", command.last())
        assertFalse(command.last().contains(mod("Local")))
        assertFalse(command.last().contains(mod("Server")))
        assertFalse(command.last().contains(mod("Off")))
    }

    @Test
    fun `a plugin mod that is an existing folder is loaded first`() {
        val plugin = folder("plugin")
        val workspace = File(dir, ".Workbench").path
        val prepared = PreparedWorkbench(listOf(workspace))
        val command =
            ProcessManager.workbenchCommand(
                AppConfig(workbenchPluginPath = plugin.path, clientMods = listOf(ModEntry(mod("A")))),
                prepared,
            )
        assertEquals("\"-mod=${plugin.path};$workspace\"", command.last())

        val missing =
            ProcessManager.workbenchCommand(
                AppConfig(workbenchPluginPath = File(dir, "no-such").path, clientMods = listOf(ModEntry(mod("A")))),
                prepared,
            )
        assertEquals("\"-mod=$workspace\"", missing.last())
    }

    @Test
    fun `workbench keeps its workspace mount when no source mods are enabled`() {
        val workspace = File(dir, ".Workbench").path
        val command = ProcessManager.workbenchCommand(AppConfig(), PreparedWorkbench(listOf(workspace)))
        assertEquals("\"-mod=$workspace\"", command.last())
    }

    @Test
    fun `prepared packed paths with spaces and unicode keep their order in one mod argument`() {
        val paths = listOf(File(dir, ".Workbench").path, mod("Локальный PBO"), workshop("@Packed Mod"))
        val command = ProcessManager.workbenchCommand(AppConfig(), PreparedWorkbench(paths))
        assertEquals("\"-mod=${paths.joinToString(";")}\"", command.last())
        assertEquals(1, command.count { it.contains("-mod=") })
    }

    @Test
    fun `workbench always starts next to its executable regardless of the game path`() {
        val game = folder("game")
        val exe = File(game, "DayZDiag_x64.exe").also { it.writeText("") }
        val workbench = folder("Workbench Tools").absoluteFile
        for (gamePath in listOf(game.path, exe.path, File(dir, "no-such").path, "")) {
            assertEquals(
                workbench,
                ProcessManager.workbenchDirectory(AppConfig(gamePath = gamePath, workbenchPath = workbench.path)),
                "the game path must not affect Workbench's working directory: $gamePath",
            )
        }
    }

    @Test
    fun `a relative workbench directory is resolved to an absolute path`() {
        val workbench = File("relative tools", "Workbench")
        val directory = ProcessManager.workbenchDirectory(AppConfig(workbenchPath = workbench.path))
        assertTrue(directory.isAbsolute)
        assertEquals(workbench.absoluteFile, directory)
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
    fun `the old name of the file system flag is renamed in the arguments of every program`() {
        assertEquals(
            listOf("DayZDiag_x64.exe", "-window", "-loadRVFilesViaEnfusionFS=0"),
            ProcessManager.buildArgs("DayZDiag_x64.exe", "-window -resolveFilePatchingUsingEnfusion=0", emptyList()),
        )
        val workbench =
            ProcessManager.workbenchCommand(
                AppConfig(workbenchArgs = "-resolveFilePatchingUsingEnfusion=0"),
                PreparedWorkbench(listOf(File(dir, ".Workbench").path)),
            )
        assertEquals(listOf("-loadRVFilesViaEnfusionFS=0"), workbench.filter { it.contains("EnfusionFS", ignoreCase = true) })
        assertTrue(workbench.none { it.contains("resolveFilePatching", ignoreCase = true) })
    }

    @Test
    fun `file patching asks for the resolve flag unless it is there`() {
        assertEquals(
            "\"-filePatching\" \"-loadRVFilesViaEnfusionFS=1\"",
            ToolsManager.launchArgs("-filePatching", splitConnect = false),
        )
        assertEquals(
            "\"-filePatching\" \"-loadRVFilesViaEnfusionFS=0\"",
            ToolsManager.launchArgs("-filePatching -loadRVFilesViaEnfusionFS=0", splitConnect = false),
        )
        // The name before 1.30 Update 2 that is in the settings becomes the new one with its value, and the new one is not added twice
        assertEquals(
            "\"-filePatching\" \"-loadRVFilesViaEnfusionFS=0\"",
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

    @Test
    fun `vanilla scripts remain linked until cleanup without changing the source`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val scripts = folder("vanilla/scripts")
        val marker = File(scripts, "source.txt").also { it.writeText("original") }
        val game = folder("game")
        val exe = File(game, "DayZDiag_x64.exe").also { it.writeText("") }
        val link = File(game, "scripts").toPath()

        ModificationManager.createScriptsSymlink(scripts.path, exe.path)
        ModificationManager.createScriptsSymlink(scripts.path, exe.path)
        assertEquals(JunctionKind.JUNCTION, WindowsJunctions.inspect(link).kind)
        assertEquals("original", File(link.toFile(), marker.name).readText())

        ModificationManager.removeScriptsSymlink(scripts.path, exe.path)
        assertEquals(JunctionKind.MISSING, WindowsJunctions.inspect(link).kind)
        assertEquals("original", marker.readText())
    }

    @Test
    fun `vanilla cleanup preserves foreign folders and replaced junctions`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val scripts = folder("vanilla/scripts")
        val game = folder("game")
        val foreign = File(game, "scripts").also { it.mkdirs() }
        ModificationManager.createScriptsSymlink(scripts.path, game.path)
        ModificationManager.removeScriptsSymlink(scripts.path, game.path)
        assertTrue(foreign.isDirectory)

        foreign.delete()
        ModificationManager.createScriptsSymlink(scripts.path, game.path)
        WindowsJunctions.remove(foreign.toPath())
        val replacement = folder("replacement")
        WindowsJunctions.create(foreign.toPath(), replacement.toPath())
        ModificationManager.removeScriptsSymlink(scripts.path, game.path)
        assertEquals(JunctionKind.JUNCTION, WindowsJunctions.inspect(foreign.toPath()).kind)
        assertEquals(replacement.toPath(), WindowsJunctions.inspect(foreign.toPath()).target)
        WindowsJunctions.remove(foreign.toPath())
    }
}
