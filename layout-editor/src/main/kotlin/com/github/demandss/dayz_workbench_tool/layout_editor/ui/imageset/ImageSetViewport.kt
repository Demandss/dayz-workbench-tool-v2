package com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.imageset.ImageDef
import com.github.demandss.dayz_workbench_tool.layout_editor.state.ImageSetDocument
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.checkImageSet
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.GuideLine
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.ResizeHandle
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.handleAt
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.resizeCursor
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.resizedRect
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.snapMove
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas.snapResized
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val RegionColor = Color(0xFF5BC0EB)
private val SelectedColor = Color(0xFF4A9EFF)
private val ProblemColor = Color(0xFFFF5555)
private val GuideColor = Color(0xFFFF4FA3)

/** How the atlas is shown: zoom and position, the tool and what is drawn on top. One per open imageset. */
class ImageSetView {
    var zoom by mutableStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    var drawMode by mutableStateOf(false)
    var showGrid by mutableStateOf(true)
    var showNames by mutableStateOf(true)
    var canvas by mutableStateOf(Size.Zero)
    var fitted by mutableStateOf(false)
    var hoverHandle by mutableStateOf<ResizeHandle?>(null)
    var spaceDown by mutableStateOf(false)
    var guides by mutableStateOf<List<GuideLine>>(emptyList())

    fun fit(
        refW: Int,
        refH: Int,
    ) {
        if (canvas == Size.Zero || refW <= 0 || refH <= 0) return
        val z = min(canvas.width * 0.9f / refW, canvas.height * 0.9f / refH).coerceIn(0.05f, 64f)
        zoom = z
        pan = Offset((canvas.width - refW * z) / 2f, (canvas.height - refH * z) / 2f)
    }

    fun zoomAbout(
        factor: Float,
        at: Offset = Offset(canvas.width / 2f, canvas.height / 2f),
    ) {
        val z = (zoom * factor).coerceIn(0.05f, 64f)
        val world = (at - pan) / zoom
        zoom = z
        pan = at - world * z
    }

    fun zoomTo(rect: Rect) {
        if (canvas == Size.Zero) return
        val z = min(canvas.width * 0.5f / max(rect.width, 1f), canvas.height * 0.5f / max(rect.height, 1f)).coerceIn(0.05f, 64f)
        zoom = z
        pan = Offset(canvas.width / 2f - rect.center.x * z, canvas.height / 2f - rect.center.y * z)
    }
}

internal fun ImageDef.rect() = Rect(x.toFloat(), y.toFloat(), right.toFloat(), bottom.toFloat())

/** The texture of the imageset with its images drawn over it. Images are picked, moved, resized and drawn here. */
@Composable
fun ImageSetViewport(
    state: LayoutEditorState,
    doc: ImageSetDocument,
    view: ImageSetView,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val focus = remember { FocusRequester() }
    val model = doc.model
    val textureRef = model.textures.minByOrNull { it.mpix }
    val file: File? = remember(doc.path, textureRef?.path) { textureRef?.let { state.resolveImageSetTexture(doc.path, it.path) } }
    val bitmap = state.imageSetBitmap(file)
    val problemIds =
        remember(
            model,
            bitmap?.width,
            bitmap?.height,
        ) { checkImageSet(model, bitmap?.width ?: 0, bitmap?.height ?: 0).flatMap { it.ids }.toSet() }
    var drawing by remember { mutableStateOf<Rect?>(null) }
    var boxSelect by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(view.canvas, model.refWidth, model.refHeight) {
        if (!view.fitted && view.canvas != Size.Zero && model.refWidth > 0) {
            view.fit(model.refWidth, model.refHeight)
            view.fitted = true
        }
    }

    Box(modifier.background(Color(0xFF1A1B1E)).clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .pointerHoverIcon(resizeCursor(view.hoverHandle))
                .onSizeChanged { view.canvas = Size(it.width.toFloat(), it.height.toFloat()) }
                .onKeyEvent { ev -> handleImageSetKey(doc, view, ev) }
                .pointerInput(doc) { imageSetPointer(doc, view, { drawing = it }, { boxSelect = it }, focus) },
        ) {
            drawAtlas(doc, view, bitmap, problemIds, drawing, boxSelect, measurer)
        }
    }
}

