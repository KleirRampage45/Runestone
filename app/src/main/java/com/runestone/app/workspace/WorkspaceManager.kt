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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages installed games on the device.
 *
 * The on-disk folder is still named "original" for compatibility with older
 * installs, but it is the playable installed game directory. Runestone runs
 * games from that directory and uses sparse patch backups for touched files
 * instead of duplicating the full game into an active workspace.
 *
 * Structure:
 * ```
 * {filesDir}/games/
 *   {gameName}/
 *     original/       -> installed playable game files
 *     active/         -> legacy duplicate workspace, not used for launch
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
        @Deprecated("Runestone launches from originalPath; activePath is kept for legacy callers.")
        val activePath: String,
        val fileCount: Int,
    )

    val gamesBaseDir: File
        get() = File(context.filesDir, "games")

    fun scanInstalledGames(): List<GameInfo> {
        val dir = gamesBaseDir
        ensureNoMedia(dir)
        if (!dir.exists()) return emptyList()

        // Try cache first to avoid full filesystem scan on every call
        getGameScanCache()?.let { return it }

        val games = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { gameDir ->
                ensureNoMedia(gameDir)
                repairInterruptedInstall(gameDir)
                val originalDir = File(gameDir, "original")
                if (!originalDir.isDirectory) return@mapNotNull null
                ensureNoMedia(originalDir)

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

        // Persist scan result to cache for subsequent calls
        saveGameScanCache(games)
        return games
    }

    /** Try to load previously-cached game list. Returns null on miss/stale/error. */
    private fun getGameScanCache(): List<GameInfo>? {
        val cacheFile = gameScanCacheFile
        if (!cacheFile.exists()) return null
        return try {
            val json = JSONObject(cacheFile.readText())
            val versionCode = json.optInt("versionCode", -1)
            if (versionCode != appVersionCode) {
                cacheFile.delete()
                return null
            }
            val arr = json.getJSONArray("games")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                GameInfo(
                    storageName = obj.getString("storageName"),
                    displayName = obj.getString("displayName"),
                    engineType = EngineType.valueOf(obj.getString("engineType")),
                    originalPath = obj.getString("originalPath"),
                    activePath = obj.getString("originalPath"),
                    fileCount = obj.getInt("fileCount"),
                )
            }
        } catch (e: Exception) {
            cacheFile.delete()
            null
        }
    }

    /** Persist scanned game list to cache. Failures are non-critical. */
    private fun saveGameScanCache(games: List<GameInfo>) {
        try {
            val arr = JSONArray()
            games.forEach { game ->
                arr.put(JSONObject().apply {
                    put("storageName", game.storageName)
                    put("displayName", game.displayName)
                    put("engineType", game.engineType.name)
                    put("originalPath", game.originalPath)
                    put("fileCount", game.fileCount)
                })
            }
            gameScanCacheFile.writeText(JSONObject().apply {
                put("versionCode", appVersionCode)
                put("games", arr)
            }.toString())
        } catch (e: Exception) { /* cache write failure is non-critical */ }
    }

    private val gameScanCacheFile: File
        get() = File(context.filesDir, "game_scan_cache.json")

    private val appVersionCode: Int
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (e: Exception) { 0 }

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
            EngineType.RENPY -> {
                listOf(
                    File(originalDir, "game/options.rpy"),
                    File(originalDir, "game/script.rpy"),
                ).firstNotNullOfOrNull { file ->
                    if (!file.isFile) return@firstNotNullOfOrNull null
                    runCatching {
                        Regex("""define\s+config\.name\s*=\s*["'](.+?)["']""")
                            .find(file.readText())
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                    }.getOrNull()
                }
            }
            else -> null
        }
    }

    private fun repairInterruptedInstall(gameDir: File) {
        val originalDir = File(gameDir, "original")
        if (originalDir.isDirectory) return

        val children = gameDir.listFiles()?.toList().orEmpty()
        if (children.isEmpty()) return

        val ignoredNames = setOf("active", "saves", "incoming", "manifest.json", "install_state.json")
        val gamePayload = children.filter { it.name !in ignoredNames }
        if (gamePayload.isEmpty()) return

        val engineType = EngineDetector.detect(gameDir)
        if (engineType == EngineType.UNKNOWN) return

        val repairDir = File(gameDir, "original_repair")
        if (repairDir.exists()) repairDir.deleteRecursively()
        require(repairDir.mkdirs()) { "Could not create repair workspace for ${gameDir.name}" }

        try {
            gamePayload.forEach { file ->
                require(file.renameTo(File(repairDir, file.name))) {
                    "Could not move ${file.name} into repaired install"
                }
            }
            require(repairDir.renameTo(originalDir)) {
                "Could not finalize repaired install for ${gameDir.name}"
            }
            ensureWorkspace(gameDir.name)
            ensureNoMedia(originalDir)

            val fileCount = originalDir.walkTopDown().count { it.isFile }
            File(gameDir, "manifest.json").writeText(JSONObject().apply {
                put("storageName", gameDir.name)
                put("engineType", engineType.name)
                put("engineLabel", engineType.label)
                put("fileCount", fileCount)
                put("importedAt", System.currentTimeMillis())
                put("repaired", true)
            }.toString(2))
        } catch (e: Exception) {
            repairDir.listFiles()?.forEach { file ->
                file.renameTo(File(gameDir, file.name))
            }
            repairDir.deleteRecursively()
            throw e
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
    fun saveBackupsDir(storageName: String): File = File(gameDir(storageName), "save_backups")
    fun incomingDir(storageName: String): File = File(gameDir(storageName), "incoming")

    fun ensureWorkspace(storageName: String): File {
        val dir = gameDir(storageName)
        listOf(dir, File(dir, "original"), File(dir, "incoming"), File(dir, "saves"), File(dir, "save_backups"), File(dir, "patches")).forEach {
            it.mkdirs()
            ensureNoMedia(it)
        }
        ensureNoMedia(gamesBaseDir)
        return dir
    }

    fun ensureNoMedia(storageName: String) {
        ensureNoMedia(gameDir(storageName))
        listOf(originalDir(storageName), incomingDir(storageName), savesDir(storageName), saveBackupsDir(storageName), File(gameDir(storageName), "patches")).forEach {
            ensureNoMedia(it)
        }
    }

    @Deprecated(
        "Full active workspace duplication is no longer part of the install model. " +
            "Games run from originalDir(), with patches protected by sparse backups.",
    )
    fun rebuildActiveWorkspace(storageName: String) = Unit

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
            val saves = savesDir(storageName)
            val preservedSaves = File(context.cacheDir, "preserved_saves_${storageName}_${System.currentTimeMillis()}")
            if (saves.exists()) {
                saves.copyRecursively(preservedSaves, overwrite = true)
            }
            dir.deleteRecursively()
            dir.mkdirs()
            if (preservedSaves.exists()) {
                preservedSaves.copyRecursively(File(dir, "saves"), overwrite = true)
                preservedSaves.deleteRecursively()
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

    private fun ensureNoMedia(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            if (dir.isDirectory) {
                val marker = File(dir, ".nomedia")
                if (!marker.exists()) marker.writeText("")
            }
        }
    }
}
