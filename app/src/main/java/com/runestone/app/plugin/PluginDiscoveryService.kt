/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Plugin discovery service — finds installed engine plugin APKs
 * via Android PackageManager and their RUNESTONE_PLUGIN intent filter.
 */

package com.runestone.app.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log

/**
 * Discovers installed Runestone engine plugins and provides
 * installation status for known plugins.
 *
 * Discovery mechanism:
 *   1. Query PackageManager for activities with ACTION_RUNESTONE_PLUGIN
 *   2. Read meta-data from each activity: engine_id, engine_name, etc.
 *   3. Return list of PluginInfo for discovered plugins
 *   4. Cross-reference with KNOWN_PLUGINS to identify missing ones
 */
class PluginDiscoveryService(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Discover all installed Runestone plugins.
     * Returns list of PluginInfo for every plugin that declares
     * the RUNESTONE_PLUGIN intent filter.
     */
    fun discoverPlugins(): List<PluginInfo> {
        val intent = Intent(PluginConstants.ACTION_RUNESTONE_PLUGIN)
        val resolved: List<ResolveInfo> = try {
            pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query plugins", e)
            emptyList()
        }

        return resolved.mapNotNull { resolve ->
            val activityInfo = resolve.activityInfo ?: return@mapNotNull null
            val meta = activityInfo.metaData ?: return@mapNotNull null

            val engineId = meta.getString(PluginConstants.META_ENGINE_ID) ?: return@mapNotNull null
            val engineName = meta.getString(PluginConstants.META_ENGINE_NAME) ?: engineId
            val version = meta.getString(PluginConstants.META_VERSION) ?: "unknown"
            val typesStr = meta.getString(PluginConstants.META_SUPPORTED_TYPES) ?: ""
            val supportedTypes = typesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            PluginInfo(
                packageName = activityInfo.packageName,
                activityClass = activityInfo.name,
                engineId = engineId,
                engineName = engineName,
                version = version,
                supportedTypes = supportedTypes,
                isInstalled = true,
                iconRes = activityInfo.icon,
            )
        }.also {
            Log.i(TAG, "Discovered ${it.size} plugins: ${it.map { p -> p.engineId }}")
        }
    }

    /**
     * Check whether a specific engine plugin is installed.
     */
    fun isPluginInstalled(engineId: String): Boolean {
        // First check discovered plugins
        if (discoverPlugins().any { it.engineId == engineId }) return true

        // Fallback: check PackageManager directly by package name
        val info = PluginConstants.KNOWN_PLUGINS[engineId] ?: return false
        return try {
            pm.getPackageInfo(info.packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Get installation status for all known plugins.
     * Includes both installed and not-installed plugins.
     */
    fun getKnownPluginsStatus(): List<PluginInfo> {
        val discovered = discoverPlugins()
        val discoveredIds = discovered.map { it.engineId }.toSet()

        val allPlugins = discovered.toMutableList()

        // Add known plugins that aren't discovered
        for ((engineId, info) in PluginConstants.KNOWN_PLUGINS) {
            if (engineId !in discoveredIds) {
                // Check if package exists but didn't declare intent (broken plugin)
                val isInstalled = try {
                    pm.getPackageInfo(info.packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }

                allPlugins.add(PluginInfo(
                    packageName = info.packageName,
                    activityClass = "",  // unknown — plugin broken or not installed
                    engineId = engineId,
                    engineName = info.name,
                    version = if (isInstalled) "installed (intent missing)" else "not installed",
                    supportedTypes = emptyList(),
                    isInstalled = isInstalled,
                    iconRes = null,
                ))
            }
        }

        return allPlugins
    }

    /**
     * Launch a game using a discovered plugin.
     * Sends an Intent to the plugin's activity with the game path and config.
     */
    fun launchWithPlugin(
        plugin: PluginInfo,
        gameFolder: String,
        configJson: String = "{}",
    ): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(plugin.packageName, plugin.activityClass)
                putExtra(EXTRA_GAME_PATH, gameFolder)
                putExtra(EXTRA_CONFIG_JSON, configJson)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched ${plugin.engineId} plugin for $gameFolder")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch plugin ${plugin.engineId}", e)
            false
        }
    }

    /**
     * Open the download page for a plugin.
     * Returns true if a browser intent was launched.
     */
    fun openPluginDownload(engineId: String): Boolean {
        val info = PluginConstants.KNOWN_PLUGINS[engineId] ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download for $engineId", e)
            false
        }
    }

    companion object {
        private const val TAG = "PluginDiscovery"
        const val EXTRA_GAME_PATH = "runestone.game_path"
        const val EXTRA_CONFIG_JSON = "runestone.config_json"
    }
}
