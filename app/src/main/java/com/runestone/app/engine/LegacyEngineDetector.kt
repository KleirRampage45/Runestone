/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Detects legacy RPG Maker engines (RM95, Dante 98).
 * These are detection-only — no open-source runtime exists.
 * Users are notified that these require the original engine on PC.
 */

package com.runestone.app.engine

import com.runestone.app.data.EngineType
import java.io.File

object LegacyEngineDetector {

    /**
     * Detect if a game directory contains an RPG Maker 95 game.
     *
     * RM95 file signatures:
     * - RPG_RT.exe (main executable)
     * - *.ldb files (database)
     * - *.lmt files (map tree)
     * - Often has "Harmony.dll" or "RPG95" folder
     * - Map files: Map0001.lmu, Map0002.lmu...
     * - Character files: *.chr
     */
    fun detectRm95(rootDir: File): Boolean {
        if (!rootDir.isDirectory) return false
        val names = rootDir.listFiles()?.map { it.name }?.toSet() ?: return false

        // RM95 signature: RPG_RT.exe + map/chr files
        val hasExe = names.contains("RPG_RT.exe")
        val hasMaps = names.any { it.matches(Regex("Map\\d{4}\\.lmu")) }
        val hasChars = names.any { it.endsWith(".chr") }
        val hasHarmony = names.any { it.equals("Harmony.dll", ignoreCase = true) }

        return hasExe && (hasMaps || hasChars || hasHarmony)
    }

    /**
     * Detect if a game directory contains an RPG Tsukuru Dante 98 game.
     *
     * Dante 98 (1992) file signatures:
     * - RPGDT98.EXE or RPGMAKE.EXE
     * - *.dat files (database)
     * - GAMEDATA/ folder
     * - *.map files (maps)
     * - Very old format, predates even RM95
     */
    fun detectDante98(rootDir: File): Boolean {
        if (!rootDir.isDirectory) return false
        val names = rootDir.listFiles()?.map { it.name }?.toSet() ?: return false

        val hasExe = names.any { it.equals("RPGDT98.EXE", ignoreCase = true) ||
                                 it.equals("RPGMAKE.EXE", ignoreCase = true) }
        val hasDat = names.any { it.endsWith(".dat") }
        val hasGameData = File(rootDir, "GAMEDATA").isDirectory

        return hasExe || (hasDat && hasGameData)
    }

    /** Try to detect a legacy engine from a game folder. Returns null if not legacy. */
    fun detect(rootDir: File): EngineType? {
        if (detectDante98(rootDir)) return EngineType.DANTE98
        if (detectRm95(rootDir)) return EngineType.RM95
        return null
    }
}
