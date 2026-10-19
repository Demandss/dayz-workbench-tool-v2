package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import java.awt.Cursor
import java.util.UUID
import kotlin.math.round
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Pure helpers
// ---------------------------------------------------------------------------

private fun worldToScreen(
    world: Offset,
    zoom: Float,
    pan: Offset,
) = world * zoom + pan

private fun screenToWorld(
    screen: Offset,
    zoom: Float,
    pan: Offset,
) = (screen - pan) / zoom

/** The ratio of the texture of a picture, once it is loaded. */
private fun textureAspect(
    state: LayoutEditorState,
    node: EditorWidgetNode,
): Float? {
    val texture = node.prop("imageTexture")?.let(state::textureFor) ?: return null
    return if (texture.height > 0) texture.width.toFloat() / texture.height else null
}

/** The rectangle of the screen the layout is drawn on: the chosen resolution, or the real screen for FitToWindow. */
internal fun screenRect(state: LayoutEditorState): Rect {
    val r = state.selectedResolution
    return if (r == Resolution.FIT) {
        Rect(0f, 0f, state.screenSize.width, state.screenSize.height)
    } else {
        Rect(0f, 0f, r.width.toFloat(), r.height.toFloat())
    }
}

private fun hitTest(
    node: EditorWidgetNode,
    worldPos: Offset,
    layout: TreeLayout,
    hidden: Set<UUID>,
    locked: Set<UUID>,
): EditorWidgetNode? {
    if (!node.isVisible() || node.id in hidden) return null
    val rect = layout.rects[node.id] ?: return null
    val inside = rect.contains(worldPos)
    // Children that stick out of a parent which does not clip them can still be hit
    if (!inside && node.clipsChildren()) return null
    for (child in node.drawOrder().asReversed()) {
        hitTest(child, worldPos, layout, hidden, locked)?.let { return it }
    }
    return if (inside && node.id !in locked) node else null
}

private fun selectInBox(
    node: EditorWidgetNode,
    box: Rect,
    layout: TreeLayout,
    out: MutableList<UUID>,
    hidden: Set<UUID>,
    locked: Set<UUID>,
    isRoot: Boolean = false,
) {
    if (!node.isVisible() || node.id in hidden) return
    val rect = layout.rects[node.id] ?: return
    if (!isRoot && node.id !in locked && box.overlaps(rect)) out.add(node.id)
    for (child in node.children) selectInBox(child, box, layout, out, hidden, locked)
}

// ---------------------------------------------------------------------------
// Drag state
// ---------------------------------------------------------------------------

// How far the pointer has to move before a press on a widget turns into a drag
private const val DRAG_THRESHOLD = 3f

private data class WidgetDragInfo(
    val id: UUID,
    val origPosX: Float,
    val origPosY: Float,
    val hexactpos: Boolean,
    val vexactpos: Boolean,
    val signX: Float,
    val signY: Float,
    val parentRect: Rect,
)

// ---------------------------------------------------------------------------
// Draw helpers
// ---------------------------------------------------------------------------

