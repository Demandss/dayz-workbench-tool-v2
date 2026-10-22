package com.github.demandss.dayz_workbench_tool.layout_editor.state

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.EditorSession
import com.github.demandss.dayz_workbench_tool.imageset.ImageSetFormat
import com.github.demandss.dayz_workbench_tool.layout.LayoutParser
import com.github.demandss.dayz_workbench_tool.layout.LayoutSerializer
import com.github.demandss.dayz_workbench_tool.layout.WidgetNode
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.containsId
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.removeNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toEditorNode
import com.github.demandss.dayz_workbench_tool.layout_editor.toWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.AlignMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.BitmapFont
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.BulkMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.ColorSpaceMode
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.EdgeDistance
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.GameFont
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.GuideLine
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.NINE_SLICE_SLOTS
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.Reorder
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.ResizeHandle
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.SdfFont
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.alignedRoot
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.allNames
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.appearance
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.bulkReplace
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.clipboardText
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.distributed
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.measureText
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.movedBy
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.parentOf
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.parseClipboard
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.parseWidgetColor
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.reanchored
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.reordered
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.sanitizeWidgetName
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.scriptSnippet
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.ungrouped
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.uniqueName
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.withAppearance
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.withCopyOf
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.wrapped
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import com.github.demandss.dayz_workbench_tool.pbo.PboArchive
import com.github.demandss.dayz_workbench_tool.pbo.PboFile
import com.github.demandss.dayz_workbench_tool.pbo.childOf
import com.github.demandss.dayz_workbench_tool.pbo.fileAt
import com.github.demandss.dayz_workbench_tool.pbo.inRoot
import com.github.demandss.dayz_workbench_tool.pbo.readBytesOf
import com.github.demandss.dayz_workbench_tool.pbo.readTextOf
import com.github.demandss.dayz_workbench_tool.styles.StyleDef
import com.github.demandss.dayz_workbench_tool.styles.StyleSheet
import com.github.demandss.dayz_workbench_tool.styles.StylesFormat
import com.github.demandss.dayz_workbench_tool.texture.EddsCodec
import kotlinx.coroutines.*
import java.awt.Toolkit
import java.io.File
import java.nio.file.*
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.min

// ---------------------------------------------------------------------------
// Drag & drop types
// ---------------------------------------------------------------------------

sealed class DragSource {
    data class FromPalette(
        val type: WidgetType,
    ) : DragSource()

    data class FromHierarchy(
        val nodeId: UUID,
    ) : DragSource()
}

data class DragSession(
    val source: DragSource,
)

// ---------------------------------------------------------------------------
// Pending widget creation (shown before name dialog)
// ---------------------------------------------------------------------------

