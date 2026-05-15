package su.demands.dayz_workbench_tool.layout_editor.state

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.*
import kotlin.math.min
import su.demands.dayz_workbench_tool.config.ConfigManager
import su.demands.dayz_workbench_tool.layout.LayoutParser
import su.demands.dayz_workbench_tool.layout.LayoutSerializer
import su.demands.dayz_workbench_tool.layout.WidgetNode
import su.demands.dayz_workbench_tool.layout.WidgetType
import su.demands.dayz_workbench_tool.layout_editor.EditorWidgetNode
import su.demands.dayz_workbench_tool.layout_editor.containsId
import su.demands.dayz_workbench_tool.layout_editor.findNode
import su.demands.dayz_workbench_tool.layout_editor.removeNode
import su.demands.dayz_workbench_tool.layout_editor.toEditorNode
import su.demands.dayz_workbench_tool.layout_editor.toWidgetNode
import su.demands.dayz_workbench_tool.layout_editor.updateNode
import java.io.File
import java.nio.file.*
import java.util.UUID

// ---------------------------------------------------------------------------
// Drag & drop types
// ---------------------------------------------------------------------------

sealed class DragSource {
    data class FromPalette(val type: WidgetType) : DragSource()
    data class FromHierarchy(val nodeId: UUID) : DragSource()
}

data class DragSession(val source: DragSource)

// ---------------------------------------------------------------------------
// Pending widget creation (shown before name dialog)
// ---------------------------------------------------------------------------

data class PendingAdd(
    val type: WidgetType,
    val parentId: UUID?,
    val worldPos: Offset?
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

class LayoutEditorState(
    val workDrivePath: String,
    private val scope: CoroutineScope
) {
    val screenSize: Size = java.awt.Toolkit.getDefaultToolkit().screenSize.let {
        Size(it.width.toFloat(), it.height.toFloat())
    }
    val openFiles = mutableStateListOf<LayoutDocument>()
    var activeFileIndex by mutableStateOf(-1)
    val selectedIds = mutableStateListOf<UUID>()
    val filesChangedOnDisk = mutableStateListOf<String>()

    var zoom by mutableStateOf(1f)
    var panOffset by mutableStateOf(Offset.Zero)
    var selectedResolution by mutableStateOf(Resolution.R1920x1080)

    var lastCanvasSize by mutableStateOf(Size.Zero)

    var snapToGrid by mutableStateOf(false)
    var snapToShapes by mutableStateOf(false)
    var showBorders by mutableStateOf(true)
    var showSafeFrame by mutableStateOf(false)
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

    private val undoLimit get() = ConfigManager.LayoutEditor.load().undoLimit

    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val watchedDirs = mutableMapOf<Path, WatchKey>()

    init { startWatcher() }

    fun openFile(path: String) {
        val normalized = runCatching { File(path).canonicalPath }.getOrDefault(path)
        val existing = openFiles.indexOfFirst { it.path == normalized }
        if (existing >= 0) { activeFileIndex = existing; return }
        runCatching {
            val root = LayoutParser.parse(File(normalized).readText()).toEditorNode()
            openFiles += LayoutDocument(path = normalized, root = root)
            activeFileIndex = openFiles.lastIndex
            File(normalized).parentFile?.toPath()?.let { watchDirectory(it) }
        }
    }

    fun closeFile(index: Int) {
        openFiles.removeAt(index)
        activeFileIndex = when {
            openFiles.isEmpty() -> -1
            index <= activeFileIndex -> (activeFileIndex - 1).coerceAtLeast(0)
            else -> activeFileIndex
        }
    }

    fun saveActiveFile() {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        runCatching {
            File(doc.path).writeText(LayoutSerializer.serialize(doc.root.toWidgetNode()))
            openFiles[idx] = doc.copy(isModified = false)
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

    fun pushUndo(index: Int, snapshot: EditorWidgetNode) {
        val doc = openFiles.getOrNull(index) ?: return
        doc.undoStack.addLast(snapshot)
        while (doc.undoStack.size > undoLimit) doc.undoStack.removeFirst()
        doc.redoStack.clear()
        openFiles[index] = doc.copy(isModified = true)
    }

    fun undo(index: Int) {
        val doc = openFiles.getOrNull(index) ?: return
        if (doc.undoStack.isEmpty()) return
        doc.redoStack.addLast(doc.root)
        val prev = doc.undoStack.removeLast()
        openFiles[index] = doc.copy(root = prev, isModified = true)
    }

    fun redo(index: Int) {
        val doc = openFiles.getOrNull(index) ?: return
        if (doc.redoStack.isEmpty()) return
        doc.undoStack.addLast(doc.root)
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
            panOffset = Offset(
                (cs.width - refW * fitZoom) / 2f,
                (cs.height - refH * fitZoom) / 2f
            )
            return
        }
        val refW = selectedResolution.width.toFloat()
        val refH = selectedResolution.height.toFloat()
        val fitZoom = min(cs.width * 0.9f / refW, cs.height * 0.9f / refH)
        zoom = fitZoom
        panOffset = Offset(
            (cs.width - refW * fitZoom) / 2f,
            (cs.height - refH * fitZoom) / 2f
        )
    }

    fun addWidgetToLayout(type: WidgetType, name: String, parentId: UUID?, worldPos: Offset?) {
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        val targetId = parentId ?: selectedIds.firstOrNull() ?: doc.root.id
        val usePixel = worldPos != null
        val newNode = EditorWidgetNode(
            node = WidgetNode(
                type = type,
                originalClassName = type.className,
                name = name,
                properties = linkedMapOf(
                    "position" to if (usePixel) "${fmt(worldPos!!.x)} ${fmt(worldPos.y)}" else "0 0",
                    "size" to if (usePixel) "100 50" else "0.1 0.1",
                    "hexactpos" to if (usePixel) "1" else "0",
                    "vexactpos" to if (usePixel) "1" else "0",
                    "hexactsize" to if (usePixel) "1" else "0",
                    "vexactsize" to if (usePixel) "1" else "0"
                )
            )
        )
        pushUndo(idx, doc.root)
        val newRoot = doc.root.updateNode(targetId) { n ->
            n.copy(children = (n.children + newNode).toMutableList())
        }
        updateActiveRoot(newRoot)
        selectedIds.clear()
        selectedIds.add(newNode.id)
    }

    fun reparentNode(nodeId: UUID, newParentId: UUID) {
        if (nodeId == newParentId) return
        val idx = activeFileIndex
        val doc = openFiles.getOrNull(idx) ?: return
        if (doc.root.id == nodeId) return
        val movingNode = doc.root.findNode(nodeId) ?: return
        if (movingNode.containsId(newParentId)) return
        pushUndo(idx, doc.root)
        val stripped = doc.root.removeNode(nodeId)
        val newRoot = stripped.updateNode(newParentId) { n ->
            n.copy(children = (n.children + movingNode).toMutableList())
        }
        updateActiveRoot(newRoot)
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
                    val fullPath = runCatching {
                        dir.resolve(ev.context()).toFile().canonicalPath
                    }.getOrNull() ?: continue
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