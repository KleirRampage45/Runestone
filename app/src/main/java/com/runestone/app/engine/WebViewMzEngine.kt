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
 * Engine plugin for RPG Maker MZ games via Android WebView.
 *
 * RPG Maker MZ is the successor to MV, also exports as HTML5/JavaScript.
 * Similar to MV but with some structural differences (e.g., js/rmmz_*.js files).
 *
 * Detection: www/index.html + package.json with "MZ" or js/rmmz_*.js files
 *
 * This is a wrapper around the existing WebViewEngine class that implements
 * the GameEngine interface for the plugin system.
 */
class WebViewMzEngine : GameEngine {

    override val id = "webview-mz"
    override val name = "RPG Maker MZ"
    override val version = "1.0.0"
    override val priority = 35  // Check before MV (more specific signatures)

    companion object {
        private const val TAG = "WebViewMzEngine"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false

        // Check for MZ project file
        val files = gameFolder.listFiles() ?: return false
        val names = files.map { it.name }.toSet()

        if (names.any { it.equals("Game.rmmzproject", ignoreCase = true) }) {
            return true
        }

        // Check www structure
        val wwwDir = File(gameFolder, "www")
        if (!wwwDir.exists() || !wwwDir.isDirectory) return false

        val indexHtml = File(wwwDir, "index.html")
        if (!indexHtml.exists()) return false

        // MZ-specific: check for rmmz_*.js files
        val jsDir = File(wwwDir, "js")
        if (jsDir.exists() && jsDir.isDirectory) {
            val hasRmmzJs = jsDir.listFiles()?.any { file ->
                file.name.startsWith("rmmz_") && file.name.endsWith(".js")
            } ?: false

            if (hasRmmzJs) return true
        }

        // Check package.json for MZ mention
        val packageJson = File(wwwDir, "package.json")
        if (packageJson.exists()) {
            val content = try {
                packageJson.readText()
            } catch (e: Exception) {
                return false
            }

            if (content.contains("MZ", ignoreCase = true)) return true
        }

        return false
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null

        val title = detectTitleFromPackageJson(gameFolder) ?: gameFolder.name

        return EngineMetadata(
            engine = id,
            version = "MZ",
            title = title,
            icon = null
        )
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching ${gameFolder.name} via WebView (MZ)")

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
        // MZ saves are in www/save/ as .rmmzsave files (JSON)
        val saveDir = File(gameFolder, "www/save")
        if (!saveDir.exists() || !saveDir.isDirectory) return emptyList()

        val saves = mutableListOf<SaveFile>()

        val saveFiles = saveDir.listFiles { file ->
            file.name.endsWith(".rmmzsave") || file.name.endsWith(".rpgsave")
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
