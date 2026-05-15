package su.demands.dayz_workbench_tool.layout_editor.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import su.demands.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import su.demands.dayz_workbench_tool.ui.theme.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

private data class TreeEntry(
    val path: Path,
    val name: String,
    val isDir: Boolean,
    val depth: Int
)

private fun buildFlatTree(root: Path, expandedDirs: Set<Path>): List<TreeEntry> {
    val result = mutableListOf<TreeEntry>()
    fun traverse(dir: Path, depth: Int) {
        val children = runCatching {
            Files.list(dir).sorted(Comparator { a, b ->
                val da = if (Files.isDirectory(a)) 0 else 1
                val db = if (Files.isDirectory(b)) 0 else 1
                if (da != db) da - db
                else a.fileName.toString().lowercase().compareTo(b.fileName.toString().lowercase())
            }).toList()
        }.getOrDefault(emptyList())
        for (child in children) {
            val isDir = Files.isDirectory(child)
            result.add(TreeEntry(child, child.fileName.toString(), isDir, depth))
            if (isDir && expandedDirs.contains(child)) traverse(child, depth + 1)
        }
    }
    if (Files.exists(root)) traverse(root, 0)
    return result
}

// ---------------------------------------------------------------------------
// Main composable
// ---------------------------------------------------------------------------

