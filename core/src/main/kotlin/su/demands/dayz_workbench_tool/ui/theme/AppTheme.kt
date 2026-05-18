package su.demands.dayz_workbench_tool.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppColors = staticCompositionLocalOf { BuiltinThemes.Dark.toAppColors() }

val FrameBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.frameBackground

val ElementsBackground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.elementsBackground

val SelectColor: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.selectColor

val SubSelectColor: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.subSelectColor

val DarkenedBorder: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.darkenedBorder

val TextForeground: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.textForeground

val GhostlyText: Color
    @Composable @ReadOnlyComposable get() = LocalAppColors.current.ghostlyText

private val zero = RoundedCornerShape(0)

private val AllRectangle = Shapes(
    extraSmall = zero, small = zero, medium = zero, large = zero, extraLarge = zero
)

@Composable
fun AppTheme(colors: AppColors = BuiltinThemes.Dark.toAppColors(), content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary          = colors.subSelectColor,
        onPrimary        = colors.textForeground,
        background       = colors.frameBackground,
        onBackground     = colors.textForeground,
        surface          = colors.frameBackground,
        onSurface        = colors.textForeground,
        surfaceVariant   = colors.elementsBackground,
        onSurfaceVariant = colors.textForeground,
        outline          = colors.subSelectColor,
        secondary        = colors.elementsBackground,
        onSecondary      = colors.textForeground
    )
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, shapes = AllRectangle, content = content)
    }
}