data class PendingAdd(
    val type: WidgetType,
    val parentId: UUID?,
    val worldPos: Offset?,
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

private const val MAX_RECENT = 8

// A hovered button without Highlight images gets this much white added, as measured on the Editor style in Workbench
private const val HIGHLIGHT_LIFT = 0.27f

enum class PaletteMode { COMMANDS, FILES, WIDGETS }

/** The menu of the widgets under the cursor, opened by the right button on the canvas at [position] (canvas pixels). */
class CanvasMenu(
    val position: Offset,
    val hits: List<EditorWidgetNode>,
)

/** A top level folder of the file tree: [label] is shown instead of the folder name; [archive] when it is a PBO. */
class TreeRoot(
    val path: String,
    val label: String,
    val archive: PboArchive? = null,
)

class LayoutEditorState(
    val workDrivePath: String,
    private val scope: CoroutineScope,
    /** The folder of the game (the one with `dta`): its `gui.pbo` is the source of the vanilla layouts, styles and fonts. */
    private val gameFolder: String = "",
    /** The folders of the connected mods; null shows the whole work drive instead. */
    private val modFolders: List<String>? = null,
) {
    /** `gui.pbo` of the game once its header is read. Its files are read from it when needed, never unpacked. */
    var gamePbo by mutableStateOf<PboArchive?>(null)
        private set

    /** The top level folders of the file tree: the game's data and the connected mods. Null: the work drive itself. */
    val treeRoots: List<TreeRoot>?
        get() {
            val mods = modFolders ?: return null
            return buildList {
                gamePbo?.let { add(TreeRoot(it.file.path, "DayZ", it)) }
                mods.filter { File(it).isDirectory }.forEach { add(TreeRoot(it, File(it).name.ifEmpty { it })) }
            }
        }

    val screenSize: Size =
        Toolkit.getDefaultToolkit().screenSize.let {
            Size(it.width.toFloat(), it.height.toFloat())
        }
    val openFiles = mutableStateListOf<LayoutDocument>()
    var activeFileIndex by mutableStateOf(-1)
    val selectedIds = mutableStateListOf<UUID>()

    // Editor-only: hidden widgets are not drawn or hit, locked ones cannot be picked on the canvas. Never saved.
    val hiddenIds = mutableStateListOf<UUID>()
    val lockedIds = mutableStateListOf<UUID>()

    fun toggleHidden(id: UUID) {
        if (!hiddenIds.remove(id)) hiddenIds.add(id)
    }

    fun toggleLocked(id: UUID) {
        if (!lockedIds.remove(id)) {
            lockedIds.add(id)
            selectedIds.remove(id)
        } else {
            lockedIds.remove(id)
        }
    }

    val filesChangedOnDisk = mutableStateListOf<String>()

    // Texture viewer tabs. When activeTextureIndex >= 0 the viewport shows that texture instead of a layout
    val openTextures = mutableStateListOf<TextureDocument>()
    var activeTextureIndex by mutableStateOf(-1)
    val activeTexture get() = openTextures.getOrNull(activeTextureIndex)

    // Imageset tabs. When activeImageSetIndex >= 0 the window shows the atlas editor for that file
    val openImageSets = mutableStateListOf<ImageSetDocument>()
    var activeImageSetIndex by mutableStateOf(-1)
    val activeImageSet get() = openImageSets.getOrNull(activeImageSetIndex)

    // Style sheet tabs. When activeStylesIndex >= 0 the window shows the style editor for that file
    val openStyles = mutableStateListOf<StylesDocument>()
    var activeStylesIndex by mutableStateOf(-1)
    val activeStyles get() = openStyles.getOrNull(activeStylesIndex)

    // Decoded textures of imagesets by file, null while loading or when the file cannot be read
    private val imageSetBitmaps = mutableStateMapOf<String, ImageBitmap?>()
    private val imageSetBitmapsLoading = mutableSetOf<String>()

    var zoom by mutableStateOf(1f)
    var panOffset by mutableStateOf(Offset.Zero)
    var selectedResolution by mutableStateOf(Resolution.R1920x1080)

    var lastCanvasSize by mutableStateOf(Size.Zero)

    var snapToGrid by mutableStateOf(false)
    var snapToShapes by mutableStateOf(false)
    var showBorders by mutableStateOf(true)
    var showSafeFrame by mutableStateOf(false)
    var showMeasure by mutableStateOf(true)

    private var workbench130State by mutableStateOf(false)

    /** Draws like the Workbench of 1.30 (colours read as linear light, blended in linear light) instead of like the game. */
    var workbenchColors: Boolean
        get() = workbench130State
        set(value) {
            workbench130State = value
            ColorSpaceMode.workbench130 = value
        }

    // A clean view: no borders, labels, selection or measurements, only what the widgets draw
    var livePreview by mutableStateOf(false)

    /** F3: frame counter in the corner. */
    var debugOverlay by mutableStateOf(false)

    // Live Preview: the button under the pointer, the one held down and the one that was clicked last
    var previewHover by mutableStateOf<UUID?>(null)
    var previewPressed by mutableStateOf<UUID?>(null)
    var previewFocus by mutableStateOf<UUID?>(null)

    /** The state a widget is in while Live Preview runs: Disabled, Pushed, Highlight (the pointer is over it), Focus or Normal. */
    fun previewStateOf(node: EditorWidgetNode): String {
        if (!livePreview) return "Normal"
        return when {
            node.node.properties["disabled"]?.trim() == "1" -> "Disabled"
            node.id == previewPressed && node.id == previewHover -> "Pushed"
            node.id == previewHover -> "Highlight"
            node.id == previewFocus -> "Focus"
            else -> "Normal"
        }
    }

    // Files opened last, newest first
    val recentFiles = mutableStateListOf<String>()

    // A Workbench screenshot laid over the work area to compare the canvas with
    var showReference by mutableStateOf(false)
    var referenceImage by mutableStateOf<ImageBitmap?>(null)
    var referencePath by mutableStateOf("")
    var referenceOpacity by mutableStateOf(0.5f)

    // Where the user dragged the reference panel to, from its corner. Kept here so hiding the panel does not reset it
    var referencePanelOffset by mutableStateOf(Offset.Zero)
    var scaledMode by mutableStateOf(false)
    var gridSize by mutableStateOf(10)
    var snapMargin by mutableStateOf(5)

    // Drag & drop
    var dragSession by mutableStateOf<DragSession?>(null)
    var dragScreenPos by mutableStateOf(Offset.Zero)
    var dragHoverNodeId by mutableStateOf<UUID?>(null)

    // Bounds updated via onGloballyPositioned (window coordinate space)
    var canvasBoundsInWindow by mutableStateOf(Rect.Zero)
    var hierarchyBoundsInWindow by mutableStateOf(Rect.Zero)
    val hierarchyRowBounds = HashMap<UUID, Rect>()

    // Pending widget add (shows name dialog)
    var pendingAdd by mutableStateOf<PendingAdd?>(null)

    val activeFile get() = openFiles.getOrNull(activeFileIndex)

    private val textureCache = mutableStateMapOf<String, ImageBitmap?>()
    private val texturesLoading = HashSet<String>()

    private val undoLimit get() = ConfigManager.LayoutEditor.load().undoLimit

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val watchedDirs = mutableMapOf<Path, WatchKey>()

    // Session persistence: stored per work drive so a test stand does not mix with the real project
    private val sessionKey = workDrivePath.trim().trimEnd('\\', '/').lowercase()
    private var restoring = false
    private var treeExpanded: List<String> = emptyList()
    private var treeSelected: String = ""

    val savedExpandedDirs: List<String> get() = treeExpanded
    val savedSelectedPath: String get() = treeSelected

    init {
        openGamePbo()
        startWatcher()
        loadSession().let {
            treeExpanded = it.expandedDirs
            treeSelected = it.selectedPath
            recentFiles.addAll(it.recent.filter { f -> File(f).isFile })
        }
    }

    // The mods of the layouts that were opened: the mod a layout belongs to is connected while it is edited, listed or not
    private val detectedMods = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun connectedMods(): List<String> = (modFolders.orEmpty() + detectedMods).distinct()

    /** Connects the mod that holds [path] (the nearest folder above it with a `config.cpp`), if it is not connected yet. */
    private fun connectModOf(path: String) {
        var dir = File(path).parentFile
        while (dir != null && !File(dir, "config.cpp").isFile) dir = dir.parentFile
        val mod = dir?.path ?: return
        if (mod in connectedMods()) return
        detectedMods += mod
        resourceRootsCache.clear()
        bitmapSizesCache.clear()
        scanMods()
    }

    /** The `.styles` and `.imageset` files of the connected mods, found by a scan in the background. */
    private var modStyleFiles: List<File> = emptyList()
    private var modImageSetFiles: List<File> = emptyList()

    // Read by whatever draws from the mods' files, so that it draws again once the scan is done
    private var modScanDone by mutableStateOf(0)

    private fun scanMods() {
        val mods = connectedMods().map(::File).filter { it.isDirectory }
        if (mods.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val styles = mutableListOf<File>()
            val sets = mutableListOf<File>()
            for (mod in mods) {
                mod
                    .walkTopDown()
                    .onEnter { it.name != ".git" }
                    .filter { it.isFile }
                    .forEach {
                        when (it.extension.lowercase()) {
                            "styles" -> styles += it
                            "imageset" -> sets += it
                        }
                    }
            }
            modStyleFiles = styles
            modImageSetFiles = sets
            withContext(Dispatchers.Main) {
                vanillaSheetFrom = null
                imageSetIndexFor = null
                skinPlans.clear()
                skinCache.clear()
                modScanDone++
            }
        }
    }

    /** Reads the header of `dta/gui.pbo` in the game folder (a few dozen kilobytes). */
    private fun openGamePbo() {
        val pbo = File(gameFolder, "dta/gui.pbo").takeIf { gameFolder.isNotBlank() && it.isFile } ?: return
        gamePbo = PboArchive.open(pbo)?.also { PboFile.register(it) }
    }

    fun openFile(path: String) {
        val normalized = runCatching { File(path).canonicalPath }.getOrDefault(path)
        val existing = openFiles.indexOfFirst { it.path == normalized }
        if (existing >= 0) {
            noteRecent(normalized)
            selectLayout(existing)
            return
        }
        runCatching {
            val root = LayoutParser.parse(readTextOf(fileAt(normalized))).toEditorNode()
            openFiles += LayoutDocument(path = normalized, root = root)
            connectModOf(normalized)
            noteRecent(normalized)
            selectLayout(openFiles.lastIndex)
            File(normalized).parentFile?.toPath()?.let { watchDirectory(it) }
        }.onFailure {
            notify("Не удалось открыть ${File(path).name}: ${it.message ?: it.javaClass.simpleName}", ToastKind.ERROR)
        }
    }

    fun closeFile(index: Int) {
        openFiles.removeAt(index)
        activeFileIndex =
            when {
                openFiles.isEmpty() -> -1
                index <= activeFileIndex -> (activeFileIndex - 1).coerceAtLeast(0)
                else -> activeFileIndex
            }
        saveSession()
    }

    // When the editor wrote a file itself, so that the watcher does not report it as a change from outside
    private val ownWrites = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun saveActiveFile() {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        if (PboFile.resolve(doc.path) != null) {
            notify("Файл игры только для чтения: сохраните копию в свой мод", ToastKind.ERROR)
            return
        }
        ownWrites[doc.path] = System.currentTimeMillis()
        runCatching {
            File(doc.path).writeText(LayoutSerializer.serialize(doc.root.toWidgetNode()))
            openFiles[idx] = doc.copy(isModified = false)
        }.onSuccess {
            notify("Сохранено: ${doc.fileName}", ToastKind.SUCCESS)
        }.onFailure {
            notify("Не удалось сохранить ${doc.fileName}: ${it.message ?: it.javaClass.simpleName}", ToastKind.ERROR)
        }
    }

    fun reloadFile(path: String) {
        val idx = openFiles.indexOfFirst { it.path == path }
        if (idx < 0) return
        runCatching {
            val root = LayoutParser.parse(readTextOf(fileAt(path))).toEditorNode()
            openFiles[idx] = LayoutDocument(path = path, root = root)
        }
        filesChangedOnDisk.remove(path)
    }

    fun dismissChangedNotification(path: String) {
        filesChangedOnDisk.remove(path)
    }

    var commandPaletteOpen by mutableStateOf(false)

    /** What the palette lists: commands (Ctrl+Shift+P), files (Ctrl+P) or the widgets of the open layout (Ctrl+F). */
    var paletteMode by mutableStateOf(PaletteMode.COMMANDS)

    fun openPalette(mode: PaletteMode) {
        if (mode == PaletteMode.FILES) scanWorkFiles()
        paletteMode = mode
        commandPaletteOpen = true
    }

    /** F1: the list of shortcuts. */
    var shortcutsOpen by mutableStateOf(false)

    /** Ctrl+B hides the file browser to give the editor the whole window. */
    var showFileTree by mutableStateOf(true)

    /** Ctrl+F in the imageset editor puts the cursor into the search above the list of images. */
    var imageSetSearchTick by mutableStateOf(0)

    /** Files of the work drive that the editor can open, found once in the background. */
    var workFiles by mutableStateOf<List<File>>(emptyList())
        private set
    private var scanning = false

    private fun scanWorkFiles() {
        if (scanning || workFiles.isNotEmpty() || workDrivePath.isBlank()) return
        scanning = true
        scope.launch(Dispatchers.IO) {
            val found = ArrayList<File>()
            val wanted = setOf("layout", "imageset", "styles", "edds", "png")
            runCatching {
                File(workDrivePath)
                    .walkTopDown()
                    .onEnter { it.name != ".git" && it.name != "node_modules" }
                    .take(200_000)
                    .filter { it.isFile && it.extension.lowercase() in wanted }
                    .take(30_000)
                    .forEach { found += it }
            }
            withContext(Dispatchers.Main) {
                workFiles = found
                scanning = false
            }
        }
    }

    /** Ctrl+Tab: the next tab to the right (or the previous one for a negative [step]), over layouts, textures and imagesets. */
    fun cycleTab(step: Int) {
        val tabs = allTabs()
        if (tabs.size < 2) return
        val current =
            when {
                activeStylesIndex >= 0 -> 3 to activeStylesIndex
                activeImageSetIndex >= 0 -> 2 to activeImageSetIndex
                activeTextureIndex >= 0 -> 1 to activeTextureIndex
                else -> 0 to activeFileIndex
            }
        val next = tabs[((tabs.indexOf(current).coerceAtLeast(0) + step) % tabs.size + tabs.size) % tabs.size]
        selectTab(next.first, next.second)
    }

    /** Ctrl+1…9: the tab with this number, counted from the left. */
    fun selectTabNumber(number: Int) {
        val tabs = allTabs()
        val tab = tabs.getOrNull(if (number == 9) tabs.lastIndex else number - 1) ?: return
        selectTab(tab.first, tab.second)
    }

    private fun allTabs() =
        openFiles.indices.map { 0 to it } + openTextures.indices.map { 1 to it } + openImageSets.indices.map { 2 to it } +
            openStyles.indices.map { 3 to it }

    private fun selectTab(
        kind: Int,
        index: Int,
    ) {
        when (kind) {
            0 -> selectLayout(index)
            1 -> selectTexture(index)
            2 -> selectImageSet(index)
            else -> selectStyles(index)
        }
    }

    /** Ctrl+= and Ctrl+-: the layout canvas zooms about its centre. */
    fun zoomLayoutBy(factor: Float) {
        val cs = lastCanvasSize
        if (cs == Size.Zero) return
        val newZoom = (zoom * factor).coerceIn(0.1f, 8f)
        val centre = Offset(cs.width / 2f, cs.height / 2f)
        val world = (centre - panOffset) / zoom
        zoom = newZoom
        panOffset = centre - world * newZoom
    }

    // A question with a confirm button, shown over the whole window
    var confirmation by mutableStateOf<Confirmation?>(null)

    // Find and replace dialog, and the strip that shows the layout at several resolutions at once
    var bulkDialog by mutableStateOf<BulkMode?>(null)
    var compareResolutions by mutableStateOf(false)

    fun applyBulk(
        mode: BulkMode,
        key: String,
        find: String,
        replace: String,
        selectedOnly: Boolean,
    ) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val scope = if (selectedOnly) selectedIds.toSet() else null
        val (root, changed) = bulkReplace(doc.root, scope, mode, key, find, replace)
        if (changed == 0) return
        pushUndo(idx, doc.root, if (mode == BulkMode.NAME) "Замена в именах ($changed)" else "Замена $key ($changed)")
        updateActiveRoot(root)
        notify("Изменено виджетов: $changed", ToastKind.SUCCESS)
    }

    /** Puts Enforce Script that declares and finds the selected widgets on the clipboard. */
    fun copyScriptSnippet() {
        val doc = activeFile ?: return
        val nodes = selectedIds.filter { it != doc.root.id }.mapNotNull { doc.root.findNode(it) }
        if (nodes.isEmpty()) return
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(scriptSnippet(nodes)), null)
        }.onFailure {
            notify("Не удалось скопировать: ${it.message ?: it.javaClass.simpleName}", ToastKind.ERROR)
            return
        }
        notify("Код доступа скопирован: ${nodes.size}", ToastKind.SUCCESS)
    }

    // The look copied with "Copy properties"
    private var copiedAppearance: Map<String, String>? = null

    /** Wraps the selected widgets (they must share a parent) in a new container of [type]. */
    fun groupSelected(
        type: WidgetType = WidgetType.FRAME,
        label: String = "Group",
    ) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id }
        if (ids.isEmpty()) return
        val layout = layoutTree(doc.root, screenRect(this))
        val name = uniqueName(label, doc.root.allNames())
        val result = wrapped(doc.root, ids, layout, type, name)
        if (result == null) {
            notify("Группировать можно виджеты одного родителя", ToastKind.INFO)
            return
        }
        pushUndo(idx, doc.root, "Группировка в $name")
        updateActiveRoot(result.first)
        selectedIds.clear()
        selectedIds.add(result.second)
    }

    fun ungroupSelected() {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val id = selectedIds.singleOrNull()?.takeIf { it != doc.root.id } ?: return
        val result = ungrouped(doc.root, id, layoutTree(doc.root, screenRect(this)))
        if (result == null) {
            notify("Разгруппировать можно контейнер с виджетами внутри", ToastKind.INFO)
            return
        }
        pushUndo(idx, doc.root, "Разгруппировка")
        updateActiveRoot(result.first)
        selectedIds.clear()
        selectedIds.addAll(result.second)
    }

    fun reorderSelected(how: Reorder) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id }
        if (ids.isEmpty()) return
        // Going forward the one nearest the end goes first, going back the first one, so that they do not step over each other
        val ordered = if (how == Reorder.FORWARD || how == Reorder.FRONT) ids.reversed() else ids
        var root = doc.root
        var changed = false
        for (id in ordered) {
            root = reordered(root, id, how)?.also { changed = true } ?: root
        }
        if (!changed) return
        pushUndo(idx, doc.root, "Порядок отрисовки")
        updateActiveRoot(root)
    }

    fun distributeSelected(horizontal: Boolean) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id }
        if (ids.size < 3) {
            notify("Для равных интервалов нужно выбрать три виджета и больше", ToastKind.INFO)
            return
        }
        val result = distributed(doc.root, ids, layoutTree(doc.root, screenRect(this)), horizontal)
        pushUndo(idx, doc.root, if (horizontal) "Интервалы по горизонтали" else "Интервалы по вертикали")
        updateActiveRoot(result)
    }

    /** Changes the anchor of a widget. The offset on the axis that changed goes back to zero. */
    fun reanchor(
        id: UUID,
        halign: String,
        valign: String,
    ) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val result = reanchored(doc.root, id, halign, valign)
        pushUndo(idx, doc.root, "Якорь $halign, $valign")
        updateActiveRoot(result)
    }

    fun copyAppearance() {
        val node = selectedIds.firstOrNull()?.let { id -> activeFile?.root?.findNode(id) } ?: return
        copiedAppearance = node.appearance()
        notify("Свойства скопированы: ${node.node.name}")
    }

    fun pasteAppearance() {
        val look = copiedAppearance
        if (look == null) {
            notify("Сначала скопируйте свойства (Ctrl+Alt+C)", ToastKind.INFO)
            return
        }
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id }
        if (ids.isEmpty()) return
        pushUndo(idx, doc.root, "Вставка свойств")
        updateActiveRoot(ids.fold(doc.root) { acc, id -> acc.updateNode(id) { it.withAppearance(look) } })
    }

    /** Zooms so that the selected widgets fill most of the canvas. */
    fun zoomToSelection() {
        val doc = activeFile ?: return
        val cs = lastCanvasSize
        if (cs == Size.Zero) return
        val layout = layoutTree(doc.root, screenRect(this))
        val rects = selectedIds.mapNotNull { layout.rects[it] }
        if (rects.isEmpty()) return
        val union = Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
        val z = min(cs.width * 0.8f / union.width.coerceAtLeast(1f), cs.height * 0.8f / union.height.coerceAtLeast(1f)).coerceIn(0.1f, 8f)
        zoom = z
        panOffset = Offset(cs.width / 2f - union.center.x * z, cs.height / 2f - union.center.y * z)
    }

    // Shown while dragging (snap guides) and while Alt is held over another widget (distances between the two)
    var snapGuides by mutableStateOf<List<GuideLine>>(emptyList())

    // The edge or corner under the pointer, for the cursor, and the size shown while a widget is resized
    var hoverHandle by mutableStateOf<ResizeHandle?>(null)
    var resizeLabel by mutableStateOf<Pair<Rect, String>?>(null)
    var snapDistances by mutableStateOf<List<EdgeDistance>>(emptyList())
    var hoverWorld by mutableStateOf<Offset?>(null)
    var altHeld by mutableStateOf(false)

    // The tab of the right panel, and signals other parts of the window send: put the cursor in the name field,
    // give the canvas the keyboard back
    var rightTab by mutableStateOf(0)
    var focusNameTick by mutableStateOf(0)
    var canvasFocusTick by mutableStateOf(0)

    var canvasMenu by mutableStateOf<CanvasMenu?>(null)

    /** Set while the window that picks an icon is open: what to do with the chosen `set:NAME image:IMAGE` text. */
    var iconPicker by mutableStateOf<((String) -> Unit)?>(null)

    /** Writes the layout reference of a file chosen by the user: with the `{GUID}` of its `.meta`, when it has one. */
    fun textureReference(text: String): String {
        val clean =
            text
                .trim()
                .trim('"')
                .replace(Regex("^\\{[0-9A-Fa-f]+}"), "")
                .replace('\\', '/')
        if (clean.isEmpty()) return ""
        val file = listOf(File(clean), File(workDrivePath, clean)).firstOrNull { it.isFile }
        return file?.let { resourceReference(it) } ?: clean
    }

    fun requestRename() {
        if (selectedIds.size != 1) return
        rightTab = 0
        focusNameTick++
    }

    private var lastNudgeAt = 0L

    /** Moves the selection by [dx], [dy] layout pixels. Keys pressed in a row become one step of the history. */
    fun nudgeSelected(
        dx: Float,
        dy: Float,
    ) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id && it !in lockedIds }
        if (ids.isEmpty()) return
        val layout = layoutTree(doc.root, screenRect(this))
        val moved = ids.fold(doc.root) { acc, id -> acc.movedBy(id, dx, dy, layout.parentRects[id] ?: screenRect(this)) }
        val now = System.currentTimeMillis()
        if (doc.undoLabels.lastOrNull() != "Сдвиг" || now - lastNudgeAt > 800) pushUndo(idx, doc.root, "Сдвиг")
        lastNudgeAt = now
        updateActiveRoot(moved)
    }

    fun copySelected(): Boolean {
        val doc = activeFile ?: return false
        val nodes = selectedIds.filter { it != doc.root.id }.mapNotNull { doc.root.findNode(it) }
        if (nodes.isEmpty()) return false
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(clipboardText(nodes)), null)
        }.onFailure {
            notify("Не удалось скопировать: ${it.message ?: it.javaClass.simpleName}", ToastKind.ERROR)
            return false
        }
        notify(if (nodes.size == 1) "Скопировано: ${nodes[0].node.name}" else "Скопировано виджетов: ${nodes.size}")
        return true
    }

    fun cutSelected() {
        if (copySelected()) deleteSelected()
    }

    /** Puts the widgets of the clipboard next to the selected widget, or into the root. */
    fun paste() {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val text =
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
            }.getOrNull().orEmpty()
        val pasted = parseClipboard(text)
        if (pasted == null) {
            notify("В буфере нет виджетов", ToastKind.INFO)
            return
        }
        val target = selectedIds.firstOrNull()?.takeIf { it != doc.root.id }?.let { doc.root.parentOf(it) } ?: doc.root
        val taken = doc.root.allNames().toMutableSet()
        val named =
            pasted.map { n ->
                val name = uniqueName(n.node.name, taken)
                taken += name
                n.copy(node = n.node.copy(name = name))
            }
        val screen = screenRect(this)
        var root = doc.root.updateNode(target.id) { it.copy(children = (it.children + named).toMutableList()) }
        val parentRect = layoutTree(root, screen).rects[target.id] ?: screen
        // Shifted, so that a copy pasted over the original does not hide it
        for (n in named) root = root.movedBy(n.id, 10f, 10f, parentRect)
        pushUndo(idx, doc.root, if (named.size == 1) "Вставка ${named[0].node.name}" else "Вставка (${named.size})")
        updateActiveRoot(root)
        selectedIds.clear()
        selectedIds.addAll(named.map { it.id })
    }

    var showRulers by mutableStateOf(false)
    var showMinimap by mutableStateOf(false)

    fun deleteSelected() {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id }
        if (ids.isEmpty()) return
        val label =
            if (ids.size == 1) "Удаление ${doc.root.findNode(ids[0])?.node?.name.orEmpty()}".trim() else "Удаление ${ids.size} виджетов"
        pushUndo(idx, doc.root, label)
        updateActiveRoot(ids.fold(doc.root) { acc, id -> acc.removeNode(id) })
        selectedIds.clear()
    }

    fun duplicateSelected(offset: Float = 10f) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id }
        if (ids.isEmpty()) return
        val screen = screenRect(this)
        var root = doc.root
        val copies = mutableListOf<UUID>()
        for (id in ids) {
            val (withCopy, copyId) = root.withCopyOf(id) ?: continue
            val parent = layoutTree(withCopy, screen).parentRects[copyId] ?: screen
            // The copy is shifted a little, so that it does not hide the original
            root = if (offset == 0f) withCopy else withCopy.movedBy(copyId, offset, offset, parent)
            copies += copyId
        }
        if (copies.isEmpty()) return
        pushUndo(idx, doc.root, if (copies.size == 1) "Дубликат" else "Дубликаты (${copies.size})")
        updateActiveRoot(root)
        selectedIds.clear()
        selectedIds.addAll(copies)
    }

    fun alignSelected(mode: AlignMode) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val ids = selectedIds.filter { it != doc.root.id }
        if (ids.size < 2) return
        val layout = layoutTree(doc.root, screenRect(this))
        val aligned = alignedRoot(doc.root, ids, layout, mode)
        pushUndo(idx, doc.root, "Выравнивание")
        updateActiveRoot(aligned)
    }

    // Messages in the corner. They remove themselves after a few seconds
    val toasts = mutableStateListOf<Toast>()
    private var toastCounter = 0L

    fun notify(
        text: String,
        kind: ToastKind = ToastKind.INFO,
    ) {
        scope.launch(Dispatchers.Main) {
            val toast = Toast(++toastCounter, text, kind)
            toasts.add(toast)
            while (toasts.size > 4) toasts.removeAt(0)
            delay(if (kind == ToastKind.ERROR) 8000 else 3500)
            toasts.remove(toast)
        }
    }

    /** Goes back [steps] actions, or forward when [steps] is negative. */
    fun jumpHistory(steps: Int) {
        val idx = activeFileIndex
        repeat(kotlin.math.abs(steps)) { if (steps > 0) undo(idx) else redo(idx) }
    }

    fun pushUndo(
        index: Int,
        snapshot: EditorWidgetNode,
        label: String = "Изменение",
    ) {
        val doc = openFiles.getOrNull(index) ?: return
        doc.undoStack.addLast(snapshot)
        doc.undoLabels.addLast(label)
        while (doc.undoStack.size > undoLimit) {
            doc.undoStack.removeFirst()
            doc.undoLabels.removeFirst()
        }
        doc.redoStack.clear()
        doc.redoLabels.clear()
        openFiles[index] = doc.copy(isModified = true)
    }

    fun undo(index: Int) {
        val doc = openFiles.getOrNull(index) ?: return
        if (doc.undoStack.isEmpty()) return
        doc.redoStack.addLast(doc.root)
        doc.redoLabels.addLast(doc.undoLabels.removeLast())
        val prev = doc.undoStack.removeLast()
        openFiles[index] = doc.copy(root = prev, isModified = true)
    }

    fun redo(index: Int) {
        val doc = openFiles.getOrNull(index) ?: return
        if (doc.redoStack.isEmpty()) return
        doc.undoStack.addLast(doc.root)
        doc.undoLabels.addLast(doc.redoLabels.removeLast())
        val next = doc.redoStack.removeLast()
        openFiles[index] = doc.copy(root = next, isModified = true)
    }

    fun updateActiveRoot(newRoot: EditorWidgetNode) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        openFiles[idx] = doc.copy(root = newRoot, isModified = true)
    }

    /** 100%: one layout pixel on one screen pixel, the screen in the middle of the view. */
    fun zoomActual() {
        val cs = lastCanvasSize
        if (cs == Size.Zero) return
        val refW = if (selectedResolution == Resolution.FIT) screenSize.width else selectedResolution.width.toFloat()
        val refH = if (selectedResolution == Resolution.FIT) screenSize.height else selectedResolution.height.toFloat()
        zoom = 1f
        panOffset = Offset((cs.width - refW) / 2f, (cs.height - refH) / 2f)
    }

    fun centerView() {
        val cs = lastCanvasSize
        if (cs == Size.Zero) return
        if (selectedResolution == Resolution.FIT) {
            val refW = screenSize.width
            val refH = screenSize.height
            val fitZoom = min(cs.width * 0.9f / refW, cs.height * 0.9f / refH)
            zoom = fitZoom
            panOffset =
                Offset(
                    (cs.width - refW * fitZoom) / 2f,
                    (cs.height - refH * fitZoom) / 2f,
                )
            return
        }
        val refW = selectedResolution.width.toFloat()
        val refH = selectedResolution.height.toFloat()
        val fitZoom = min(cs.width * 0.9f / refW, cs.height * 0.9f / refH)
        zoom = fitZoom
        panOffset =
            Offset(
                (cs.width - refW * fitZoom) / 2f,
                (cs.height - refH * fitZoom) / 2f,
            )
    }

    fun addWidgetToLayout(
        type: WidgetType,
        name: String,
        parentId: UUID?,
        worldPos: Offset?,
    ) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val targetId = parentId ?: selectedIds.firstOrNull() ?: doc.root.id
        val usePixel = worldPos != null
        val cleanName = sanitizeWidgetName(name)
        if (cleanName != name.trim()) notify("Имя виджета записано латиницей: $cleanName", ToastKind.INFO)
        val newNode =
            EditorWidgetNode(
                node =
                    WidgetNode(
                        type = type,
                        originalClassName = type.className,
                        name = cleanName,
                        properties =
                            linkedMapOf(
                                "position" to if (usePixel) "${fmt(worldPos!!.x)} ${fmt(worldPos.y)}" else "0 0",
                                "size" to if (usePixel) "100 50" else "0.1 0.1",
                                "hexactpos" to if (usePixel) "1" else "0",
                                "vexactpos" to if (usePixel) "1" else "0",
                                "hexactsize" to if (usePixel) "1" else "0",
                                "vexactsize" to if (usePixel) "1" else "0",
                            ).also { props ->
                                // A new widget starts with the empty vanilla style, when its type has one
                                if (vanillaStylesFor(type.className, doc.path).any { it.name == "blank" }) props["style"] = "blank"
                            },
                    ),
            )
        pushUndo(idx, doc.root, "Добавление $cleanName")
        val newRoot =
            doc.root.updateNode(targetId) { n ->
                n.copy(children = (n.children + newNode).toMutableList())
            }
        updateActiveRoot(newRoot)
        selectedIds.clear()
        selectedIds.add(newNode.id)
    }

    fun reparentNode(
        nodeId: UUID,
        newParentId: UUID,
    ) {
        if (nodeId == newParentId) return
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        if (doc.root.id == nodeId) return
        val movingNode = doc.root.findNode(nodeId) ?: return
        if (movingNode.containsId(newParentId)) return
        pushUndo(idx, doc.root, "Перенос ${movingNode.node.name.ifEmpty { "виджета" }}")
        val stripped = doc.root.removeNode(nodeId)
        val newRoot =
            stripped.updateNode(newParentId) { n ->
                n.copy(children = (n.children + movingNode).toMutableList())
            }
        updateActiveRoot(newRoot)
    }

    /**
     * Returns the decoded texture for an `imageTexture` value (`"{GUID}path/to/file.edds"`), or null while it is
     * loading or when the file cannot be found or decoded. Reading it from a draw scope redraws once loaded.
     */
    fun textureFor(ref: String): ImageBitmap? {
        val key =
            ref
                .trim()
                .trim('"')
                .replace(Regex("^\\{[0-9A-Fa-f]+}"), "")
                .replace('\\', '/')
        if (key.isEmpty()) return null
        if (textureCache.containsKey(key)) return textureCache[key]
        if (texturesLoading.add(key)) {
            scope.launch(Dispatchers.IO) {
                val bitmap = runCatching { loadTexture(key) }.getOrNull()
                withContext(Dispatchers.Main) { textureCache[key] = bitmap }
            }
        }
        return null
    }

    private val pictureCache = HashMap<String, ImageBitmap?>()

    private val sdfFonts = mutableStateMapOf<String, SdfFont?>()
    private val sdfFontsLoading = HashSet<String>()

    /**
     * The SDF font a `font` property names (`gui/fonts/sdf_MetronBook24`), or null while it loads, when it is not an SDF font
     * or when its files are not found. Reading it from a draw scope redraws once it is there.
     */
    internal fun sdfFontFor(
        fontProperty: String,
        nearPath: String?,
    ): SdfFont? {
        val path =
            fontProperty
                .trim()
                .trim('"')
                .replace('\\', '/')
        // The bitmap Metron fonts (`gui/fonts/Metron16`, `Metron-Bold`...) are drawn from the SDF font of their family: the same
        // letters at the size the widget asks for
        val last = path.substringAfterLast('/')
        val sdfPath =
            when {
                last.startsWith("sdf_", ignoreCase = true) -> path
                last.startsWith("metron", ignoreCase = true) && last.contains("light", ignoreCase = true) -> "gui/fonts/sdf_MetronLight24"
                last.startsWith("metron", ignoreCase = true) -> "gui/fonts/sdf_MetronBook24"
                else -> return null
            }
        val key = sdfPath.lowercase() + "|" + workDrivePath
        if (sdfFonts.containsKey(key)) return sdfFonts[key]
        if (sdfFontsLoading.add(key)) {
            val roots = resourceRoots(nearPath)
            scope.launch(Dispatchers.IO) {
                val font = runCatching { loadSdfFont(sdfPath, roots) }.getOrNull()
                withContext(Dispatchers.Main) { sdfFonts[key] = font }
            }
        }
        return null
    }

    private val bitmapSizesCache = java.util.concurrent.ConcurrentHashMap<String, List<Int>>()
    private val bitmapFonts = mutableStateMapOf<String, BitmapFont?>()
    private val bitmapFontsLoading = HashSet<String>()

    /**
     * The font a `font` property names, at the size [pixels]: an SDF font as it is, a bitmap font (`gui/fonts/Metron`, `Metron16`,
     * `Metron-Bold`) as the file of the size that is nearest to [pixels], falling back to the SDF font of its family. Null
     * while it loads or when no file is found.
     */
    internal fun gameFontFor(
        fontProperty: String,
        nearPath: String?,
        pixels: Float,
    ): GameFont? {
        val path =
            fontProperty
                .trim()
                .trim('"')
                .replace('\\', '/')
        val last = path.substringAfterLast('/').trimEnd('.')
        if (last.startsWith("sdf_", ignoreCase = true)) return sdfFontFor(path, nearPath)
        val directory = path.substringBeforeLast('/', "gui/fonts")
        val family = last.trimEnd { it.isDigit() }
        val fixed = last.takeLastWhile { it.isDigit() }.toIntOrNull()
        val roots = resourceRoots(nearPath)
        // A font of a mod can be made in a size of its own, written in its name
        // Asked for every text on every frame, so the files are looked for once
        val sizes =
            bitmapSizesCache.getOrPut("$directory|$family|$fixed|$workDrivePath|${gamePbo?.file}|${nearPath?.let { File(it).parent }}") {
                (BITMAP_FONT_SIZES.toList() + listOfNotNull(fixed)).distinct().sorted().filter { size ->
                    roots.any { inRoot(it, "$directory/$family$size.fnt").isFile }
                }
            }
        if (sizes.isEmpty()) return sdfFontFor(path, nearPath)
        val size =
            when {
                fixed != null && fixed in sizes -> fixed
                else -> sizes.minBy { kotlin.math.abs(kotlin.math.ln(it / pixels.coerceAtLeast(1f))) }
            }
        val key = "$directory/$family$size".lowercase() + "|" + workDrivePath
        if (bitmapFonts.containsKey(key)) return bitmapFonts[key]
        if (bitmapFontsLoading.add(key)) {
            scope.launch(Dispatchers.IO) {
                val font = runCatching { loadBitmapFont("$directory/$family$size", roots) }.getOrNull()
                withContext(Dispatchers.Main) { bitmapFonts[key] = font }
            }
        }
        return null
    }

    private fun loadBitmapFont(
        path: String,
        roots: List<File>,
    ): BitmapFont? {
        fun find(extension: String) = roots.map { inRoot(it, "$path.$extension") }.firstOrNull { it.isFile }
        val fnt = find("fnt") ?: return null
        val edds = find("edds") ?: return null
        val (size, glyphs) = BitmapFont.readGlyphs(fnt) ?: return null
        return BitmapFont(path, size, glyphs, EddsCodec.decode(readBytesOf(edds)).toBufferedImage())
    }

    /** The name of the font of a text widget: `font`, else the font of its style, else the MetronBook of the style `Normal`. */
    fun fontNameFor(
        node: EditorWidgetNode,
        nearPath: String?,
    ): String {
        node.node.properties["font"]
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        val styleName =
            node.node.properties["style"]
                ?.trim()
                ?.trim('"')
                .orEmpty()
        val className = node.node.originalClassName.ifEmpty { node.node.type.className }
        val styles = vanillaStylesFor(className, nearPath)
        val style = styles.firstOrNull { it.name == styleName } ?: styles.firstOrNull { it.name == "Normal" || it.name == "DayZNormal" }
        return style?.font?.takeIf { it.isNotEmpty() } ?: "gui/fonts/MetronBook"
    }

    /** The size the text of [node] takes for a box of [boxWidth] by [boxHeight] pixels, for `size to text h` and `v`. */
    internal fun textSizeFor(
        node: EditorWidgetNode,
        nearPath: String?,
        boxWidth: Float,
        boxHeight: Float,
    ): androidx.compose.ui.geometry.Size? {
        if (node.node.type !in setOf(WidgetType.TEXT, WidgetType.RICH_TEXT, WidgetType.MULTILINE_TEXT)) return null
        val fontName = fontNameFor(node, nearPath)
        return measureText(node, { px -> gameFontFor(fontName, nearPath, px) }, boxWidth, boxHeight, fontName)
    }

    /** The work drive and every folder above [nearPath]: where `gui/...` paths are looked for. */
    private fun fileRoots(nearPath: String?): List<File> {
        val roots = mutableListOf<File>()
        gamePbo?.let { roots += PboFile(it, "") }
        if (workDrivePath.isNotBlank()) roots += File(workDrivePath)
        // A path in a layout starts with the folder of its mod
        connectedMods().forEach { mod -> File(mod).parentFile?.let { roots += it } }
        var dir = nearPath?.let { File(it).parentFile }
        while (dir != null) {
            roots += dir
            dir = dir.parentFile
        }
        return roots
    }

    private val resourceRootsCache = java.util.concurrent.ConcurrentHashMap<String, List<File>>()

    /**
     * [fileRoots] with the root of the newest vanilla data first. A work drive can keep several extractions of the game's
     * `gui` folder (one per game build, told apart by `gui/rap.txt`); styles, imagesets, fonts and textures of an old build
     * draw an old picture, so the newest is tried first.
     */
    private fun resourceRoots(nearPath: String?): List<File> =
        resourceRootsCache.getOrPut("$workDrivePath|${gamePbo?.file}|${nearPath?.let { File(it).parent }}") {
            val all = fileRoots(nearPath).distinct()
            val vanilla = all.filter { inRoot(it, "gui/looknfeel/dayzwidgets.styles").isFile }

            fun build(root: File) =
                runCatching {
                    Regex("version=(\\d+)")
                        .find(readTextOf(inRoot(root, "gui/rap.txt")))
                        ?.groupValues
                        ?.get(1)
                        ?.toLong()
                }.getOrNull() ?: 0L
            val newest = vanilla.maxByOrNull(::build)
            if (newest == null) all else listOf(newest) + all.filter { it != newest }
        }

    private fun loadSdfFont(
        path: String,
        roots: List<File>,
    ): SdfFont? {
        fun find(
            relative: String,
            extension: String,
        ) = roots.map { inRoot(it, "$relative.$extension") }.firstOrNull { it.isFile }
        val fnt = find(path, "fnt") ?: return null
        val edds = find(path, "edds") ?: return null
        val (size, glyphs) = SdfFont.readGlyphs(fnt) ?: return null
        val atlas = EddsCodec.decode(readBytesOf(edds)).toBufferedImage()
        val cell = (glyphs[1].x - glyphs[0].x - 2).coerceAtLeast(8)
        // Advances in thousandths of an em: the biggest size of the family has the finest ones (a size 72 font tells them to
        // 1/72), the 24 px MetronBook has them exactly in its text description
        val family = path.replace(Regex("\\d+$"), "")
        val fine = find(family + "72", "fnt")?.let { SdfFont.readGlyphs(it) }
        val exact = find(path, "fsdf")?.let { runCatching { readTextOf(it) }.getOrNull() }?.let(::fsdfAdvances)
        val advances =
            IntArray(SdfFont.LAST - SdfFont.FIRST + 1) { i ->
                exact?.get(i) ?: fine?.let { (fineSize, records) -> records[i].advance * 1000 / fineSize }
                    ?: (glyphs[i].advance * 1000 / size)
            }
        return SdfFont(path, size, glyphs, atlas, advances, cell, SdfFont.estimateSlope(atlas, glyphs['I'.code - SdfFont.FIRST], cell))
    }

    /** The `Advances` of the ASCII characters from the text description of an SDF font. */
    private fun fsdfAdvances(text: String): IntArray? {
        fun numbers(name: String) =
            Regex(name + "\\s*\\{([^}]*)}")
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toIntOrNull() }
        val characters = numbers("Characters") ?: return null
        val advances = numbers("Advances") ?: return null
        val byChar = characters.zip(advances).toMap()
        if (!(SdfFont.FIRST..SdfFont.LAST).all { it in byChar }) return null
        return IntArray(SdfFont.LAST - SdfFont.FIRST + 1) { byChar.getValue(SdfFont.FIRST + it) }
    }

    /** The colour of the letters of a text widget: the colour of its style times its own `color`. */
    fun textColorFor(
        node: EditorWidgetNode,
        nearPath: String?,
    ): Color {
        val styleName =
            node.node.properties["style"]
                ?.trim()
                ?.trim('"')
                .orEmpty()
        val className = node.node.originalClassName.ifEmpty { node.node.type.className }
        val base =
            styleName
                .takeIf { it.isNotEmpty() }
                ?.let { name -> vanillaStylesFor(className, nearPath).firstOrNull { it.name == name } }
                ?.let { styleColorOf(it.color) }
                ?: Color.White
        // A button names the colour of its letters itself with `text color` (a dark label on a white button)
        if (node.node.type == WidgetType.BUTTON) {
            node.node.properties["text color"]?.let { return parseWidgetColor(it) }
        }
        // The `color` of a button tints its skin; the letters keep the colour of the style (measured: a red button, white text)
        if (node.node.type == WidgetType.BUTTON || node.node.type == WidgetType.CHECKBOX ||
            node.node.type == WidgetType.THREE_STATE_CHECKBOX
        ) {
            return base
        }
        val own = node.node.properties["color"]?.let { parseWidgetColor(it) } ?: return base
        return Color(base.red * own.red, base.green * own.green, base.blue * own.blue, base.alpha * own.alpha)
    }

    /**
     * The picture an image widget shows: `image0` is either `set:NAME image:IMAGE` (a piece of an imageset, cut out once)
     * or the path of a texture; `imageTexture` is the fallback of widgets that have no `image0`. Null while the file loads
     * or when nothing is found.
     */
    fun pictureFor(
        node: EditorWidgetNode,
        nearPath: String?,
    ): ImageBitmap? {
        val picture = rawPictureFor(node, nearPath) ?: return null
        // Without `src alpha 1` Workbench ignores the alpha of the texture and shows its colours (a black icon on white)
        val useAlpha = node.node.properties["src alpha"]?.trim() == "1"
        return if (useAlpha) picture else opaqueCache.getOrPut(picture) { picture.withoutAlpha() }
    }

    private val opaqueCache = java.util.WeakHashMap<ImageBitmap, ImageBitmap>()

    private fun ImageBitmap.withoutAlpha(): ImageBitmap {
        val source = toAwtImage()
        val copy = java.awt.image.BufferedImage(source.width, source.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) copy.setRGB(x, y, source.getRGB(x, y) or (0xFF shl 24))
        }
        return copy.toComposeImageBitmap()
    }

    /** The piece of an imageset called [imageName] in the set [setName], cut from its atlas once; null while the atlas loads or when it is missing. */
    fun imagePiece(
        setName: String,
        imageName: String,
        nearPath: String?,
    ): ImageBitmap? {
        val key = "$setName|$imageName|$workDrivePath|$nearPath"
        if (pictureCache.containsKey(key)) return pictureCache[key]
        val set = imageSetOf(setName, nearPath)
        val def = set?.all?.firstOrNull { it.name == imageName }
        val texture = imageSetTextureOf(setName, nearPath)
        if (set == null || def == null || texture == null) {
            pictureCache[key] = null
            return null
        }
        val atlas = imageSetBitmap(texture) ?: return null
        val kx = atlas.width.toFloat() / set.refWidth
        val ky = atlas.height.toFloat() / set.refHeight
        val x = (def.x * kx).toInt().coerceIn(0, atlas.width - 1)
        val y = (def.y * ky).toInt().coerceIn(0, atlas.height - 1)
        val w = (def.width * kx).toInt().coerceIn(1, atlas.width - x)
        val h = (def.height * ky).toInt().coerceIn(1, atlas.height - y)
        val piece = runCatching { awtOf(atlas).getSubimage(x, y, w, h).toComposeImageBitmap() }.getOrNull()
        pictureCache[key] = piece
        return piece
    }

    /** Every image of every imageset the editor knows (the game's and the connected mods'): the set name and the image name. */
    fun allIcons(nearPath: String?): List<Pair<String, String>> =
        imageSetNames(nearPath).flatMap { set -> imageNamesOf(set, nearPath).distinct().map { set to it } }

    private fun rawPictureFor(
        node: EditorWidgetNode,
        nearPath: String?,
    ): ImageBitmap? {
        val image0 =
            node.node.properties["image0"]
                ?.trim()
                ?.trim('"')
                .orEmpty()
        val setMatch = Regex("^set:(\\S+)\\s+image:(\\S+)").find(image0)
        if (setMatch != null) {
            val (setName, imageName) = setMatch.destructured
            return imagePiece(setName, imageName, nearPath)
        }
        val path = image0.takeIf { it.isNotEmpty() } ?: node.node.properties["imageTexture"]
        return path?.let(::textureFor)
    }

    fun reloadTextures() {
        bitmapSizesCache.clear()
        pictureCache.clear()
        sdfFonts.clear()
        sdfFontsLoading.clear()
        bitmapFonts.clear()
        bitmapFontsLoading.clear()
        textureCache.clear()
        texturesLoading.clear()
    }

    private fun loadTexture(path: String): ImageBitmap? {
        val file =
            resourceRoots(activeFile?.path).map { inRoot(it, path) }.firstOrNull { it.isFile }
                ?: return null
        val image =
            if (file.extension.equals("edds", ignoreCase = true)) {
                EddsCodec.decode(readBytesOf(file)).toBufferedImage()
            } else {
                ImageIO.read(java.io.ByteArrayInputStream(readBytesOf(file)))
            }
        return image?.toComposeImageBitmap()
    }

    /** Puts a file at the top of the recent list, keeping the last eight. */
    fun noteRecent(path: String) {
        recentFiles.remove(path)
        recentFiles.add(0, path)
        while (recentFiles.size > MAX_RECENT) recentFiles.removeAt(recentFiles.lastIndex)
    }

    /** Opens a layout in the editor and anything else as a texture. */
    fun openAny(path: String) {
        when {
            path.endsWith(".layout", ignoreCase = true) -> openFile(path)
            path.endsWith(".imageset", ignoreCase = true) -> openImageSet(path)
            path.endsWith(".styles", ignoreCase = true) -> openStyles(path)
            else -> openTexture(path)
        }
    }

    fun selectLayout(index: Int) {
        activeFileIndex = index
        activeTextureIndex = -1
        activeImageSetIndex = -1
        activeStylesIndex = -1
        saveSession()
    }

    fun selectTexture(index: Int) {
        if (index !in openTextures.indices) return
        activeTextureIndex = index
        activeImageSetIndex = -1
        activeStylesIndex = -1
        saveSession()
    }

    /** Opens an .edds or an image file in a texture viewer tab, or switches to its tab when already open. */
    fun openTexture(path: String) {
        val normalized = runCatching { File(path).canonicalPath }.getOrDefault(path)
        val existing = openTextures.indexOfFirst { it.path == normalized }
        if (existing >= 0) {
            noteRecent(normalized)
            selectTexture(existing)
            return
        }
        openTextures += TextureDocument(path = normalized)
        noteRecent(normalized)
        selectTexture(openTextures.lastIndex)
        loadTextureDocument(normalized)
    }

    fun reloadTexture(path: String) {
        val idx = openTextures.indexOfFirst { it.path == path }
        if (idx < 0) return
        openTextures[idx] = TextureDocument(path = path)
        reloadTextures()
        loadTextureDocument(path)
    }

    /** Opens an `.imageset` in the atlas editor, or switches to its tab when it is open already. */
    fun openImageSet(path: String) {
        val normalized = runCatching { File(path).canonicalPath }.getOrDefault(path)
        val existing = openImageSets.indexOfFirst { it.path == normalized }
        if (existing >= 0) {
            noteRecent(normalized)
            selectImageSet(existing)
            return
        }
        val parsed = runCatching { ImageSetFormat.parse(readTextOf(fileAt(normalized))) }
        val set = parsed.getOrNull()
        if (set == null) {
            notify("Не удалось открыть ${File(path).name}: " + (parsed.exceptionOrNull()?.message ?: "это не imageset"), ToastKind.ERROR)
            return
        }
        openImageSets += ImageSetDocument(normalized, set.toModel())
        noteRecent(normalized)
        selectImageSet(openImageSets.lastIndex)
    }

    fun selectImageSet(index: Int) {
        if (index !in openImageSets.indices) return
        activeImageSetIndex = index
        activeStylesIndex = -1
        saveSession()
    }

    fun closeImageSet(index: Int) {
        val doc = openImageSets.getOrNull(index) ?: return
        if (doc.isModified) {
            confirmation =
                Confirmation(
                    title = "Закрыть ${doc.fileName}?",
                    message = "Есть несохранённые изменения, они пропадут.",
                    confirmLabel = "Закрыть",
                    danger = true,
                ) { dropImageSet(index) }
        } else {
            dropImageSet(index)
        }
    }

    private fun dropImageSet(index: Int) {
        if (index !in openImageSets.indices) return
        openImageSets.removeAt(index)
        activeImageSetIndex =
            when {
                openImageSets.isEmpty() -> -1
                index < activeImageSetIndex -> activeImageSetIndex - 1
                index == activeImageSetIndex -> (index - 1).coerceAtLeast(0)
                else -> activeImageSetIndex
            }
        saveSession()
    }

    /** Opens a `.styles` in the style editor, or switches to its tab when it is open already. */
    fun openStyles(path: String) {
        val normalized = runCatching { File(path).canonicalPath }.getOrDefault(path)
        val existing = openStyles.indexOfFirst { it.path == normalized }
        if (existing >= 0) {
            noteRecent(normalized)
            selectStyles(existing)
            return
        }
        val parsed = runCatching { StylesFormat.parse(readTextOf(fileAt(normalized))) }
        val sheet = parsed.getOrNull()
        if (sheet == null) {
            notify("Не удалось открыть ${File(path).name}: " + (parsed.exceptionOrNull()?.message ?: "это не .styles"), ToastKind.ERROR)
            return
        }
        openStyles += StylesDocument(normalized, sheet)
        noteRecent(normalized)
        selectStyles(openStyles.lastIndex)
    }

    fun selectStyles(index: Int) {
        if (index !in openStyles.indices) return
        activeStylesIndex = index
        saveSession()
    }

    fun closeStyles(index: Int) {
        val doc = openStyles.getOrNull(index) ?: return
        if (doc.isModified) {
            confirmation =
                Confirmation(
                    title = "Закрыть ${doc.fileName}?",
                    message = "Есть несохранённые изменения, они пропадут.",
                    confirmLabel = "Закрыть",
                    danger = true,
                ) { dropStyles(index) }
        } else {
            dropStyles(index)
        }
    }

    private fun dropStyles(index: Int) {
        if (index !in openStyles.indices) return
        openStyles.removeAt(index)
        activeStylesIndex =
            when {
                openStyles.isEmpty() -> -1
                index < activeStylesIndex -> activeStylesIndex - 1
                index == activeStylesIndex -> (index - 1).coerceAtLeast(0)
                else -> activeStylesIndex
            }
        saveSession()
    }

    fun saveActiveStyles() {
        val doc = activeStyles ?: return
        runCatching {
            File(doc.path).writeText(StylesFormat.serialize(doc.sheet))
        }.onSuccess {
            doc.markSaved()
            refreshVanillaStyles()
            notify("Сохранено: ${doc.fileName}", ToastKind.SUCCESS)
        }.onFailure {
            notify("Не удалось сохранить ${doc.fileName}: ${it.message ?: it.javaClass.simpleName}", ToastKind.ERROR)
        }
    }

    /** Forgets what was read from the vanilla styles and imagesets, so that the next draw reads the files again. */
    fun refreshVanillaStyles() {
        vanillaSheetFrom = null
        vanillaSheet = null
        skinPlans.clear()
        tileCache.clear()
        skinCache.clear()
        parsedImageSets.clear()
        imageSetIndexFor = null
    }

    fun saveActiveImageSet() {
        val doc = activeImageSet ?: return
        runCatching {
            File(doc.path).writeText(ImageSetFormat.serialize(doc.model.toImageSet()))
        }.onSuccess {
            doc.markSaved()
            notify("Сохранено: ${doc.fileName}", ToastKind.SUCCESS)
        }.onFailure {
            notify("Не удалось сохранить ${doc.fileName}: ${it.message ?: it.javaClass.simpleName}", ToastKind.ERROR)
        }
    }

    /**
     * The file that holds the texture of an imageset: the path from the file, tried against the work drive, against the
     * folders above the imageset and next to it. Null when none of them exists.
     */
    fun resolveImageSetTexture(
        imageSetPath: String,
        texturePath: String,
    ): File? {
        val clean =
            texturePath
                .trim()
                .trim('"')
                .replace(Regex("^\\{[0-9A-Fa-f]+}"), "")
                .replace('\\', '/')
        if (clean.isEmpty()) return null
        // The folders above the imageset come first: a texture belongs to the extraction its imageset is in
        val candidates = mutableListOf<File>()
        var dir = fileAt(imageSetPath).parentFile
        while (dir != null) {
            candidates += inRoot(dir, clean)
            dir = dir.parentFile
        }
        if (workDrivePath.isNotBlank()) candidates += File(workDrivePath, clean)
        connectedMods().forEach { mod -> File(mod).parentFile?.let { candidates += File(it, clean) } }
        candidates += File(File(imageSetPath).parentFile, clean.substringAfterLast('/'))
        return candidates.firstOrNull { it.isFile }
    }

    /** The decoded texture file, or null while it loads or when it cannot be read. Reading it from a draw scope redraws once loaded. */
    fun imageSetBitmap(file: File?): ImageBitmap? {
        if (file == null) return null
        val key = file.absolutePath
        if (imageSetBitmaps.containsKey(key)) return imageSetBitmaps[key]
        if (imageSetBitmapsLoading.add(key)) {
            scope.launch(Dispatchers.IO) {
                val bitmap =
                    runCatching {
                        val image =
                            if (file.extension.equals(
                                    "edds",
                                    ignoreCase = true,
                                )
                            ) {
                                EddsCodec.decode(readBytesOf(file)).toBufferedImage()
                            } else {
                                ImageIO.read(java.io.ByteArrayInputStream(readBytesOf(file)))
                            }
                        image?.toComposeImageBitmap()
                    }.onFailure { error ->
                        withContext(Dispatchers.Main) {
                            notify(
                                "Не удалось прочитать текстуру ${file.name}: ${error.message ?: error.javaClass.simpleName}",
                                ToastKind.ERROR,
                            )
                        }
                    }.getOrNull()
                withContext(Dispatchers.Main) { imageSetBitmaps[key] = bitmap }
            }
        }
        return null
    }

    /**
     * How an imageset names a file: the path from the work drive with forward slashes, and the `{GUID}` of its
     * `.meta` file in front when there is one. A file outside the work drive keeps its full path.
     */
    fun resourceReference(file: File): String {
        val root = workDrivePath.takeIf { it.isNotBlank() }?.let { File(it).canonicalFile }
        val canonical = runCatching { file.canonicalFile }.getOrDefault(file)
        val relative =
            if (root != null && canonical.path.startsWith(root.path, ignoreCase = true)) {
                canonical.path
                    .substring(root.path.length)
                    .trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')
            } else {
                canonical.path.replace(File.separatorChar, '/')
            }
        val guid =
            runCatching {
                Regex("Name\\s+\"(\\{[0-9A-Fa-f]+})").find(File(canonical.path + ".meta").readText())?.groupValues?.get(1)
            }.getOrNull().orEmpty()
        return guid + relative
    }

    // The styles of the game, read from gui/looknfeel/dayzwidgets.styles the first time they are asked for
    private var vanillaSheet: StyleSheet? = null
    private var vanillaSheetFrom: String? = null

    /**
     * The vanilla styles for [widgetClass], or an empty list when the file is not found or the widget type has none.
     * The file is looked for in the work drive and in the folders above [nearPath].
     */
    fun vanillaStylesFor(
        widgetClass: String,
        nearPath: String?,
    ): List<StyleDef> {
        val key = workDrivePath + "|" + nearPath + "|" + modScanDone
        if (vanillaSheetFrom != key) {
            vanillaSheetFrom = key
            val vanilla = findVanillaStyles(nearPath)?.let { runCatching { StylesFormat.parse(readTextOf(it)) }.getOrNull() }
            // The styles of the connected mods come after the vanilla ones
            val fromMods = modStyleFiles.mapNotNull { f -> runCatching { StylesFormat.parse(f.readText()) }.getOrNull() }
            vanillaSheet =
                if (fromMods.isEmpty()) vanilla else StyleSheet((vanilla?.widgets.orEmpty()) + fromMods.flatMap { it.widgets })
        }
        return vanillaSheet?.stylesFor(widgetClass).orEmpty()
    }

    private fun findVanillaStyles(nearPath: String?): File? {
        val relative = "gui/looknfeel/dayzwidgets.styles"
        return resourceRoots(nearPath).map { inRoot(it, relative) }.firstOrNull { it.isFile }
    }

    // imageset name -> file, for the folder gui/imagesets next to gui/looknfeel
    private var imageSetIndex: Map<String, File> = emptyMap()
    private var imageSetIndexFor: String? = null
    private val skinPlans = HashMap<String, SkinPlan?>()
    private val tileCache = HashMap<String, ImageBitmap?>()
    private val skinCache = HashMap<String, WidgetSkin>()
    private val awtOfAtlas = java.util.WeakHashMap<ImageBitmap, java.awt.image.BufferedImage>()

    // The atlas as an AWT image, converted once: every piece of a skin is cut from it
    private fun awtOf(atlas: ImageBitmap): java.awt.image.BufferedImage = awtOfAtlas.getOrPut(atlas) { atlas.toAwtImage() }

    private val parsedImageSets = HashMap<String, com.github.demandss.dayz_workbench_tool.imageset.ImageSet?>()

    /**
     * What the vanilla style of [node] paints in the normal state, or null when the widget has no vanilla style, the
     * style draws nothing or its atlas is still loading (reading it from a draw scope redraws once it is there).
     */
    fun skinFor(
        node: EditorWidgetNode,
        nearPath: String?,
        stateName: String = "Normal",
    ): WidgetSkin? {
        val styleName =
            node.node.properties["style"]
                ?.trim()
                ?.trim('"')
                .orEmpty()
                // A widget without `style` has the style `Default` of its class (a check box draws its box, a panel nothing)
                .ifEmpty { "Default" }
        val className = node.node.originalClassName.ifEmpty { node.node.type.className }
        val key = "$className|$styleName|$stateName|$workDrivePath|$nearPath"
        val plan =
            if (skinPlans.containsKey(key)) {
                skinPlans[key]
            } else {
                buildSkinPlan(className, styleName, stateName, nearPath).also { skinPlans[key] = it }
            } ?: return null
        val lifted = stateName == "Highlight" && plan.highlightEmpty && node.node.type == WidgetType.BUTTON
        return skinFromPlan(plan, key, if (lifted) HIGHLIGHT_LIFT else 0f)
    }

    /** True when the style of [node] is one of the vanilla styles of its type: whatever it paints, it is not a guess. */
    fun styleKnown(
        node: EditorWidgetNode,
        nearPath: String?,
    ): Boolean {
        val name =
            node.node.properties["style"]
                ?.trim()
                ?.trim('"')
                .orEmpty()
        return name.isNotEmpty() &&
            vanillaStylesFor(node.node.originalClassName.ifEmpty { node.node.type.className }, nearPath).any { it.name == name }
    }

    /** What [style] paints in the state [stateName], for the preview of the style editor. Null while the atlas loads. */
    fun previewSkin(
        style: StyleDef,
        stateName: String,
        nearPath: String?,
    ): WidgetSkin? {
        val key = "preview|$stateName|${style.hashCode()}|$nearPath"
        val plan =
            if (skinPlans.containsKey(key)) {
                skinPlans[key]
            } else {
                planFor(style, stateName, nearPath).also { skinPlans[key] = it }
            } ?: return null
        return skinFromPlan(plan, key, if (stateName == "Highlight" && plan.highlightEmpty) HIGHLIGHT_LIFT else 0f)
    }

    private fun skinFromPlan(
        plan: SkinPlan,
        key: String,
        lift: Float = 0f,
    ): WidgetSkin? {
        val atlas = imageSetBitmap(plan.textureFile) ?: return null
        // The same skin is asked for on every frame, so it is built once
        val cacheKey = "$key|$lift"
        skinCache[cacheKey]?.let { return it }
        val under = plan.base?.let { skinFromPlan(it, "$key|base") ?: return null }
        val kx = atlas.width.toFloat() / plan.refWidth
        val ky = atlas.height.toFloat() / plan.refHeight
        val source = HashMap<String, androidx.compose.ui.unit.IntRect>()
        val native = HashMap<String, Size>()
        for ((slot, d) in plan.images) {
            source[slot] =
                androidx.compose.ui.unit.IntRect(
                    (d.x * kx).toInt(),
                    (d.y * ky).toInt(),
                    ((d.x + d.width) * kx).toInt(),
                    ((d.y + d.height) * ky).toInt(),
                )
            // In texture pixels: Workbench draws an edge of the @2x texture four pixels wide, not the two of the reference size
            native[slot] = Size((source.getValue(slot).width).toFloat(), (source.getValue(slot).height).toFloat())
        }
        val flags = plan.images.mapValues { it.value.flags }
        // Every slot is cut out of the atlas once: drawing a piece of a 2048 px atlas on every frame is very slow
        val tiles =
            source
                .mapNotNull { (slot, r) ->
                    tileCache
                        .getOrPut("$key|$slot|${atlas.width}") {
                            runCatching {
                                awtOf(atlas)
                                    .getSubimage(r.left, r.top, r.width.coerceAtLeast(1), r.height.coerceAtLeast(1))
                                    .toComposeImageBitmap()
                            }.getOrNull()
                        }?.let { slot to it }
                }.toMap()
        return WidgetSkin(atlas, source, native, plan.styleColor, flags, tiles, lift, under).also { skinCache[cacheKey] = it }
    }

    private fun buildSkinPlan(
        widgetClass: String,
        styleName: String,
        stateName: String,
        nearPath: String?,
    ): SkinPlan? {
        val styles = vanillaStylesFor(widgetClass, nearPath)
        // A class with no style called Default (the three state check box) uses its first one
        val style =
            styles.firstOrNull { it.name == styleName } ?: (if (styleName == "Default") styles.firstOrNull() else null) ?: return null
        return planFor(style, stateName, nearPath)
    }

    /** The imagesets of gui/imagesets next to the looknfeel folder, by the name written inside them. */
    private fun imageSetFiles(nearPath: String?): Map<String, File> {
        val scanned = modScanDone
        val gui = findVanillaStyles(nearPath)?.parentFile?.parentFile
        val setsDir = gui?.let { childOf(it, "imagesets") }
        val key = "${setsDir?.absolutePath}|$scanned"
        if (imageSetIndexFor != key) {
            imageSetIndexFor = key
            parsedImageSets.clear()

            fun indexOf(files: List<File>) =
                files.mapNotNull { f ->
                    runCatching { ImageSetFormat.parse(readTextOf(f))?.name }.getOrNull()?.let { it to f }
                }
            val vanilla =
                setsDir
                    ?.listFiles { f -> f.extension.equals("imageset", ignoreCase = true) }
                    .orEmpty()
                    .toList()
            // A mod's imageset adds its name; it does not replace a vanilla one
            imageSetIndex = (indexOf(modImageSetFiles).reversed() + indexOf(vanilla)).toMap()
        }
        return imageSetIndex
    }

    private fun parsedImageSet(file: File) =
        parsedImageSets.getOrPut(file.absolutePath) { runCatching { ImageSetFormat.parse(readTextOf(file)) }.getOrNull() }

    /** The imageset called [setName] next to the looknfeel folder, or null. */
    fun imageSetOf(
        setName: String,
        nearPath: String?,
    ) = imageSetFiles(nearPath)[setName]?.let(::parsedImageSet)

    /** The texture file of the imageset called [setName] (the sharpest one it lists, which is what Workbench draws), or null when it is not found. */
    fun imageSetTextureOf(
        setName: String,
        nearPath: String?,
    ): File? {
        val file = imageSetFiles(nearPath)[setName] ?: return null
        val texture = parsedImageSet(file)?.textures?.maxByOrNull { it.mpix } ?: return null
        return resolveImageSetTexture(file.absolutePath, texture.path)
    }

    /** The names of the imagesets a style can use, for the drop-down of the style editor. */
    fun imageSetNames(nearPath: String?): List<String> = imageSetFiles(nearPath).keys.sorted()

    /** The names of the images in the imageset called [setName]. */
    fun imageNamesOf(
        setName: String,
        nearPath: String?,
    ): List<String> =
        imageSetFiles(nearPath)[setName]
            ?.let(::parsedImageSet)
            ?.all
            ?.map { it.name }
            .orEmpty()

    private fun planFor(
        style: StyleDef,
        stateName: String,
        nearPath: String?,
    ): SkinPlan? {
        val setFile = imageSetFiles(nearPath)[style.imageSet] ?: return null
        val set = parsedImageSet(setFile) ?: return null
        val byName = set.all.associateBy { it.name }

        // A state with images of its own is painted over the resting state (a lone Center fills the whole widget and covers
        // the frame of Normal when it is opaque). A state with none paints Normal.
        fun imagesOf(name: String) =
            style.states
                .firstOrNull { it.name == name }
                ?.items
                .orEmpty()
                .filter { it.image.isNotEmpty() }
                .mapNotNull { item -> byName[item.image]?.let { item.name to it } }
                .toMap()
        // A three state check box has no Normal: its resting look is Empty
        val resting = if (style.states.none { it.name == "Normal" } && style.states.any { it.name == "Empty" }) "Empty" else "Normal"
        val own = imagesOf(stateName)
        val images = own.ifEmpty { imagesOf(resting) }
        if (images.isEmpty()) return null
        val texture = set.textures.maxByOrNull { it.mpix } ?: return null
        val file = resolveImageSetTexture(setFile.absolutePath, texture.path) ?: return null
        val color = styleColorOf(style.color)
        val base =
            imagesOf(resting).takeIf { own.isNotEmpty() && stateName != resting && it.isNotEmpty() }?.let {
                SkinPlan(file, set.refWidth, set.refHeight, it, color)
            }
        return SkinPlan(file, set.refWidth, set.refHeight, images, color, imagesOf("Highlight").isEmpty(), base)
    }

    fun reloadImageSetTextures() {
        skinCache.clear()
        tileCache.clear()
        pictureCache.clear()
        imageSetBitmaps.clear()
        imageSetBitmapsLoading.clear()
    }

    fun closeTexture(index: Int) {
        if (index !in openTextures.indices) return
        openTextures.removeAt(index)
        activeTextureIndex =
            when {
                openTextures.isEmpty() -> -1
                index < activeTextureIndex -> activeTextureIndex - 1
                index == activeTextureIndex -> (index - 1).coerceAtLeast(0)
                else -> activeTextureIndex
            }
        saveSession()
    }

    /** Reopens what was open when the editor was last closed on this work drive. Missing files are skipped. */
    fun restoreSession() {
        val session = loadSession()
        restoring = true
        try {
            session.layouts.filter { File(it).isFile }.forEach { openFile(it) }
            session.textures.filter { File(it).isFile }.forEach { openTexture(it) }
            session.imageSets.filter { File(it).isFile }.forEach { openImageSet(it) }
            session.styles.filter { File(it).isFile }.forEach { openStyles(it) }
            if (session.activeIsStyles) {
                openStyles.indexOfFirst { it.path == session.active }.takeIf { it >= 0 }?.let { selectStyles(it) }
            } else if (session.activeIsImageSet) {
                openImageSets.indexOfFirst { it.path == session.active }.takeIf { it >= 0 }?.let { selectImageSet(it) }
            } else if (session.activeIsTexture) {
                openTextures.indexOfFirst { it.path == session.active }.takeIf { it >= 0 }?.let { selectTexture(it) }
            } else {
                openFiles.indexOfFirst { it.path == session.active }.takeIf { it >= 0 }?.let { selectLayout(it) }
            }
            session.referencePath.takeIf { it.isNotBlank() && File(it).isFile }?.let {
                referenceOpacity = session.referenceOpacity
                loadReference(it, show = session.referenceShown)
            }
        } finally {
            restoring = false
        }
        saveSession()
    }

    /** Loads a screenshot to lay over the canvas and shows it. */
    fun loadReference(
        path: String,
        show: Boolean = true,
    ) {
        scope.launch(Dispatchers.IO) {
            val bitmap = runCatching { ImageIO.read(File(path))?.toComposeImageBitmap() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    referenceImage = bitmap
                    referencePath = path
                    showReference = show
                    saveSession()
                }
            }
        }
    }

    fun clearReference() {
        referenceImage = null
        referencePath = ""
        saveSession()
    }

    fun saveReferenceSettings() = saveSession()

    fun saveTreeState(
        expanded: List<String>,
        selected: String,
    ) {
        treeExpanded = expanded
        treeSelected = selected
        saveSession()
    }

    private fun loadSession(): EditorSession =
        runCatching { ConfigManager.LayoutEditor.load().sessions[sessionKey] }.getOrNull() ?: EditorSession()

    private fun saveSession() {
        if (restoring) return
        runCatching {
            val config = ConfigManager.LayoutEditor.load()
            val texture = activeTexture
            val session =
                EditorSession(
                    layouts = openFiles.map { it.path },
                    textures = openTextures.map { it.path },
                    imageSets = openImageSets.map { it.path },
                    styles = openStyles.map { it.path },
                    activeIsStyles = activeStyles != null,
                    active = activeStyles?.path ?: activeImageSet?.path ?: texture?.path ?: activeFile?.path.orEmpty(),
                    activeIsTexture = texture != null && activeImageSet == null && activeStyles == null,
                    activeIsImageSet = activeImageSet != null,
                    expandedDirs = treeExpanded,
                    selectedPath = treeSelected,
                    referencePath = referencePath,
                    referenceOpacity = referenceOpacity,
                    referenceShown = showReference,
                    recent = recentFiles.toList(),
                )
            ConfigManager.LayoutEditor.save(config.copy(sessions = config.sessions + (sessionKey to session)))
        }
    }

    private fun loadTextureDocument(path: String) {
        scope.launch(Dispatchers.IO) {
            val file = fileAt(path)
            val loaded =
                try {
                    if (file.extension.equals("edds", ignoreCase = true)) {
                        val image = EddsCodec.decode(readBytesOf(file))
                        TextureDocument(path, image.toBufferedImage(), image.format, image.mipCount, file.length(), loading = false)
                    } else {
                        val image =
                            ImageIO.read(java.io.ByteArrayInputStream(readBytesOf(file))) ?: error("Неподдерживаемый формат изображения")
                        TextureDocument(path, image, file.extension.uppercase(), 1, file.length(), loading = false)
                    }
                } catch (e: Exception) {
                    TextureDocument(path, null, "", 0, file.length(), e.message ?: e.javaClass.simpleName, false)
                }
            withContext(Dispatchers.Main) {
                val idx = openTextures.indexOfFirst { it.path == path }
                if (idx >= 0) openTextures[idx] = loaded
                loaded.error?.let { notify("Не удалось прочитать ${file.name}: $it", ToastKind.ERROR) }
            }
        }
    }

    fun close() {
        runCatching { watchService.close() }
    }

    private fun fmt(f: Float): String {
        val l = f.toLong()
        return if (f == l.toFloat()) "$l" else "%.4g".format(f)
    }

    private fun watchDirectory(dir: Path) {
        if (watchedDirs.containsKey(dir)) return
        runCatching {
            watchedDirs[dir] = dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
        }
    }

    private fun startWatcher() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val key = runCatching { watchService.take() }.getOrNull() ?: break
                for (event in key.pollEvents()) {
                    @Suppress("UNCHECKED_CAST")
                    val ev = event as? WatchEvent<Path> ?: continue
                    val dir = key.watchable() as? Path ?: continue
                    val fullPath =
                        runCatching {
                            dir.resolve(ev.context()).toFile().canonicalPath
                        }.getOrNull() ?: continue
                    val own = ownWrites[fullPath]?.let { System.currentTimeMillis() - it < 2000 } == true
                    if (own) continue
                    withContext(Dispatchers.Main) {
                        if (openFiles.any { it.path == fullPath } &&
                            !filesChangedOnDisk.contains(fullPath)
                        ) {
                            filesChangedOnDisk += fullPath
                        }
                    }
                }
                key.reset()
            }
        }
    }

    // After every property above, which the scan fills in when it is done
    init {
        scanMods()
    }
}

// The sizes the bitmap fonts of the game are made in (gui/fonts/<Family><Size>.fnt)
private val BITMAP_FONT_SIZES = intArrayOf(12, 14, 16, 22, 28, 48, 58)
