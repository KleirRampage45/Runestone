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

/**
 * A run-time package (RTP) that mkxp-z or WebView engines need to play
 * some RPG Maker games. The mapping [id] -> expected folder under
 * filesDir/rtp/<id>/ is fixed; do not rename ids once shipped because
 * existing installations use them as the on-disk path.
 */
enum class RtpPack(
    val id: String,
    val displayName: String,
    val rtpIniToken: String,
    val approxBytes: Long,
    val sourceUrl: String,
    val sourceAttribution: String,
) {
    RPG_VX_ACE(
        id = "vx_ace",
        displayName = "RPG Maker VX Ace Runtime Package",
        rtpIniToken = "RPGVXAce",
        approxBytes = 195_000_000L,
        sourceUrl = "https://archive.org/download/RPG_Maker_RTP_Collection/English/RTP%20VX%20Ace%20(RGSS3).zip",
        sourceAttribution = "Internet Archive mirror of the official Enterbrain/Kadokawa RPG Maker VX Ace RTP",
    );

    companion object {
        fun forToken(token: String?): RtpPack? =
            values().firstOrNull { it.rtpIniToken.equals(token, ignoreCase = true) }

        fun forId(id: String): RtpPack? =
            values().firstOrNull { it.id == id }
    }
}
