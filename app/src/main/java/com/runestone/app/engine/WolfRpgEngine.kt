/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.engine

import android.content.Context
import java.io.File

class WolfRpgEngine : GameEngine {
    override val id: String = "wolf"
    override val name: String = "Wolf RPG Editor"
    override val version: String = "detect-only"
    override val priority: Int = 18

    override fun canRun(gameFolder: File): Boolean {
        return gameFolder.walkTopDown()
            .maxDepth(3)
            .any { file -> file.isFile && file.name.equals("Data.wolf", ignoreCase = true) }
    }

    override fun detectTitle(gameFolder: File): String? {
        return gameFolder.name
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            .takeIf { it.isNotBlank() }
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        UnavailableEngine.show(context, name)
    }
}
