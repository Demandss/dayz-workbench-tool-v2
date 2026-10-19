package com.github.demandss.dayz_workbench_tool.layout_editor.state

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.github.demandss.dayz_workbench_tool.config.ConfigManager
import com.github.demandss.dayz_workbench_tool.config.EditorSession
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
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.GuideLine
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.alignedRoot
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.allNames
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.clipboardText
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.layoutTree
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.movedBy
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.parentOf
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.parseClipboard
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.screenRect
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.uniqueName
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.withCopyOf
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
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

class LayoutEditorState(
    val workDrivePath: String,
    private val scope: CoroutineScope,
) {
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

    var zoom by mutableStateOf(1f)
    var panOffset by mutableStateOf(Offset.Zero)
    var selectedResolution by mutableStateOf(Resolution.R1920x1080)

    var lastCanvasSize by mutableStateOf(Size.Zero)

    var snapToGrid by mutableStateOf(false)
    var snapToShapes by mutableStateOf(false)
    var showBorders by mutableStateOf(true)
    var showSafeFrame by mutableStateOf(false)
    var showMeasure by mutableStateOf(true)

    // A clean view: no borders, labels, selection or measurements, only what the widgets draw
    var livePreview by mutableStateOf(false)

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
        startWatcher()
        loadSession().let {
            treeExpanded = it.expandedDirs
            treeSelected = it.selectedPath
            recentFiles.addAll(it.recent.filter { f -> File(f).isFile })
        }
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
            val root = LayoutParser.parse(File(normalized).readText()).toEditorNode()
            openFiles += LayoutDocument(path = normalized, root = root)
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
            val root = LayoutParser.parse(File(path).readText()).toEditorNode()
            openFiles[idx] = LayoutDocument(path = path, root = root)
        }
        filesChangedOnDisk.remove(path)
    }

    fun dismissChangedNotification(path: String) {
        filesChangedOnDisk.remove(path)
    }

    var commandPaletteOpen by mutableStateOf(false)

    // Shown while dragging (snap guides) and while Alt is held over another widget (distances between the two)
    var snapGuides by mutableStateOf<List<GuideLine>>(emptyList())
    var hoverWorld by mutableStateOf<Offset?>(null)
    var altHeld by mutableStateOf(false)

    // The tab of the right panel, and signals other parts of the window send: put the cursor in the name field,
    // give the canvas the keyboard back
    var rightTab by mutableStateOf(0)
    var focusNameTick by mutableStateOf(0)
    var canvasFocusTick by mutableStateOf(0)

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
        val newNode =
            EditorWidgetNode(
                node =
                    WidgetNode(
                        type = type,
                        originalClassName = type.className,
                        name = name,
                        properties =
                            linkedMapOf(
                                "position" to if (usePixel) "${fmt(worldPos!!.x)} ${fmt(worldPos.y)}" else "0 0",
                                "size" to if (usePixel) "100 50" else "0.1 0.1",
                                "hexactpos" to if (usePixel) "1" else "0",
                                "vexactpos" to if (usePixel) "1" else "0",
                                "hexactsize" to if (usePixel) "1" else "0",
                                "vexactsize" to if (usePixel) "1" else "0",
                            ),
                    ),
            )
        pushUndo(idx, doc.root, "Добавление $name")
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

    fun reloadTextures() {
        textureCache.clear()
        texturesLoading.clear()
    }

    private fun loadTexture(path: String): ImageBitmap? {
        val file = File(workDrivePath, path)
        if (!file.isFile) return null
        val image =
            if (file.extension.equals("edds", ignoreCase = true)) {
                EddsCodec.decode(file).toBufferedImage()
            } else {
                ImageIO.read(file)
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
        if (path.endsWith(".layout", ignoreCase = true)) openFile(path) else openTexture(path)
    }

    fun selectLayout(index: Int) {
        activeFileIndex = index
        activeTextureIndex = -1
        saveSession()
    }

    fun selectTexture(index: Int) {
        if (index !in openTextures.indices) return
        activeTextureIndex = index
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
            if (session.activeIsTexture) {
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
                    active = texture?.path ?: activeFile?.path.orEmpty(),
                    activeIsTexture = texture != null,
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
            val file = File(path)
            val loaded =
                try {
                    if (file.extension.equals("edds", ignoreCase = true)) {
                        val image = EddsCodec.decode(file)
                        TextureDocument(path, image.toBufferedImage(), image.format, image.mipCount, file.length(), loading = false)
                    } else {
                        val image = ImageIO.read(file) ?: error("Неподдерживаемый формат изображения")
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
}
