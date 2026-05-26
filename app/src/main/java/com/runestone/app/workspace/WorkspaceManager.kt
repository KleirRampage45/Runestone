/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.workspace

import android.content.Context
import com.runestone.app.data.EngineType
import com.runestone.app.data.GameEntry
import com.runestone.app.data.InstallStatus
import com.runestone.app.engine.EngineDetector
import java.io.File

/**
 * Manages isolated game storage on the device.
 *
 * Structure:
 * ```
 * {filesDir}/games/
 *   {gameName}/
 *     original/    -> imported game files (read-only after import)
 *     saves/       -> per-game save files
 *     manifest.json -> engine type, settings, timestamps
 * ```
 */
class WorkspaceManager(private val context: Context) {

    private val gamesDir: File
        get() = File(context.filesDir, "games")

    fun scanInstalledGames(): List<GameEntry> {
        val dir = gamesDir
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { gameDir ->
                val originalDir = File(gameDir, "original")
                val manifestFile = File(gameDir, "manifest.json")

                if (!originalDir.isDirectory) return@mapNotNull null

                val engineType = EngineDetector.detect(originalDir)
                val displayName = gameDir.name
                    .replace("-", " ")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                GameEntry(
                    storageName = gameDir.name,
                    displayName = displayName,
                    engineType = engineType,
                    gamePath = originalDir.absolutePath,
                    status = InstallStatus.READY,
                )
            }
            ?: emptyList()
    }

    fun gameDir(gameName: String): File = File(gamesDir, gameName)

    fun originalDir(gameName: String): File = File(gameDir(gameName), "original")

    fun savesDir(gameName: String): File = File(gameDir(gameName), "saves")
}
