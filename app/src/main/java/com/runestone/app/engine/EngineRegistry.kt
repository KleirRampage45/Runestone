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

import android.util.Log
import java.io.File

/**
 * Central registry for all game engine plugins.
 *
 * Engines register themselves at app startup. The launcher uses [detect]
 * to auto-detect which engine a game needs, then retrieves it via [get].
 *
 * Usage:
 * ```
 * // At app startup
 * EngineRegistry.register(MkxpZEngine())
 * EngineRegistry.register(EasyRpgEngine())
 * EngineRegistry.register(WebViewMvEngine())
 * EngineRegistry.register(WebViewMzEngine())
 *
 * // When importing a game
 * val engine = EngineRegistry.detect(gameFolder)
 * if (engine != null) {
 *     engine.launch(context, gameFolder)
 * }
 * ```
 */
object EngineRegistry {

    private const val TAG = "EngineRegistry"

    private val engines = mutableMapOf<String, GameEngine>()

    /** Register an engine plugin. Called once at app startup. */
    fun register(engine: GameEngine) {
        engines[engine.id] = engine
        Log.i(TAG, "Registered engine: ${engine.id} (${engine.name} v${engine.version})")
    }

    /** Get a registered engine by ID. */
    fun get(id: String): GameEngine? = engines[id]

    /** Get all registered engines. */
    fun all(): List<GameEngine> = engines.values.toList()

    /**
     * Auto-detect which engine can run the game in [gameFolder].
     * Checks engines in priority order (lower priority number = checked first).
     * Returns the first match, or null if no engine can run the game.
     */
    fun detect(gameFolder: File): GameEngine? {
        return engines.values
            .sortedBy { it.priority }
            .firstOrNull { engine ->
                try {
                    engine.canRun(gameFolder)
                } catch (e: Exception) {
                    Log.w(TAG, "Engine ${engine.id} threw during detection", e)
                    false
                }
            }
    }

    /**
     * Detect metadata for a game without committing to an engine.
     * Returns metadata from the highest-priority matching engine.
     */
    fun detectMetadata(gameFolder: File): EngineMetadata? {
        val engine = detect(gameFolder) ?: return null
        return engine.detect(gameFolder)
    }

    /** Initialize all built-in engines. Call once from Application.onCreate(). */
    fun initDefaults() {
        // Native engines (highest priority — specific file signatures)
        register(MkxpZEngine())
        register(EasyRpgEngine())

        // WebView engines (medium priority — HTML-based)
        register(WebViewMzEngine())
        register(WebViewMvEngine())
        register(TyranoEngine())
        register(ConstructEngine())

        // Future engines (lowest priority)
        register(RenpyEngine())
    }
}
