/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.runtime

import com.runestone.app.data.RunnerSettings
import java.io.File

sealed interface RuntimeLaunchResult {
    data object Stubbed : RuntimeLaunchResult
    data class Failed(val reason: String) : RuntimeLaunchResult
}

class NativeBridge {
    fun launch(gamePath: String, configPath: String): RuntimeLaunchResult {
        return if (gamePath.isBlank() || configPath.isBlank()) {
            RuntimeLaunchResult.Failed("Missing game or config path")
        } else {
            RuntimeLaunchResult.Stubbed
        }
    }
}
