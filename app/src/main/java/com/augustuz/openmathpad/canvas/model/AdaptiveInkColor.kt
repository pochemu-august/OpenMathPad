package com.augustuz.openmathpad.canvas.model

import androidx.compose.ui.graphics.Color

/**
 * Theme-aware Adaptive Ink Color engine for OpenMathPad.
 *
 * Guarantees contrast across Light and Dark canvas backgrounds:
 * - Default ink resolves to Deep Charcoal (#0F172A) in Light Mode and Crisp Chalk White (#F8FAFC) in Dark Mode.
 * - Standard STEM colors (Royal Blue, Crimson Red, Forest Green, Vibrant Violet, Orange) retain their distinctive hue
 *   with tuned luminance for optimal readability against dark or light canvas surfaces.
 */
object AdaptiveInkColor {
    // Canonical default adaptive ink colors
    val LightDefault = Color(0xFF0F172A) // Deep Charcoal
    val DarkDefault = Color(0xFFF8FAFC)  // Crisp Chalk White

    // Sentinel representing the default adaptive pen color
    val Default = LightDefault

    // Standard STEM Palette (Light canvas)
    val RoyalBlueLight = Color(0xFF1D4ED8)
    val CrimsonRedLight = Color(0xFFC62828)
    val ForestGreenLight = Color(0xFF059669)
    val VibrantVioletLight = Color(0xFF7C3AED)
    val OrangeLight = Color(0xFFEA580C)

    // Standard STEM Palette (Dark canvas - tuned luminance for contrast)
    val RoyalBlueDark = Color(0xFF38BDF8)   // Vibrant Cyan/Sky Blue
    val CrimsonRedDark = Color(0xFFF87171)  // High-contrast Coral Red
    val ForestGreenDark = Color(0xFF34D399) // Pastel Mint Green
    val VibrantVioletDark = Color(0xFFA78BFA) // Pastel Lavender Violet
    val OrangeDark = Color(0xFFFB923C)       // Crisp Bright Orange

    val PresetPenColorsLight = listOf(
        LightDefault,
        RoyalBlueLight,
        CrimsonRedLight,
        ForestGreenLight,
        VibrantVioletLight,
        OrangeLight
    )

    val PresetPenColorsDark = listOf(
        DarkDefault,
        RoyalBlueDark,
        CrimsonRedDark,
        ForestGreenDark,
        VibrantVioletDark,
        OrangeDark
    )

    /**
     * Checks if a color represents the default ink (or legacy pitch black / white).
     */
    fun isDefaultInk(color: Color): Boolean {
        return color == LightDefault ||
                color == DarkDefault ||
                color == Color(0xFF111827) ||
                color == Color(0xFF1A1A1A) ||
                color == Color(0xFF000000) ||
                color == Color(0xFFFFFFFF)
    }

    /**
     * Dynamically resolves the stroke color for the current canvas background theme.
     */
    fun resolve(color: Color, isDark: Boolean): Color {
        if (isDefaultInk(color)) {
            return if (isDark) DarkDefault else LightDefault
        }

        if (isDark) {
            return when (color) {
                RoyalBlueLight -> RoyalBlueDark
                CrimsonRedLight -> CrimsonRedDark
                ForestGreenLight -> ForestGreenDark
                VibrantVioletLight -> VibrantVioletDark
                OrangeLight, Color(0xFFFF8800) -> OrangeDark
                else -> color
            }
        } else {
            return when (color) {
                RoyalBlueDark -> RoyalBlueLight
                CrimsonRedDark -> CrimsonRedLight
                ForestGreenDark -> ForestGreenLight
                VibrantVioletDark -> VibrantVioletLight
                OrangeDark -> OrangeLight
                else -> color
            }
        }
    }
}
