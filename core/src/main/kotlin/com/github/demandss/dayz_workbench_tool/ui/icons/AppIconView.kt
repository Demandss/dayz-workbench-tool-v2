package com.github.demandss.dayz_workbench_tool.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.demandss.dayz_workbench_tool.ui.theme.TextForeground

private const val GRID = 16f
private const val TINT_ALPHA = 0.28f

/** Draws [icon] at [size]. [tint] overrides the icon's own colour; icons without one take the text colour. */
@Composable
fun AppIconView(
    icon: AppIcon,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color? = null,
) {
    val fallback = TextForeground
    val color = tint ?: icon.tint?.let { Color(it) } ?: fallback
    val paths = remember(icon) { icon.shapes.map { PathParser().parsePathString(it.d).toPath() } }

    Canvas(modifier.size(size)) {
        // Shapes are authored on a 16 unit grid; scaling the canvas scales the stroke with them
        val k = this.size.width / GRID
        scale(k, k, pivot = Offset.Zero) {
            icon.shapes.forEachIndexed { i, shape ->
                if (shape.fill != IconFill.NONE) {
                    drawPath(paths[i], color, alpha = if (shape.fill == IconFill.TINT) TINT_ALPHA else 1f)
                }
                if (shape.fill != IconFill.SOLID) {
                    drawPath(
                        paths[i],
                        color,
                        style =
                            Stroke(
                                width = shape.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = if (shape.dash.isEmpty()) null else PathEffect.dashPathEffect(shape.dash),
                            ),
                    )
                }
            }
        }
    }
}

/** The icon of a file or folder in the project tree. */
fun fileIcon(
    name: String,
    isDir: Boolean = false,
    expanded: Boolean = false,
): AppIcon =
    when {
        isDir -> {
            if (expanded) AppIcon.FOLDER_OPEN else AppIcon.FOLDER
        }

        else -> {
            when (name.substringAfterLast('.', "").lowercase()) {
                "layout" -> AppIcon.LAYOUT
                "edds" -> AppIcon.EDDS
                "png", "jpg", "jpeg" -> AppIcon.IMAGE
                "imageset" -> AppIcon.IMAGESET
                "styles" -> AppIcon.STYLES
                "c" -> AppIcon.SCRIPT
                "cpp" -> AppIcon.CONFIG
                "csv" -> AppIcon.CSV
                "json" -> AppIcon.JSON
                "meta" -> AppIcon.META
                else -> AppIcon.FILE
            }
        }
    }