private fun DrawScope.drawAtlas(
    doc: ImageSetDocument,
    view: ImageSetView,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    problemIds: Set<Int>,
    drawing: Rect?,
    boxSelect: Rect?,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val model = doc.model
    val zoom = view.zoom
    val pan = view.pan
    val refW = model.refWidth.toFloat()
    val refH = model.refHeight.toFloat()
    val origin = pan
    val area = Size(refW * zoom, refH * zoom)

    // A chequered backdrop shows the transparency of the texture
    // One draw with a repeating 2 x 2 pattern; a loop over every cell is thousands of draws on a big atlas
    withTransform({ translate(origin.x, origin.y) }) {
        drawRect(CHEQUER_BRUSH, Offset.Zero, area)
    }
    if (bitmap != null) {
        drawImage(
            bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(bitmap.width, bitmap.height),
            dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
            dstSize = IntSize(area.width.roundToInt().coerceAtLeast(1), area.height.roundToInt().coerceAtLeast(1)),
            filterQuality = if (zoom >= 2f) FilterQuality.None else FilterQuality.Low,
        )
    }
    drawRect(Color(0xFF6F737A), origin, area, style = Stroke(1f))

    // A pixel grid once the pixels are big enough
    if (view.showGrid && zoom >= 8f) {
        val left = max(0f, -origin.x)
        val top = max(0f, -origin.y)
        val right = min(area.width, size.width - origin.x)
        val bottom = min(area.height, size.height - origin.y)
        var gx = (kotlin.math.floor(left / zoom) * zoom)
        while (gx <= right) {
            drawLine(Color(0x22FFFFFF), Offset(origin.x + gx, origin.y + top), Offset(origin.x + gx, origin.y + bottom), 1f)
            gx += zoom
        }
        var gy = (kotlin.math.floor(top / zoom) * zoom)
        while (gy <= bottom) {
            drawLine(Color(0x22FFFFFF), Offset(origin.x + left, origin.y + gy), Offset(origin.x + right, origin.y + gy), 1f)
            gy += zoom
        }
    }

    for (entry in model.entries) {
        val r = entry.def.rect()
        // Regions outside the view cost nothing
        if (origin.x + r.right * zoom < 0f || origin.y + r.bottom * zoom < 0f || origin.x + r.left * zoom > size.width ||
            origin.y + r.top * zoom > size.height
        ) {
            continue
        }
        val tl = Offset(origin.x + r.left * zoom, origin.y + r.top * zoom)
        val sz = Size(r.width * zoom, r.height * zoom)
        val selected = entry.id in doc.selected
        val bad = entry.id in problemIds
        if (selected) drawRect(SelectedColor.copy(alpha = 0.22f), tl, sz)
        drawRect(
            when {
                selected -> SelectedColor
                bad -> ProblemColor
                else -> RegionColor.copy(alpha = 0.85f)
            },
            tl,
            sz,
            style = Stroke(if (selected) 2f else 1f),
        )
        if (view.showNames && sz.width > 36f && sz.height > 12f) {
            val label =
                (if (LABEL_CACHE.size > 3000) LABEL_CACHE.clear() else Unit).let {
                    LABEL_CACHE.getOrPut(
                        entry.def.name,
                    ) { measurer.measure(entry.def.name, TextStyle(color = Color.White, fontSize = 10.sp), maxLines = 1) }
                }
            if (label.size.width < sz.width) {
                drawRect(Color(0xAA000000), tl, Size(label.size.width + 6f, label.size.height + 2f))
                drawText(label, topLeft = Offset(tl.x + 3f, tl.y + 1f))
            }
        }
    }

    // Handles of the one selected image
    val single = doc.selected.singleOrNull()?.let { model.entry(it) }
    if (single != null) {
        val r = single.def.rect()
        val xs = floatArrayOf(r.left, r.center.x, r.right)
        val ys = floatArrayOf(r.top, r.center.y, r.bottom)
        for (x in xs) {
            for (y in ys) {
                if (x == r.center.x && y == r.center.y) continue
                val p = Offset(origin.x + x * zoom, origin.y + y * zoom)
                drawRect(Color.White, Offset(p.x - 3f, p.y - 3f), Size(6f, 6f))
                drawRect(SelectedColor, Offset(p.x - 3f, p.y - 3f), Size(6f, 6f), style = Stroke(1f))
            }
        }
    }

    for (g in view.guides) {
        val a =
            if (g.vertical) {
                Offset(origin.x + g.at * zoom, origin.y + g.from * zoom)
            } else {
                Offset(
                    origin.x + g.from * zoom,
                    origin.y + g.at * zoom,
                )
            }
        val b =
            if (g.vertical) {
                Offset(origin.x + g.at * zoom, origin.y + g.to * zoom)
            } else {
                Offset(
                    origin.x + g.to * zoom,
                    origin.y + g.at * zoom,
                )
            }
        drawLine(GuideColor, a, b, 1f)
    }
    drawing?.let {
        val tl = Offset(origin.x + it.left * zoom, origin.y + it.top * zoom)
        drawRect(SelectedColor.copy(alpha = 0.25f), tl, Size(it.width * zoom, it.height * zoom))
        drawRect(SelectedColor, tl, Size(it.width * zoom, it.height * zoom), style = Stroke(1f))
        chip(measurer, "${it.width.roundToInt()} × ${it.height.roundToInt()}", Offset(tl.x, tl.y + it.height * zoom + 6f))
    }
    boxSelect?.let {
        val tl = Offset(origin.x + it.left * zoom, origin.y + it.top * zoom)
        drawRect(Color(0x334A9EFF), tl, Size(it.width * zoom, it.height * zoom))
        drawRect(SelectedColor, tl, Size(it.width * zoom, it.height * zoom), style = Stroke(1f))
    }
}

