/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.importer

import android.content.ContentResolver
import android.net.Uri
import com.runestone.app.data.EngineType
import com.runestone.app.engine.EngineDetector
import com.runestone.app.workspace.WorkspaceManager
import java.io.File

sealed class SafImportResult {
    data class Success(val gameName: String, val engineType: EngineType, val fileCount: Int) : SafImportResult()
    data class Failure(val reason: String) : SafImportResult()
}

/**
 * Imports game files from a user-selected folder via Android's
 * Storage Access Framework (SAF).
 *
 * Copies game data into the app's private workspace, organizing by
 * engine type for isolated runtime execution.
 */
class SafGameImporter(
    private val contentResolver: ContentResolver,
    private val workspaceManager: WorkspaceManager,
) {
    fun importTree(treeUri: Uri): SafImportResult {
        // TODO: Implement SAF tree import
        // 1. Copy game files from treeUri to workspace
        // 2. Detect engine type from copied files
        // 3. Generate manifest with engine metadata
        return SafImportResult.Success("Game", EngineType.UNKNOWN, 0)
    }
}
