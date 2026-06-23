package com.runestone.app.ui

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import com.runestone.app.ui.theme.ThemeColors
import com.runestone.app.ui.theme.ThemeProvider

data class ColorPalette(
    val name: String,
    val accent: Int,
    val accentBright: Int,
    val accentDim: Int,
    val accentBg: Int,
    val accentStroke: Int,
    val panelStroke: Int,
    val accentRed: Int,
    val accentGreen: Int,
)

object Theme {
    val TEXT: Int get() = tp()?.text ?: Color.rgb(232, 229, 220)
    val MUTED: Int get() = tp()?.muted ?: Color.rgb(140, 130, 112)
    val MUTED_DIM: Int get() = tp()?.mutedDim ?: Color.rgb(120, 112, 104)
    val PANEL_BG: Int get() = tp()?.let { c ->
        Color.argb(190, Color.red(c.surface), Color.green(c.surface), Color.blue(c.surface))
    } ?: Color.argb(190, 12, 11, 16)
    val BACKGROUND: Int get() = tp()?.background ?: Color.rgb(3, 3, 4)
    val SURFACE: Int get() = tp()?.surface ?: Color.rgb(12, 11, 16)
    val CARD_BG: Int get() = tp()?.cardBackground ?: Color.rgb(22, 20, 26)
    val ERROR: Int get() = tp()?.error ?: Color.rgb(240, 120, 120)
    val SUCCESS: Int get() = tp()?.success ?: Color.rgb(140, 220, 140)

    val Amber = ColorPalette(
        name = "Amber",
        accent = Color.rgb(207, 174, 126),
        accentBright = Color.rgb(220, 200, 160),
        accentDim = Color.rgb(160, 140, 100),
        accentBg = Color.argb(50, 207, 174, 126),
        accentStroke = Color.argb(80, 207, 174, 126),
        panelStroke = Color.argb(60, 207, 174, 126),
        accentRed = Color.rgb(240, 120, 120),
        accentGreen = Color.rgb(140, 220, 140),
    )
    val Emerald = ColorPalette(
        name = "Emerald",
        accent = Color.rgb(120, 200, 140),
        accentBright = Color.rgb(160, 220, 170),
        accentDim = Color.rgb(90, 160, 110),
        accentBg = Color.argb(50, 120, 200, 140),
        accentStroke = Color.argb(80, 120, 200, 140),
        panelStroke = Color.argb(60, 120, 200, 140),
        accentRed = Color.rgb(240, 120, 120),
        accentGreen = Color.rgb(120, 200, 140),
    )
    val Royal = ColorPalette(
        name = "Royal",
        accent = Color.rgb(190, 140, 220),
        accentBright = Color.rgb(210, 170, 240),
        accentDim = Color.rgb(150, 100, 180),
        accentBg = Color.argb(50, 190, 140, 220),
        accentStroke = Color.argb(80, 190, 140, 220),
        panelStroke = Color.argb(60, 190, 140, 220),
        accentRed = Color.rgb(240, 120, 120),
        accentGreen = Color.rgb(140, 220, 140),
    )
    val Crimson = ColorPalette(
        name = "Crimson",
        accent = Color.rgb(220, 120, 120),
        accentBright = Color.rgb(240, 160, 150),
        accentDim = Color.rgb(180, 90, 90),
        accentBg = Color.argb(50, 220, 120, 120),
        accentStroke = Color.argb(80, 220, 120, 120),
        panelStroke = Color.argb(60, 220, 120, 120),
        accentRed = Color.rgb(255, 100, 100),
        accentGreen = Color.rgb(140, 220, 140),
    )
    val Ocean = ColorPalette(
        name = "Ocean",
        accent = Color.rgb(100, 180, 220),
        accentBright = Color.rgb(140, 210, 240),
        accentDim = Color.rgb(70, 140, 180),
        accentBg = Color.argb(50, 100, 180, 220),
        accentStroke = Color.argb(80, 100, 180, 220),
        panelStroke = Color.argb(60, 100, 180, 220),
        accentRed = Color.rgb(240, 120, 120),
        accentGreen = Color.rgb(140, 220, 140),
    )
    val Monochrome = ColorPalette(
        name = "Monochrome",
        accent = Color.rgb(200, 195, 185),
        accentBright = Color.rgb(220, 215, 205),
        accentDim = Color.rgb(150, 145, 135),
        accentBg = Color.argb(50, 200, 195, 185),
        accentStroke = Color.argb(80, 200, 195, 185),
        panelStroke = Color.argb(60, 200, 195, 185),
        accentRed = Color.rgb(240, 120, 120),
        accentGreen = Color.rgb(140, 220, 140),
    )

    val palettes = listOf(Amber, Emerald, Royal, Crimson, Ocean, Monochrome)
    var active: ColorPalette = Amber

    fun byName(name: String): ColorPalette = palettes.find { it.name == name } ?: Amber

    fun isReducedMotion(context: Context): Boolean {
        val appPrefs = context.getSharedPreferences("runestone-settings-v1", Context.MODE_PRIVATE)
        val appSetting = appPrefs.getBoolean("reduceMotion", false)
        val systemScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return appSetting || systemScale == 0f
    }

    private var tpInstance: ThemeProvider? = null
    private fun tp(): ThemeColors? {
        if (tpInstance == null) return null
        return tpInstance!!.colors
    }

    fun bind(provider: ThemeProvider) { tpInstance = provider }
}
