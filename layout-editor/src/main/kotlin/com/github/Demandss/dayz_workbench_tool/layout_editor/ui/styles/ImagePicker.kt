package com.github.demandss.dayz_workbench_tool.layout_editor.ui.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.imageset.ImageDef
import com.github.demandss.dayz_workbench_tool.imageset.ImageSet
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.CHEQUER_BRUSH
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.ImageSetView
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.imageset.thumbnailOf
import com.github.demandss.dayz_workbench_tool.layout_editor.ui.panels.FlatDialog
import com.github.demandss.dayz_workbench_tool.styles.SlotSet
import com.github.demandss.dayz_workbench_tool.ui.components.DarkButton
import com.github.demandss.dayz_workbench_tool.ui.theme.DarkenedBorder
import com.github.demandss.dayz_workbench_tool.ui.theme.ElementsBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.FrameBackground
import com.github.demandss.dayz_workbench_tool.ui.theme.GhostlyText
import com.github.demandss.dayz_workbench_tool.ui.theme.SelectColor
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground
import kotlin.math.roundToInt

private val OutlineColor = Color(0x885BC0EB)
private val HoverColor = Color(0xFFFFD166)
private val PickedColor = Color(0xFF4A9EFF)

private class PickerEntry(
    val def: ImageDef,
    val group: String?,
)

/**
 * Chooses one image of the imageset [setName] by looking at the atlas: every image has an outline, a click picks it, the
 * wheel zooms and a drag moves the atlas. The list on the left holds the same images by name, grouped like the imageset.
 */
