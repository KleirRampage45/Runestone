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
 * Supported RPG Maker RTP (Run-Time Package) packs.
 *
 * Each pack is a set of shared assets (tilesets, characters, etc.)
 * required by games made with that engine version. mkxp-z loads them
 * via a `RTP` array in `mkxp.json`.
 *
 * Currently only VX Ace is supported. XP and VX can be added later
 * when games needing them turn up in the wild.
 */
enum class RtpPack(
    /** Human-readable label shown in the install dialog. */
    val label: String,
    /** Short directory slug under the RTP root. */
    val slug: String,
    /** RGSS version string that appears in Game.ini [Game] RTP= */
    val iniName: String,
    /** Engine types that can require this pack. */
    val engineTags: List<String>,
    /** Direct download URL for the ZIP. */
    val zipUrl: String,
    /** Expected top-level prefix inside the ZIP to strip (e.g. "RTP100/"). */
    val zipPrefix: String,
    /** Check file under the install dir that confirms this pack was extracted. */
    val markerFile: String,
) {
    VX_ACE(
        label = "RPG Maker VX Ace RTP",
        slug = "vx_ace",
        iniName = "RPGVXAce",
        engineTags = listOf("rgss3", "vxace", "vx_ace", "rgss_vx_ace"),
        zipUrl = "https://archive.org/download/RPG_Maker_RTP_Collection/English/RTP%20VX%20Ace%20%28RGSS3%29.zip",
        zipPrefix = "RTP100/",
        markerFile = "Graphics/Tilesets/World_A1.png",
    );

    companion object {
        /** Map from [iniName] to pack for quick lookup after parsing Game.ini. */
        private val byIniName = entries.associateBy { it.iniName }

        /** Map from engine tag to pack for quick lookup from engine detection. */
        private val byEngineTag = entries.flatMap { pack ->
            pack.engineTags.map { it to pack }
        }.toMap()

        /** Resolve a pack by its [iniName] (e.g. `"RPGVXAce"`). */
        fun fromIniName(name: String): RtpPack? = byIniName[name]

        /** Resolve a pack by an engine tag string. */
        fun fromEngineTag(tag: String): RtpPack? = byEngineTag[tag.lowercase()]
    }
}
