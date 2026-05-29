/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.provider

import android.util.Log
import com.runestone.app.data.EngineType
import java.io.File

object GameDetector {

    private const val TAG = "GameDetector"

    data class DetectionResult(
        val engineType: EngineType,
        val suggestedName: String,
    )

    fun detect(gameDir: File): DetectionResult {
        Log.i(TAG, "Detecting engine in: ${gameDir.absolutePath}")

        val files = gameDir.listFiles() ?: emptyArray()
        val fileNames = files.map { it.name }.toSet()
        val lowerNames = fileNames.map { it.lowercase() }.toSet()

        // RPG Maker MZ
        if (fileNames.any { it.endsWith(".rmmzproject") }) {
            return DetectionResult(EngineType.MZ, extractName(gameDir))
        }

        // RPG Maker VX Ace
        if (fileNames.any { it.endsWith(".rvproj2") } || fileNames.any { it.endsWith(".rgss3a") }) {
            return DetectionResult(EngineType.RGSS_VX_ACE, extractName(gameDir))
        }

        // RPG Maker VX
        if (fileNames.any { it.endsWith(".rvproj") } || lowerNames.any { it == "scripts.rvdata" }) {
            return DetectionResult(EngineType.RGSS_VX, extractName(gameDir))
        }

        // RPG Maker XP
        if (fileNames.any { it.endsWith(".rxproj") } || lowerNames.any { it == "scripts.rxdata" }) {
            return DetectionResult(EngineType.RGSS_XP, extractName(gameDir))
        }

        // MV or MZ via www/index.html
        val wwwDir = File(gameDir, "www")
        if (wwwDir.isDirectory) {
            val indexHtml = File(wwwDir, "index.html")
            if (indexHtml.isFile) {
                val pkg = File(wwwDir, "package.json")
                if (pkg.isFile) {
                    val content = pkg.readText()
                    if (content.contains("rpgmaker-mz") || content.contains("\"mz\"")) {
                        return DetectionResult(EngineType.MZ, extractName(gameDir))
                    }
                }
                return DetectionResult(EngineType.MV, extractName(gameDir))
            }
        }

        // Data directory with RGSS data files
        val dataDir = File(gameDir, "Data")
        if (dataDir.isDirectory) {
            val dataFiles = dataDir.listFiles()?.map { it.name.lowercase() } ?: emptyList()
            when {
                dataFiles.any { it.endsWith(".rvdata2") } -> return DetectionResult(EngineType.RGSS_VX_ACE, extractName(gameDir))
                dataFiles.any { it.endsWith(".rvdata") } -> return DetectionResult(EngineType.RGSS_VX, extractName(gameDir))
                dataFiles.any { it.endsWith(".rxdata") } -> return DetectionResult(EngineType.RGSS_XP, extractName(gameDir))
            }
        }

        // EasyRPG
        if (lowerNames.any { it == "rpg_rt.ldb" }) {
            return DetectionResult(EngineType.EASYRPG, extractName(gameDir))
        }

        // Ren'Py
        val renpyDir = File(gameDir, "renpy")
        if (renpyDir.isDirectory || lowerNames.any { it.endsWith(".rpy") }) {
            return DetectionResult(EngineType.RENPY, extractName(gameDir))
        }

        // Game.ini with RGSS reference
        val gameIni = File(gameDir, "Game.ini")
        if (gameIni.isFile) {
            val content = gameIni.readText()
            when {
                content.contains("RGSS3") -> return DetectionResult(EngineType.RGSS_VX_ACE, extractName(gameDir))
                content.contains("RGSS2") -> return DetectionResult(EngineType.RGSS_VX, extractName(gameDir))
                content.contains("RGSS") -> return DetectionResult(EngineType.RGSS_XP, extractName(gameDir))
            }
        }

        Log.w(TAG, "Could not detect engine, defaulting to UNKNOWN")
        return DetectionResult(EngineType.UNKNOWN, extractName(gameDir))
    }

    private fun extractName(gameDir: File): String {
        // Try to get name from Game.ini
        val gameIni = File(gameDir, "Game.ini")
        if (gameIni.isFile) {
            val title = gameIni.readLines()
                .firstOrNull { it.startsWith("Title=", ignoreCase = true) }
                ?.substringAfter("=")?.trim()
            if (!title.isNullOrBlank()) return title
        }

        // Try www/data/System.json for MV/MZ
        val sysJson = File(gameDir, "www/data/System.json")
        if (sysJson.isFile) {
            try {
                val json = org.json.JSONObject(sysJson.readText())
                val title = json.optString("gameTitle", "")
                if (title.isNotBlank()) return title
            } catch (_: Exception) {}
        }

        // Fall back to directory name
        return gameDir.name
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.uppercase() else c.toString() } }
    }
}
