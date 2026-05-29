/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.data

enum class UIMode(val label: String, val description: String) {
    GRID("Grid", "Default card grid"),
    CAROUSEL_3D("3D Shelf", "Perspective carousel"),
    LIST("Compact List", "Vertical text list"),
    TILES("Tiles", "Smaller cards in rows"),
}

data class RunnerSettings(
    val layoutMode: LayoutMode = LayoutMode.PORTRAIT_CONSOLE,
    val touchOpacity: Float = 0.72f,
    val touchScale: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
    val hapticIntensity: Float = 0.55f,
    val showExtraButtons: Boolean = false,
    val integerScaling: Boolean = false,
    val smoothScaling: Boolean = false,
    val textScale: Float = 1.0f,
    val forceAudioExt: String = ".ogg",
    val uiMode: UIMode = UIMode.GRID,
)
