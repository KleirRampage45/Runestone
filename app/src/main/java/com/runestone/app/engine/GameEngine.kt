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
import android.graphics.Bitmap
import java.io.File

/**
 * Plugin interface for all game engines.
 *
 * Each engine (mkxp-z, EasyRPG, WebView, Ren'Py, etc.) implements this
 * interface and registers itself via [EngineRegistry]. The launcher calls
 * [detect] to auto-detect which engine a game needs, then [launch] to
 * start it.
 *
 * Engines are independent — they should not depend on each other.
 */
interface GameEngine {

    /** Unique identifier, e.g. "mkxp-z", "easyrpg", "webview-mv" */
    val id: String

    /** Human-readable name shown in UI, e.g. "RPG Maker XP/VX/VX Ace" */
    val name: String

    /** Semantic version of this engine plugin */
    val version: String

    /** Lower number = checked first during detection. Default 50. */
    val priority: Int get() = 50

    /**
     * Check whether this engine can run the game in [gameFolder].
     * Should be fast — just inspect file names, not read contents.
     */
    fun canRun(gameFolder: File): Boolean

    /**
     * Detect engine metadata from a game directory.
     * Returns null if [canRun] would return false.
     */
    fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null
        return EngineMetadata(
            engine = id,
            version = detectVersion(gameFolder),
            title = detectTitle(gameFolder) ?: gameFolder.name,
            icon = null
        )
    }

    /** Launch the game. Called on the main thread. */
    fun launch(context: Context, gameFolder: File, config: GameConfig = GameConfig())

    /** List save files for this game. May be empty if engine doesn't expose saves. */
    fun getSaves(gameFolder: File): List<SaveFile> = emptyList()

    /** Whether this engine supports applying translation/mod patches. */
    fun supportsPatching(): Boolean = false

    /** Apply a patch (translation, mod) to the game. */
    fun applyPatch(gameFolder: File, patchFile: File): Boolean = false

    // --- Optional overrides ---

    /** Extract version string from game files (e.g. "RGSS3" from VX Ace). */
    fun detectVersion(gameFolder: File): String? = null

    /** Extract game title from game files (e.g. from System.json or Game.ini). */
    fun detectTitle(gameFolder: File): String? = null
}

/**
 * Metadata about a detected game.
 */
data class EngineMetadata(
    val engine: String,
    val version: String?,
    val title: String,
    val icon: Bitmap?
)

/**
 * Launch configuration passed to [GameEngine.launch].
 */
data class GameConfig(
    val debug: Boolean = false,
    val fullscreen: Boolean = true,
    val customFont: String? = null,
    val locale: String? = null,
    val extraArgs: Map<String, String> = emptyMap()
)

/**
 * Represents a save file found in a game directory.
 */
data class SaveFile(
    val name: String,
    val file: File,
    val timestamp: Long,
    val slot: Int? = null
)
