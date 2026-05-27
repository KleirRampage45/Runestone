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
 * Engine plugin for Construct 2/3 HTML5 games via Android WebView.
 *
 * Construct 2/3 exports games as HTML5 with c2runtime.js or c3runtime.js.
 * Runs in WebView with minimal configuration.
 *
 * Detection: index.html + c2runtime.js or c3runtime.js
 *
 * Status: Phase 3 — Stub implementation. Not yet tested.
 */
class ConstructEngine : GameEngine {

    override val id = "construct"
    override val name = "Construct 2/3"
    override val version = "1.0.0"
    override val priority = 60  // Lower priority (generic HTML games)

    companion object {
        private const val TAG = "ConstructEngine"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false

        val indexHtml = File(gameFolder, "index.html")
        if (!indexHtml.exists()) return false

        // Check for Construct runtime files
        val hasC2Runtime = File(gameFolder, "c2runtime.js").exists()
        val hasC3Runtime = File(gameFolder, "c3runtime.js").exists()

        return hasC2Runtime || hasC3Runtime
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null

        val version = if (File(gameFolder, "c3runtime.js").exists()) {
            "Construct 3"
        } else {
            "Construct 2"
        }

        return EngineMetadata(
            engine = id,
            version = version,
            title = gameFolder.name,
            icon = null
        )
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching ${gameFolder.name} via WebView (Construct)")

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
        // Construct games typically use localStorage, not file-based saves
        // We could intercept localStorage in WebView, but for now return empty
        return emptyList()
    }
}
