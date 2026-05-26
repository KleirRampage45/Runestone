/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.engine

import java.io.File

/**
 * Launches RGSS-based games (XP/VX/VX Ace) via the mkxp-z native runtime.
 *
 * mkxp-z is a native .so library that handles:
 * - RGSS1/RGSS2/RGSS3 script execution
 * - SDL2 rendering, input, audio
 * - File I/O (archive loading via .rgssad/.rgss2a/.rgss3a)
 *
 * The runtime is launched as a separate native activity (mkxp-z's MainActivity).
 */
object RgssEngine {

    fun launch(gamePath: String) {
        // TODO: Launch mkxp-z native activity with game path configuration
        // This will mirror the current Grimmobile approach:
        // - Set gameDirectory in mkxp config
        // - Start com.hatkid.mkxpz.MainActivity with appropriate extras
    }
}
