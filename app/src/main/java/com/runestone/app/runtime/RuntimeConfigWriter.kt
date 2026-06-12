/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.runtime

import android.content.Context
import android.util.Log
import com.runestone.app.rtp.RtpManager
import com.runestone.app.rtp.RtpPack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Writes the per-game mkxp.json config that the native mkxp-z interpreter
 * reads from the game folder and `customDataPath` (= SDL_GetPrefPath on
 * Android). The interpreter uses the `RTP` array to register additional
 * search paths beyond the game folder, which is how Runestone shares a
 * single installed RTP across every game that needs it.
 *
 * Called before launching a native mkxp-z game from
 * [com.runestone.app.GameActivity.launchRgssGame].
 */
class RuntimeConfigWriter {

    companion object {
        private const val TAG = "RuntimeConfigWriter"
    }

    /**
     * Writes a fresh mkxp.json for the game at [gameDir] with the title
     * found in its Game.ini ([gameTitle] is used as a fallback if Game.ini
     * is missing or unreadable).
     */
    fun writeMkxpConfig(
        context: Context,
        gameDir: File,
        gameTitle: String,
        rtpManager: RtpManager,
    ): File {
        val customDataDir = File(
            context.filesDir,
            "./$gameTitle",
        ).apply { mkdirs() }
        val customDataConfig = File(customDataDir, "mkxp.json")
        val gameConfig = File(gameDir, "mkxp.json")

        val installed = rtpManager.installedIds().toList()
        val rtps = JSONArray()
        for (id in installed) {
            val pack = RtpPack.forId(id) ?: continue
            rtps.put(rtpManager.packDir(pack).canonicalPath)
        }

        val json = JSONObject().apply {
            put("RTP", rtps)
            put("gameFolder", gameDir.absolutePath)
        }

        val text = json.toString(2)
        gameConfig.writeText(text)
        customDataConfig.writeText(text)
        Log.i(
            TAG,
            "Wrote mkxp.json to ${gameConfig.absolutePath} and ${customDataConfig.absolutePath} " +
                "(RTP entries: ${installed.size})",
        )
        return customDataConfig
    }
}
