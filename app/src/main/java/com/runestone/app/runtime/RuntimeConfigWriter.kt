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

import android.util.Log
import com.runestone.app.data.RunnerSettings
import com.runestone.app.rtp.RtpInstaller
import com.runestone.app.rtp.RtpManager
import com.runestone.app.rtp.RtpPack
import org.json.JSONObject
import java.io.File

/**
 * Writes runtime configuration for native game engines.
 *
 * For mkxp-z, writes an `mkxp.json` file into the game directory
 * with the game folder path and optional RTP array. mkxp-z reads this
 * file at startup to find shared RPG Maker asset packs.
 *
 * The old `.conf` format is replaced — mkxp-z only reads `mkxp.json`.
 */
class RuntimeConfigWriter(
    private val rtpInstaller: RtpInstaller? = null,
) {

    companion object {
        private const val TAG = "RuntimeCfg"
        private const val MKXP_JSON = "mkxp.json"
    }

    /**
     * Write `mkxp.json` to the game directory.
     *
     * If [rtpInstaller] and [rtpPacks] are provided and the pack is
     * installed, the RTP path is included so mkxp-z can find shared assets.
     *
     * @param storageName  Internal storage name of the game.
     * @param activeGamePath  The game directory on disk.
     * @param settings  Runner settings (touch, layout, etc.) — currently
     *                  not serialised into mkxp.json since mkxp-z has its
     *                  own config mechanism.
     * @param rtpPacks  Optional list of RTP packs to include. Only packs
     *                  that are actually installed will be written.
     * @return The mkxp.json file that was written.
     */
    fun writeConfig(
        storageName: String,
        activeGamePath: File,
        settings: RunnerSettings,
        rtpPacks: List<RtpPack> = emptyList(),
    ): File {
        val mkxpFile = File(activeGamePath, MKXP_JSON)
        val rtpPaths = rtpPacks.mapNotNull { pack ->
            if (rtpInstaller?.isInstalled(pack) == true) {
                rtpInstaller.getRtpPath(pack)
            } else {
                Log.w(TAG, "RTP pack ${pack.slug} is not installed — skipping")
                null
            }
        }

        val json = JSONObject().apply {
            put("gameFolder", activeGamePath.absolutePath)
            if (rtpPaths.isNotEmpty()) {
                put("RTP", rtpPaths)
            }
        }

        mkxpFile.writeText(json.toString(2))
        Log.i(TAG, "Wrote $MKXP_JSON for $storageName: ${json.toString()}")
        return mkxpFile
    }

    /**
     * Convenience: parse Game.ini and write mkxp.json with the correct RTP pack.
     */
    fun writeConfigWithRtpDetection(
        storageName: String,
        activeGamePath: File,
        settings: RunnerSettings,
    ): File {
        val rtpPacks = if (rtpInstaller != null) {
            val manager = RtpManager(rtpInstaller.context)
            val iniFile = File(activeGamePath, "Game.ini")
            val pack = manager.detectRequiredPack(iniFile)
            if (pack != null) listOf(pack) else emptyList()
        } else {
            emptyList()
        }
        return writeConfig(storageName, activeGamePath, settings, rtpPacks)
    }
}
