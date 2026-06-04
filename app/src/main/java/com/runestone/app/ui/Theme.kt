/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.ui

import android.content.Context
import android.graphics.Color
import android.provider.Settings
/**
 * Shared UI theme constants and color palette system.
 * Use these instead of duplicating color values across screens.
 */

data class ColorPalette(
    val name: String,
    val accent: Int,
    val accentBright: Int,
    val accentDim: Int,
    val accentBg: Int,       // background highlight (panels, cards)
    val accentStroke: Int,   // border for selected/active elements
    val panelStroke: Int,    // default panel border
    val accentRed: Int,      // destructive actions
    val accentGreen: Int,    // success states
)

object Theme {
    // Text colors — consistent across all palettes
    val TEXT = Color.rgb(232, 229, 220)
    val MUTED = Color.rgb(140, 130, 112)
    val MUTED_DIM = Color.rgb(120, 112, 104)  // #787068 — 4.5:1 AA on #0F0E10
    val PANEL_BG = Color.argb(190, 12, 11, 16)

    // ── Palette Definitions ──
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

    // Palette registry
    val palettes = listOf(
        Amber, Emerald, Royal, Crimson, Ocean, Monochrome,
    )

    // Current active palette (updated at runtime)
    var active: ColorPalette = Amber

    fun byName(name: String): ColorPalette =
        palettes.find { it.name == name } ?: Amber

    fun isReducedMotion(context: Context): Boolean {
        val appPrefs = context.getSharedPreferences("runestone-settings-v1", Context.MODE_PRIVATE)
        val appSetting = appPrefs.getBoolean("reduceMotion", false)
        val systemScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return appSetting || systemScale == 0f
    }
}
