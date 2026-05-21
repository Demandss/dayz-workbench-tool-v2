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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.layout.WidgetType
import com.github.demandss.dayz_workbench_tool.layout_editor.EditorWidgetNode
import com.github.demandss.dayz_workbench_tool.layout_editor.findNode
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.Resolution
import com.github.demandss.dayz_workbench_tool.layout_editor.updateNode
import java.util.UUID

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

private fun computeRect(
    node: EditorWidgetNode,
    parentRect: Rect,
): Rect {
    val props = node.node.properties
    val pp = (props["position"] ?: "0 0").trim().split(Regex("\\s+"))
    val sp = (props["size"] ?: "0.1 0.1").trim().split(Regex("\\s+"))
    val px = pp.getOrNull(0)?.toFloatOrNull() ?: 0f
    val py = pp.getOrNull(1)?.toFloatOrNull() ?: 0f
    val sw = sp.getOrNull(0)?.toFloatOrNull() ?: 0.1f
    val sh = sp.getOrNull(1)?.toFloatOrNull() ?: 0.1f
    val hxp = props["hexactpos"] == "1"
    val vyp = props["vexactpos"] == "1"
    val hxs = props["hexactsize"] == "1"
    val vys = props["vexactsize"] == "1"
    val ax = if (hxp) px else px * parentRect.width
    val ay = if (vyp) py else py * parentRect.height
    val aw = if (hxs) sw else sw * parentRect.width
    val ah = if (vys) sh else sh * parentRect.height
    val bx =
        when (props["halign"]) {
            "right_ref", "right" -> parentRect.right - aw - ax
            "center_ref" -> parentRect.left + parentRect.width / 2f - aw / 2f + ax
            else -> parentRect.left + ax
        }
    val posY =
        when (props["valign"]) {
            "bottom_ref", "bottom" -> parentRect.bottom - ah - ay
            "center_ref" -> parentRect.top + parentRect.height / 2f - ah / 2f + ay
            else -> parentRect.top + ay
        }
    return Rect(bx, posY, bx + aw, posY + ah)
}

private fun hitTest(
    node: EditorWidgetNode,
    worldPos: Offset,
    parentRect: Rect,
    isRoot: Boolean = false,
): EditorWidgetNode? {
    val rect = if (isRoot) parentRect else computeRect(node, parentRect)
    if (!rect.contains(worldPos)) return null
    for (child in node.children.asReversed()) {
        hitTest(child, worldPos, rect)?.let { return it }
    }
    return node
}

private fun buildParentRectMap(
    node: EditorWidgetNode,
    myParentRect: Rect,
    result: MutableMap<UUID, Rect> = mutableMapOf(),
    isRoot: Boolean = false,
): Map<UUID, Rect> {
    result[node.id] = myParentRect
    val myRect = if (isRoot) myParentRect else computeRect(node, myParentRect)
    for (child in node.children) buildParentRectMap(child, myRect, result)
    return result
}

private fun selectInBox(
    node: EditorWidgetNode,
    box: Rect,
    parentRect: Rect,
    out: MutableList<UUID>,
    isRoot: Boolean = false,
) {
    val rect = if (isRoot) parentRect else computeRect(node, parentRect)
    if (!isRoot && box.overlaps(rect)) out.add(node.id)
    for (child in node.children) selectInBox(child, box, rect, out)
}

private fun parseColor(v: String?): Color {
    if (v == null) return Color.Transparent
    val p = v.trim().split(Regex("\\s+"))
    return Color(
        p.getOrNull(0)?.toFloatOrNull() ?: 0f,
        p.getOrNull(1)?.toFloatOrNull() ?: 0f,
        p.getOrNull(2)?.toFloatOrNull() ?: 0f,
        p.getOrNull(3)?.toFloatOrNull() ?: 1f,
    )
}

private fun fmt(f: Float): String {
    val l = f.toLong()
    return if (f == l.toFloat()) "$l" else "%.4g".format(f)
}

// ---------------------------------------------------------------------------
// Drag state
// ---------------------------------------------------------------------------

