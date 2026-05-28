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

class RuntimeConfigWriter {
    fun writeConfig(storageName: String, activeGamePath: File, settings: RunnerSettings): File {
        val config = File(activeGamePath.parentFile, "runtime-$storageName.conf")
        config.writeText(
            """
            storageName=$storageName
            gamePath=${activeGamePath.absolutePath}
            layout=${settings.layoutMode.name}
            textScale=${settings.textScale}
            integerScaling=${settings.integerScaling}
            smoothScaling=${settings.smoothScaling}
            """.trimIndent(),
        )
        return config
    }
}
