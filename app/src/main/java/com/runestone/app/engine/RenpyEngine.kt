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
import android.util.Log
import java.io.File

/**
 * Engine plugin for Ren'Py visual novels.
 *
 * Ren'Py is a Python-based visual novel engine with massive adoption.
 * Thousands of VNs are built with Ren'Py (DDLC, Monster Prom, Butterfly Soup, etc.)
 *
 * Detection: renpy/ directory or .rpy files
 *
 * Status: Phase 2 — Stub implementation. Native integration is complex:
 * - Ren'Py runtime is Python + SDL2 + Ren'Py libs (~100MB+)
 * - Options: separate APK plugin, dynamic download, or embedded
 * - Recommend separate APK to keep core app lightweight
 *
 * Integration approach (future):
 * 1. Create separate "Runestone Ren'Py Plugin" APK
 * 2. Core app checks if plugin is installed via PackageManager
 * 3. If installed, launch plugin's activity with game path
 * 4. If not installed, prompt user to download from GitHub/F-Droid
 */
class RenpyEngine : GameEngine {

    override val id = "renpy"
    override val name = "Ren'Py"
    override val version = "1.0.0"
    override val priority = 20  // Check early (specific signatures)

    companion object {
        private const val TAG = "RenpyEngine"

        // Plugin package name (future)
        const val PLUGIN_PACKAGE = "com.runestone.plugin.renpy"
        const val PLUGIN_ACTIVITY = "com.runestone.plugin.renpy.RenpyActivity"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false

        val files = gameFolder.listFiles() ?: return false
        val names = files.map { it.name }.toSet()

        // Check for renpy/ directory
        if (names.contains("renpy") && File(gameFolder, "renpy").isDirectory) {
            return true
        }

        // Check for .rpy script files
        if (files.any { it.name.endsWith(".rpy") }) {
            return true
        }

        // Check for Ren'Py launcher
        if (names.contains("renpy.py") || names.contains("renpy.exe")) {
            return true
        }

        return false
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null

        val version = detectRenpyVersion(gameFolder)
        val title = detectTitleFromGameRpy(gameFolder) ?: gameFolder.name

        return EngineMetadata(
            engine = id,
            version = version,
            title = title,
            icon = null
        )
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching ${gameFolder.name} via Ren'Py")
        // Ren'Py plugin is not installed — show informational toast
        android.widget.Toast.makeText(
            context,
            "Ren'Py plugin required. Download from runestone.app/plugins/renpy",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    override fun getSaves(gameFolder: File): List<SaveFile> {
        // Ren'Py saves are in game/saves/ as pickle files
        val saveDir = File(gameFolder, "game/saves")
        if (!saveDir.exists() || !saveDir.isDirectory) return emptyList()

        val saves = mutableListOf<SaveFile>()

        val saveFiles = saveDir.listFiles { file ->
            file.name.endsWith(".save") || file.name.endsWith(".rpy-save")
        } ?: return emptyList()

        saveFiles.forEachIndexed { index, file ->
            saves.add(SaveFile(
                name = file.nameWithoutExtension,
                file = file,
                timestamp = file.lastModified(),
                slot = index
            ))
        }

        return saves
    }

    private fun isPluginInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PLUGIN_PACKAGE, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun detectRenpyVersion(gameFolder: File): String {
        // Check renpy/VERSION.txt if it exists
        val versionFile = File(gameFolder, "renpy/VERSION.txt")
        if (versionFile.exists()) {
            return try {
                versionFile.readText().trim()
            } catch (e: Exception) {
                "Ren'Py (unknown version)"
            }
        }

        return "Ren'Py"
    }

    private fun detectTitleFromGameRpy(gameFolder: File): String? {
        // Title is usually in game/script.rpy or game/options.rpy
        // Look for "define config.name = \"Title\""
        val scriptFile = File(gameFolder, "game/script.rpy")
        val optionsFile = File(gameFolder, "game/options.rpy")

        val files = listOf(scriptFile, optionsFile).filter { it.exists() }

        for (file in files) {
            try {
                val lines = file.readLines()
                for (line in lines) {
                    val match = Regex("""define\s+config\.name\s*=\s*["'](.+?)["']""").find(line)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse ${file.name}", e)
            }
        }

        return null
    }
}
