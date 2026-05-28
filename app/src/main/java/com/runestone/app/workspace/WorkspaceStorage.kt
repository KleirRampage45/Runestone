/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.workspace

import java.io.File

data class WorkspaceStorage(
    val storageName: String,
    val originalBytes: Long,
    val savesBytes: Long,
    val otherBytes: Long,
) {
    val totalBytes: Long = originalBytes + savesBytes + otherBytes
}

class WorkspaceStorageReporter(private val workspaceManager: WorkspaceManager) {
    fun collect(storageName: String): WorkspaceStorage {
        val original = workspaceManager.originalDir(storageName)
        val saves = workspaceManager.savesDir(storageName)

        val originalSaves = original.rpgMakerSaveSize()
        val backupSaves = saves.directorySize()

        val total = workspaceManager.gameDir(storageName).directorySize()

        return WorkspaceStorage(
            storageName = storageName,
            originalBytes = (original.directorySize() - originalSaves).coerceAtLeast(0L),
            savesBytes = backupSaves + originalSaves,
            otherBytes = (total - original.directorySize() - backupSaves).coerceAtLeast(0L),
        )
    }
}

internal fun File.rpgMakerSaveSize(): Long {
    if (!exists() || !isDirectory) return 0L
    return walkTopDown().filter { it.isFile && it.name.isRpgMakerSaveName() }.sumOf { it.length() }
}

internal fun File.directorySize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
