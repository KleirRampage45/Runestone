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
    WOLF("Wolf RPG Editor", EngineTier.LEGACY),
    KIRIKIRI("KiriKiri / KAG", EngineTier.LEGACY),
    UNITY("Unity", EngineTier.LEGACY),
    UNREAL("Unreal Engine", EngineTier.LEGACY),
    GAMEMAKER("GameMaker", EngineTier.LEGACY),
    AGS("Adventure Game Studio", EngineTier.LEGACY),
    ELECTRON("Electron", EngineTier.DOWNLOAD),

    // ══ Legacy (detect only — no open-source runtime exists) ══
    RM95("RPG Maker 95", EngineTier.LEGACY),
    DANTE98("RPG Tsukuru Dante 98", EngineTier.LEGACY),

    // ══ Unknown ══
    UNKNOWN("Unknown Engine", EngineTier.LEGACY);

    companion object {
        fun fromEngineId(id: String): EngineType = when (id.trim().lowercase().replace("-", "_")) {
            "mkxp_z", "rgss", "rpgmaker", "rpg_maker" -> RGSS_VX_ACE
            "rgss_xp", "xp", "rpgmaker_xp", "rpg_maker_xp" -> RGSS_XP
            "rgss_vx", "vx", "rpgmaker_vx", "rpg_maker_vx" -> RGSS_VX
            "rgss_vx_ace", "vxace", "vx_ace", "rgss3", "rpgmaker_vx_ace", "rpg_maker_vx_ace" -> RGSS_VX_ACE
            "easyrpg", "rpg_rt", "rpgmaker_2000", "rpgmaker_2003", "2k", "2k3" -> EASYRPG
            "renpy", "ren_py" -> RENPY
            "godot", "godot3" -> GODOT3
            "godot4" -> GODOT4
            "webview_mv", "mv", "rpgmaker_mv", "rpg_maker_mv" -> MV
            "webview_mz", "mz", "rpgmaker_mz", "rpg_maker_mz" -> MZ
            "tyrano", "tyranobuilder" -> TYRANO
            "construct", "construct2", "construct3" -> CONSTRUCT
            "html", "html5" -> HTML
            "twine" -> TWINE
            "vnmaker", "vn_maker" -> VNMAKER
            "ruffle", "flash", "swf" -> RUFFLE
            "nscripter", "onscripter" -> NSCRIPTER
            "wolf", "wolfrpg", "wolf_rpg", "wolf_rpg_editor" -> WOLF
            "kirikiri", "kirikiri2", "kirikiri_z", "kag", "xp3" -> KIRIKIRI
            "unity", "unity3d" -> UNITY
            "unreal", "ue4", "ue5", "unreal_engine" -> UNREAL
            "gamemaker", "game_maker", "gms", "gms2" -> GAMEMAKER
            "ags", "adventure_game_studio" -> AGS
            "electron" -> ELECTRON
            "rm95", "rpgmaker_95" -> RM95
            "dante98" -> DANTE98
            else -> UNKNOWN
        }
    }
}

enum class EngineTier { BUNDLED, DOWNLOAD, LEGACY }
