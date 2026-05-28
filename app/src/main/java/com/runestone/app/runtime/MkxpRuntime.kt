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

class MkxpRuntime(
    private val nativeBridge: NativeBridge = NativeBridge(),
    private val configWriter: RuntimeConfigWriter = RuntimeConfigWriter(),
) {
    fun launch(storageName: String, activeGamePath: File, settings: RunnerSettings): RuntimeLaunchResult {
        val config = configWriter.writeConfig(storageName, activeGamePath, settings)
        return nativeBridge.launch(activeGamePath.absolutePath, config.absolutePath)
    }
}