private fun DrawScope.chip(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    at: Offset,
) {
    val label = measurer.measure(text, TextStyle(color = Color.White, fontSize = 10.sp))
    drawRect(Color(0xFF346BD8), at, Size(label.size.width + 10f, label.size.height + 6f))
    drawText(label, topLeft = Offset(at.x + 5f, at.y + 3f))
}

/** Keys while the atlas has the keyboard. Returns true when the key was used. */
private fun handleImageSetKey(
    doc: ImageSetDocument,
    view: ImageSetView,
    ev: androidx.compose.ui.input.key.KeyEvent,
): Boolean {
    if (ev.key == Key.Spacebar) {
        view.spaceDown = ev.type == KeyEventType.KeyDown
        return true
    }
    if (ev.type != KeyEventType.KeyDown) return false
    val ctrl = ev.isCtrlPressed
    val step = if (ev.isShiftPressed) 8 else 1

    fun nudge(
        dx: Int,
        dy: Int,
    ) {
        val ids = doc.selected.toSet()
        if (ids.isEmpty()) return
        doc.edit { m -> ids.fold(m) { acc, id -> acc.withUpdated(id) { it.copy(x = it.x + dx, y = it.y + dy) } } }
    }
    return when {
        ev.key == Key.Delete && !ctrl -> {
            if (doc.selected.isNotEmpty()) {
                val ids = doc.selected.toSet()
                doc.edit { it.withRemoved(ids) }
                doc.selected.clear()
            }
            true
        }

        ev.key == Key.DirectionLeft && !ctrl -> {
            nudge(-step, 0)
            true
        }

        ev.key == Key.DirectionRight && !ctrl -> {
            nudge(step, 0)
            true
        }

        ev.key == Key.DirectionUp && !ctrl -> {
            nudge(0, -step)
            true
        }

        ev.key == Key.DirectionDown && !ctrl -> {
            nudge(0, step)
            true
        }

        ctrl && ev.key == Key.D -> {
            duplicateSelected(doc)
            true
        }

        ctrl && ev.key == Key.A -> {
            doc.selected.clear()
            doc.selected.addAll(doc.model.entries.map { it.id })
            true
        }

        ctrl && (ev.key == Key.Equals || ev.key == Key.Plus || ev.key == Key.NumPadAdd) -> {
            view.zoomAbout(1.25f)
            true
        }

        ctrl && (ev.key == Key.Minus || ev.key == Key.NumPadSubtract) -> {
            view.zoomAbout(0.8f)
            true
        }

        ctrl && (ev.key == Key.Zero || ev.key == Key.NumPad0) -> {
            view.fit(doc.model.refWidth, doc.model.refHeight)
            true
        }

        ev.key == Key.F2 -> {
            doc.focusNameTick++
            true
        }

        ev.key == Key.One && ev.isShiftPressed -> {
            view.fit(doc.model.refWidth, doc.model.refHeight)
            true
        }

        ev.key == Key.Two && ev.isShiftPressed -> {
            val rects =
                doc.selected.mapNotNull {
                    doc.model
                        .entry(it)
                        ?.def
                        ?.rect()
                }
            if (rects.isNotEmpty()) {
                view.zoomTo(Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom }))
            }
            true
        }

        else -> {
            false
        }
    }
}

internal fun duplicateSelected(doc: ImageSetDocument) {
    val ids = doc.selected.toSet()
    if (ids.isEmpty()) return
    var created: List<Int> = emptyList()
    doc.edit { m ->
        val (next, newIds) = m.withDuplicated(ids)
        created = newIds
        next
    }
    doc.selected.clear()
    doc.selected.addAll(created)
}

