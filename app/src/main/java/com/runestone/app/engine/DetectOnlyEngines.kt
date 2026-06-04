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

import android.content.Context
import java.io.File

abstract class DetectOnlyEngine(
    override val id: String,
    override val name: String,
    override val priority: Int,
) : GameEngine {
    override val version: String = "detect-only"

    override fun detectTitle(gameFolder: File): String? =
        gameFolder.name
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
            .takeIf { it.isNotBlank() }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        UnavailableEngine.show(context, name)
    }

    protected fun File.child(name: String): File = File(this, name)
}

class KirikiriEngine : DetectOnlyEngine("kirikiri", "KiriKiri / KAG", 36) {
    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val files = gameFolder.listFiles().orEmpty()
        return files.any { it.isFile && it.name.equals("startup.tjs", ignoreCase = true) } ||
            files.any { it.isFile && it.name.endsWith(".xp3", ignoreCase = true) }
    }
}

class AgsEngine : DetectOnlyEngine("ags", "Adventure Game Studio", 37) {
    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val files = gameFolder.listFiles().orEmpty()
        return files.any { it.isFile && it.name.equals("ac2game.dat", ignoreCase = true) } ||
            files.any { it.isFile && it.name.endsWith(".ags", ignoreCase = true) } ||
            files.any { it.isFile && it.name.equals("speech.vox", ignoreCase = true) } ||
            files.any { it.isFile && it.name.equals("audio.vox", ignoreCase = true) }
    }
}

class GameMakerEngine : DetectOnlyEngine("gamemaker", "GameMaker", 38) {
    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val files = gameFolder.listFiles().orEmpty()
        return files.any { it.isFile && it.name.equals("data.win", ignoreCase = true) } ||
            files.any { it.isFile && it.name.equals("game.unx", ignoreCase = true) } ||
            files.any { it.isFile && it.name.equals("audiogroup1.dat", ignoreCase = true) }
    }
}

class UnityEngine : DetectOnlyEngine("unity", "Unity", 58) {
    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val files = gameFolder.listFiles().orEmpty()
        return files.any { it.isDirectory && it.name.endsWith("_Data", ignoreCase = true) } ||
            files.any { it.isFile && it.name.equals("UnityPlayer.dll", ignoreCase = true) } ||
            files.any { it.isFile && it.name.equals("GameAssembly.dll", ignoreCase = true) } ||
            gameFolder.walkTopDown()
                .maxDepth(3)
                .any { it.isFile && it.name.equals("globalgamemanagers", ignoreCase = true) }
    }
}

class UnrealEngine : DetectOnlyEngine("unreal", "Unreal Engine", 59) {
    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        val files = gameFolder.listFiles().orEmpty()
        return files.any { it.isDirectory && it.name.equals("Engine", ignoreCase = true) } &&
            gameFolder.walkTopDown()
                .maxDepth(5)
                .any { it.isFile && it.name.endsWith(".pak", ignoreCase = true) } ||
            files.any { it.isFile && it.name.endsWith(".uproject", ignoreCase = true) }
    }
}
