/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.rtp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * Manages RTP install state: directory layout, marker checks, Game.ini parsing.
 *
 * RTP packs are stored under `context.getExternalFilesDir("rtp")/<slug>/`.
 * A pack is considered "installed" when its [RtpPack.markerFile] exists on disk.
 */
class RtpManager(private val context: Context) {

    companion object {
        private const val TAG = "RtpManager"
        private const val DIR_NAME = "rtp"
    }

    /** Root directory where all RTP packs live. */
    val rtpRoot: File
        get() = File(context.getExternalFilesDir(null), DIR_NAME)

    /** Directory for a specific pack. */
    fun packDir(pack: RtpPack): File = File(rtpRoot, pack.slug)

    /** Whether this pack is fully installed on disk. */
    fun isInstalled(pack: RtpPack): Boolean {
        val marker = File(packDir(pack), pack.markerFile)
        return marker.exists() && marker.isFile
    }

    /**
     * Parse a Game.ini file and return the RTP line value, or null if absent.
     *
     * Format: `RTP=RPGVXAce` in the `[Game]` section.
     */
    fun parseRtpFromIni(iniFile: File): String? {
        if (!iniFile.exists()) return null
        try {
            val text = FileInputStream(iniFile).bufferedReader().use { it.readText() }
            val gameSection = text.substringAfter("[Game]", "")
                .substringBefore("[")
            val rtpLine = gameSection.lines()
                .firstOrNull { it.trimStart().startsWith("RTP=", ignoreCase = true) }
                ?: return null
            val value = rtpLine.substringAfter("=").trim()
            return value.ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Game.ini: ${e.message}")
            return null
        }
    }

    /**
     * Parse a Game.ini and return the [RtpPack] it requires, or null.
     */
    fun detectRequiredPack(iniFile: File): RtpPack? {
        val iniName = parseRtpFromIni(iniFile) ?: return null
        return RtpPack.fromIniName(iniName)
    }

    /**
     * Detect whether a game folder needs an RTP pack, based on
     * Game.ini content + whether required asset directories are missing.
     *
     * Returns the pack if one is needed and not already installed.
     */
    fun missingPackForGame(gameDir: File): RtpPack? {
        val iniFile = File(gameDir, "Game.ini")
        if (!iniFile.exists()) return null

        val pack = detectRequiredPack(iniFile) ?: return null
        if (isInstalled(pack)) return null

        // Check if the game ships its own RTP assets (no external need)
        val graphicsDir = File(gameDir, "Graphics")
        val tilesetsDir = File(graphicsDir, "Tilesets")
        if (tilesetsDir.exists() && (tilesetsDir.listFiles()?.isNotEmpty() == true)) {
            Log.i(TAG, "${gameDir.name} already has Graphics/Tilesets — no RTP needed")
            return null
        }

        return pack
    }

    /** Write a `.nomedia` file in the RTP root to keep media scanners out. */
    fun ensureNoMedia(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            if (dir.isDirectory) {
                val marker = File(dir, ".nomedia")
                if (!marker.exists()) marker.writeText("")
            }
        }
    }
}
