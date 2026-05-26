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
 */
object EngineDetector {

    /**
     * Detect engine type from a game's root directory.
     *
     * Detection rules:
     * - RGSS XP:   Game.rxproj (or Data/Scripts.rxdata)
     * - RGSS VX:   Game.rvproj (or Data/Scripts.rvdata)
     * - RGSS VX Ace: Game.rvproj2 (or Game.rgss3a, Data/Scripts.rvdata2)
     * - MV:        www/index.html + package.json
     * - MZ:        www/index.html + package.json (different structure in data/)
     *             Also: Game.rmmzproject
     */
    fun detect(rootDir: File): EngineType {
        if (!rootDir.isDirectory) return EngineType.UNKNOWN

        val files = rootDir.listFiles() ?: return EngineType.UNKNOWN
        val names = files.map { it.name }.toSet()

        // Check for MZ project file
        if (names.any { it.equals("Game.rmmzproject", ignoreCase = true) }) {
            return EngineType.MZ
        }

        // Check for VX Ace
        if (names.any { it.equals("Game.rvproj2", ignoreCase = true) }) {
            return EngineType.RGSS_VX_ACE
        }
        // Also check for common VX Ace data files
        if (names.contains("Game.rgss3a") || names.contains("Game.rgss3a")) {
            return EngineType.RGSS_VX_ACE
        }

        // Check for VX
        if (names.any { it.equals("Game.rvproj", ignoreCase = true) }) {
            return EngineType.RGSS_VX
        }

        // Check for XP
        if (names.any { it.equals("Game.rxproj", ignoreCase = true) }) {
            return EngineType.RGSS_XP
        }

        // Check for MV/MZ (www folder + index.html)
        val wwwDir = files.find { it.name.equals("www", ignoreCase = true) && it.isDirectory }
        if (wwwDir != null) {
            val wwwFiles = wwwDir.listFiles() ?: emptyArray()
            val wwwNames = wwwFiles.map { it.name }.toSet()

            if (wwwNames.contains("index.html")) {
                // Check package.json for MV vs MZ distinction
                val pkgJson = wwwFiles.find { it.name.equals("package.json", ignoreCase = true) }
                if (pkgJson != null && pkgJson.exists()) {
                    val content = pkgJson.readText()
                    if (content.contains("MZ", ignoreCase = true)) {
                        return EngineType.MZ
                    }
                }
                return EngineType.MV
            }
        }

        // Fallback: check inside www for browser-compatible game
        val dataDir = files.find { it.name.equals("Data", ignoreCase = true) && it.isDirectory }
        if (dataDir != null) {
            val dataFiles = dataDir.listFiles() ?: emptyArray()
            val dataNames = dataFiles.map { it.name }.toSet()

            if (dataNames.any { it.endsWith(".rvdata2") }) return EngineType.RGSS_VX_ACE
            if (dataNames.any { it.endsWith(".rvdata") }) return EngineType.RGSS_VX
            if (dataNames.any { it.endsWith(".rxdata") }) return EngineType.RGSS_XP
        }

        return EngineType.UNKNOWN
    }
}
