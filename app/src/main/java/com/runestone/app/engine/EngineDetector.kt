/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.engine

import com.runestone.app.data.EngineType
import java.io.File

/**
 * Detects which RPG Maker engine a game directory uses by inspecting
 * its file structure.
 * 
 * DEPRECATED: This class is kept for backward compatibility.
 * New code should use EngineRegistry.detect() instead.
 * 
 * @see EngineRegistry
 */
@Deprecated("Use EngineRegistry.detect() instead", ReplaceWith("EngineRegistry.detect(rootDir)?.id"))
object EngineDetector {

    /**
     * Detect engine type from a game's root directory.
     * 
     * DEPRECATED: Use EngineRegistry.detect() instead.
     */
    @Deprecated("Use EngineRegistry.detect() instead")
    fun detect(rootDir: File): EngineType {
        val engine = EngineRegistry.detect(rootDir) ?: return EngineType.UNKNOWN
        return EngineType.fromEngineId(engine.id)
    }
}
