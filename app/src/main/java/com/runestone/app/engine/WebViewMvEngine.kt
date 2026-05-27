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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.WebView
import java.io.File

/**
 * Engine plugin for RPG Maker MV games via Android WebView.
 *
 * RPG Maker MV exports games as HTML5/JavaScript that run in nw.js on desktop.
 * On Android, we replace nw.js with the built-in Chromium WebView.
 *
 * Detection: www/index.html + package.json (without MZ-specific files)
 *
 * This is a wrapper around the existing WebViewEngine class that implements
 * the GameEngine interface for the plugin system.
 */
class WebViewMvEngine : GameEngine {

    override val id = "webview-mv"
    override val name = "RPG Maker MV"
    override val version = "1.0.0"
    override val priority = 40  // Lower than native engines, higher than generic HTML

    companion object {
        private const val TAG = "WebViewMvEngine"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false

        val wwwDir = File(gameFolder, "www")
        if (!wwwDir.exists() || !wwwDir.isDirectory) return false

        val indexHtml = File(wwwDir, "index.html")
        val packageJson = File(wwwDir, "package.json")

        if (!indexHtml.exists()) return false

        // MV has package.json but NOT MZ-specific files
        if (packageJson.exists()) {
            val content = try {
                packageJson.readText()
            } catch (e: Exception) {
                return false
            }

            // If it mentions "MZ", it's not MV
            if (content.contains("MZ", ignoreCase = true)) return false

            return true
        }

        return false
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null

        val title = detectTitleFromPackageJson(gameFolder) ?: gameFolder.name

        return EngineMetadata(
            engine = id,
            version = "MV",
            title = title,
            icon = null
        )
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching ${gameFolder.name} via WebView (MV)")

        val wwwDir = File(gameFolder, "www")
        val indexHtml = File(wwwDir, "index.html")

        if (!indexHtml.exists()) {
            throw RuntimeException("index.html not found in $wwwDir")
        }

        // Launch GameActivity which will create a WebViewEngine instance
        val intent = Intent(context, Class.forName("com.runestone.app.GameActivity")).apply {
            putExtra("game_path", gameFolder.absolutePath)
            putExtra("engine_type", id)
            putExtra("entry_point", "www/index.html")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    override fun getSaves(gameFolder: File): List<SaveFile> {
        // MV saves are in www/save/ as .rpgsave files (JSON)
        val saveDir = File(gameFolder, "www/save")
        if (!saveDir.exists() || !saveDir.isDirectory) return emptyList()

        val saves = mutableListOf<SaveFile>()

        val saveFiles = saveDir.listFiles { file ->
            file.name.endsWith(".rpgsave")
        } ?: return emptyList()

        saveFiles.forEachIndexed { index, file ->
            val slotMatch = Regex("""file(\d+)""").find(file.name)
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

    private fun detectTitleFromPackageJson(gameFolder: File): String? {
        val packageJson = File(gameFolder, "www/package.json")
        if (!packageJson.exists()) return null

        return try {
            val content = packageJson.readText()
            // Simple JSON parsing without library dependency
            val titleMatch = Regex(""""title"\s*:\s*"([^"]+)"""").find(content)
            titleMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse package.json", e)
            null
        }
    }
}
