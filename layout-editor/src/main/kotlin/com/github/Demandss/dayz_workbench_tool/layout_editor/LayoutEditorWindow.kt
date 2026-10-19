package com.github.demandss.dayz_workbench_tool.layout_editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSession
import com.github.demandss.dayz_workbench_tool.layout_editor.state.DragSource
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutDocument
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.PendingAdd
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.LayoutCanvas
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.TextureViewer
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.findProblems
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.Breadcrumbs
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.CommandPalette
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.EmptyState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.FileTreePanel
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.HierarchyPanel
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.HistoryPanel
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.Minimap
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.PaletteContent
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ProblemsPanel
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.PropertiesContent
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ReferencePanel
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.Rulers
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.SelectionBar
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.StatusBar
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ToastHost
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.ViewportChips
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.toolbar.Toolbar
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.components.DarkTabRow
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIconView
import com.github.demandss.dayz_workbench_tool.ui.icons.fileIcon
import com.github.demandss.dayz_workbench_tool.ui.pickFile
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import com.github.demandss.dayz_workbench_tool.ui.theme.AppColors
import com.github.demandss.dayz_workbench_tool.ui.theme.BuiltinThemes
import kotlinx.coroutines.launch
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private val appIcon by lazy {
    BitmapPainter(
        ImageIO
            .read(
                Thread
                    .currentThread()
                    .contextClassLoader!!
                    .getResourceAsStream("assets/logo/logo.png"),
            ).toComposeImageBitmap(),
    )
}

