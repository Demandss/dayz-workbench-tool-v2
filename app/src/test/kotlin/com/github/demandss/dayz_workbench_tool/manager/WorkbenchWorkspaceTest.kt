package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.model.ModSide
import com.github.demandss.dayz_workbench_tool.model.Modification
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkbenchWorkspaceTest {
    @TempDir
    lateinit var temp: Path

    private val root get() = temp.resolve(".Workbench")
    private val journal get() = temp.resolve("settings/managed-links.json")

    @BeforeTest
    fun windows() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
    }

    private fun folder(name: String): Path = temp.resolve(name).createDirectories()

    private fun service() = WorkbenchWorkspace(journal, root)

    private fun mod(
        source: Path,
        side: ModSide = ModSide.CLIENT,
        enabled: Boolean = true,
    ) = Modification(source.toString(), side, enabled = enabled)

    private fun exists(path: Path) = Files.exists(path, NOFOLLOW_LINKS)

    @Test
    fun `loose sources get two junctions while mixed-case PBO mods keep their physical paths`() {
        val loose = folder("sources/NH")
        loose.resolve("marker.txt").writeText("source")
        val packed = folder("mods/@Packed")
        packed
            .resolve("aDdOnS")
            .createDirectories()
            .resolve("Scripts.PBO")
            .writeText("packed")
        packed.resolve("config.cpp").writeText("a mixed mod is still packed")
        val game = folder("game")
        val exe = game.resolve("DayZDiag_x64.exe").also { it.writeText("") }
        val prepared =
            service().prepare(
                AppConfig(gamePath = exe.toString(), workDrivePath = temp.resolve("other-drive").toString()),
                listOf(mod(packed), mod(temp.resolve("missing"), enabled = false), mod(loose)),
            )

        assertEquals(listOf(root.toString(), packed.toString()), prepared.modPaths)
        assertTrue(Files.getAttribute(root, "dos:hidden") as Boolean)
        assertContentEquals("name = \"WorkDir\";\r\n".toByteArray(), Files.readAllBytes(root.resolve("mod.cpp")))
        assertEquals("source", root.resolve("NH/marker.txt").readText())
        assertEquals("source", game.resolve("NH/marker.txt").readText())
        assertEquals(JunctionKind.JUNCTION, WindowsJunctions.inspect(root.resolve("NH")).kind)
        assertFalse(exists(root.resolve("@Packed")))
        assertFalse(exists(game.resolve("@Packed")))
        assertFalse(exists(loose.resolve("mod.cpp")), "metadata belongs only to the workspace")
        assertTrue(exists(journal))
    }

    @Test
    fun `one source used on both sides stays until its last enabled entry disappears`() {
        val source = folder("NH")
        source.resolve("marker.txt").writeText("unchanged")
        val game = folder("game")
        val config = AppConfig(gamePath = game.toString())
        val client = mod(source)
        val server = mod(source, ModSide.SERVER)
        val workspace = service()

        workspace.prepare(config, listOf(client, server))
        workspace.prepare(config, listOf(client.copy(enabled = false), server))
        assertTrue(exists(root.resolve("NH")))
        assertTrue(exists(game.resolve("NH")))
        workspace.prepare(config, listOf(client.copy(enabled = false)))
        assertFalse(exists(root.resolve("NH")))
        assertFalse(exists(game.resolve("NH")))
        assertEquals("unchanged", source.resolve("marker.txt").readText())
        assertTrue(exists(root.resolve("mod.cpp")))
    }

    @Test
    fun `the journal survives a restart and shutdown removes only game links`() {
        val source = folder("NH")
        val game = folder("game")
        val config = AppConfig(gamePath = game.toString())
        service().prepare(config, listOf(mod(source)))

        val restarted = service()
        assertEquals(listOf(root.toString()), restarted.prepare(config, listOf(mod(source))).modPaths)
        restarted.cleanupGameLinks()
        assertTrue(exists(root.resolve("NH")))
        assertTrue(exists(root.resolve("mod.cpp")))
        assertFalse(exists(game.resolve("NH")))
        service().prepare(config, listOf(mod(source)))
        assertTrue(exists(game.resolve("NH")))
    }

    @Test
    fun `existing metadata is preserved byte for byte and packed-only preparation still creates WorkDir`() {
        root.createDirectories()
        val original = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "name = \"Custom\";\n// мой файл\n".toByteArray()
        Files.write(root.resolve("mod.cpp"), original)
        val packed = folder("Packed")
        packed
            .resolve("Addons")
            .createDirectories()
            .resolve("only.pbo")
            .writeText("pbo")

        service().prepare(AppConfig(), listOf(mod(packed)))

        assertContentEquals(original, Files.readAllBytes(root.resolve("mod.cpp")))
        assertTrue(Files.getAttribute(root, "dos:hidden") as Boolean)
        assertFalse(exists(root.resolve("Packed")))
    }

    @Test
    fun `different sources with case-insensitive equal names are rejected before creating anything`() {
        val first = folder("client/NH")
        val second = folder("server/nh")
        val error =
            assertFailsWith<WorkspacePreparationException> {
                service().prepare(AppConfig(), listOf(mod(first), mod(second, ModSide.SERVER)))
            }

        assertEquals(WorkspaceFailure.NAME_COLLISION, error.reason)
        assertTrue(first.toString() in error.paths && second.toString() in error.paths)
        assertFalse(exists(root))
        assertFalse(exists(journal))
    }

    @Test
    fun `foreign workspace objects and a same-target unowned junction are never adopted or deleted`() {
        val source = folder("NH")
        root.createDirectories()
        val foreign = root.resolve("keep.txt").also { it.writeText("keep") }
        assertEquals(
            WorkspaceFailure.FOREIGN_ENTRY,
            assertFailsWith<WorkspacePreparationException> { service().prepare(AppConfig(), listOf(mod(source))) }.reason,
        )
        assertEquals("keep", foreign.readText())
        Files.delete(foreign)
        WindowsJunctions.create(root.resolve("NH"), source)
        assertEquals(
            WorkspaceFailure.FOREIGN_ENTRY,
            assertFailsWith<WorkspacePreparationException> { service().prepare(AppConfig(), listOf(mod(source))) }.reason,
        )
        assertEquals(JunctionKind.JUNCTION, WindowsJunctions.inspect(root.resolve("NH")).kind)
        assertFalse(exists(journal))
    }

    @Test
    fun `a managed link replaced by a different target blocks preparation and remains untouched`() {
        val source = folder("NH")
        val other = folder("Other")
        other.resolve("keep.txt").writeText("foreign target")
        val workspace = service()
        workspace.prepare(AppConfig(), listOf(mod(source)))
        WindowsJunctions.remove(root.resolve("NH"))
        WindowsJunctions.create(root.resolve("NH"), other)

        assertEquals(
            WorkspaceFailure.LINK_CHANGED,
            assertFailsWith<WorkspacePreparationException> { workspace.prepare(AppConfig(), emptyList()) }.reason,
        )
        assertEquals("foreign target", root.resolve("NH/keep.txt").readText())
        assertTrue(exists(other.resolve("keep.txt")))
    }

    @Test
    fun `self and ancestor sources are rejected without introducing a traversal cycle`() {
        val workspace = service()
        assertEquals(
            WorkspaceFailure.PATH_CYCLE,
            assertFailsWith<WorkspacePreparationException> { workspace.prepare(AppConfig(), listOf(mod(temp))) }.reason,
        )
        assertFalse(exists(root))
        root.createDirectories()
        assertEquals(
            WorkspaceFailure.PATH_CYCLE,
            assertFailsWith<WorkspacePreparationException> { workspace.prepare(AppConfig(), listOf(mod(root))) }.reason,
        )
    }

    @Test
    fun `an unavailable source or corrupt ownership journal leaves the workspace unmodified`() {
        assertEquals(
            WorkspaceFailure.UNAVAILABLE_PATH,
            assertFailsWith<WorkspacePreparationException> { service().prepare(AppConfig(), listOf(mod(temp.resolve("missing")))) }.reason,
        )
        assertFalse(exists(root))
        journal.parent.createDirectories()
        journal.writeText("corrupt journal")
        assertEquals(
            WorkspaceFailure.JOURNAL,
            assertFailsWith<WorkspacePreparationException> { service().prepare(AppConfig(), emptyList()) }.reason,
        )
        assertEquals("corrupt journal", journal.readText())
        assertFalse(exists(root))
    }

    @Test
    fun `a foreign game directory collision stops preparation before creating WorkDir`() {
        val source = folder("sources/NH")
        val game = folder("game")
        val foreign =
            game
                .resolve("NH")
                .createDirectories()
                .resolve("keep.txt")
                .also { it.writeText("keep") }
        val error =
            assertFailsWith<WorkspacePreparationException> {
                service().prepare(AppConfig(gamePath = game.toString()), listOf(mod(source)))
            }
        assertEquals(WorkspaceFailure.FOREIGN_ENTRY, error.reason)
        assertEquals("keep", foreign.readText())
        assertFalse(exists(root))
    }

    @Test
    fun `an owned source move replaces both junctions without touching either source`() {
        val old = folder("old/NH")
        old.resolve("marker.txt").writeText("old")
        val moved = folder("new/NH")
        moved.resolve("marker.txt").writeText("new")
        val game = folder("game")
        val config = AppConfig(gamePath = game.toString())
        val workspace = service()
        workspace.prepare(config, listOf(mod(old)))

        workspace.prepare(config, listOf(mod(moved)))

        assertEquals("new", root.resolve("NH/marker.txt").readText())
        assertEquals("new", game.resolve("NH/marker.txt").readText())
        assertEquals("old", old.resolve("marker.txt").readText())
        assertEquals("new", moved.resolve("marker.txt").readText())
    }

    @Test
    fun `packing an existing source removes its managed links and switches to the absolute mod path`() {
        val source = folder("NH")
        source.resolve("marker.txt").writeText("source")
        val game = folder("game")
        val config = AppConfig(gamePath = game.toString())
        val workspace = service()
        workspace.prepare(config, listOf(mod(source)))
        source
            .resolve("Addons")
            .createDirectories()
            .resolve("Scripts.pbo")
            .writeText("packed")

        assertEquals(listOf(root.toString(), source.toString()), workspace.prepare(config, listOf(mod(source))).modPaths)
        assertFalse(exists(root.resolve("NH")))
        assertFalse(exists(game.resolve("NH")))
        assertEquals("source", source.resolve("marker.txt").readText())
        assertTrue(exists(source.resolve("Addons/Scripts.pbo")))
    }

    @Test
    fun `disabled sources can lose their target directory without preventing safe link cleanup`() {
        val source = folder("NH")
        val game = folder("game")
        val config = AppConfig(gamePath = game.toString())
        val workspace = service()
        workspace.prepare(config, listOf(mod(source)))
        Files.delete(source)

        workspace.prepare(config, listOf(mod(source, enabled = false)))

        assertFalse(exists(root.resolve("NH")))
        assertFalse(exists(game.resolve("NH")))
        assertTrue(exists(root.resolve("mod.cpp")))
    }

    @Test
    fun `junction operations accept Unicode spaces and shell metacharacters as literal paths`() {
        val source = folder("sources/[Мод] & 'money${'$'} %PATH%")
        source.resolve("marker.txt").writeText("literal")
        val game = folder("game [parent] & test")
        val config = AppConfig(gamePath = game.toString())
        val workspace = service()

        workspace.prepare(config, listOf(mod(source)))
        assertEquals("literal", root.resolve(source.fileName).resolve("marker.txt").readText())
        assertEquals("literal", game.resolve(source.fileName).resolve("marker.txt").readText())
        workspace.prepare(config, emptyList())
        assertEquals("literal", source.resolve("marker.txt").readText())
    }

    @Test
    fun `a missing owned junction is recreated while a nonjunction workspace root is required`() {
        val source = folder("NH")
        val workspace = service()
        workspace.prepare(AppConfig(), listOf(mod(source)))
        WindowsJunctions.remove(root.resolve("NH"))
        workspace.prepare(AppConfig(), listOf(mod(source)))
        assertEquals(JunctionKind.JUNCTION, WindowsJunctions.inspect(root.resolve("NH")).kind)

        val otherRoot = temp.resolve("foreign-root")
        WindowsJunctions.create(otherRoot, root)
        assertEquals(
            WorkspaceFailure.FOREIGN_ENTRY,
            assertFailsWith<WorkspacePreparationException> {
                WorkbenchWorkspace(temp.resolve("other-journal.json"), otherRoot).prepare(AppConfig(), emptyList())
            }.reason,
        )
        assertEquals(JunctionKind.JUNCTION, WindowsJunctions.inspect(otherRoot).kind)
    }
}
