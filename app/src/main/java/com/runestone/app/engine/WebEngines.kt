/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * HTML5 / Twine / VN Maker engines — all run in WebView.
 * These are lightweight detection wrappers that reuse WebViewEngine.
 */

package com.runestone.app.engine

import android.content.Context
import android.util.Log
import java.io.File

// ── HTML5 Game Engine ────────────────────────────────────────────

class HtmlGameEngine : GameEngine {

    override val id = "html"
    override val name = "HTML5 Game"
    override val version = "1.0.0"
    override val priority = 40

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val names = gameFolder.listFiles()?.map { it.name }?.toSet() ?: return false
        // Has index.html but NOT standard RPG Maker files
        return names.contains("index.html") &&
            !names.contains("Game.exe") &&
            !names.contains("www/index.html") &&
            gameFolder.listFiles()?.any { it.name.endsWith(".html") } ?: false
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i("HtmlEngine", "Launching HTML5 game: ${gameFolder.name}")
        val intent = android.content.Intent(context, Class.forName("com.runestone.app.GameActivity")).apply {
            putExtra("game_path", gameFolder.absolutePath)
            putExtra("engine_type", "html")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

// ── Twine Engine ─────────────────────────────────────────────────

class TwineEngine : GameEngine {

    override val id = "twine"
    override val name = "Twine"
    override val version = "1.0.0"
    override val priority = 41

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val names = gameFolder.listFiles()?.map { it.name }?.toSet() ?: return false
        // Twine games ship as a single HTML file with Twine-specific markers
        return names.any { it.endsWith(".html") } &&
            gameFolder.listFiles()?.any { f ->
                f.isFile && f.extension == "html" && runCatching {
                    f.readText().contains("tw-storydata") || f.readText().contains("Twine")
                }.getOrDefault(false)
            } ?: false
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        val intent = android.content.Intent(context, Class.forName("com.runestone.app.GameActivity")).apply {
            putExtra("game_path", gameFolder.absolutePath)
            putExtra("engine_type", "twine")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

// ── VN Maker Engine ──────────────────────────────────────────────

class VnMakerEngine : GameEngine {

    override val id = "vnmaker"
    override val name = "VN Maker"
    override val version = "1.0.0"
    override val priority = 42

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val names = gameFolder.listFiles()?.map { it.name }?.toSet() ?: return false
        // VN Maker games have specific structure
        return names.contains("index.html") &&
            (names.contains("data") || names.contains("Data")) &&
            gameFolder.listFiles()?.any { it.name.endsWith(".json") } ?: false
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        val intent = android.content.Intent(context, Class.forName("com.runestone.app.GameActivity")).apply {
            putExtra("game_path", gameFolder.absolutePath)
            putExtra("engine_type", "vnmaker")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

// ── NScripter Engine (ONScripter) ────────────────────────────────

class NScripterEngine : GameEngine {

    override val id = "nscripter"
    override val name = "NScripter (ONScripter)"
    override val version = "1.0.0"
    override val priority = 35

    companion object {
        private const val TAG = "NScripterEngine"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val names = gameFolder.listFiles()?.map { it.name }?.toSet() ?: return false
        // NScripter: has nscript.dat or 0.txt/1.txt or arc.nsa
        return names.any { it.equals("nscript.dat", ignoreCase = true) } ||
            names.any { it.endsWith(".txt") && it[0].isDigit() } ||
            names.any { it.endsWith(".nsa") }
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null
        val title = gameFolder.listFiles()?.find { it.name.equals("nscript.dat", ignoreCase = true) }
            ?.let { "NScripter — ${gameFolder.name}" } ?: gameFolder.name
        return EngineMetadata(engine = id, version = "NScripter", title = title, icon = null)
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "ONScripter unavailable: ${gameFolder.name}")
        UnavailableEngine.show(context, "ONScripter")
    }
}

// ── Electron Engine (detect only — too heavy for mobile) ─────────

class ElectronEngine : GameEngine {

    override val id = "electron"
    override val name = "Electron App"
    override val version = "1.0.0"
    override val priority = 60

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val names = gameFolder.listFiles()?.map { it.name }?.toSet() ?: return false
        return names.any { it == "resources" } &&
            (names.any { it.endsWith(".asar") } || File(gameFolder, "resources/app").isDirectory)
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        // Electron is a full Chromium + Node.js runtime — impractical on mobile
        android.app.AlertDialog.Builder(
            context as? android.app.Activity ?: return
        ).apply {
            setTitle("Electron Not Supported")
            setMessage("Electron apps bundle a full Chromium browser and cannot run on Android.\n\n" +
                "These games require a desktop PC.")
            setPositiveButton("OK") { _, _ -> }
            show()
        }
    }
}