private data class WidgetDragInfo(
    val id: UUID,
    val origPosX: Float,
    val origPosY: Float,
    val hexactpos: Boolean,
    val vexactpos: Boolean,
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

private fun DrawScope.drawWidgetTree(
    node: EditorWidgetNode,
    parentRect: Rect,
    isRoot: Boolean,
    selectedIds: Set<UUID>,
    showBorders: Boolean,
    textMeasurer: TextMeasurer,
    zoom: Float,
    pan: Offset,
) {
    val wRect = if (isRoot) parentRect else computeRect(node, parentRect)
    val tl = worldToScreen(Offset(wRect.left, wRect.top), zoom, pan)
    val br = worldToScreen(Offset(wRect.right, wRect.bottom), zoom, pan)
    val ss = Size((br.x - tl.x).coerceAtLeast(0f), (br.y - tl.y).coerceAtLeast(0f))
    val isSelected = selectedIds.contains(node.id)

    // Fill by type
    when (node.node.type) {
        WidgetType.PANEL -> {
            val col = parseColor(node.node.properties["color"])
            drawRect(col, tl, ss)
        }

        WidgetType.FRAME, WidgetType.WINDOW -> {
            val col = parseColor(node.node.properties["color"])
            if (col != Color.Transparent) {
                drawRect(col, tl, ss)
            } else {
                drawRect(Color(0x0AFFFFFF), tl, ss)
            }
        }

        WidgetType.IMAGE -> {
            drawRect(Color(0x22FFFFFF), tl, ss)
            drawLine(Color(0x55FFFFFF), tl, br, 1f)
            drawLine(Color(0x55FFFFFF), Offset(tl.x, br.y), Offset(br.x, tl.y), 1f)
        }

        WidgetType.TEXT, WidgetType.MULTILINE_TEXT, WidgetType.RICH_TEXT -> {
            drawRect(Color(0x15FFFFFF), tl, ss)
            val label = node.node.properties["text"] ?: node.node.name
            if (label.isNotEmpty() && ss.width > 4f && ss.height > 8f) {
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
            if (label.isNotEmpty() && ss.width > 4f && ss.height > 8f) {
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
    if (!isRoot && ss.width > 40f && ss.height > 14f) {
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

    // Recurse children
    for (child in node.children) {
        drawWidgetTree(child, wRect, false, selectedIds, showBorders, textMeasurer, zoom, pan)
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
                    } else {
                        false
                    }
                }.onSizeChanged {
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
                                    if (!event.keyboardModifiers.isCtrlPressed) continue
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

                                        isPrimary -> {
                                            val worldPos = screenToWorld(pos, zoom, pan)
                                            val doc = state.activeFile
                                            val res = state.selectedResolution
                                            val refRect = Rect(0f, 0f, res.width.toFloat(), res.height.toFloat())
                                            val hit = doc?.let { hitTest(it.root, worldPos, refRect, isRoot = true) }
                                            val hitIsRoot = hit?.id == doc?.root?.id

                                            if (hit != null && !hitIsRoot) {
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
                                                val parentRects = buildParentRectMap(doc.root, refRect, isRoot = true)
                                                dragInfos =
                                                    state.selectedIds.mapNotNull { id ->
                                                        val n = doc.root.findNode(id) ?: return@mapNotNull null
                                                        val pr = parentRects[id] ?: refRect
                                                        val pp = (n.node.properties["position"] ?: "0 0").trim().split(Regex("\\s+"))
                                                        WidgetDragInfo(
                                                            id = id,
                                                            origPosX = pp.getOrNull(0)?.toFloatOrNull() ?: 0f,
                                                            origPosY = pp.getOrNull(1)?.toFloatOrNull() ?: 0f,
                                                            hexactpos = n.node.properties["hexactpos"] == "1",
                                                            vexactpos = n.node.properties["vexactpos"] == "1",
                                                            parentRect = pr,
                                                        )
                                                    }
                                                isDragging = true
                                                dragStartScreen = pos
                                                state.pushUndo(state.activeFileIndex, doc.root)
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

                                        isDragging && dragInfos.isNotEmpty() -> {
                                            val worldDelta = (pos - dragStartScreen) / zoom
                                            val doc = state.activeFile
                                            if (doc != null) {
                                                var newRoot = doc.root
                                                for (info in dragInfos) {
                                                    val nx =
                                                        info.origPosX +
                                                            if (info.hexactpos) worldDelta.x else worldDelta.x / info.parentRect.width
                                                    val ny =
                                                        info.origPosY +
                                                            if (info.vexactpos) worldDelta.y else worldDelta.y / info.parentRect.height
                                                    newRoot =
                                                        newRoot.updateNode(info.id) { n ->
                                                            n.copy(
                                                                node =
                                                                    n.node.copy(
                                                                        properties =
                                                                            LinkedHashMap(n.node.properties).also {
                                                                                it["position"] = "${fmt(nx)} ${fmt(ny)}"
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
                                    }
                                }

                                PointerEventType.Release -> {
                                    if (isBoxSelecting) {
                                        val box = currentBox
                                        val doc = state.activeFile
                                        if (box != null && doc != null) {
                                            val res2 = state.selectedResolution
                                            val refRect = Rect(0f, 0f, res2.width.toFloat(), res2.height.toFloat())
                                            val hits = mutableListOf<UUID>()
                                            selectInBox(doc.root, box, refRect, hits, isRoot = true)
                                            hits.remove(doc.root.id)
                                            if (!event.keyboardModifiers.isShiftPressed) state.selectedIds.clear()
                                            for (id in hits) if (!state.selectedIds.contains(id)) state.selectedIds.add(id)
                                        }
                                        selectionBox = null
                                        currentBox = null
                                    }
                                    isPanning = false
                                    isDragging = false
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
        if (doc != null) {
            drawWidgetTree(
                doc.root,
                Rect(0f, 0f, refW, refH),
                isRoot = true,
                selectedSet,
                state.showBorders,
                textMeasurer,
                zoom,
                pan,
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
