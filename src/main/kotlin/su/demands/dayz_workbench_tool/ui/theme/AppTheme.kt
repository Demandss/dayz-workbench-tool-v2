package su.demands.dayz_workbench_tool.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// DarkSwing exact color palette
val FrameBackground  = Color(0xFF2B2D30)
val ElementsBackground = Color(0xFF43454A)
val SelectColor      = Color(0xFF2E436E)
val SubSelectColor   = Color(0xFF346BD8)
val DarkenedBorder   = Color(0xFF1E1F22)
val TextForeground   = Color(0xFFDCD4CF)
val GhostlyText      = Color(0xFF6F737A)

private val DarkSwingColorScheme = darkColorScheme(
    primary          = SubSelectColor,
    onPrimary        = TextForeground,
    background       = FrameBackground,
    onBackground     = TextForeground,
    surface          = FrameBackground,
    onSurface        = TextForeground,
    surfaceVariant   = ElementsBackground,
    onSurfaceVariant = TextForeground,
    outline          = SubSelectColor,
    secondary        = ElementsBackground,
    onSecondary      = TextForeground
)

private val zero = RoundedCornerShape(0)

private val AllRectangle = Shapes(
    extraSmall = zero,
    small      = zero,
    medium     = zero,
    large      = zero,
    extraLarge = zero
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkSwingColorScheme,
        shapes      = AllRectangle,
        content     = content
    )
}