@Composable
fun LayoutEditorWindow(
    state: LayoutEditorState,
    themeColors: AppColors = BuiltinThemes.Dark.toAppColors(),
    onCloseRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val windowTitle =
        buildString {
            append("DayZ Layout Editor")
            val shownName = state.activeTexture?.fileName ?: state.activeFile?.fileName
            shownName?.let { append(" — $it") }
        }

    Window(
        onCloseRequest = {
            state.close()
            onCloseRequest()
        },
        title = windowTitle,
        icon = appIcon,
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
        resizable = true,
    ) {
        AppTheme(themeColors) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(FrameBackground)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (state.activeTexture != null) {
                            // The viewport shows a texture: only closing the tab applies
                            if (event.isCtrlPressed && event.key == Key.W) {
                                state.closeTexture(state.activeTextureIndex)
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent false
                        }
                        when {
                            event.isCtrlPressed && event.key == Key.P -> {
                                state.commandPaletteOpen = !state.commandPaletteOpen
                                true
                            }

                            state.commandPaletteOpen -> {
                                false
                            }

                            event.isCtrlPressed && event.key == Key.D -> {
                                state.duplicateSelected()
                                true
                            }

                            event.isCtrlPressed && !event.isShiftPressed && event.key == Key.S -> {
                                state.saveActiveFile()
                                true
                            }

                            event.isCtrlPressed && event.key == Key.Z -> {
                                state.undo(state.activeFileIndex)
                                true
                            }

                            event.isCtrlPressed && event.key == Key.Y -> {
                                state.redo(state.activeFileIndex)
                                true
                            }

                            event.isCtrlPressed && event.key == Key.W -> {
                                val idx = state.activeFileIndex
                                if (idx >= 0) state.closeFile(idx)
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }
                    // Drag interceptor on the root itself — parent Initial pass sees events
                    // before children's Main pass, and never consumes, so clicks work normally
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pos = event.changes.firstOrNull()?.position ?: continue
                                when (event.type) {
                                    PointerEventType.Move -> {
                                        if (state.dragSession != null) {
                                            state.dragScreenPos = pos
                                            state.dragHoverNodeId =
                                                state.hierarchyRowBounds
                                                    .entries
                                                    .firstOrNull { (_, rect) -> rect.contains(pos) }
                                                    ?.key
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        val session = state.dragSession
                                        if (session != null) {
                                            handleDrop(state, session, pos)
                                            state.dragSession = null
                                            state.dragHoverNodeId = null
                                        }
                                    }

                                    else -> {}
                                }
                                // Never consume — just observe
                            }
                        }
                    },
            ) {
                // ── Main layout ───────────────────────────────────────────
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        FileTreePanel(state, width = 220.dp)

                        Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))

                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            FileTabBar(state)

                            val texture = state.activeTexture
                            if (state.openFiles.isEmpty() && state.openTextures.isEmpty()) {
                                EmptyState(state, Modifier.weight(1f).fillMaxWidth()) {
                                    scope.launch {
                                        pickFile("Открыть файл", "*", state.workDrivePath.ifBlank { null })?.let { state.openAny(it) }
                                    }
                                }
                            } else if (texture != null) {
                                TextureViewer(state, texture, Modifier.weight(1f).fillMaxWidth())
                            } else {
                                Toolbar(state) {
                                    scope.launch {
                                        val path =
                                            pickFile(
                                                "Открыть layout-файл",
                                                "layout",
                                                state.workDrivePath.ifBlank { null },
                                            )
                                        if (path != null) state.openFile(path)
                                    }
                                }

                                val changedPath =
                                    state.activeFile
                                        ?.path
                                        ?.takeIf { state.filesChangedOnDisk.contains(it) }
                                if (changedPath != null) {
                                    ChangedOnDiskBanner(
                                        onReload = { state.reloadFile(changedPath) },
                                        onIgnore = { state.dismissChangedNotification(changedPath) },
                                    )
                                }

                                Breadcrumbs(state)

                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    Box(Modifier.weight(1f).fillMaxHeight()) {
                                        LayoutCanvas(
                                            state,
                                            Modifier
                                                .fillMaxSize()
                                                .onGloballyPositioned { coords ->
                                                    state.canvasBoundsInWindow = coords.boundsInWindow()
                                                },
                                        )
                                        ReferencePanel(state, Modifier.align(Alignment.TopEnd).padding(12.dp))
                                        if (state.showRulers && !state.livePreview) Rulers(state)
                                        if (state.showMinimap &&
                                            !state.livePreview
                                        ) {
                                            Minimap(state, Modifier.align(Alignment.BottomEnd).padding(10.dp))
                                        }
                                        ViewportChips(
                                            state,
                                            Modifier
                                                .align(
                                                    Alignment.BottomStart,
                                                ).padding(10.dp)
                                                .let { if (state.showRulers) it.padding(start = 18.dp) else it },
                                        )
                                        SelectionBar(state, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                                    }

                                    Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))

                                    RightPanel(state)
                                }
                            }
                        }
                    }

                    StatusBar(state)
                }

                // ── Drag ghost ────────────────────────────────────────────
                val drag = state.dragSession
                if (drag != null) {
                    val label =
                        when (val src = drag.source) {
                            is DragSource.FromPalette -> {
                                src.type.className.removeSuffix("WidgetClass")
                            }

                            is DragSource.FromHierarchy -> {
                                state.activeFile?.root?.findNode(src.nodeId)?.node?.let { n ->
                                    n.type.className.removeSuffix("WidgetClass") +
                                        if (n.name.isNotEmpty()) " · ${n.name}" else ""
                                } ?: "Widget"
                            }
                        }
                    val gx = (state.dragScreenPos.x + 14f).roundToInt()
                    val gy = (state.dragScreenPos.y + 14f).roundToInt()
                    Box(
                        Modifier
                            .size(0.dp)
                            .absoluteOffset { IntOffset(gx, gy) }
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .background(ElementsBackground)
                            .border(1.dp, Color(0xFF4A9EFF))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(label, color = TextForeground, fontSize = 11.sp)
                    }
                }

                ToastHost(state, Modifier.align(Alignment.BottomEnd).padding(bottom = 22.dp))
                CommandPalette(state) {
                    scope.launch {
                        pickFile("Открыть файл", "*", state.workDrivePath.ifBlank { null })?.let { state.openAny(it) }
                    }
                }

                // ── Name dialog ───────────────────────────────────────────
                state.pendingAdd?.let { pending ->
                    NameDialog(
                        typeName = pending.type.className.removeSuffix("WidgetClass"),
                        onConfirm = { name ->
                            state.addWidgetToLayout(pending.type, name, pending.parentId, pending.worldPos)
                            state.pendingAdd = null
                        },
                        onDismiss = { state.pendingAdd = null },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Drop handler
// ---------------------------------------------------------------------------

private fun screenToWorld(
    screen: Offset,
    zoom: Float,
    pan: Offset,
) = (screen - pan) / zoom

private fun handleDrop(
    state: LayoutEditorState,
    session: DragSession,
    releasePos: Offset,
) {
    val canvasBounds = state.canvasBoundsInWindow
    val hierarchyBounds = state.hierarchyBoundsInWindow
    val hoverNode = state.dragHoverNodeId

    when (val src = session.source) {
        is DragSource.FromPalette -> {
            when {
                canvasBounds.contains(releasePos) -> {
                    val localPos = releasePos - Offset(canvasBounds.left, canvasBounds.top)
                    val worldPos = screenToWorld(localPos, state.zoom, state.panOffset)
                    state.pendingAdd = PendingAdd(src.type, null, worldPos)
                }

                hierarchyBounds.contains(releasePos) && hoverNode != null -> {
                    state.pendingAdd = PendingAdd(src.type, hoverNode, null)
                }
            }
        }

        is DragSource.FromHierarchy -> {
            if (hoverNode != null && hoverNode != src.nodeId) {
                state.reparentNode(src.nodeId, hoverNode)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Name dialog
// ---------------------------------------------------------------------------

@Composable
private fun NameDialog(
    typeName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый $typeName", color = TextForeground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Имя виджета:", color = GhostlyText, fontSize = 12.sp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextForeground, fontSize = 13.sp),
                    cursorBrush = SolidColor(TextForeground),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(FrameBackground)
                                .border(1.dp, DarkenedBorder)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) { inner() }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent { ev ->
                                if (ev.key == Key.Enter && ev.type == KeyEventType.KeyDown) {
                                    onConfirm(name)
                                    true
                                } else {
                                    false
                                }
                            },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Добавить", color = SelectColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = GhostlyText)
            }
        },
        containerColor = ElementsBackground,
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// ---------------------------------------------------------------------------
// File tab bar
// ---------------------------------------------------------------------------

@Composable
private fun FileTabBar(state: LayoutEditorState) {
    if (state.openFiles.isEmpty() && state.openTextures.isEmpty()) return
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(ElementsBackground)
            // The wheel scrolls the tabs sideways, there is no vertical scrolling to give it to
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val dy =
                                event.changes
                                    .firstOrNull()
                                    ?.scrollDelta
                                    ?.y ?: 0f
                            scope.launch { scroll.scrollBy(dy * 40f) }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }.horizontalScroll(scroll),
    ) {
        state.openFiles.forEachIndexed { index, doc ->
            FileTab(
                icon = AppIcon.LAYOUT,
                label = doc.fileName,
                modified = doc.isModified,
                isActive = state.activeTextureIndex < 0 && index == state.activeFileIndex,
                onClick = { state.selectLayout(index) },
                onClose = { state.closeFile(index) },
            )
        }
        state.openTextures.forEachIndexed { index, doc ->
            FileTab(
                icon = fileIcon(doc.fileName),
                label = doc.fileName,
                isActive = index == state.activeTextureIndex,
                onClick = { state.selectTexture(index) },
                onClose = { state.closeTexture(index) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FileTab(
    icon: AppIcon,
    label: String,
    modified: Boolean = false,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val bg = if (isActive) FrameBackground else ElementsBackground
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(isActive) { if (isActive) requester.bringIntoView() }
    Row(
        modifier =
            Modifier
                .bringIntoViewRequester(requester)
                .background(bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(icon)
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextForeground, fontSize = 12.sp, maxLines = 1)
        if (modified) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).background(Color(0xFF4A9EFF), CircleShape))
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppIconView(AppIcon.UI_CLOSE, size = 12.dp, tint = GhostlyText)
        }
        Spacer(Modifier.width(2.dp))
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))
}

// ---------------------------------------------------------------------------
// Changed-on-disk banner
// ---------------------------------------------------------------------------

@Composable
private fun ChangedOnDiskBanner(
    onReload: () -> Unit,
    onIgnore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF4A3C00))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Файл изменён на диске.",
            color = Color(0xFFFFD04A),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        DarkButton("Перезагрузить", onClick = onReload)
        DarkButton("Игнорировать", onClick = onIgnore)
    }
}

// ---------------------------------------------------------------------------
// Right panel
// ---------------------------------------------------------------------------

@Composable
private fun RightPanel(state: LayoutEditorState) {
    var rightTab by remember { mutableStateOf(0) }
    Column(Modifier.width(260.dp).fillMaxHeight()) {
        HierarchyPanel(state, Modifier.weight(1f).fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(1.dp).background(DarkenedBorder))
        val problemCount = state.activeFile?.let { remember(it.root) { findProblems(it.root).size } } ?: 0
        DarkTabRow(
            tabs = listOf("Properties", "Palette", if (problemCount > 0) "Problems $problemCount" else "Problems", "History"),
            selectedIndex = rightTab,
            onTabSelected = { rightTab = it },
        )
        when (rightTab) {
            0 -> PropertiesContent(state, Modifier.weight(1.7f).fillMaxWidth())
            1 -> PaletteContent(state, Modifier.weight(1.7f).fillMaxWidth())
            2 -> ProblemsPanel(state, Modifier.weight(1.7f).fillMaxWidth())
            3 -> HistoryPanel(state, Modifier.weight(1.7f).fillMaxWidth())
        }
    }
}