/** The pointer of the atlas: pan, wheel zoom, pick, move, resize, draw a new image and box select. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.imageSetPointer(
    doc: ImageSetDocument,
    view: ImageSetView,
    setDrawing: (Rect?) -> Unit,
    setBox: (Rect?) -> Unit,
    focus: FocusRequester,
) {
    awaitPointerEventScope {
        var panning = false
        var panStart = Offset.Zero
        var panOrigin = Offset.Zero

        var moving = false
        var moveStart = Offset.Zero
        var moveBase: Map<Int, ImageDef> = emptyMap()
        var moveCandidates: List<Rect> = emptyList()
        var moveUnion = Rect.Zero
        var moveActive = false

        var resizeHandle: ResizeHandle? = null
        var resizeId = -1
        var resizeBase = Rect.Zero
        var resizeStart = Offset.Zero
        var resizeCandidates: List<Rect> = emptyList()

        var drawStart: Offset? = null
        var boxStart: Offset? = null

        fun world(p: Offset) = (p - view.pan) / view.zoom

        fun hit(w: Offset): Int? =
            doc.model.entries
                .filter { it.def.rect().contains(w) }
                .minByOrNull { it.def.width.toLong() * it.def.height }
                ?.id

        fun handleFor(w: Offset): ResizeHandle? {
            val id = doc.selected.singleOrNull() ?: return null
            val r =
                doc.model
                    .entry(id)
                    ?.def
                    ?.rect() ?: return null
            return handleAt(r, w, 6f / view.zoom)
        }

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            val pos = change.position
            when (event.type) {
                PointerEventType.Scroll -> {
                    val dy = change.scrollDelta.y
                    if (dy != 0f) {
                        view.zoomAbout(if (dy > 0) 0.9f else 1.1f, pos)
                        change.consume()
                    }
                }

                PointerEventType.Press -> {
                    runCatching { focus.requestFocus() }
                    val w = world(pos)
                    when {
                        event.buttons.isTertiaryPressed || (event.buttons.isPrimaryPressed && view.spaceDown) -> {
                            panning = true
                            panStart = pos
                            panOrigin = view.pan
                            change.consume()
                        }

                        event.buttons.isPrimaryPressed -> {
                            val handle = handleFor(w)
                            val id = hit(w)
                            when {
                                handle != null -> {
                                    resizeHandle = handle
                                    resizeId = doc.selected.single()
                                    resizeBase =
                                        doc.model
                                            .entry(resizeId)!!
                                            .def
                                            .rect()
                                    resizeStart = pos
                                    resizeCandidates =
                                        doc.model.entries
                                            .filter { it.id != resizeId }
                                            .map { it.def.rect() }
                                }

                                id != null && !view.drawMode -> {
                                    if (event.keyboardModifiers.isShiftPressed) {
                                        doc.toggleSelected(id)
                                    } else if (id !in doc.selected) {
                                        doc.select(id)
                                    }
                                    val ids = doc.selected.toSet()
                                    moveBase =
                                        doc.model.entries
                                            .filter { it.id in ids }
                                            .associate { it.id to it.def }
                                    moveCandidates =
                                        doc.model.entries
                                            .filter { it.id !in ids }
                                            .map { it.def.rect() }
                                    val rs = moveBase.values.map { it.rect() }
                                    moveUnion =
                                        if (rs.isEmpty()) {
                                            Rect.Zero
                                        } else {
                                            Rect(
                                                rs.minOf { it.left },
                                                rs.minOf { it.top },
                                                rs.maxOf { it.right },
                                                rs.maxOf { it.bottom },
                                            )
                                        }
                                    moving = id in ids
                                    moveStart = pos
                                    moveActive = false
                                }

                                view.drawMode || event.keyboardModifiers.isCtrlPressed -> {
                                    drawStart = w
                                }

                                else -> {
                                    if (!event.keyboardModifiers.isShiftPressed) doc.selected.clear()
                                    boxStart = w
                                }
                            }
                            change.consume()
                        }
                    }
                }

                PointerEventType.Move -> {
                    val w = world(pos)
                    when {
                        panning -> {
                            view.pan = panOrigin + (pos - panStart)
                            change.consume()
                        }

                        resizeHandle != null -> {
                            val delta = (pos - resizeStart) / view.zoom
                            var rect =
                                resizedRect(
                                    resizeBase,
                                    resizeHandle,
                                    delta,
                                    keepAspect = event.keyboardModifiers.isShiftPressed,
                                    fromCentre = event.keyboardModifiers.isAltPressed,
                                )
                            if (!event.keyboardModifiers.isCtrlPressed) {
                                val (snapped, guides) = snapResized(rect, resizeHandle, resizeCandidates, 5f / view.zoom)
                                rect = snapped
                                view.guides = guides
                            }
                            val x = rect.left.roundToInt()
                            val y = rect.top.roundToInt()
                            val width = max(1, rect.width.roundToInt())
                            val height = max(1, rect.height.roundToInt())
                            doc.editInGesture { m -> m.withUpdated(resizeId) { it.copy(x = x, y = y, width = width, height = height) } }
                            change.consume()
                        }

                        moving -> {
                            if (!moveActive && (pos - moveStart).getDistance() >= 3f) moveActive = true
                            if (moveActive) {
                                var delta = (pos - moveStart) / view.zoom
                                if (!event.keyboardModifiers.isCtrlPressed) {
                                    val snap = snapMove(moveUnion, delta, moveCandidates, 5f / view.zoom)
                                    delta += Offset(snap.dx, snap.dy)
                                    view.guides = snap.guides
                                }
                                val dx = delta.x.roundToInt()
                                val dy = delta.y.roundToInt()
                                doc.editInGesture { m ->
                                    moveBase.entries.fold(m) { acc, (id, base) ->
                                        acc.withUpdated(id) {
                                            it.copy(
                                                x = base.x + dx,
                                                y =
                                                    base.y + dy,
                                            )
                                        }
                                    }
                                }
                            }
                            change.consume()
                        }

                        drawStart != null -> {
                            val s = drawStart
                            setDrawing(
                                Rect(
                                    min(s.x, w.x).roundToInt().toFloat(),
                                    min(s.y, w.y).roundToInt().toFloat(),
                                    max(s.x, w.x).roundToInt().toFloat(),
                                    max(s.y, w.y).roundToInt().toFloat(),
                                ),
                            )
                            change.consume()
                        }

                        boxStart != null -> {
                            val s = boxStart
                            setBox(Rect(min(s.x, w.x), min(s.y, w.y), max(s.x, w.x), max(s.y, w.y)))
                            change.consume()
                        }

                        else -> {
                            view.hoverHandle = handleFor(w)
                        }
                    }
                }

                PointerEventType.Release -> {
                    val w = world(pos)
                    drawStart?.let { s ->
                        val r =
                            Rect(
                                min(s.x, w.x).roundToInt().toFloat(),
                                min(s.y, w.y).roundToInt().toFloat(),
                                max(s.x, w.x).roundToInt().toFloat(),
                                max(s.y, w.y).roundToInt().toFloat(),
                            )
                        if (r.width >= 2f && r.height >= 2f) {
                            var newId = -1
                            doc.edit { m ->
                                val (next, id) =
                                    m.withAdded(
                                        ImageDef("Image", r.left.toInt(), r.top.toInt(), r.width.toInt(), r.height.toInt()),
                                    )
                                newId = id
                                next
                            }
                            doc.select(newId)
                        }
                    }
                    boxStart?.let { s ->
                        val box = Rect(min(s.x, w.x), min(s.y, w.y), max(s.x, w.x), max(s.y, w.y))
                        if (box.width > 1f || box.height > 1f) {
                            doc.model.entries.filter { it.def.rect().overlaps(box) }.forEach {
                                if (it.id !in
                                    doc.selected
                                ) {
                                    doc.selected.add(it.id)
                                }
                            }
                        }
                    }
                    panning = false
                    moving = false
                    moveActive = false
                    resizeHandle = null
                    drawStart = null
                    boxStart = null
                    setDrawing(null)
                    setBox(null)
                    view.guides = emptyList()
                    doc.endGesture()
                }

                else -> {}
            }
        }
    }
}

internal val CHEQUER_BRUSH: androidx.compose.ui.graphics.ShaderBrush by lazy {
    val cell = 8
    val image = java.awt.image.BufferedImage(cell * 2, cell * 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until cell * 2) {
        for (x in 0 until cell * 2) {
            image.setRGB(x, y, if ((x / cell + y / cell) % 2 == 0) 0xFF3A3A3F.toInt() else 0xFF2B2B30.toInt())
        }
    }
    androidx.compose.ui.graphics.ShaderBrush(
        androidx.compose.ui.graphics.ImageShader(
            image.toComposeImageBitmap(),
            androidx.compose.ui.graphics.TileMode.Repeated,
            androidx.compose.ui.graphics.TileMode.Repeated,
        ),
    )
}

// Measuring a label is slow and the text of a region rarely changes, so the layouts are kept
private val LABEL_CACHE = HashMap<String, androidx.compose.ui.text.TextLayoutResult>()
