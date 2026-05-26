/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.workspace

import android.content.Context
import com.runestone.app.data.EngineType
import com.runestone.app.engine.EngineDetector
import java.io.File

/**
 * Manages installed games on the device.
 *
 * Structure:
 * ```
 * {filesDir}/games/
 *   {gameName}/
 *     original/       -> imported game files
 *     saves/          -> per-game save files
 *     manifest.json   -> engine type, settings
 * ```
 */
class WorkspaceManager(private val context: Context) {

    data class GameInfo(
        val storageName: String,
        val displayName: String,
        val engineType: EngineType,
        val gamePath: String,
        val fileCount: Int,
    )

    val gamesBaseDir: File
        get() = File(context.filesDir, "games")

    /**
     * Scan for installed games in the games directory.
     */
    fun scanInstalledGames(): List<GameInfo> {
        val dir = gamesBaseDir
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { gameDir ->
                val originalDir = File(gameDir, "original")
                if (!originalDir.isDirectory) return@mapNotNull null

                val engineType = EngineDetector.detect(originalDir)
                val fileCount = originalDir.walkTopDown().count { it.isFile }

                GameInfo(
                    storageName = gameDir.name,
                    displayName = formatDisplayName(gameDir.name),
                    engineType = engineType,
                    gamePath = originalDir.absolutePath,
                    fileCount = fileCount,
                )
            }
            ?.sortedBy { it.displayName }
            ?: emptyList()
    }

    /**
     * Check if a game name is already installed (to avoid duplicates).
     */
    fun isInstalled(gameName: String): Boolean {
        return File(gamesBaseDir, gameName).exists()
    }

    /**
     * Create a unique game directory name.
     */
    fun allocateGameDir(baseName: String): File {
        var dirName = sanitizeName(baseName)
        var dir = File(gamesBaseDir, dirName)
        var counter = 1
        while (dir.exists()) {
            dirName = "${sanitizeName(baseName)}-$counter"
            dir = File(gamesBaseDir, dirName)
            counter++
        }
        return dir
    }

    /**
     * Resolve paths for a given game.
     */
    fun gameDir(storageName: String): File = File(gamesBaseDir, storageName)
    fun originalDir(storageName: String): File = File(gameDir(storageName), "original")
    fun savesDir(storageName: String): File = File(gameDir(storageName), "saves")

    /**
     * Remove an installed game and all its data.
     */
    fun removeGame(storageName: String) {
        val dir = gameDir(storageName)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun formatDisplayName(dirName: String): String {
        return dirName
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
            }
    }

    private fun sanitizeName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(64)
    }
}
