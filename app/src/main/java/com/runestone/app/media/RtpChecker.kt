/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * RTP (Run-Time Package) detection and download prompts.
 * RGSS games often require the RTP which isn't included.
 */

package com.runestone.app.media

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Detects missing RTP resources and prompts the user to download them.
 *
 * RPG Maker RTP versions:
 * - RPG Maker XP RTP (Standard)
 * - RPG Maker VX RTP
 * - RPG Maker VX Ace RTP
 */
object RtpChecker {

    /** Common RTP resource files that indicate RTP is present. */
    private val RTP_SIGNATURE_FILES = mapOf(
        "xp" to listOf("Audio/BGM/001-Battle01.mid", "Graphics/Characters/001-Fighter01.png"),
        "vx" to listOf("Audio/BGM/001-Battle01.ogg", "Graphics/Characters/Actor1.png"),
        "vxace" to listOf("Audio/BGM/Battle1.ogg", "Graphics/Characters/People1.png"),
    )

    /**
     * Check if a game appears to need RTP (has references to RTP resources
     * but the resource files don't exist locally).
     */
    fun needsRtp(gameDir: File, engineType: String): Boolean {
        val key = when {
            engineType.contains("xp", true) -> "xp"
            engineType.contains("vxace", true) -> "vxace"
            engineType.contains("vx", true) -> "vx"
            else -> return false
        }
        val signatures = RTP_SIGNATURE_FILES[key] ?: return false
        // If all signature files exist locally, RTP is likely present
        return !signatures.all { File(gameDir, it).exists() }
    }

    /**
     * Show a dialog prompting the user to download the RTP.
     * Returns true if the dialog was shown.
     */
    fun promptRtpDownload(context: Context, engineType: String): Boolean {
        val rtpName = when {
            engineType.contains("xp", true) -> "RPG Maker XP RTP"
            engineType.contains("vxace", true) -> "RPG Maker VX Ace RTP"
            engineType.contains("vx", true) -> "RPG Maker VX RTP"
            else -> return false
        }

        // RTP download URLs (community-hosted, not official)
        val url = when {
            engineType.contains("xp", true) -> "https://www.rpgmakerweb.com/run-time-package"
            engineType.contains("vxace", true) -> "https://www.rpgmakerweb.com/run-time-package"
            engineType.contains("vx", true) -> "https://www.rpgmakerweb.com/run-time-package"
            else -> return false
        }

        AlertDialog.Builder(context)
            .setTitle("RTP Required")
            .setMessage("This game may need the $rtpName to run correctly.\n\n" +
                "Without the RTP, you may experience missing graphics, audio, or errors.\n\n" +
                "Download the RTP and extract it into the game folder?")
            .setPositiveButton("Download Info") { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .setNegativeButton("Ignore") { _, _ -> }
            .setNeutralButton("Don't Ask Again") { _, _ -> }
            .show()

        return true
    }
}
