/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Godot Engine plugin — MIT licensed.
 * Detection: project.godot or .pck files.
 * Launch: bundled GodotActivity or download prompt.
 */

package com.runestone.app.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File

class GodotEngine : GameEngine {

    override val id = "godot"
    override val name = "Godot Engine"
    override val version = "1.0.0"
    override val priority = 25

    companion object {
        private const val TAG = "GodotEngine"
        const val GODOT_ACTIVITY = "org.godotengine.android.GodotActivity"
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
        Log.i(TAG, "Launching Godot (bundled): ${gameFolder.name}")
        val intent = Intent().apply {
            setClassName(context.packageName, GODOT_ACTIVITY)
            putExtra("godot_arg", "-path")
            putExtra("godot_arg_value", gameFolder.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
