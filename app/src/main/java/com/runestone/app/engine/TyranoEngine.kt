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
 * Engine plugin for TyranoBuilder visual novels via Android WebView.
 *
 * TyranoBuilder exports games as HTML5/JavaScript, similar to RPG Maker MV/MZ.
 * Runs in WebView with minimal configuration.
 *
 * Detection: data/ and tyrano/ directories + index.html
 *
 * Status: Phase 2 — Stub implementation. Not yet tested.
 */
class TyranoEngine : GameEngine {

    override val id = "tyrano"
    override val name = "TyranoBuilder"
    override val version = "1.0.0"
    override val priority = 50  // Medium priority

    companion object {
        private const val TAG = "TyranoEngine"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false

        // TyranoBuilder games have data/ and tyrano/ directories
        val dataDir = File(gameFolder, "data")
        val tyranoDir = File(gameFolder, "tyrano")

        if (!dataDir.exists() || !tyranoDir.exists()) return false

        // Must have an index.html
        val indexHtml = File(gameFolder, "index.html")
        return indexHtml.exists()
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null

        val title = detectTitleFromTyrano(gameFolder) ?: gameFolder.name

        return EngineMetadata(
            engine = id,
            version = "TyranoBuilder",
            title = title,
            icon = null
        )
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching ${gameFolder.name} via WebView (TyranoBuilder)")

        val indexHtml = File(gameFolder, "index.html")
        if (!indexHtml.exists()) {
            throw RuntimeException("index.html not found in $gameFolder")
        }

        // Launch GameActivity which will create a WebViewEngine instance
        val intent = Intent(context, Class.forName("com.runestone.app.GameActivity")).apply {
            putExtra("game_path", gameFolder.absolutePath)
            putExtra("engine_type", id)
            putExtra("entry_point", "index.html")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    override fun getSaves(gameFolder: File): List<SaveFile> {
        // TyranoBuilder saves are in data/save/ as .sav files
        val saveDir = File(gameFolder, "data/save")
        if (!saveDir.exists() || !saveDir.isDirectory) return emptyList()

        val saves = mutableListOf<SaveFile>()

        val saveFiles = saveDir.listFiles { file ->
            file.name.endsWith(".sav")
        } ?: return emptyList()

        saveFiles.forEachIndexed { index, file ->
            saves.add(SaveFile(
                name = file.name,
                file = file,
                timestamp = file.lastModified(),
                slot = index
            ))
        }

        return saves
    }

    private fun detectTitleFromTyrano(gameFolder: File): String? {
        // Title is usually in data/system/Config.tjs
        // For now, just use folder name
        // TODO: Parse Config.tjs to extract title
        return null
    }
}
