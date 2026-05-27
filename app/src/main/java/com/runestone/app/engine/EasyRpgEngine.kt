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
 * Engine plugin for RPG Maker 2000 and 2003 via EasyRPG Player.
 *
 * EasyRPG is a C++ runtime that runs:
 * - RPG Maker 2000 (RPG_RT.exe, .lmt/.ldb files)
 * - RPG Maker 2003 (RPG_RT.exe, .lmt/.ldb files, newer format)
 *
 * The runtime is built as a native .so library and launched via
 * EasyRPG's Android activity from the EasyRPG/Player submodule.
 *
 * Status: Phase 1 — Stub implementation. Native build not yet integrated.
 */
class EasyRpgEngine : GameEngine {

    override val id = "easyrpg"
    override val name = "RPG Maker 2000/2003"
    override val version = "1.0.0"
    override val priority = 15  // Check after mkxp-z but before WebView engines

    companion object {
        private const val TAG = "EasyRpgEngine"

        // Detection signatures for RM2000/2003
        private val SIGNATURE_FILES = setOf(
            "RPG_RT.exe",       // Main executable
            "RPG_RT.lmt",       // Map tree
            "RPG_RT.ldb"        // Database
        )
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false

        val files = gameFolder.listFiles() ?: return false
        val names = files.map { it.name }.toSet()

        // RPG Maker 2000/2003 must have RPG_RT.exe and either .lmt or .ldb
        return names.contains("RPG_RT.exe") &&
               (names.contains("RPG_RT.lmt") || names.contains("RPG_RT.ldb"))
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null

        val version = detectVersion(gameFolder)
        val title = detectTitleFromLdb(gameFolder) ?: gameFolder.name

        return EngineMetadata(
            engine = id,
            version = version,
            title = title,
            icon = null
        )
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching ${gameFolder.name} via EasyRPG")

        try {
            // Launch EasyRPG native activity
            // The submodule provides org.easyrpg.player.GameActivity
            val intent = Intent().apply {
                setClassName(
                    context.packageName,
                    "org.easyrpg.player.GameActivity"
                )
                putExtra("game_path", gameFolder.absolutePath)

                config.extraArgs.forEach { (key, value) ->
                    putExtra(key, value)
                }

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch EasyRPG activity", e)
            // TODO: Show user-friendly error
            // "EasyRPG runtime not available. Please rebuild with native support."
            throw RuntimeException("EasyRPG runtime not available", e)
        }
    }

    override fun getSaves(gameFolder: File): List<SaveFile> {
        // EasyRPG saves are in Save/ subdirectory
        // Format: Save01.lsd, Save02.lsd, etc.
        val saveDir = File(gameFolder, "Save")
        if (!saveDir.exists() || !saveDir.isDirectory) return emptyList()

        val saves = mutableListOf<SaveFile>()

        val saveFiles = saveDir.listFiles { file ->
            file.name.startsWith("Save") && file.name.endsWith(".lsd")
        } ?: return emptyList()

        saveFiles.forEachIndexed { index, file ->
            val slotMatch = Regex("""Save(\d+)""").find(file.name)
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

    private fun detectVersion(gameFolder: File): String {
        // RPG Maker 2003 has a different .ldb format than 2000
        // Simple heuristic: check for .ldb file size or specific bytes
        // For now, just return generic version
        val ldbFile = File(gameFolder, "RPG_RT.ldb")
        if (!ldbFile.exists()) return "RPG Maker 2000/2003"

        // 2003 databases tend to be larger
        // This is a rough heuristic — proper detection would parse the header
        val sizeKb = ldbFile.length() / 1024
        return if (sizeKb > 100) "RPG Maker 2003" else "RPG Maker 2000"
    }

    private fun detectTitleFromLdb(gameFolder: File): String? {
        // Title is embedded in RPG_RT.ldb binary file
        // For now, just use folder name
        // TODO: Parse .ldb to extract title
        return null
    }
}
