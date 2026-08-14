/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright 2026 Gerson (KleirRampage45)
 *
 * Writes the resolved filter config JSON to the game directory
 * before launching a native engine. Native code reads this file
 * at startup and checks for updates each frame.
 */

package com.runestone.app.filters

import android.util.Log
import com.runestone.app.data.VideoSection
import java.io.File

object FilterConfigWriter {

    private const val TAG = "FilterCfg"
    const val FILTER_CONFIG_FILE = "runestone-filters.json"

    /**
     * Resolve the video settings into a filter config and write it
     * to the game directory.
     *
     * @param gameDir  The game's root directory on disk.
     * @param video    Per-game video settings with preset ID and overrides.
     * @return The written config file, or null if write fails.
     */
    fun write(gameDir: File, video: VideoSection): File? {
        return try {
            val config = FilterManager.resolve(video)
            val file = File(gameDir, FILTER_CONFIG_FILE)
            file.writeText(config.toJson().toString(2))
            Log.i(TAG, "Wrote $FILTER_CONFIG_FILE: preset=${config.preset}, " +
                    "enabled=${config.enabled}, passes=${config.passes.size}")
            file
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write filter config: ${e.message}")
            null
        }
    }

    /**
     * Write a disabled config (used when no per-game config exists
     * or filter is set to "off").
     */
    fun writeDisabled(gameDir: File): File? {
        return try {
            val file = File(gameDir, FILTER_CONFIG_FILE)
            file.writeText(ResolvedFilterConfig.DISABLED.toJson().toString(2))
            Log.i(TAG, "Wrote disabled filter config")
            file
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write disabled filter config: ${e.message}")
            null
        }
    }
}
