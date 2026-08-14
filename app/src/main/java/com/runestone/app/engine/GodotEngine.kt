/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Godot Engine plugin — MIT licensed.
 * Detection: project.godot or .pck files.
 * Launch: unavailable until the Android activity wrapper is integrated.
 */

package com.runestone.app.engine

import android.content.Context
import android.util.Log
import java.io.File

class GodotEngine : GameEngine {

    override val id = "godot"
    override val name = "Godot Engine"
    override val version = "1.0.0"
    override val priority = 25

    companion object {
        private const val TAG = "GodotEngine"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val names = gameFolder.listFiles()?.map { it.name }?.toSet() ?: return false
        return names.contains("project.godot") || names.any { it.endsWith(".pck") }
    }

    override fun detect(gameFolder: File): EngineMetadata? {
        if (!canRun(gameFolder)) return null
        val projectFile = File(gameFolder, "project.godot")
        var title = gameFolder.name
        if (projectFile.exists()) {
            projectFile.readLines().forEach { line ->
                val match = Regex("config/name\\s*=\\s*\"(.+?)\"").find(line)
                if (match != null) { title = match.groupValues[1]; return@forEach }
            }
        }
        return EngineMetadata(engine = id, version = "Godot", title = title, icon = null)
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        if (EngineRegistry.isPluginInstalled(context, "com.runestone.plugin.godot")) {
            Log.i(TAG, "Launching Godot via plugin: ${gameFolder.name}")
            val intent = android.content.Intent("com.runestone.plugin.LAUNCH_GAME").apply {
                putExtra("game_path", gameFolder.absolutePath)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w(TAG, "Godot plugin not found, falling back to unavailable dialog")
            }
        }
        Log.i(TAG, "Godot unavailable: ${gameFolder.name}")
        UnavailableEngine.show(context, "Godot")
    }
}
