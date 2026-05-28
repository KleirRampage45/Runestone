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

import com.runestone.app.data.EngineType
import org.json.JSONObject
import java.io.File

data class GameInstallState(
    val storageName: String,
    val engineType: EngineType,
    val fileCount: Int,
    val importedAtMillis: Long,
    val engineOverride: EngineType? = null, // manual override if autodetect fails
)

class InstallStateStore(private val workspaceManager: WorkspaceManager) {

    fun load(storageName: String): GameInstallState? {
        val file = File(workspaceManager.gameDir(storageName), "install_state.json")
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText())
            GameInstallState(
                storageName = json.getString("storageName"),
                engineType = EngineType.valueOf(json.getString("engineType")),
                fileCount = json.optInt("fileCount", 0),
                importedAtMillis = json.getLong("importedAtMillis"),
                engineOverride = if (json.has("engineOverride")) {
                    runCatching { EngineType.valueOf(json.getString("engineOverride")) }.getOrNull()
                } else null,
            )
        }.getOrNull()
    }

    fun save(state: GameInstallState) {
        val dir = workspaceManager.ensureWorkspace(state.storageName)
        val json = JSONObject()
            .put("storageName", state.storageName)
            .put("engineType", state.engineType.name)
            .put("fileCount", state.fileCount)
            .put("importedAtMillis", state.importedAtMillis)
        if (state.engineOverride != null) {
            json.put("engineOverride", state.engineOverride.name)
        }
        File(dir, "install_state.json").writeText(json.toString(2))
    }

    fun listAll(): List<GameInstallState> {
        val dir = workspaceManager.gamesBaseDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { load(it.name) }
            ?: emptyList()
    }
}
