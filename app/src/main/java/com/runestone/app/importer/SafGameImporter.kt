/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.importer

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.runestone.app.data.EngineType
import com.runestone.app.engine.EngineDetector
import com.runestone.app.workspace.WorkspaceManager
import java.io.File
import java.io.FileOutputStream

sealed class SafImportResult {
    data class Success(val gameName: String, val engineType: EngineType) : SafImportResult()
    data class Failure(val reason: String) : SafImportResult()
}

/**
 * Imports games via Android's Storage Access Framework (SAF).
 *
 * The user picks a folder (the game root or www folder) and we:
 * 1. Walk the tree URI recursively
 * 2. Copy all files to the app's private workspace
 * 3. Detect the engine type
 * 4. Generate a manifest
 */
class SafGameImporter(
    private val contentResolver: ContentResolver,
    private val workspaceManager: WorkspaceManager,
) {
    companion object {
        private const val TAG = "RunestoneImport"
        private val SKIP_DIRS = setOf(
            ".git", ".svn", "__MACOSX", ".DS_Store",
            "node_modules", "bower_components",
        )
    }

    fun importTree(treeUri: Uri): SafImportResult {
        return try {
            val gameName = deriveGameName(treeUri)
            val gameDir = workspaceManager.allocateGameDir(gameName)
            val originalDir = File(gameDir, "original")
            val savesDir = File(gameDir, "saves")

            originalDir.mkdirs()
            savesDir.mkdirs()

            var totalFiles = 0
            var totalBytes = 0L

            val filesCopied = copyTree(treeUri, originalDir)
            totalFiles = filesCopied.first
            totalBytes = filesCopied.second

            if (totalFiles == 0) {
                gameDir.deleteRecursively()
                return SafImportResult.Failure("No files found in selected folder")
            }

            // Detect engine from the copied files
            val engineType = EngineDetector.detect(originalDir)

            Log.d(TAG, "Import complete: $totalFiles files, ${totalBytes / 1024}KB, engine=$engineType")

            // Store metadata in a manifest file
            val manifest = org.json.JSONObject().apply {
                put("storageName", gameDir.name)
                put("engineType", engineType.name)
                put("engineLabel", engineType.label)
                put("fileCount", totalFiles)
                put("importedAt", System.currentTimeMillis())
            }
            File(gameDir, "manifest.json").writeText(manifest.toString(2))

            SafImportResult.Success(
                gameName = gameDir.name,
                engineType = engineType,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            SafImportResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Recursively copy files from a SAF tree URI to a local directory.
     */
    private fun copyTree(uri: Uri, destDir: File): Pair<Int, Long> {
        var fileCount = 0
        var byteCount = 0L

        val children = listChildren(uri)
        for (child in children) {
            val childName = child.first
            val childUri = child.second

            if (shouldSkip(childName)) continue

            val childDest = File(destDir, childName)

            if (isDirectory(childUri)) {
                childDest.mkdirs()
                val result = copyTree(childUri, childDest)
                fileCount += result.first
                byteCount += result.second
            } else {
                try {
                    val inputStream = contentResolver.openInputStream(childUri)
                    if (inputStream != null) {
                        childDest.parentFile?.mkdirs()
                        FileOutputStream(childDest).use { output ->
                            inputStream.copyTo(output)
                        }
                        inputStream.close()
                        fileCount++
                        byteCount += childDest.length()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to copy $childName: ${e.message}")
                }
            }
        }

        return Pair(fileCount, byteCount)
    }

    /**
     * List children of a directory URI using ContentResolver.
     */
    private fun listChildren(uri: Uri): List<Pair<String, Uri>> {
        val children = mutableListOf<Pair<String, Uri>>()

        // Use DocumentsContract to list children
        try {
            val childUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                android.provider.DocumentsContract.getDocumentId(uri)
            )

            val cursor = contentResolver.query(
                childUri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null
            )

            cursor?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: "unknown"
                    val docId = c.getString(1) ?: continue
                    val childDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        uri, docId
                    )
                    children.add(Pair(name, childDocUri))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list children of $uri", e)
        }

        return children
    }

    private fun isDirectory(uri: Uri): Boolean {
        return try {
            val mimeType = contentResolver.getType(uri)
            mimeType?.startsWith("vnd.android.document/directory") == true
        } catch (e: Exception) {
            false
        }
    }

    private fun shouldSkip(name: String): Boolean {
        return name in SKIP_DIRS || name.startsWith(".")
    }

    /**
     * Try to derive a human-readable game name from the URI.
     */
    private fun deriveGameName(treeUri: Uri): String {
        // Try to get the last segment of the URI path
        val path = treeUri.lastPathSegment ?: return "imported-game"
        // Try to decode and clean it up
        val decoded = Uri.decode(path)
        return decoded
            .replace(Regex("^tree/|^document/|^primary:"), "")
            .trimEnd('/')
            .ifEmpty { "imported-game" }
    }
}
