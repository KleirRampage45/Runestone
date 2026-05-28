/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Plugin API — contract all engine plugins implement.
 * Shared between core app and plugin APKs via a shared library module.
 */

package com.runestone.app.plugin

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import java.io.File

/**
 * Metadata about a discovered plugin.
 */
data class PluginInfo(
    val packageName: String,
    val activityClass: String,
    val engineId: String,           // "mkxp-z", "easyrpg", "renpy", etc.
    val engineName: String,         // "RPG Maker XP/VX/VX Ace"
    val version: String,
    val supportedTypes: List<String>, // ["rpgmxp", "rpgmvx", "rpgmvxace"]
    val isInstalled: Boolean,
    val iconRes: Int? = null,
)

/**
 * Game metadata extracted by a plugin.
 */
data class PluginGameMetadata(
    val title: String,
    val engine: String,
    val engineVersion: String?,
    val author: String?,
    val version: String?,
    val icon: Bitmap?,
    val requiresRtp: Boolean = false,
)

/**
 * Save file info from a plugin.
 */
data class PluginSaveInfo(
    val name: String,
    val slot: Int?,
    val size: Long,
    val lastModified: Long,
    val screenshot: Bitmap?,
    val metadata: Map<String, String>?,
)

/**
 * Interface that all Runestone engine plugins must implement.
 *
 * Plugin APKs expose this via their launcher Activity.
 * The core app discovers them via PackageManager and calls these methods
 * through the Activity.
 *
 * Lifecycle:
 *   1. Plugin APK installs → declares RUNESTONE_PLUGIN intent filter
 *   2. Core app discovers plugin via PluginDiscoveryService
 *   3. User imports a game → core calls canRun(gameFolder)
 *   4. User launches game → core calls launch(context, gameFolder, config)
 */
interface PluginAPI {

    /** Check whether this engine can run the game in [gameFolder]. */
    fun canRun(gameFolder: File): Boolean

    /** Launch the game. Called on the main thread. */
    fun launch(context: Context, gameFolder: File, configJson: String)

    /** List save files for this game. */
    fun getSaves(gameFolder: File): List<PluginSaveInfo>

    /** Extract metadata from game files. */
    fun getMetadata(gameFolder: File): PluginGameMetadata

    /**
     * Optional per-plugin settings view.
     * Return null if plugin has no configurable settings.
     */
    fun getSettingsView(context: Context): View? = null

    /**
     * Called when a game is imported for this engine.
     * Return false to reject the import.
     */
    fun onImport(context: Context, gameFolder: File): Boolean = true

    /**
     * Apply a patch (translation, mod) to the game.
     */
    fun applyPatch(gameFolder: File, patchFile: File): Boolean = false
}

/**
 * Constants shared between core app and plugins.
 */
object PluginConstants {
    /** Intent action declared by all plugin APKs. */
    const val ACTION_RUNESTONE_PLUGIN = "com.runestone.plugin.RUNESTONE_PLUGIN"

    /** Meta-data keys in plugin AndroidManifest.xml. */
    const val META_ENGINE_ID = "runestone.engine_id"
    const val META_ENGINE_NAME = "runestone.engine_name"
    const val META_SUPPORTED_TYPES = "runestone.supported_types"
    const val META_VERSION = "runestone.version"

    /** Plugin package name prefix. */
    const val PLUGIN_PACKAGE_PREFIX = "com.runestone.plugin."

    /** Known plugin packages and their download URLs. */
    val KNOWN_PLUGINS = mapOf(
        "mkxp-z" to PluginDownloadInfo(
            packageName = "com.runestone.plugin.mkxpz",
            name = "RPG Maker XP/VX/VX Ace (mkxp-z)",
            downloadUrl = "https://github.com/KleirRampage45/Runestone/releases",
        ),
        "easyrpg" to PluginDownloadInfo(
            packageName = "com.runestone.plugin.easyrpg",
            name = "EasyRPG Player (RM2000/2003)",
            downloadUrl = "https://github.com/EasyRPG/Player/releases/latest",
        ),
        "renpy" to PluginDownloadInfo(
            packageName = "com.runestone.plugin.renpy",
            name = "Ren'Py Engine",
            downloadUrl = "https://runestone.app/plugins/renpy",
        ),
        "godot3" to PluginDownloadInfo(
            packageName = "com.runestone.plugin.godot3",
            name = "Godot 3.x Engine",
            downloadUrl = "https://runestone.app/plugins/godot3",
        ),
        "godot4" to PluginDownloadInfo(
            packageName = "com.runestone.plugin.godot4",
            name = "Godot 4.x Engine",
            downloadUrl = "https://runestone.app/plugins/godot4",
        ),
        "ruffle" to PluginDownloadInfo(
            packageName = "com.runestone.plugin.ruffle",
            name = "Ruffle Flash Player",
            downloadUrl = "https://runestone.app/plugins/ruffle",
        ),
    )
}

data class PluginDownloadInfo(
    val packageName: String,
    val name: String,
    val downloadUrl: String,
)
