package com.runestone.app.ui.theme

import android.graphics.Color

data class ThemeColors(
    val name: String,
    val background: Int,
    val surface: Int,
    val cardBackground: Int,
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentMuted: Int,
    val muted: Int,
    val mutedDim: Int,
    val overlayDim: Int,
    val error: Int,
    val success: Int,
) {
    companion object {
        val DARK = ThemeColors(
            name = "Dark",
            background = Color.rgb(3, 3, 4),
            surface = Color.rgb(12, 11, 16),
            cardBackground = Color.rgb(22, 20, 26),
            text = Color.rgb(232, 229, 220),
            textSecondary = Color.rgb(180, 160, 140),
            accent = Color.rgb(207, 174, 126),
            accentMuted = Color.argb(60, 207, 174, 126),
            muted = Color.rgb(140, 130, 112),
            mutedDim = Color.rgb(100, 95, 85),
            overlayDim = Color.argb(180, 0, 0, 0),
            error = Color.rgb(240, 120, 120),
            success = Color.rgb(140, 220, 140),
        )

        val LIGHT = ThemeColors(
            name = "Light",
            background = Color.rgb(245, 243, 240),
            surface = Color.rgb(255, 255, 255),
            cardBackground = Color.rgb(240, 238, 235),
            text = Color.rgb(30, 28, 26),
            textSecondary = Color.rgb(100, 95, 90),
            accent = Color.rgb(170, 130, 80),
            accentMuted = Color.argb(60, 170, 130, 80),
            muted = Color.rgb(140, 135, 130),
            mutedDim = Color.rgb(180, 175, 170),
            overlayDim = Color.argb(160, 40, 38, 35),
            error = Color.rgb(200, 60, 60),
            success = Color.rgb(80, 170, 80),
        )
    }
}
