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
 * Enumeration of all supported game engines.
 * 
 * This is kept for backward compatibility with existing code.
 * New code should use the GameEngine interface and EngineRegistry instead.
 * 
 * @see com.runestone.app.engine.GameEngine
 * @see com.runestone.app.engine.EngineRegistry
 */
enum class EngineType(val label: String) {
    // Native engines (bundled or bundled-able)
    RGSS_XP("RPG Maker XP"),
    RGSS_VX("RPG Maker VX"),
    RGSS_VX_ACE("RPG Maker VX Ace"),
    RGSS_2000("RPG Maker 2000"),          // EasyRPG (GPLv3 — bundlable)
    RGSS_2003("RPG Maker 2003"),          // EasyRPG (GPLv3 — bundlable)
    EASYRPG("RPG Maker 2000/2003"),       // Legacy alias
    RENPY("Ren'Py"),                       // MIT — bundlable
    GODOT("Godot Engine"),                 // MIT — bundlable

    // Legacy engines (detection only — no open-source runtime exists)
    RM95("RPG Maker 95"),                  // Proprietary, 1997
    DANTE98("RPG Tsukuru Dante 98"),       // Proprietary, 1992

    // WebView engines
    MV("RPG Maker MV"),
    MZ("RPG Maker MZ"),
    TYRANO("TyranoBuilder"),
    CONSTRUCT("Construct 2/3"),

    // Unknown
    UNKNOWN("Unknown Engine");

    companion object {
        fun fromEngineId(id: String): EngineType = when (id) {
            "mkxp-z" -> RGSS_VX_ACE
            "easyrpg" -> EASYRPG
            "renpy" -> RENPY
            "godot" -> GODOT
            "webview-mv" -> MV
            "webview-mz" -> MZ
            "tyrano" -> TYRANO
            "construct" -> CONSTRUCT
            "rm95" -> RM95
            "dante98" -> DANTE98
            else -> UNKNOWN
        }
    }
}
