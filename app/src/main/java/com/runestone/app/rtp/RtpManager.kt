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
import org.json.JSONObject
import java.io.File

/**
 * Tracks which RTPs are installed on disk. Each pack's on-disk root is
 * `filesDir/rtp/<id>/`. A pack is "installed" when its root directory
 * exists AND contains a marker file written after successful extraction.
 *
 * State is also persisted in SharedPreferences so the UI can show
 * "installed" state even if a marker file is somehow missing.
 */
class RtpManager(private val context: Context) {

    companion object {
        private const val TAG = "RtpManager"
        private const val PREFS = "runestone_rtps"
        private const val MARKER_NAME = ".runestone_rtp_ready"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rootDir(): File = File(context.filesDir, "rtp").apply { mkdirs() }

    fun packDir(pack: RtpPack): File = File(rootDir(), pack.id)

    fun isInstalled(pack: RtpPack): Boolean {
        val dir = packDir(pack)
        if (!dir.isDirectory) return false
        if (File(dir, MARKER_NAME).exists()) return true
        // No marker — check for actual RTP content (direct layout or RTP100 wrapper)
        return File(dir, "Audio").isDirectory || File(dir, "RTP100").isDirectory
    }

    fun installedIds(): Set<String> =
        RtpPack.values().filter { isInstalled(it) }.map { it.id }.toSet()

    fun markInstalled(pack: RtpPack) {
        val dir = packDir(pack)
        dir.mkdirs()
        File(dir, MARKER_NAME).writeText(
            JSONObject(mapOf(
                "pack" to pack.id,
                "installedAt" to System.currentTimeMillis(),
            )).toString(),
        )
        prefs.edit().putLong("installed_${pack.id}", System.currentTimeMillis()).apply()
        Log.i(TAG, "Marked ${pack.id} as installed at ${dir.absolutePath}")
    }

    fun markUninstalled(pack: RtpPack) {
        val dir = packDir(pack)
        if (dir.exists()) dir.deleteRecursively()
        prefs.edit().remove("installed_${pack.id}").apply()
        Log.i(TAG, "Removed ${pack.id} from ${dir.absolutePath}")
    }

    /**
     * Returns the list of [RtpPack]s whose `rtpIniToken` matches one of the
     * `RTP=` lines in the given `Game.ini` content. Used to decide which
     * RTPs are required for a game.
     */
    fun requiredPacksForIni(iniContent: String): List<RtpPack> {
        val tokens = iniContent.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("RTP=", ignoreCase = true) }
            .map { it.substring(4).trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return tokens.mapNotNull { RtpPack.forToken(it) }.distinct()
    }
}
