package com.github.demandss.dayz_workbench_tool.layout_editor.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.demandss.dayz_workbench_tool.i18n.tr
import com.github.demandss.dayz_workbench_tool.layout_editor.state.LayoutEditorState
import com.github.demandss.dayz_workbench_tool.layout_editor.state.TextureDocument
import com.github.demandss.dayz_workbench_tool.ui.components.DarkToolButton
import com.github.demandss.dayz_workbench_tool.ui.icons.AppIcon
import com.github.demandss.dayz_workbench_tool.ui.theme.*
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_ZOOM = 0.05f
private const val MAX_ZOOM = 32f
private const val CHECKER_CELL = 8f

/**
 * Viewport for a texture tab: checker background, zoom to the cursor, pan, channel toggles and
 * the pixel under the cursor, like the texture viewer of Workbench.
 */
@Composable
fun TextureViewer(
    state: LayoutEditorState,
    doc: TextureDocument,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(doc.path) { mutableStateOf(1f) }
    var pan by remember(doc.path) { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var fitted by remember(doc.path, doc.source) { mutableStateOf(false) }
    var channel by remember { mutableStateOf(ChannelMode.ARGB) }
    var checker by remember { mutableStateOf(true) }
    var hover by remember { mutableStateOf<IntOffset?>(null) }

    val source = doc.source
    val bitmap =
        remember(source, channel) {
            source?.let { channelView(it, channel).toComposeImageBitmap() }
        }

    fun fit(limitTo100: Boolean) {
        val s = source ?: return
        if (viewSize == Size.Zero) return
        var z = min(viewSize.width * 0.9f / s.width, viewSize.height * 0.9f / s.height)
        if (limitTo100) z = min(z, 1f)
        z = z.coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoom = z
        pan = Offset((viewSize.width - s.width * z) / 2f, (viewSize.height - s.height * z) / 2f)
    }

    fun zoomAboutCentre(factor: Float) {
        if (viewSize == Size.Zero) return
        val z = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val centre = Offset(viewSize.width / 2f, viewSize.height / 2f)
        pan = centre - (centre - pan) * (z / zoom)
        zoom = z
    }

    LaunchedEffect(source, viewSize) {
        if (!fitted && source != null && viewSize != Size.Zero) {
            fit(limitTo100 = true)
            fitted = true
        }
    }

    Column(modifier.background(FrameBackground)) {
        // Toolbar
        Row(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(ElementsBackground)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DarkToolButton("Fit", { fit(limitTo100 = false) }, icon = AppIcon.UI_FIT, tooltip = tr("Вписать текстуру в окно"))
            DarkToolButton("100%", tooltip = tr("Один пиксель текстуры на один пиксель экрана"), onClick = {
                val s = source
                if (s != null && viewSize != Size.Zero) {
                    zoom = 1f
                    pan = Offset((viewSize.width - s.width) / 2f, (viewSize.height - s.height) / 2f)
                }
            })
            DarkToolButton(null, { zoomAboutCentre(1f / 1.25f) }, icon = AppIcon.UI_ZOOMOUT, tooltip = tr("Уменьшить"))
            DarkToolButton(null, { zoomAboutCentre(1.25f) }, icon = AppIcon.UI_ZOOMIN, tooltip = tr("Увеличить"))
            ToolSeparator()
            ChannelMode.entries.forEach { mode ->
                DarkToolButton(mode.label, { channel = mode }, active = channel == mode, tooltip = tr("Показать канал {0}", mode.label))
            }
            ToolSeparator()
            DarkToolButton(tr("Шахматка"), {
                checker = !checker
            }, icon = AppIcon.UI_GRID, active = checker, tooltip = tr("Шахматный фон, чтобы видеть прозрачность"))
            DarkToolButton(
                "Reload",
                { state.reloadTexture(doc.path) },
                icon = AppIcon.UI_RELOAD,
                tooltip = tr("Прочитать файл с диска заново"),
            )
            Spacer(Modifier.weight(1f))
            Text("${(zoom * 100).roundToInt()}%", color = GhostlyText, fontSize = 11.sp)
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
                        .pointerInput(doc.path) {
                            awaitPointerEventScope {
                                var panning = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull() ?: continue
                                    val pos = change.position
                                    when (event.type) {
                                        PointerEventType.Scroll -> {
                                            val dy = change.scrollDelta.y
                                            if (dy != 0f) {
                                                val newZoom = (zoom * if (dy > 0) 1f / 1.1f else 1.1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                                val under = (pos - pan) / zoom
                                                zoom = newZoom
                                                pan = pos - under * newZoom
                                                change.consume()
                                            }
                                        }

                                        PointerEventType.Press -> {
                                            if (event.buttons.isPrimaryPressed || event.buttons.isTertiaryPressed) {
                                                panning = true
                                                change.consume()
                                            }
                                        }

                                        PointerEventType.Move -> {
                                            if (panning) {
                                                pan += change.position - change.previousPosition
                                                change.consume()
                                            }
                                            val s = source
                                            hover =
                                                if (s != null) {
                                                    val x = floor((pos.x - pan.x) / zoom).toInt()
                                                    val y = floor((pos.y - pan.y) / zoom).toInt()
                                                    if (x in 0 until s.width && y in 0 until s.height) IntOffset(x, y) else null
                                                } else {
                                                    null
                                                }
                                        }

                                        PointerEventType.Release -> {
                                            panning = false
                                        }

                                        PointerEventType.Exit -> {
                                            hover = null
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        },
                ) {
                    drawRect(Color(0xFF1A1B1E))
                    val img = bitmap ?: return@Canvas
                    val tl = pan
                    val size = Size(img.width * zoom, img.height * zoom)
                    if (checker) drawChecker(tl, size) else drawRect(Color(0xFF101010), tl, size)
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                        // Crisp pixels once magnified, smooth when reduced
                        filterQuality = if (zoom >= 2f) FilterQuality.None else FilterQuality.Medium,
                    )
                    drawRect(Color(0xFF555860), tl, size, style = Stroke(1f))
                }

                when {
                    doc.loading -> StatusText(tr("Загрузка…"))
                    doc.error != null -> StatusText(tr("Не удалось открыть: {0}", doc.error))
                }
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(DarkenedBorder))

            InfoPanel(doc, hover)
        }
    }
}

@Composable
private fun BoxScope.StatusText(text: String) {
    Text(text, color = GhostlyText, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun ToolSeparator() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(DarkenedBorder),
    )
}

@Composable
private fun InfoPanel(
    doc: TextureDocument,
    hover: IntOffset?,
) {
    val s = doc.source
    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(ElementsBackground)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Line(tr("Файл"), doc.fileName)
        if (s != null) {
            Line(tr("Размер"), "${s.width} × ${s.height}")
            Line(tr("Формат"), doc.format)
            if (doc.mipCount > 0) Line(tr("Мипы"), doc.mipCount.toString())
        }
        Line(tr("На диске"), formatBytes(doc.fileSize))
        if (s != null) {
            Spacer(Modifier.height(6.dp))
            if (hover != null) {
                val p = s.getRGB(hover.x, hover.y)
                Line(tr("Пиксель"), "${hover.x}, ${hover.y}")
                Line("R G B A", "${(p shr 16) and 0xFF}  ${(p shr 8) and 0xFF}  ${p and 0xFF}  ${p ushr 24}")
                Line("Hex", "#%08X".format(p))
            } else {
                Line(tr("Пиксель"), "—")
            }
        }
    }
}

@Composable
private fun Line(
    label: String,
    value: String,
) {
    Column {
        Text(label, color = GhostlyText, fontSize = 10.sp)
        Text(value, color = TextForeground, fontSize = 12.sp, maxLines = 2)
    }
}

private fun formatBytes(n: Long): String =
    when {
        n >= 1_048_576 -> tr("%.1f МБ").format(n / 1_048_576.0)
        n >= 1024 -> tr("%.1f КБ").format(n / 1024.0)
        else -> tr("{0} Б", n)
    }

/** What the viewport shows. A single channel is drawn as an opaque grey image. */
private enum class ChannelMode(
    val label: String,
) {
    ARGB("ARGB"),
    RGB("RGB"),
    R("R"),
    G("G"),
    B("B"),
    A("A"),
}

private fun channelView(
    src: BufferedImage,
    mode: ChannelMode,
): BufferedImage {
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getRGB(0, 0, w, h, px, 0, w)
    if (mode != ChannelMode.ARGB) {
        for (i in px.indices) {
            val p = px[i]
            px[i] =
                when (mode) {
                    ChannelMode.RGB -> p or (0xFF shl 24)
                    ChannelMode.R -> grey((p shr 16) and 0xFF)
                    ChannelMode.G -> grey((p shr 8) and 0xFF)
                    ChannelMode.B -> grey(p and 0xFF)
                    ChannelMode.A -> grey(p ushr 24)
                    ChannelMode.ARGB -> p
                }
        }
    }
    return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, w, h, px, 0, w) }
}