private fun DrawScope.drawGrid(
    gridSize: Int,
    refW: Float,
    refH: Float,
    zoom: Float,
    pan: Offset,
) {
    val step = gridSize.toFloat()
    val col = Color(0x1AFFFFFF)
    var x = 0f
    while (x <= refW) {
        val sx = x * zoom + pan.x
        drawLine(col, Offset(sx, 0f), Offset(sx, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y <= refH) {
        val sy = y * zoom + pan.y
        drawLine(col, Offset(0f, sy), Offset(size.width, sy), 1f)
        y += step
    }
}

private val MeasureColor = Color(0xFFF2994A)

/** Draws each gap with a tick at both ends and its size in a chip on the line. */
private fun DrawScope.drawEdgeDistances(
    distances: List<EdgeDistance>,
    textMeasurer: TextMeasurer,
    zoom: Float,
    pan: Offset,
) {
    val tick = 4f
    val pad = 3f
    for (d in distances) {
        val a = worldToScreen(d.from, zoom, pan)
        val b = worldToScreen(d.to, zoom, pan)
        drawLine(MeasureColor, a, b, 1f)
        val horizontal = d.from.y == d.to.y
        for (end in listOf(a, b)) {
            if (horizontal) {
                drawLine(MeasureColor, Offset(end.x, end.y - tick), Offset(end.x, end.y + tick), 1f)
            } else {
                drawLine(MeasureColor, Offset(end.x - tick, end.y), Offset(end.x + tick, end.y), 1f)
            }
        }
        val label = textMeasurer.measure(formatNumber(round(d.value * 10f) / 10f), TextStyle(color = Color(0xFF1E1F22), fontSize = 10.sp))
        val chip = Size(label.size.width + pad * 2, label.size.height + pad * 2)
        val topLeft = Offset((a.x + b.x) / 2f - chip.width / 2f, (a.y + b.y) / 2f - chip.height / 2f)
        drawRect(MeasureColor, topLeft, chip)
        drawText(label, topLeft = Offset(topLeft.x + pad, topLeft.y + pad))
    }
}

internal fun DrawScope.drawWidgetTree(
    node: EditorWidgetNode,
    layout: TreeLayout,
    isRoot: Boolean,
    selectedIds: Set<UUID>,
    hidden: Set<UUID>,
    showBorders: Boolean,
    showLabels: Boolean,
    textMeasurer: TextMeasurer,
    zoom: Float,
    pan: Offset,
    textureFor: (String) -> ImageBitmap?,
) {
    if (!node.isVisible() || node.id in hidden) return
    val wRect = layout.rects[node.id] ?: return
    val tl = worldToScreen(Offset(wRect.left, wRect.top), zoom, pan)
    val br = worldToScreen(Offset(wRect.right, wRect.bottom), zoom, pan)
    val ss = Size((br.x - tl.x).coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f))
    val isSelected = selectedIds.contains(node.id)

    // Fill by type
    when (node.node.type) {
        WidgetType.PANEL, WidgetType.SMART_PANEL, WidgetType.WRAP_SPACER, WidgetType.GRID_SPACER, WidgetType.SCROLL, WidgetType.WINDOW -> {
            // What the style paints; a widget that paints nothing still gets a faint tint so it can be found
            val fill = node.fillColor()
            drawRect(fill ?: Color(0x0AFFFFFF), tl, ss)
        }

        WidgetType.FRAME -> {
            drawRect(Color(0x0AFFFFFF), tl, ss)
        }

        WidgetType.IMAGE -> {
            val texture = node.node.properties["imageTexture"]?.let(textureFor)
            if (texture != null && ss.width >= 1f && ss.height >= 1f) {
                val tint = node.node.properties["color"]?.let { parseWidgetColor(it) }
                drawImage(
                    image = texture,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(texture.width, texture.height),
                    dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                    dstSize = IntSize(ss.width.toInt().coerceAtLeast(1), ss.height.toInt().coerceAtLeast(1)),
                    colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) },
                )
            } else {
                drawRect(Color(0x22FFFFFF), tl, ss)
                drawLine(Color(0x55FFFFFF), tl, br, 1f)
                drawLine(Color(0x55FFFFFF), Offset(tl.x, br.y), Offset(br.x, tl.y), 1f)
            }
        }

        WidgetType.TEXT, WidgetType.MULTILINE_TEXT, WidgetType.RICH_TEXT -> {
            drawRect(Color(0x15FFFFFF), tl, ss)
            val label = node.node.properties["text"] ?: node.node.name
            if (showLabels && label.isNotEmpty() && ss.width > 4f && ss.height > 8f) {
                val measured =
                    textMeasurer.measure(
                        label,
                        TextStyle(color = Color(0xCCFFFFFF), fontSize = 11.sp),
                    )
                drawText(measured, topLeft = Offset(tl.x + 2f, tl.y + 1f))
            }
        }

        WidgetType.BUTTON -> {
            drawRect(Color(0x22AAAAFF), tl, ss)
            val label = node.node.properties["text"] ?: node.node.name
            if (showLabels && label.isNotEmpty() && ss.width > 4f && ss.height > 8f) {
                val measured =
                    textMeasurer.measure(
                        label,
                        TextStyle(color = Color(0xCCCCCCFF), fontSize = 11.sp),
                    )
                drawText(measured, topLeft = Offset(tl.x + 2f, tl.y + 1f))
            }
        }

        else -> {
            drawRect(Color(0x0DFFFFFF), tl, ss)
        }
    }

    // Border
    val showBorder = showBorders || isSelected
    if (showBorder && ss.width > 0f && ss.height > 0f) {
        val borderColor = if (isSelected) Color(0xFF4A9EFF) else Color(0x44FFFFFF)
        val stroke = if (isSelected) 2f else 1f
        drawRect(borderColor, tl, ss, style = Stroke(stroke))
    }

    // Widget type label (screen-space, fixed 10sp, only when big enough)
    if (showLabels && !isRoot && ss.width > 40f && ss.height > 14f) {
        val className =
            node.node.type.className
                .removeSuffix("WidgetClass")
        val label = if (node.node.name.isNotEmpty()) "$className · ${node.node.name}" else className
        val measured =
            textMeasurer.measure(
                label,
                TextStyle(color = Color(0x88FFFFFF), fontSize = 9.sp),
            )
        drawText(measured, topLeft = Offset(tl.x + 2f, tl.y + 2f))
    }

    // Recurse children: by priority, and cut at the parent's edge unless clipchildren is 0
    val children = node.drawOrder()
    if (node.clipsChildren()) {
        clipRect(tl.x, tl.y, br.x, br.y) {
            for (child in children) {
                drawWidgetTree(child, layout, false, selectedIds, hidden, showBorders, showLabels, textMeasurer, zoom, pan, textureFor)
            }
        }
    } else {
        for (child in children) {
            drawWidgetTree(child, layout, false, selectedIds, hidden, showBorders, showLabels, textMeasurer, zoom, pan, textureFor)
        }
    }

    // Selection resize handles
    if (isSelected && !isRoot && ss.width > 8f && ss.height > 8f) {
        val h = 6f
        for (corner in listOf(tl, Offset(br.x - h, tl.y), Offset(tl.x, br.y - h), Offset(br.x - h, br.y - h))) {
            drawRect(Color(0xFF4A9EFF), corner, Size(h, h))
            drawRect(Color(0xFF1A3A6E), corner, Size(h, h), style = Stroke(1f))
        }
    }
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

