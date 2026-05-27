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
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Engine plugin for RPG Maker XP, VX, and VX Ace via mkxp-z native runtime.
 *
 * mkxp-z is a C++ RGSS interpreter that runs:
 * - RPG Maker XP (RGSS1, .rxproj, Game.exe)
 * - RPG Maker VX (RGSS2, .rvproj, Game.exe)
 * - RPG Maker VX Ace (RGSS3, .rvproj2, Game.exe)
 *
 * The runtime is built as a native .so library and launched via
 * com.hatkid.mkxpz.MainActivity from the mkxp-z-android submodule.
 */
class MkxpZEngine : GameEngine {

    override val id = "mkxp-z"
    override val name = "RPG Maker XP/VX/VX Ace"
    override val version = "1.0.0"
    override val priority = 10  // Check first — specific file signatures

    companion object {
        private const val TAG = "MkxpZEngine"

        // Detection signatures
        private val PROJECT_FILES = setOf(
            "Game.rxproj",      // XP
            "Game.rvproj",      // VX
            "Game.rvproj2",     // VX Ace
            "Game.rgss3a"       // VX Ace (compiled)
        )

        // RGSS archive files
        private val RGSS_ARCHIVES = setOf(
            "Game.rgssad",      // XP
            "Game.rgss2a",      // VX
            "Game.rgss3a"       // VX Ace
        )
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false

        val files = gameFolder.listFiles() ?: return false
        val names = files.map { it.name }.toSet()

        // Check for project files or RGSS archives
        return PROJECT_FILES.any { names.contains(it) } ||
               RGSS_ARCHIVES.any { names.contains(it) }
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null

        val version = detectRgssVersion(gameFolder)
        val title = detectTitleFromIni(gameFolder) ?: gameFolder.name

        return EngineMetadata(
            engine = id,
            version = version,
            title = title,
            icon = null  // TODO: Extract icon from Game.exe or use default
        )
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching ${gameFolder.name} via mkxp-z")

        try {
            // Launch mkxp-z native activity
            // The submodule provides com.hatkid.mkxpz.MainActivity
            val intent = Intent().apply {
                setClassName(
                    context.packageName,
                    "com.hatkid.mkxpz.MainActivity"
                )
                putExtra("game_path", gameFolder.absolutePath)
                putExtra("mkxp_debug", config.debug)

                // Pass any extra config
                config.extraArgs.forEach { (key, value) ->
                    putExtra(key, value)
                }

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch mkxp-z activity", e)
            // TODO: Show user-friendly error
            // "mkxp-z runtime not available. Please rebuild with native support."
            throw RuntimeException("mkxp-z runtime not available", e)
        }
    }

    override fun getSaves(gameFolder: File): List<SaveFile> {
        // RPG Maker saves are in the game root directory
        // Format: Save1.rvdata2, Save2.rvdata2, etc. (or .rxdata for XP)
        val saves = mutableListOf<SaveFile>()

        val saveFiles = gameFolder.listFiles { file ->
            file.name.startsWith("Save") &&
            (file.name.endsWith(".rvdata2") ||
             file.name.endsWith(".rvdata") ||
             file.name.endsWith(".rxdata"))
        } ?: return emptyList()

        saveFiles.forEachIndexed { index, file ->
            val slotMatch = Regex("""Save(\d+)""").find(file.name)
            val slot = slotMatch?.groupValues?.get(1)?.toIntOrNull()

            saves.add(SaveFile(
                name = file.name,
                file = file,
                timestamp = file.lastModified(),
                slot = slot
            ))
        }

        return saves.sortedBy { it.slot ?: 999 }
    }

    private fun detectRgssVersion(gameFolder: File): String {
        val files = gameFolder.listFiles() ?: return "Unknown"
        val names = files.map { it.name }.toSet()

        return when {
            names.contains("Game.rvproj2") || names.contains("Game.rgss3a") -> "RGSS3 (VX Ace)"
            names.contains("Game.rvproj") -> "RGSS2 (VX)"
            names.contains("Game.rxproj") -> "RGSS1 (XP)"
            else -> "RGSS (Unknown version)"
        }
    }

    private fun detectTitleFromIni(gameFolder: File): String? {
        // Try to read title from Game.ini
        val iniFile = File(gameFolder, "Game.ini")
        if (!iniFile.exists()) return null

        return try {
            iniFile.readLines().firstOrNull { line ->
                line.startsWith("Title=")
            }?.substringAfter("Title=")?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Game.ini", e)
            null
        }
    }
}
