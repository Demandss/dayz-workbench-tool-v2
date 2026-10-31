package com.github.demandss.dayz_workbench_tool.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

data class AppColors(
    // UI
    val frameBackground: Color,
    val elementsBackground: Color,
    val selectColor: Color,
    val subSelectColor: Color,
    val darkenedBorder: Color,
    val textForeground: Color,
    val ghostlyText: Color,
    // Icons
    val modIconClientSide: Color,
    val modIconServerSide: Color,
    val modIconEnabled: Color,
    val modIconDisabled: Color,
    val modIconWorkshop: Color,
)

@Serializable
data class ThemeDefinition(
    val id: String,
    val name: String,
    // UI
    val frameBackground: Long,
    val elementsBackground: Long,
    val selectColor: Long,
    val subSelectColor: Long,
    val darkenedBorder: Long,
    val textForeground: Long,
    val ghostlyText: Long,
    // Icons
    val modIconClientSide: Long = 0xFF346BD8L,
    val modIconServerSide: Long = 0xFF346BD8L,
    val modIconEnabled: Long = 0xFF346BD8L,
    val modIconDisabled: Long = 0xFF346BD8L,
    val modIconWorkshop: Long = 0xFF346BD8L,
) {
    fun toAppColors() =
        AppColors(
            frameBackground = Color(frameBackground.toInt()),
            elementsBackground = Color(elementsBackground.toInt()),
            selectColor = Color(selectColor.toInt()),
            subSelectColor = Color(subSelectColor.toInt()),
            darkenedBorder = Color(darkenedBorder.toInt()),
            textForeground = Color(textForeground.toInt()),
            ghostlyText = Color(ghostlyText.toInt()),
            modIconClientSide = Color(modIconClientSide.toInt()),
            modIconServerSide = Color(modIconServerSide.toInt()),
            modIconEnabled = Color(modIconEnabled.toInt()),
            modIconDisabled = Color(modIconDisabled.toInt()),
            modIconWorkshop = Color(modIconWorkshop.toInt()),
        )
}

object BuiltinThemes {
    val Dark =
        ThemeDefinition(
            id = "dark",
            name = "Dark",
            frameBackground = 0xFF2B2D30,
            elementsBackground = 0xFF43454A,
            selectColor = 0xFF2E436E,
            subSelectColor = 0xFF346BD8,
            darkenedBorder = 0xFF1E1F22,
            textForeground = 0xFFDCD4CF,
            ghostlyText = 0xFF9499A1,
            modIconClientSide = 0xFF346BD8,
            modIconServerSide = 0xFF346BD8,
            modIconEnabled = 0xFF346BD8,
            modIconDisabled = 0xFF346BD8,
            modIconWorkshop = 0xFF346BD8,
        )
    val Light =
        ThemeDefinition(
            id = "light",
            name = "Light",
            frameBackground = 0xFFF0F0F0,
            elementsBackground = 0xFFDFDFDF,
            selectColor = 0xFFBBD4FF,
            subSelectColor = 0xFF2469DE,
            darkenedBorder = 0xFFCCCCCC,
            textForeground = 0xFF1C1C1E,
            ghostlyText = 0xFF8E8E93,
            modIconClientSide = 0xFF2469DE,
            modIconServerSide = 0xFF2469DE,
            modIconEnabled = 0xFF2469DE,
            modIconDisabled = 0xFF2469DE,
            modIconWorkshop = 0xFF2469DE,
        )

    val all = listOf(Dark, Light)

    fun findById(id: String) = all.find { it.id == id }
}

fun resolveTheme(
    selectedId: String,
    customThemes: List<ThemeDefinition>,
): AppColors =
    (
        BuiltinThemes.findById(selectedId)
            ?: customThemes.find { it.id == selectedId }
            ?: BuiltinThemes.Dark
    ).toAppColors()
