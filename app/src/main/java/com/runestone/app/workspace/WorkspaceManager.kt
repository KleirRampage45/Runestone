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
 *     original/       -> clean imported game files (never modified)
 *     active/         -> playable copy (modified by mods/patches)
 *     saves/          -> protected save files
 *     incoming/       -> temp dir during import
 *     manifest.json   -> engine type, metadata
 *     install_state.json -> import status
 * ```
 */
class WorkspaceManager(private val context: Context) {

    data class GameInfo(
        val storageName: String,
        val displayName: String,
        val engineType: EngineType,
        val originalPath: String,
        val activePath: String,
        val fileCount: Int,
    )

    val gamesBaseDir: File
        get() = File(context.filesDir, "games")

    fun scanInstalledGames(): List<GameInfo> {
        val dir = gamesBaseDir
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { gameDir ->
                val originalDir = File(gameDir, "original")
                if (!originalDir.isDirectory) return@mapNotNull null

                // Check for manual engine override in install_state.json
                val override = runCatching {
                    val stateFile = File(gameDir, "install_state.json")
                    if (stateFile.isFile) {
                        val json = org.json.JSONObject(stateFile.readText())
                        if (json.has("engineOverride")) {
                            EngineType.valueOf(json.getString("engineOverride"))
                        } else null
                    } else null
                }.getOrNull()

                val engineType = override ?: EngineDetector.detect(originalDir)
                val fileCount = originalDir.walkTopDown().count { it.isFile }
                val displayName = readGameTitle(originalDir, engineType) ?: formatDisplayName(gameDir.name)

                GameInfo(
                    storageName = gameDir.name,
                    displayName = displayName,
                    engineType = engineType,
                    originalPath = originalDir.absolutePath,
                    activePath = originalDir.absolutePath,
                    fileCount = fileCount,
                )
            }
            ?.sortedBy { it.displayName }
            ?: emptyList()
    }

    /** Read the game's actual title from its metadata files. */
    private fun readGameTitle(originalDir: File, engineType: EngineType): String? {
        return when (engineType) {
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> {
                val ini = File(originalDir, "Game.ini")
                if (ini.isFile) {
                    ini.readLines().firstOrNull { it.startsWith("Title=", ignoreCase = true) }
                        ?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
                } else null
            }
            EngineType.MV, EngineType.MZ -> {
                val sys = File(originalDir, "www/data/System.json")
                if (sys.isFile) {
                    runCatching {
                        org.json.JSONObject(sys.readText()).optString("gameTitle", null)
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                } else null
            }
            else -> null
        }
    }

    fun isInstalled(gameName: String): Boolean {
        return File(gamesBaseDir, gameName).exists()
    }

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

    fun gameDir(storageName: String): File = File(gamesBaseDir, storageName)
    fun originalDir(storageName: String): File = File(gameDir(storageName), "original")
    fun activeDir(storageName: String): File = File(gameDir(storageName), "active")
    fun savesDir(storageName: String): File = File(gameDir(storageName), "saves")
    fun incomingDir(storageName: String): File = File(gameDir(storageName), "incoming")

    fun ensureWorkspace(storageName: String): File {
        val dir = gameDir(storageName)
        listOf(dir, File(dir, "saves")).forEach { it.mkdirs() }
        return dir
    }

    fun rebuildActiveWorkspace(storageName: String) {
        val gameDir = gameDir(storageName)
        val original = File(gameDir, "original")
        val active = File(gameDir, "active")

        if (!original.isDirectory) return

        active.deleteRecursively()
        active.mkdirs()
        original.copyRecursively(active, overwrite = true)
    }

    fun clearActiveWorkspace(storageName: String) {
        val active = activeDir(storageName)
        if (active.exists()) {
            active.deleteRecursively()
        }
    }

    fun removeGame(storageName: String, keepSaves: Boolean = false) {
        val dir = gameDir(storageName)
        if (!dir.exists()) return

        if (keepSaves) {
            // Save saves, nuke everything else
            val saves = savesDir(storageName)
            dir.deleteRecursively()
            dir.mkdirs()
            if (saves.exists()) {
                saves.copyRecursively(File(dir, "saves"), overwrite = true)
            }
        } else {
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
            .ifEmpty { "game" }
    }
}
