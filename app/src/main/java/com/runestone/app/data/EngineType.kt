/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.data

/**
 * All supported game engines.
 *
 * "Bundled" = native runtime included in the APK.
 * "Download" = native runtime must be downloaded (or WebView fallback).
 * "Detect only" = no runtime exists; informational dialog shown.
 *
 * @see com.runestone.app.engine.GameEngine
 * @see com.runestone.app.engine.EngineRegistry
 */
enum class EngineType(
    val label: String,
    val tier: EngineTier = EngineTier.BUNDLED,
) {
    // ══ Bundled native (GPL/MIT — runtime included in APK) ══
    RGSS_XP("RPG Maker XP", EngineTier.BUNDLED),
    RGSS_VX("RPG Maker VX", EngineTier.BUNDLED),
    RGSS_VX_ACE("RPG Maker VX Ace", EngineTier.BUNDLED),
    RGSS_2000("RPG Maker 2000", EngineTier.BUNDLED),
    RGSS_2003("RPG Maker 2003", EngineTier.BUNDLED),
    EASYRPG("RPG Maker 2000/2003", EngineTier.BUNDLED),  // legacy alias

    // ══ Bundled WebView (MIT — runs in system WebView, nothing to download) ══
    MV("RPG Maker MV", EngineTier.BUNDLED),
    MZ("RPG Maker MZ", EngineTier.BUNDLED),
    TYRANO("TyranoBuilder", EngineTier.BUNDLED),
    CONSTRUCT("Construct 2/3", EngineTier.BUNDLED),
    HTML("HTML5 Game", EngineTier.BUNDLED),
    TWINE("Twine", EngineTier.BUNDLED),
    VNMAKER("VN Maker", EngineTier.BUNDLED),
    RUFFLE("Flash (Ruffle)", EngineTier.BUNDLED),

    // ══ Bundled native (GPL/MIT — runtime .so included in APK) ══
    RENPY("Ren'Py", EngineTier.BUNDLED),
    GODOT("Godot Engine", EngineTier.BUNDLED),
    GODOT3("Godot 3.x", EngineTier.BUNDLED),
    GODOT4("Godot 4.x", EngineTier.BUNDLED),
    NSCRIPTER("NScripter / ONScripter", EngineTier.BUNDLED),
    ELECTRON("Electron", EngineTier.DOWNLOAD),

    // ══ Legacy (detect only — no open-source runtime exists) ══
    RM95("RPG Maker 95", EngineTier.LEGACY),
    DANTE98("RPG Tsukuru Dante 98", EngineTier.LEGACY),

    // ══ Unknown ══
    UNKNOWN("Unknown Engine", EngineTier.LEGACY);

    companion object {
        fun fromEngineId(id: String): EngineType = when (id) {
            "mkxp-z" -> RGSS_VX_ACE
            "easyrpg" -> EASYRPG
            "renpy" -> RENPY
            "godot", "godot3" -> GODOT3
            "godot4" -> GODOT4
            "webview-mv" -> MV
            "webview-mz" -> MZ
            "tyrano" -> TYRANO
            "construct" -> CONSTRUCT
            "html" -> HTML
            "twine" -> TWINE
            "vnmaker" -> VNMAKER
            "ruffle" -> RUFFLE
            "nscripter" -> NSCRIPTER
            "electron" -> ELECTRON
            "rm95" -> RM95
            "dante98" -> DANTE98
            else -> UNKNOWN
        }
    }
}

enum class EngineTier { BUNDLED, DOWNLOAD, LEGACY }