@Composable
fun FileTreePanel(state: LayoutEditorState, width: Dp) {
    val workRoot = state.workDrivePath
    val scope = rememberCoroutineScope()

    val expandedDirs = remember { mutableStateListOf<Path>() }
    var refreshTick by remember { mutableStateOf(0) }
    var renamingPath by remember { mutableStateOf<Path?>(null) }
    var deletingEntry by remember { mutableStateOf<TreeEntry?>(null) }
    var creatingIn by remember { mutableStateOf<Path?>(null) }
    var creatingDir by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf<Path?>(null) }

    val flatList = remember(refreshTick, expandedDirs.toList()) {
        val root = runCatching { Path.of(workRoot) }.getOrNull()
        if (root != null) buildFlatTree(root, expandedDirs.toHashSet()) else emptyList()
    }

    // File watcher — watch root and all expanded dirs
    DisposableEffect(workRoot, expandedDirs.toList()) {
        val ws = FileSystems.getDefault().newWatchService()
        val toWatch = mutableListOf<Path>()
        runCatching { toWatch.add(Path.of(workRoot)) }
        expandedDirs.forEach { toWatch.add(it) }

        for (dir in toWatch) {
            runCatching { dir.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) }
        }

        val job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val key = runCatching { ws.take() }.getOrNull() ?: break
                key.pollEvents()
                withContext(Dispatchers.Main) { refreshTick++ }
                key.reset()
            }
        }
        onDispose { job.cancel(); runCatching { ws.close() } }
    }

    Column(Modifier.width(width).fillMaxHeight().background(FrameBackground)) {
        // Header
        Row(
            Modifier.fillMaxWidth().height(22.dp).background(ElementsBackground).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Project", color = GhostlyText, fontSize = 10.sp, modifier = Modifier.weight(1f))
        }

        if (workRoot.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Не задан workdrive", color = GhostlyText, fontSize = 11.sp)
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            // Inline creation row at top of a dir
            if (creatingIn != null) {
                item(key = "creating") {
                    InlineNameField(
                        depth = 0,
                        placeholder = if (creatingDir) "Имя папки" else "Имя файла",
                        onCommit = { name ->
                            val parentDir = creatingIn!!
                            val isDir = creatingDir
                            creatingIn = null
                            if (name.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        val newName = if (!isDir && !name.endsWith(".layout")) "$name.layout" else name
                                        val newPath = parentDir.resolve(newName)
                                        if (isDir) Files.createDirectories(newPath)
                                        else {
                                            Files.createDirectories(newPath.parent)
                                            Files.writeString(newPath, "FrameWidgetClass RootFrame {\n}\n")
                                            withContext(Dispatchers.Main) { state.openFile(newPath.toString()) }
                                        }
                                    }
                                    withContext(Dispatchers.Main) { refreshTick++ }
                                }
                            }
                        },
                        onCancel = { creatingIn = null }
                    )
                }
            }

            items(flatList, key = { it.path.toString() }) { entry ->
                val isRenaming = renamingPath == entry.path
                var showMenu by remember { mutableStateOf(false) }

                if (isRenaming) {
                    InlineNameField(
                        depth = entry.depth,
                        placeholder = entry.name,
                        initialValue = entry.name,
                        onCommit = { newName ->
                            renamingPath = null
                            if (newName.isNotBlank() && newName != entry.name) {
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        Files.move(entry.path, entry.path.parent.resolve(newName))
                                    }
                                    withContext(Dispatchers.Main) { refreshTick++ }
                                }
                            }
                        },
                        onCancel = { renamingPath = null }
                    )
                } else {
                    Box {
                        TreeRow(
                            entry = entry,
                            isSelected = selectedPath == entry.path,
                            isExpanded = expandedDirs.contains(entry.path),
                            onSelect = { selectedPath = entry.path },
                            onToggle = {
                                if (expandedDirs.contains(entry.path)) expandedDirs.remove(entry.path)
                                else expandedDirs.add(entry.path)
                            },
                            onDoubleClick = {
                                if (entry.isDir) {
                                    if (expandedDirs.contains(entry.path)) expandedDirs.remove(entry.path)
                                    else expandedDirs.add(entry.path)
                                } else if (entry.name.endsWith(".layout")) {
                                    state.openFile(entry.path.toString())
                                }
                            },
                            onRightClick = { showMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(ElementsBackground)
                        ) {
                            if (entry.isDir) {
                                DropdownMenuItem(
                                    text = { Text("Новый .layout", color = TextForeground, fontSize = 11.sp) },
                                    onClick = { showMenu = false; creatingIn = entry.path; creatingDir = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Новая папка", color = TextForeground, fontSize = 11.sp) },
                                    onClick = { showMenu = false; creatingIn = entry.path; creatingDir = true }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Переименовать", color = TextForeground, fontSize = 11.sp) },
                                onClick = { showMenu = false; renamingPath = entry.path }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить...", color = Color(0xFFFF5555), fontSize = 11.sp) },
                                onClick = { showMenu = false; deletingEntry = entry }
                            )
                            DropdownMenuItem(
                                text = { Text("Показать в проводнике", color = TextForeground, fontSize = 11.sp) },
                                onClick = {
                                    showMenu = false
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            ProcessBuilder("explorer.exe", "/select,${entry.path.toAbsolutePath()}").start()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    deletingEntry?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            confirmButton = {
                TextButton(onClick = {
                    deletingEntry = null
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            if (target.isDir) {
                                Files.walk(target.path).sorted(Comparator.reverseOrder()).forEach(Files::delete)
                            } else {
                                Files.deleteIfExists(target.path)
                            }
                        }
                        withContext(Dispatchers.Main) { refreshTick++ }
                    }
                }) { Text("Удалить", color = Color(0xFFFF5555)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) { Text("Отмена", color = GhostlyText) }
            },
            title = { Text("Удалить «${target.name}»?", color = TextForeground) },
            text = { Text("Это действие необратимо.", color = GhostlyText, fontSize = 12.sp) },
            containerColor = ElementsBackground
        )
    }
}

// ---------------------------------------------------------------------------
// Tree row
// ---------------------------------------------------------------------------

@Composable
private fun TreeRow(
    entry: TreeEntry,
    isSelected: Boolean,
    isExpanded: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onDoubleClick: () -> Unit,
    onRightClick: () -> Unit
) {
    var clickTime by remember { mutableStateOf(0L) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(if (isSelected) SubSelectColor else Color.Transparent)
            .padding(start = (entry.depth * 12 + 2).dp)
            .pointerInput(entry.path) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when {
                            event.type == PointerEventType.Press && event.buttons.isPrimaryPressed -> {
                                onSelect()
                                val now = System.currentTimeMillis()
                                if (now - clickTime < 400) onDoubleClick()
                                clickTime = now
                                event.changes.forEach { it.consume() }
                            }
                            event.type == PointerEventType.Press && event.buttons.isSecondaryPressed -> {
                                onRightClick()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand arrow (dirs only)
        Box(
            Modifier
                .size(14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = entry.isDir,
                    onClick = onToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            if (entry.isDir) {
                Text(if (isExpanded) "▾" else "▸", color = GhostlyText, fontSize = 9.sp, lineHeight = 9.sp)
            }
        }

        // Icon
        Text(
            when {
                entry.isDir -> if (isExpanded) "📂" else "📁"
                entry.name.endsWith(".layout") -> "📐"
                else -> "📄"
            },
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 4.dp)
        )

        // Name
        val isLayout = !entry.isDir && entry.name.endsWith(".layout")
        Text(
            entry.name,
            color = if (isLayout) Color(0xFF8EC6FF) else TextForeground,
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Inline name field (rename / create)
// ---------------------------------------------------------------------------

@Composable
private fun InlineNameField(
    depth: Int,
    placeholder: String,
    initialValue: String = "",
    onCommit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(start = (depth * 12 + 18).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
            cursorBrush = SolidColor(TextForeground),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(FrameBackground)
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(placeholder, color = GhostlyText, fontSize = 11.sp)
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onKeyEvent { ev ->
                    when {
                        ev.key == Key.Enter && ev.type == KeyEventType.KeyDown -> { onCommit(text); true }
                        ev.key == Key.Escape && ev.type == KeyEventType.KeyDown -> { onCancel(); true }
                        else -> false
                    }
                }
        )
    }
}