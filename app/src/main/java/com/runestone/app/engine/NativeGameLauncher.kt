/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.engine

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.util.Log
import com.runestone.app.data.EngineType
import com.runestone.app.data.RunnerSettings
import java.io.File

class NativeGameLauncher(
    private val activity: Activity,
    private val settings: RunnerSettings,
    private val engineType: EngineType,
) {
    companion object {
        private const val TAG = "Runestone"
    }

    fun launchRgssGame(gameDir: File) {
        Log.i(TAG, "launchRgssGame: $gameDir (engine=$engineType)")

        try {
            val rtpManager = com.runestone.app.rtp.RtpManager(activity)
            val gameTitle = readGameTitle(gameDir) ?: gameDir.name
            com.runestone.app.runtime.RuntimeConfigWriter()
                .writeMkxpConfig(activity, gameDir, gameTitle, rtpManager)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to write mkxp.json; launching without RTP support", t)
        }

        val intent = Intent().apply {
            setClassName(activity, "com.hatkid.mkxpz.MainActivity")
            putExtra("com.runestone.app.extra.GAME_PATH", gameDir.absolutePath)
            putExtra("com.runestone.app.extra.LAYOUT_MODE", settings.layoutMode.name)
            putExtra("com.runestone.app.extra.TOUCH_OPACITY", settings.touchOpacity)
            putExtra("com.runestone.app.extra.TOUCH_SCALE", settings.touchScale)
            putExtra("com.runestone.app.extra.HAPTICS_ENABLED", settings.hapticsEnabled)
            putExtra("com.runestone.app.extra.HAPTIC_INTENSITY", settings.hapticIntensity)
            putExtra("com.runestone.app.extra.HIDE_VIRTUAL_GAMEPAD", settings.hideVirtualGamepad)
            putExtra("com.runestone.app.extra.TEXT_SCALE", settings.textScale)
            putExtra("com.runestone.app.extra.INTEGER_SCALING", settings.integerScaling)
            putExtra("com.runestone.app.extra.DISPLAY_CUTOUT_MODE", settings.displayCutoutMode.name)
            putExtra("com.runestone.app.extra.CONTROLLER_HOME_SHORTCUT", settings.controllerHomeShortcut.name)
            putExtra("com.runestone.app.extra.CONTROLLER_PRESET", settings.controllerPreset)
            putExtra("com.runestone.app.extra.CONTROLLER_BACKEND", "RunestoneCanvasV2")
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun launchEasyRpgGame(gameDir: File) {
        val projectDir = findEasyRpgProjectRoot(gameDir) ?: gameDir
        Log.i(TAG, "EasyRPG bundled: launching ${gameDir.name} project=${projectDir.absolutePath}")
        val configDir = File(activity.filesDir, "easyrpg").apply { mkdirs() }
        val saveDir = File(configDir, "saves").apply { mkdirs() }
        val logFile = File(configDir, "easyrpg-player.log")
        val commandLine = arrayOf(
            "--project-path", projectDir.absolutePath,
            "--config-path", configDir.absolutePath,
            "--save-path", saveDir.absolutePath,
            "--log-file", logFile.absolutePath,
        )
        val intent = Intent().apply {
            setClassName(activity.packageName, "org.easyrpg.player.player.EasyRpgPlayerActivity")
            putExtra("project_path", projectDir.absolutePath)
            putExtra("command_line", commandLine)
            putExtra("save_path", saveDir.absolutePath)
            putExtra("log_file", logFile.absolutePath)
            putExtra("com.runestone.app.extra.GAME_PATH", projectDir.absolutePath)
            putExtra("com.runestone.app.extra.LAYOUT_MODE", settings.layoutMode.name)
            putExtra("com.runestone.app.extra.TOUCH_OPACITY", settings.touchOpacity)
            putExtra("com.runestone.app.extra.TOUCH_SCALE", settings.touchScale)
            putExtra("com.runestone.app.extra.HAPTICS_ENABLED", settings.hapticsEnabled)
            putExtra("com.runestone.app.extra.HAPTIC_INTENSITY", settings.hapticIntensity)
            putExtra("com.runestone.app.extra.HIDE_VIRTUAL_GAMEPAD", settings.hideVirtualGamepad)
            putExtra("com.runestone.app.extra.DISPLAY_CUTOUT_MODE", settings.displayCutoutMode.name)
            putExtra("com.runestone.app.extra.CONTROLLER_HOME_SHORTCUT", settings.controllerHomeShortcut.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun launchGodotGame(gameDir: File) {
        Log.i(TAG, "Godot unavailable: ${gameDir.name}")
        UnavailableEngine.show(activity, "Godot")
    }

    fun launchNScripterGame(gameDir: File) {
        Log.i(TAG, "ONScripter bundled: launching ${gameDir.name}")
        val saveDir = File(gameDir, "saves").apply { mkdirs() }
        val intent = Intent(activity, com.runestone.app.engine.onscripter.OnscripterActivity::class.java).apply {
            putExtra("game_path", gameDir.absolutePath)
            putExtra("save_path", saveDir.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun launchRenpyGame(gameDir: File) {
        Log.i(TAG, "Ren'Py bundled: launching ${gameDir.name}")
        val saveDir = File(gameDir, "saves").apply { mkdirs() }
        val intent = Intent(activity, org.renpy.android.PythonSDLActivity::class.java).apply {
            putExtra("game_path", gameDir.absolutePath)
            putExtra("save_path", saveDir.absolutePath)
            putExtra("engine_version", "8.3.4")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
        activity.finish()
    }

    fun showLegacyDialog(type: EngineType) {
        val title: String
        val message: String
        when (type) {
            EngineType.WOLF -> {
                title = "Unsupported Engine — ${type.label}"
                message = "This game uses Wolf RPG Editor.\n\nRunestone can detect these games, but it does not bundle a Wolf RPG runtime yet. The game files are installed correctly, but this engine cannot be played here yet."
            }
            EngineType.KIRIKIRI -> {
                title = "Unsupported Engine — ${type.label}"
                message = "This game uses KiriKiri/KAG.\n\nRunestone can detect these games, but it does not bundle a KiriKiri runtime. The game files are installed correctly, but this engine cannot be played here yet."
            }
            EngineType.UNITY, EngineType.UNREAL, EngineType.GAMEMAKER, EngineType.AGS -> {
                title = "Unsupported Engine — ${type.label}"
                message = "Runestone can identify this engine, but it does not bundle a compatible Android runtime for it. The game files are installed correctly, but this engine cannot be played here yet."
            }
            else -> {
                title = "Legacy Engine — ${type.label}"
                message = "This is a legacy engine from ${if (type == EngineType.DANTE98) "1992" else "1997"}.\n\nNo open-source runtime exists. These games require the original PC software."
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> activity.finish() }
            .setCancelable(false)
            .show()
    }

    fun showElectronDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Electron Not Supported")
            .setMessage("Electron apps bundle a full Chromium browser.\n\nThey cannot run on Android and require a desktop PC.")
            .setPositiveButton("OK") { _, _ -> activity.finish() }
            .setCancelable(false)
            .show()
    }

    fun readGameTitle(gameDir: File): String? {
        val ini = File(gameDir, "Game.ini")
        if (!ini.isFile) return null
        return runCatching {
            ini.readLines()
                .firstOrNull { it.trim().startsWith("Title=", ignoreCase = true) }
                ?.substringAfter("Title=")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun findEasyRpgProjectRoot(dir: File, maxDepth: Int = 3): File? {
        if (hasEasyRpgSignature(dir)) return dir
        if (maxDepth <= 0 || !dir.isDirectory) return null

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedWith(compareBy<File> { if (it.name.equals("Data", ignoreCase = true)) 0 else 1 }.thenBy { it.name.length })
            ?.firstNotNullOfOrNull { child -> findEasyRpgProjectRoot(child, maxDepth - 1) }
    }

    fun hasEasyRpgSignature(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val names = dir.listFiles()?.map { it.name.lowercase() }?.toSet() ?: return false
        return names.contains("rpg_rt.exe") &&
            (names.contains("rpg_rt.ldb") || names.contains("rpg_rt.lmt"))
    }
}