@Composable
fun ImagePickerDialog(
    state: LayoutEditorState,
    setName: String,
    nearPath: String?,
    slot: String,
    current: String,
    sets: List<SlotSet>,
    onPick: (String) -> Unit,
    onPickSet: (SlotSet) -> Unit,
    onDismiss: () -> Unit,
) {
    val set: ImageSet? = remember(setName, nearPath) { state.imageSetOf(setName, nearPath) }
    val bitmap = state.imageSetBitmap(remember(setName, nearPath) { state.imageSetTextureOf(setName, nearPath) })
    val view = remember { ImageSetView() }
    var picked by remember { mutableStateOf(current) }

    // A set of images for all the slots at once, chosen from the list; while it is chosen no single image is
    var pickedSet by remember { mutableStateOf<SlotSet?>(null) }
    var hover by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val entries =
        remember(set) {
            set
                ?.let { s ->
                    s.images.map { PickerEntry(it, null) } + s.groups.flatMap { g -> g.images.map { PickerEntry(it, g.name) } }
                }.orEmpty()
        }
    val byName = remember(entries) { entries.associateBy { it.def.name } }
    val search = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { search.requestFocus() } }

    fun confirm() {
        pickedSet?.let { onPickSet(it) } ?: onPick(picked)
        onDismiss()
    }

    FlatDialog(
        title = tr("Картинка для {0} · {1}", slot, setName),
        onDismiss = onDismiss,
        onConfirm = { confirm() },
        width = 820.dp,
        buttons = {
            DarkButton(tr("Без картинки"), onClick = {
                onPick("")
                onDismiss()
            }, muted = true, modifier = Modifier.width(120.dp))
            DarkButton(tr("Отмена"), onClick = onDismiss, muted = true, modifier = Modifier.width(80.dp))
            DarkButton(tr("Выбрать"), onClick = { confirm() }, modifier = Modifier.width(90.dp))
        },
    ) {
        if (set == null) {
            Text(tr("Набор «{0}» не найден рядом с этим .styles", setName), color = GhostlyText, fontSize = 12.sp)
            return@FlatDialog
        }
        Row(Modifier.fillMaxWidth().height(440.dp)) {
            Column(Modifier.width(250.dp).fillMaxHeight()) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextForeground, fontSize = 11.sp),
                    cursorBrush = SolidColor(TextForeground),
                    modifier = Modifier.focusRequester(search),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(DarkenedBorder)
                                .border(1.dp, Color(0xFF3A3C40))
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isEmpty()) Text(tr("Поиск по имени…"), color = GhostlyText, fontSize = 11.sp)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
                val shown = entries.filter { query.isBlank() || it.def.name.contains(query.trim(), ignoreCase = true) }
                val rows = shown.groupBy { it.group }.toList().sortedBy { it.first ?: "" }
                val shownSets = sets.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.fillMaxSize().background(FrameBackground)) {
                    if (shownSets.isNotEmpty()) {
                        item(key = "sets") {
                            Text(
                                tr("НАБОРЫ ДЛЯ ВСЕХ СЛОТОВ"),
                                color = GhostlyText,
                                fontSize = 10.sp,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            ElementsBackground,
                                        ).padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        items(shownSets, key = { "s:${it.label}" }) { set ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .background(if (pickedSet == set) SelectColor else Color.Transparent)
                                    .clickable(remember { MutableInteractionSource() }, null) {
                                        if (pickedSet == set) confirm() else pickedSet = set
                                    }.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    set.label,
                                    color = TextForeground,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(tr("{0} слотов", set.images.size), color = GhostlyText, fontSize = 10.sp)
                            }
                        }
                    }
                    rows.forEach { (group, list) ->
                        if (group != null) {
                            item(key = "g:$group") {
                                Text(
                                    group.uppercase(),
                                    color = GhostlyText,
                                    fontSize = 10.sp,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                ElementsBackground,
                                            ).padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        items(list, key = { "i:$group:${it.def.name}" }) { entry ->
                            PickerRow(entry.def, set, bitmap, pickedSet == null && entry.def.name == picked) {
                                if (pickedSet == null && picked == entry.def.name) {
                                    confirm()
                                } else {
                                    pickedSet = null
                                    picked = entry.def.name
                                    view.showQuad(
                                        Rect(
                                            entry.def.x.toFloat(),
                                            entry.def.y.toFloat(),
                                            entry.def.right.toFloat(),
                                            entry.def.bottom.toFloat(),
                                        ),
                                        set.refWidth,
                                        set.refHeight,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, DarkenedBorder)
                    .clipToBounds(),
            ) {
                PickerAtlas(
                    set,
                    bitmap,
                    view,
                    picked,
                    pickedSet
                        ?.images
                        ?.values
                        ?.toSet()
                        .orEmpty(),
                    hover,
                    { hover = it },
                ) { name ->
                    if (pickedSet == null && picked == name) {
                        confirm()
                    } else {
                        pickedSet = null
                        picked = name
                    }
                }
                Text(
                    hover ?: pickedSet?.label ?: picked.ifEmpty { tr("ничего не выбрано") },
                    color = TextForeground,
                    fontSize = 11.sp,
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomStart,
                            ).background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    def: ImageDef,
    set: ImageSet,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val thumb = remember(bitmap, def.x, def.y, def.width, def.height) { bitmap?.let { thumbnailOf(it, def, set.refWidth, set.refHeight) } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(if (selected) SelectColor else Color.Transparent)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp).background(Color(0xFF2B2B30))) {
            if (thumb != null) {
                val k = (size.width / thumb.width).coerceAtMost(size.height / thumb.height)
                val w = (thumb.width * k).coerceAtLeast(1f)
                val h = (thumb.height * k).coerceAtLeast(1f)
                drawImage(
                    thumb,
                    dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            def.name,
            color = TextForeground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("${def.width}×${def.height}", color = GhostlyText, fontSize = 10.sp)
    }
}

@Composable
private fun PickerAtlas(
    set: ImageSet,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    view: ImageSetView,
    picked: String,
    pickedGroup: Set<String>,
    hover: String?,
    onHover: (String?) -> Unit,
    onPick: (String) -> Unit,
) {
    val images = remember(set) { set.all }
    LaunchedEffect(view.canvas, set) {
        if (!view.fitted && view.canvas != Size.Zero) {
            view.fit(set.refWidth, set.refHeight)
            view.fitted = true
        }
    }

    fun hit(pos: Offset): String? {
        val world = (pos - view.pan) / view.zoom
        return images
            .filter { world.x >= it.x && world.x < it.right && world.y >= it.y && world.y < it.bottom }
            .minByOrNull { it.width * it.height }
            ?.name
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { view.canvas = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(set) {
                awaitPointerEventScope {
                    var travelled = 0f
                    var down = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                view.zoomAbout(if (change.scrollDelta.y > 0) 0.85f else 1.18f, change.position)
                                change.consume()
                            }

                            PointerEventType.Press -> {
                                down = true
                                travelled = 0f
                            }

                            PointerEventType.Move -> {
                                if (down) {
                                    travelled += (change.position - change.previousPosition).getDistance()
                                    if (travelled > 4f) view.pan += change.position - change.previousPosition
                                } else {
                                    onHover(hit(change.position))
                                }
                            }

                            PointerEventType.Release -> {
                                if (down && travelled <= 4f) hit(change.position)?.let(onPick)
                                down = false
                            }

                            PointerEventType.Exit -> {
                                onHover(null)
                            }

                            else -> {}
                        }
                    }
                }
            },
    ) {
        val zoom = view.zoom
        val origin = view.pan
        val area = Size(set.refWidth * zoom, set.refHeight * zoom)
        withTransform({ translate(origin.x, origin.y) }) { drawRect(CHEQUER_BRUSH, Offset.Zero, area) }
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
        for (def in images) {
            val tl = Offset(origin.x + def.x * zoom, origin.y + def.y * zoom)
            val sz = Size(def.width * zoom, def.height * zoom)
            if (tl.x + sz.width < 0f || tl.y + sz.height < 0f || tl.x > size.width || tl.y > size.height) continue
            val isPicked = def.name == picked || def.name in pickedGroup
            val isHover = def.name == hover
            if (isPicked) drawRect(PickedColor.copy(alpha = 0.25f), tl, sz)
            drawRect(
                when {
                    isPicked -> PickedColor
                    isHover -> HoverColor
                    else -> OutlineColor
                },
                tl,
                sz,
                style = Stroke(if (isPicked || isHover) 2f else 1f),
            )
        }
    }
}

/** Brings a quad into the middle of the view, large enough to see but never closer than 6 times or further than the whole atlas. */
private fun ImageSetView.showQuad(
    rect: Rect,
    refWidth: Int,
    refHeight: Int,
) {
    if (canvas == Size.Zero) return
    val fitZoom = minOf(canvas.width * 0.9f / refWidth, canvas.height * 0.9f / refHeight)
    val z = minOf(canvas.width * 0.35f / maxOf(rect.width, 1f), canvas.height * 0.35f / maxOf(rect.height, 1f)).coerceIn(fitZoom, 6f)
    zoom = z
    pan = Offset(canvas.width / 2f - rect.center.x * z, canvas.height / 2f - rect.center.y * z)
}
