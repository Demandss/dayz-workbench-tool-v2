package com.github.demandss.dayz_workbench_tool.manager

import com.github.demandss.dayz_workbench_tool.config.AppConfig
import com.github.demandss.dayz_workbench_tool.model.Modification
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

interface WorkbenchPreparer {
    fun prepare(
        config: AppConfig,
        mods: List<Modification>,
    ): PreparedWorkbench

    fun cleanupGameLinks()
}

data class PreparedWorkbench(
    val modPaths: List<String>,
)

enum class WorkspaceFailure {
    INVALID_PATH,
    UNAVAILABLE_PATH,
    NAME_COLLISION,
    PATH_CYCLE,
    FOREIGN_ENTRY,
    LINK_CHANGED,
    LINK_OPERATION,
    JOURNAL,
    WORKSPACE,
}

class WorkspacePreparationException(
    val reason: WorkspaceFailure,
    val paths: List<String>,
    val detail: String = "",
    cause: Throwable? = null,
) : Exception("$reason: ${paths.joinToString()}: $detail", cause)

/** Persistent WorkDir links and session-owned game links share one ownership journal. */
class WorkbenchWorkspace(
    private val journalPath: Path,
    workspaceRoot: Path = Path.of("P:\\.Workbench"),
) : WorkbenchPreparer {
    private val root = workspaceRoot.toAbsolutePath().normalize()
    private val json = Json { prettyPrint = true }

    @Synchronized
    override fun prepare(
        config: AppConfig,
        mods: List<Modification>,
    ): PreparedWorkbench {
        val journal = readJournal()
        val sources =
            mods
                .filter { it.enabled }
                .map { source(it.path) }
                .distinctBy { key(it.target) }
        val physicalRoot = guarded(WorkspaceFailure.WORKSPACE, root) { physicalPath(root) }
        sources.forEach { source ->
            if (overlaps(source.target, physicalRoot)) fail(WorkspaceFailure.PATH_CYCLE, source.selected, root)
        }
        val loose = sources.filterNot { it.packed }
        val groups = loose.groupBy { it.name.lowercase(Locale.ROOT) }
        val conflict = groups.values.firstOrNull { it.size > 1 }
        if (conflict != null) fail(WorkspaceFailure.NAME_COLLISION, *conflict.map { it.selected }.toTypedArray())
        val reserved = loose.firstOrNull { it.name.equals("mod.cpp", ignoreCase = true) }
        if (reserved != null) fail(WorkspaceFailure.NAME_COLLISION, reserved.selected, root.resolve("mod.cpp"))
        val game = gameDirectory(config.gamePath)
        if (game != null && same(game, physicalRoot)) fail(WorkspaceFailure.PATH_CYCLE, game, root)
        val desired =
            buildList {
                loose.forEach { source ->
                    add(ManagedLink(root.resolve(source.selected.fileName).toString(), source.target.toString(), LinkScope.WORKBENCH))
                    if (game != null) {
                        val gameLink = game.resolve(source.selected.fileName)
                        if (Path.of(key(gameLink)).startsWith(Path.of(key(source.target)))) {
                            fail(WorkspaceFailure.PATH_CYCLE, source.selected, gameLink)
                        }
                        add(ManagedLink(gameLink.toString(), source.target.toString(), LinkScope.GAME))
                    }
                }
            }
        preflight(journal, desired)
        guarded(WorkspaceFailure.WORKSPACE, root) {
            if (WindowsJunctions.inspect(root).kind == JunctionKind.MISSING) Files.createDirectory(root)
            Files.setAttribute(root, "dos:hidden", true, NOFOLLOW_LINKS)
            val metadata = root.resolve("mod.cpp")
            if (!Files.exists(metadata, NOFOLLOW_LINKS)) {
                Files.writeString(metadata, "name = \"WorkDir\";\r\n", Charsets.UTF_8, CREATE_NEW)
            }
        }
        val remaining = journal.links.toMutableList()
        // Every entry was checked before any change; recheck immediately before removing it as well.
        remaining.toList().forEach { owned ->
            val replacement = desired.firstOrNull { same(Path.of(it.link), Path.of(owned.link)) }
            if (replacement == null || !same(Path.of(replacement.target), Path.of(owned.target))) {
                removeOwned(owned)
                remaining.remove(owned)
                writeJournal(LinkJournal(links = remaining.toList()))
            }
        }
        desired.forEach { entry ->
            val link = Path.of(entry.link)
            val known = remaining.firstOrNull { same(Path.of(it.link), link) }
            if (known == null || inspect(link).kind == JunctionKind.MISSING) {
                guarded(WorkspaceFailure.LINK_OPERATION, link, Path.of(entry.target)) {
                    if (WindowsJunctions.inspect(link.parent).kind != JunctionKind.DIRECTORY) {
                        fail(WorkspaceFailure.LINK_CHANGED, link.parent)
                    }
                    WindowsJunctions.create(link, Path.of(entry.target))
                    verify(entry, allowMissing = false)
                }
                if (known != null) remaining.remove(known)
                remaining += entry
                try {
                    writeJournal(LinkJournal(links = remaining.toList()))
                } catch (error: WorkspacePreparationException) {
                    // Do not leave an unrecorded link behind if committing its receipt failed.
                    runCatching { removeOwned(entry) }.exceptionOrNull()?.let { cleanupError -> error.addSuppressed(cleanupError) }
                    throw error
                }
            }
        }
        return PreparedWorkbench(listOf(root.toString()) + sources.filter { it.packed }.map { it.selected.toString() })
    }

    @Synchronized
    override fun cleanupGameLinks() {
        val journal = readJournal()
        journal.links.filter { it.scope == LinkScope.GAME }.forEach { verify(it) }
        val remaining = journal.links.toMutableList()
        journal.links.filter { it.scope == LinkScope.GAME }.forEach { entry ->
            removeOwned(entry)
            remaining.remove(entry)
            writeJournal(LinkJournal(links = remaining.toList()))
        }
    }

    private fun source(value: String): ModSource {
        val selected =
            try {
                require(value.isNotBlank() && ';' !in value)
                Path
                    .of(value)
                    .toAbsolutePath()
                    .normalize()
                    .also { require(it.fileName != null) }
            } catch (error: Exception) {
                throw WorkspacePreparationException(WorkspaceFailure.INVALID_PATH, listOf(value), error.message.orEmpty(), error)
            }
        return guarded(WorkspaceFailure.UNAVAILABLE_PATH, selected) {
            val target = selected.toRealPath()
            if (!Files.isDirectory(target)) fail(WorkspaceFailure.UNAVAILABLE_PATH, selected)
            val addons =
                Files.newDirectoryStream(target).use { entries ->
                    entries.firstOrNull { it.fileName.toString().equals("Addons", true) }
                }
            val packed =
                addons != null && Files.readAttributes(addons, BasicFileAttributes::class.java).isDirectory &&
                    Files.newDirectoryStream(addons).use { entries ->
                        entries.any {
                            it.fileName.toString().endsWith(".pbo", true) &&
                                Files.readAttributes(it, BasicFileAttributes::class.java).isRegularFile
                        }
                    }
            ModSource(selected, target, packed)
        }
    }

    private fun gameDirectory(value: String): Path? {
        if (value.isBlank()) return null
        val selected =
            try {
                Path.of(value).toAbsolutePath().normalize()
            } catch (error: Exception) {
                throw WorkspacePreparationException(WorkspaceFailure.INVALID_PATH, listOf(value), error.message.orEmpty(), error)
            }
        return guarded(WorkspaceFailure.UNAVAILABLE_PATH, selected) {
            val path = if (Files.isRegularFile(selected)) selected.parent else selected
            path.toRealPath().also { if (!Files.isDirectory(it)) fail(WorkspaceFailure.UNAVAILABLE_PATH, selected) }
        }
    }

    private fun preflight(
        journal: LinkJournal,
        desired: List<ManagedLink>,
    ) {
        when (inspect(root).kind) {
            JunctionKind.DIRECTORY -> {
                guarded(WorkspaceFailure.WORKSPACE, root) {
                    Files.newDirectoryStream(root).use { entries ->
                        entries.forEach { child ->
                            if (child.fileName.toString().equals("mod.cpp", true)) {
                                val attributes = Files.readAttributes(child, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                                if (!attributes.isRegularFile || attributes.isSymbolicLink || attributes.isOther) {
                                    fail(WorkspaceFailure.FOREIGN_ENTRY, child)
                                }
                            } else if (journal.links.none { it.scope == LinkScope.WORKBENCH && same(Path.of(it.link), child) }) {
                                fail(WorkspaceFailure.FOREIGN_ENTRY, child)
                            }
                        }
                    }
                }
            }

            JunctionKind.MISSING -> {
                if (inspect(root.parent).kind != JunctionKind.DIRECTORY) fail(WorkspaceFailure.WORKSPACE, root.parent)
            }

            else -> {
                fail(WorkspaceFailure.FOREIGN_ENTRY, root)
            }
        }
        journal.links.forEach { verify(it) }
        desired.forEach { entry ->
            val link = Path.of(entry.link)
            if (journal.links.none { same(Path.of(it.link), link) } && inspect(link).kind != JunctionKind.MISSING) {
                fail(WorkspaceFailure.FOREIGN_ENTRY, link, Path.of(entry.target))
            }
        }
    }

    private fun verify(
        entry: ManagedLink,
        allowMissing: Boolean = true,
    ) {
        val link = Path.of(entry.link)
        val actual = inspect(link)
        if (actual.kind == JunctionKind.MISSING && !allowMissing) fail(WorkspaceFailure.LINK_OPERATION, link, Path.of(entry.target))
        if (actual.kind != JunctionKind.MISSING &&
            (actual.kind != JunctionKind.JUNCTION || actual.target == null || !same(actual.target, Path.of(entry.target)))
        ) {
            fail(WorkspaceFailure.LINK_CHANGED, link, Path.of(entry.target), *listOfNotNull(actual.target).toTypedArray())
        }
    }

    private fun removeOwned(entry: ManagedLink) {
        val link = Path.of(entry.link)
        verify(entry)
        if (inspect(link).kind == JunctionKind.MISSING) return
        guarded(WorkspaceFailure.LINK_OPERATION, link, Path.of(entry.target)) { WindowsJunctions.remove(link) }
    }

    private fun inspect(path: Path): JunctionInfo = guarded(WorkspaceFailure.UNAVAILABLE_PATH, path) { WindowsJunctions.inspect(path) }

    private fun readJournal(): LinkJournal =
        guarded(WorkspaceFailure.JOURNAL, journalPath) {
            val attributes =
                try {
                    Files.readAttributes(journalPath, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                } catch (_: NoSuchFileException) {
                    return@guarded LinkJournal()
                }
            if (!attributes.isRegularFile || attributes.isSymbolicLink || attributes.isOther) fail(WorkspaceFailure.JOURNAL, journalPath)
            json.decodeFromString<LinkJournal>(Files.readString(journalPath)).also { journal ->
                require(journal.version == 1)
                val destinations = journal.links.map { key(Path.of(it.link)) }
                require(destinations.toSet().size == destinations.size)
                journal.links.forEach { entry ->
                    val link = Path.of(entry.link)
                    val target = Path.of(entry.target)
                    require(link.isAbsolute && link.fileName != null && target.isAbsolute)
                    require(!overlaps(link, target))
                    require(!link.fileName.toString().equals("mod.cpp", true))
                }
            }
        }

    private fun writeJournal(journal: LinkJournal) {
        guarded(WorkspaceFailure.JOURNAL, journalPath) {
            Files.createDirectories(journalPath.toAbsolutePath().parent)
            val temp = Files.createTempFile(journalPath.toAbsolutePath().parent, "managed-links-", ".tmp")
            try {
                Files.writeString(temp, json.encodeToString(journal), Charsets.UTF_8)
                try {
                    Files.move(temp, journalPath, ATOMIC_MOVE, REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, journalPath, REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
        }
    }

    private fun physicalPath(path: Path): Path {
        if (Files.exists(path, NOFOLLOW_LINKS)) return path.toRealPath()
        val parent = path.parent ?: throw NoSuchFileException(path.toString())
        return physicalPath(parent).resolve(path.fileName)
    }

    private inline fun <T> guarded(
        reason: WorkspaceFailure,
        vararg paths: Path,
        action: () -> T,
    ): T =
        try {
            action()
        } catch (error: WorkspacePreparationException) {
            throw error
        } catch (error: Exception) {
            throw WorkspacePreparationException(reason, paths.map(Path::toString), error.message.orEmpty(), error)
        }

    private fun fail(
        reason: WorkspaceFailure,
        vararg paths: Path,
    ): Nothing = throw WorkspacePreparationException(reason, paths.map(Path::toString))

    private fun key(path: Path): String =
        path
            .toAbsolutePath()
            .normalize()
            .toString()
            .lowercase(Locale.ROOT)

    private fun same(
        first: Path,
        second: Path,
    ): Boolean = key(first) == key(second)

    private fun overlaps(
        first: Path,
        second: Path,
    ): Boolean {
        val a = Path.of(key(first))
        val b = Path.of(key(second))
        return a.startsWith(b) || b.startsWith(a)
    }

    private data class ModSource(
        val selected: Path,
        val target: Path,
        val packed: Boolean,
    ) {
        val name: String = selected.fileName.toString()
    }

    @Serializable
    private enum class LinkScope { WORKBENCH, GAME }

    @Serializable
    private data class ManagedLink(
        val link: String,
        val target: String,
        val scope: LinkScope,
    )

    @Serializable
    private data class LinkJournal(
        val version: Int = 1,
        val links: List<ManagedLink> = emptyList(),
    )
}