@Composable
fun LayoutCanvas(
    state: LayoutEditorState,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    var spacePressed by remember { mutableStateOf(false) }
    var selectionBox by remember { mutableStateOf<Rect?>(null) }
    val focusRequester = remember { FocusRequester() }

    val resolution = state.selectedResolution
    val refW = if (resolution == Resolution.FIT) state.screenSize.width else resolution.width.toFloat()
    val refH = if (resolution == Resolution.FIT) state.screenSize.height else resolution.height.toFloat()

    LaunchedEffect(state.activeFileIndex, resolution) {
        if (state.lastCanvasSize != Size.Zero) state.centerView()
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(state.canvasFocusTick) { if (state.canvasFocusTick > 0) runCatching { focusRequester.requestFocus() } }

    Canvas(
        modifier =
            modifier
                .clipToBounds()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.key == Key.Spacebar) {
                        spacePressed = ev.type == KeyEventType.KeyDown
                        true
                    } else if (handleCanvasKey(state, ev)) {
                        true
                    } else {
                        false
                    }
                }.pointerHoverIcon(resizeCursor(state.hoverHandle))
                .onSizeChanged {
                    val newSize = Size(it.width.toFloat(), it.height.toFloat())
                    val first = state.lastCanvasSize == Size.Zero
                    state.lastCanvasSize = newSize
                    if (first && state.activeFile != null) state.centerView()
                }.pointerInput(Unit) {
                    awaitPointerEventScope {
                        var isPanning = false
                        var panStartScreen = Offset.Zero
                        var panStartOffset = Offset.Zero

                        var isDragging = false
                        var dragStartScreen = Offset.Zero
                        var dragInfos: List<WidgetDragInfo> = emptyList()
                        var dragStartRoot: EditorWidgetNode? = null
                        var dragUnion: Rect? = null
                        var dragCandidates: List<Rect> = emptyList()
                        var undoPushed = false

                        // Resizing: which handle, the rectangle at the start and what the widget sits in
                        var resizeHandle: ResizeHandle? = null
                        var resizeId: UUID? = null
                        var resizeStart: Rect? = null
                        var resizeParent: Rect? = null
                        var resizeRoot: EditorWidgetNode? = null
                        var resizeCandidates: List<Rect> = emptyList()
                        var resizePushed = false
                        var resizeStartScreen = Offset.Zero

                        var isBoxSelecting = false
                        var boxStartWorld = Offset.Zero
                        var currentBox: Rect? = null

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            val zoom = state.zoom
                            val pan = state.panOffset

                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    if (!event.keyboardModifiers.isCtrlPressed) {
                                        // The wheel moves the view, with Shift sideways, as in Figma
                                        val scroll = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                        val sideways = event.keyboardModifiers.isShiftPressed
                                        val shift =
                                            if (sideways) {
                                                Offset(-(scroll.y + scroll.x) * 40f, 0f)
                                            } else {
                                                Offset(
                                                    -scroll.x * 40f,
                                                    -scroll.y * 40f,
                                                )
                                            }
                                        state.panOffset += shift
                                        event.changes.forEach { it.consume() }
                                        continue
                                    }
                                    val dy =
                                        event.changes
                                            .firstOrNull()
                                            ?.scrollDelta
                                            ?.y ?: 0f
                                    if (dy == 0f) continue
                                    val factor = if (dy > 0) 0.9f else 1.1f
                                    val newZoom = (zoom * factor).coerceIn(0.1f, 8f)
                                    val worldUnder = screenToWorld(pos, zoom, pan)
                                    state.zoom = newZoom
                                    state.panOffset = pos - worldUnder * newZoom
                                    event.changes.forEach { it.consume() }
                                }

                                PointerEventType.Press -> {
                                    val isMiddle = event.buttons.isTertiaryPressed
                                    val isPrimary = event.buttons.isPrimaryPressed
                                    when {
                                        isMiddle || (isPrimary && spacePressed) -> {
                                            isPanning = true
                                            panStartScreen = pos
                                            panStartOffset = pan
                                            event.changes.forEach { it.consume() }
                                        }

                                        isPrimary && state.hoverHandle != null && state.selectedIds.size == 1 &&
                                            resizeTargetFor(state) != null -> {
                                            runCatching { focusRequester.requestFocus() }
                                            val target = resizeTargetFor(state)!!
                                            resizeHandle = state.hoverHandle
                                            resizeId = target.id
                                            resizeStart = target.rect
                                            resizeParent = target.parent
                                            resizeRoot = target.root
                                            resizeCandidates = target.candidates
                                            resizePushed = false
                                            resizeStartScreen = pos
                                            event.changes.forEach { it.consume() }
                                        }

                                        isPrimary -> {
                                            runCatching { focusRequester.requestFocus() }
                                            val worldPos = screenToWorld(pos, zoom, pan)
                                            val doc = state.activeFile
                                            val refRect = screenRect(state)
                                            val layout = doc?.let { layoutTree(it.root, refRect) { n -> textureAspect(state, n) } }
                                            val hit =
                                                if (doc != null &&
                                                    layout != null
                                                ) {
                                                    hitTest(doc.root, worldPos, layout, state.hiddenIds.toSet(), state.lockedIds.toSet())
                                                } else {
                                                    null
                                                }
                                            val hitIsRoot = hit?.id == doc?.root?.id

                                            if (doc != null && hit != null && !hitIsRoot) {
                                                if (event.keyboardModifiers.isShiftPressed) {
                                                    if (state.selectedIds.contains(hit.id)) {
                                                        state.selectedIds.remove(hit.id)
                                                    } else {
                                                        state.selectedIds.add(hit.id)
                                                    }
                                                } else if (!state.selectedIds.contains(hit.id)) {
                                                    state.selectedIds.clear()
                                                    state.selectedIds.add(hit.id)
                                                }
                                                // Alt drags a copy and leaves the original where it is
                                                var dragDoc = doc
                                                var dragLayout = layout
                                                if (event.keyboardModifiers.isAltPressed) {
                                                    state.duplicateSelected(0f)
                                                    dragDoc = state.activeFile ?: doc
                                                    dragLayout = layoutTree(dragDoc.root, refRect) { n -> textureAspect(state, n) }
                                                }
                                                val parentRects = dragLayout?.parentRects.orEmpty()
                                                dragInfos =
                                                    state.selectedIds.mapNotNull { id ->
                                                        val n = dragDoc.root.findNode(id) ?: return@mapNotNull null
                                                        val pr = parentRects[id] ?: refRect
                                                        val pp = (n.node.properties["position"] ?: "0 0").trim().split(Regex("\\s+"))
                                                        WidgetDragInfo(
                                                            id = id,
                                                            origPosX = pp.getOrNull(0)?.toFloatOrNull() ?: 0f,
                                                            origPosY = pp.getOrNull(1)?.toFloatOrNull() ?: 0f,
                                                            hexactpos = n.node.properties["hexactpos"] == "1",
                                                            vexactpos = n.node.properties["vexactpos"] == "1",
                                                            signX = anchorDirection(n.node.properties["halign"]),
                                                            signY = anchorDirection(n.node.properties["valign"]),
                                                            parentRect = pr,
                                                        )
                                                    }
                                                // What the dragged widgets can snap to: everything that is not part of the selection
                                                val moving =
                                                    state.selectedIds
                                                        .flatMap { id ->
                                                            dragDoc.root
                                                                .findNode(id)
                                                                ?.allIds()
                                                                .orEmpty()
                                                        }.toSet()
                                                val selectedRects = state.selectedIds.mapNotNull { dragLayout?.rects?.get(it) }
                                                dragUnion =
                                                    selectedRects.takeIf { it.isNotEmpty() }?.let { rs ->
                                                        Rect(
                                                            rs.minOf { it.left },
                                                            rs.minOf { it.top },
                                                            rs.maxOf { it.right },
                                                            rs.maxOf { it.bottom },
                                                        )
                                                    }
                                                dragCandidates =
                                                    dragLayout
                                                        ?.rects
                                                        ?.filter { (id, _) -> id !in moving && id !in state.hiddenIds }
                                                        ?.values
                                                        ?.toList()
                                                        .orEmpty()
                                                isDragging = true
                                                dragStartScreen = pos
                                                dragStartRoot = dragDoc.root
                                                undoPushed = false
                                                event.changes.forEach { it.consume() }
                                            } else {
                                                if (!event.keyboardModifiers.isShiftPressed) state.selectedIds.clear()
                                                isBoxSelecting = true
                                                boxStartWorld = worldPos
                                                currentBox = null
                                                selectionBox = null
                                            }
                                        }
                                    }
                                }

                                PointerEventType.Move -> {
                                    when {
                                        isPanning -> {
                                            state.panOffset = panStartOffset + (pos - panStartScreen)
                                            event.changes.forEach { it.consume() }
                                        }

                                        resizeHandle != null -> {
                                            val handle = resizeHandle!!
                                            val start = resizeStart!!
                                            if (!resizePushed && (pos - resizeStartScreen).getDistance() >= DRAG_THRESHOLD) {
                                                resizeRoot?.let { state.pushUndo(state.activeFileIndex, it, "Изменение размера") }
                                                resizePushed = true
                                            }
                                            if (resizePushed) {
                                                val delta = (pos - resizeStartScreen) / zoom
                                                var rect =
                                                    resizedRect(
                                                        start,
                                                        handle,
                                                        delta,
                                                        keepAspect = event.keyboardModifiers.isShiftPressed,
                                                        fromCentre = event.keyboardModifiers.isAltPressed,
                                                    )
                                                if (!event.keyboardModifiers.isCtrlPressed) {
                                                    val (snapped, guides) = snapResized(rect, handle, resizeCandidates, 6f / zoom)
                                                    rect = snapped
                                                    state.snapGuides = guides
                                                } else {
                                                    state.snapGuides = emptyList()
                                                }
                                                val doc = state.activeFile
                                                val parent = resizeParent
                                                val id = resizeId
                                                if (doc != null && parent != null && id != null) {
                                                    state.updateActiveRoot(withRect(doc.root, id, rect, parent))
                                                    state.resizeLabel =
                                                        rect to "${formatNumber(round(rect.width))} × ${formatNumber(round(rect.height))}"
                                                }
                                            }
                                            event.changes.forEach { it.consume() }
                                        }

                                        isDragging && dragInfos.isNotEmpty() -> {
                                            // A plain click only selects. The first real step of a drag is what counts as an edit
                                            if (!undoPushed && (pos - dragStartScreen).getDistance() >= DRAG_THRESHOLD) {
                                                dragStartRoot?.let {
                                                    state.pushUndo(
                                                        state.activeFileIndex,
                                                        it,
                                                        if (dragInfos.size >
                                                            1
                                                        ) {
                                                            "Перемещение ${dragInfos.size} виджетов"
                                                        } else {
                                                            "Перемещение"
                                                        },
                                                    )
                                                }
                                                undoPushed = true
                                            }
                                            var worldDelta = (pos - dragStartScreen) / zoom
                                            // Positions in pixels are whole, so the measured gaps are as well
                                            if (dragInfos.all { it.hexactpos }) worldDelta = Offset(round(worldDelta.x), worldDelta.y)
                                            if (dragInfos.all { it.vexactpos }) worldDelta = Offset(worldDelta.x, round(worldDelta.y))
                                            // Snap to the neighbours. Ctrl switches it off
                                            val union = dragUnion
                                            if (union != null && undoPushed && !event.keyboardModifiers.isCtrlPressed) {
                                                val screenRect = screenRect(state)
                                                // The edges of the screen are snapped to, but no gap is measured to them
                                                val snap = snapMove(union, worldDelta, dragCandidates, 6f / zoom) { it != screenRect }
                                                worldDelta += Offset(snap.dx, snap.dy)
                                                state.snapGuides = snap.guides
                                                state.snapDistances = snap.distances
                                            } else {
                                                state.snapGuides = emptyList()
                                                state.snapDistances = emptyList()
                                            }
                                            val doc = state.activeFile
                                            if (doc != null && undoPushed) {
                                                var newRoot = doc.root
                                                for (info in dragInfos) {
                                                    val nx =
                                                        info.origPosX +
                                                            info.signX *
                                                            if (info.hexactpos) worldDelta.x else worldDelta.x / info.parentRect.width
                                                    val ny =
                                                        info.origPosY +
                                                            info.signY *
                                                            if (info.vexactpos) worldDelta.y else worldDelta.y / info.parentRect.height
                                                    newRoot =
                                                        newRoot.updateNode(info.id) { n ->
                                                            n.copy(
                                                                node =
                                                                    n.node.copy(
                                                                        properties =
                                                                            LinkedHashMap(n.node.properties).also {
                                                                                it["position"] =
                                                                                    "${formatNumber(
                                                                                        if (info.hexactpos) round(nx) else nx,
                                                                                    )} ${formatNumber(
                                                                                        if (info.vexactpos) round(ny) else ny,
                                                                                    )}"
                                                                            },
                                                                    ),
                                                            )
                                                        }
                                                }
                                                state.updateActiveRoot(newRoot)
                                            }
                                            event.changes.forEach { it.consume() }
                                        }

                                        isBoxSelecting -> {
                                            val wp = screenToWorld(pos, zoom, pan)
                                            val box =
                                                Rect(
                                                    left = minOf(boxStartWorld.x, wp.x),
                                                    top = minOf(boxStartWorld.y, wp.y),
                                                    right = maxOf(boxStartWorld.x, wp.x),
                                                    bottom = maxOf(boxStartWorld.y, wp.y),
                                                )
                                            currentBox = box
                                            selectionBox = box
                                        }

                                        else -> {
                                            // No button is down: remember where the pointer is for the Alt measurement
                                            state.hoverWorld = screenToWorld(pos, zoom, pan)
                                            state.altHeld = event.keyboardModifiers.isAltPressed
                                            state.hoverHandle =
                                                if (state.selectedIds.size == 1 && !spacePressed) {
                                                    resizeTargetFor(state)?.let { handleAt(it.rect, state.hoverWorld!!, 6f / zoom) }
                                                } else {
                                                    null
                                                }
                                        }
                                    }
                                }

                                PointerEventType.Release -> {
                                    if (isBoxSelecting) {
                                        val box = currentBox
                                        val doc = state.activeFile
                                        if (box != null && doc != null) {
                                            val hits = mutableListOf<UUID>()
                                            selectInBox(
                                                doc.root,
                                                box,
                                                layoutTree(doc.root, screenRect(state)) { n -> textureAspect(state, n) },
                                                hits,
                                                state.hiddenIds.toSet(),
                                                state.lockedIds.toSet(),
                                                isRoot = true,
                                            )
                                            hits.remove(doc.root.id)
                                            if (!event.keyboardModifiers.isShiftPressed) state.selectedIds.clear()
                                            for (id in hits) if (!state.selectedIds.contains(id)) state.selectedIds.add(id)
                                        }
                                        selectionBox = null
                                        currentBox = null
                                    }
                                    isPanning = false
                                    resizeHandle = null
                                    state.resizeLabel = null
                                    isDragging = false
                                    state.snapGuides = emptyList()
                                    state.snapDistances = emptyList()
                                    isBoxSelecting = false
                                    dragInfos = emptyList()
                                }

                                else -> {}
                            }
                        }
                    }
                },
    ) {
        val zoom = state.zoom
        val pan = state.panOffset
        val doc = state.activeFile
        val selectedSet = state.selectedIds.toHashSet()

        // 1. Infinite dark background
        drawRect(Color(0xFF1A1B1E))

        // 2. Grid (screen-space lines aligned to world grid)
        if (state.snapToGrid) drawGrid(state.gridSize, refW, refH, zoom, pan)

        // 3. Shadow behind work area
        val wtl = worldToScreen(Offset(-6f, -6f), zoom, pan)
        val wbr = worldToScreen(Offset(refW + 6f, refH + 6f), zoom, pan)
        drawRect(Color(0x66000000), wtl, Size(wbr.x - wtl.x, wbr.y - wtl.y))

        // 4. Work area
        val atl = worldToScreen(Offset.Zero, zoom, pan)
        val abr = worldToScreen(Offset(refW, refH), zoom, pan)
        val aSize = Size(abr.x - atl.x, abr.y - atl.y)
        drawRect(Color(0xFF232528), atl, aSize)
        drawRect(Color(0xFF555860), atl, aSize, style = Stroke(1f))

        // 5. Widgets
        val layout = doc?.let { layoutTree(it.root, Rect(0f, 0f, refW, refH)) { n -> textureAspect(state, n) } }
        if (doc != null && layout != null) {
            drawWidgetTree(
                doc.root,
                layout,
                isRoot = true,
                if (state.livePreview) emptySet() else selectedSet,
                state.hiddenIds.toHashSet(),
                state.showBorders && !state.livePreview,
                !state.livePreview,
                textMeasurer,
                zoom,
                pan,
                state::textureFor,
            )
        }

        // 6. Safe frame overlay
        if (state.showSafeFrame) {
            val sx = refW * 0.05f
            val sy = refH * 0.05f
            val stl = worldToScreen(Offset(sx, sy), zoom, pan)
            val sbr = worldToScreen(Offset(refW - sx, refH - sy), zoom, pan)
            drawRect(Color(0x22FFFF44), stl, Size(sbr.x - stl.x, sbr.y - stl.y))
            drawRect(Color(0x88FFFF44), stl, Size(sbr.x - stl.x, sbr.y - stl.y), style = Stroke(1f))
        }

        // The Workbench screenshot over the work area, under the measurements
        val reference = state.referenceImage
        if (state.showReference && reference != null) {
            drawImage(
                reference,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(reference.width, reference.height),
                dstOffset = IntOffset(atl.x.roundToInt(), atl.y.roundToInt()),
                dstSize = IntSize(aSize.width.roundToInt().coerceAtLeast(1), aSize.height.roundToInt().coerceAtLeast(1)),
                alpha = state.referenceOpacity,
                filterQuality = FilterQuality.Medium,
            )
        }

        // Gaps between the selected widgets and the edges of their parents
        if (doc != null && layout != null && state.showMeasure && !state.livePreview && selectedSet.isNotEmpty()) {
            for (id in selectedSet) {
                val node = doc.root.findNode(id) ?: continue
                val rect = layout.rects[id]
                val parent = layout.parentRects[id]
                if (node.id == doc.root.id || rect == null || parent == null) continue
                val props = node.node.properties
                drawEdgeDistances(
                    edgeDistances(rect, parent, props["halign"], props["valign"]),
                    textMeasurer,
                    zoom,
                    pan,
                )
            }
        }

        // Snap guides while dragging
        for (g in state.snapGuides) {
            val a = if (g.vertical) worldToScreen(Offset(g.at, g.from), zoom, pan) else worldToScreen(Offset(g.from, g.at), zoom, pan)
            val b = if (g.vertical) worldToScreen(Offset(g.at, g.to), zoom, pan) else worldToScreen(Offset(g.to, g.at), zoom, pan)
            drawLine(Color(0xFFFF4FA3), a, b, 1f)
        }
        // The gaps to the widgets that the dragged one snapped to, in layout pixels
        if (state.snapDistances.isNotEmpty()) drawEdgeDistances(state.snapDistances, textMeasurer, zoom, pan)

        // While a widget is resized its new size shows under it
        state.resizeLabel?.let { (rect, text) ->
            val label = textMeasurer.measure(text, TextStyle(color = Color.White, fontSize = 10.sp))
            val bl = worldToScreen(Offset(rect.center.x, rect.bottom), zoom, pan)
            val chip = Size(label.size.width + 10f, label.size.height + 6f)
            val topLeft = Offset(bl.x - chip.width / 2f, bl.y + 8f)
            drawRect(Color(0xFF346BD8), topLeft, chip)
            drawText(label, topLeft = Offset(topLeft.x + 5f, topLeft.y + 3f))
        }

        // Alt over another widget: the distances between it and the selected one
        val hover = state.hoverWorld
        if (doc != null && layout != null && state.altHeld && hover != null && selectedSet.size == 1 && !state.livePreview) {
            val selectedId = selectedSet.first()
            val selectedRect = layout.rects[selectedId]
            val target = hitTest(doc.root, hover, layout, state.hiddenIds.toSet() + selectedId, emptySet())
            val targetRect = target?.let { layout.rects[it.id] }
            if (selectedRect != null && targetRect != null) {
                val tl = worldToScreen(Offset(targetRect.left, targetRect.top), zoom, pan)
                val br = worldToScreen(Offset(targetRect.right, targetRect.bottom), zoom, pan)
                drawRect(MeasureColor, tl, Size(br.x - tl.x, br.y - tl.y), style = Stroke(1f))
                drawEdgeDistances(distancesBetween(selectedRect, targetRect), textMeasurer, zoom, pan)
            }
        }

        // 7. Selection box
        selectionBox?.let { box ->
            val stl = worldToScreen(Offset(box.left, box.top), zoom, pan)
            val sbr = worldToScreen(Offset(box.right, box.bottom), zoom, pan)
            val ss = Size(sbr.x - stl.x, sbr.y - stl.y)
            drawRect(Color(0x334A9EFF), stl, ss)
            drawRect(Color(0xFF4A9EFF), stl, ss, style = Stroke(1f))
        }
    }
}

