/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.data

data class RunnerSettings(
    val layoutMode: String = LAYOUT_PORTRAIT_CONSOLE,
) {
    companion object {
        const val LAYOUT_LANDSCAPE = "landscape"
        const val LAYOUT_PORTRAIT_CONSOLE = "portrait_console"
        const val LAYOUT_GAMEPAD = "gamepad"
    }
}