private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

/** Draws the checker only over the visible part of the image rectangle. */
private fun DrawScope.drawChecker(
    tl: Offset,
    imageSize: Size,
) {
    val x0 = max(tl.x, 0f)
    val y0 = max(tl.y, 0f)
    val x1 = min(tl.x + imageSize.width, size.width)
    val y1 = min(tl.y + imageSize.height, size.height)
    if (x1 <= x0 || y1 <= y0) return
    val light = Color(0xFF3A3A3F)
    val dark = Color(0xFF2B2B30)
    val cx0 = floor((x0 - tl.x) / CHECKER_CELL).toInt()
    val cx1 = ceil((x1 - tl.x) / CHECKER_CELL).toInt()
    val cy0 = floor((y0 - tl.y) / CHECKER_CELL).toInt()
    val cy1 = ceil((y1 - tl.y) / CHECKER_CELL).toInt()
    for (cy in cy0 until cy1) {
        for (cx in cx0 until cx1) {
            val left = max(tl.x + cx * CHECKER_CELL, x0)
            val top = max(tl.y + cy * CHECKER_CELL, y0)
            val right = min(tl.x + (cx + 1) * CHECKER_CELL, x1)
            val bottom = min(tl.y + (cy + 1) * CHECKER_CELL, y1)
            drawRect(if ((cx + cy) % 2 == 0) light else dark, Offset(left, top), Size(right - left, bottom - top))
        }
    }
}