/** What a resize needs to know about the one selected widget. */
private class ResizeTarget(
    val id: UUID,
    val rect: Rect,
    val parent: Rect,
    val root: EditorWidgetNode,
    val candidates: List<Rect>,
)

/** The selected widget with its rectangle, or null when it cannot be resized by hand (root, locked, hidden, nothing). */
private fun resizeTargetFor(state: LayoutEditorState): ResizeTarget? {
    val doc = state.activeFile ?: return null
    val id = state.selectedIds.singleOrNull() ?: return null
    if (id == doc.root.id || id in state.lockedIds || id in state.hiddenIds || state.livePreview) return null
    val layout = layoutTree(doc.root, screenRect(state)) { n -> textureAspect(state, n) }
    val rect = layout.rects[id] ?: return null
    val parent = layout.parentRects[id] ?: return null
    val own =
        doc.root
            .findNode(id)
            ?.allIds()
            .orEmpty()
            .toSet()
    val others =
        layout.rects
            .filter { (other, _) -> other !in own && other !in state.hiddenIds }
            .values
            .toList()
    return ResizeTarget(id, rect, parent, doc.root, others)
}

/** The mouse cursor for a handle: arrows along the edge or the diagonal. */
private fun resizeCursor(handle: ResizeHandle?): PointerIcon =
    when (handle) {
        null -> PointerIcon.Default
        ResizeHandle.E -> PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
        ResizeHandle.W -> PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR))
        ResizeHandle.N -> PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
        ResizeHandle.S -> PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR))
        ResizeHandle.NE -> PointerIcon(Cursor(Cursor.NE_RESIZE_CURSOR))
        ResizeHandle.NW -> PointerIcon(Cursor(Cursor.NW_RESIZE_CURSOR))
        ResizeHandle.SE -> PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR))
        ResizeHandle.SW -> PointerIcon(Cursor(Cursor.SW_RESIZE_CURSOR))
    }
