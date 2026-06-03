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
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.runestone.app.data.EngineType
import com.runestone.app.engine.EngineDetector
import com.runestone.app.workspace.WorkspaceManager
import org.json.JSONObject
import java.io.File

sealed class SafImportResult {
    data class Success(val storageName: String, val engineType: EngineType, val fileCount: Int) : SafImportResult()
    data class Failure(val reason: String) : SafImportResult()
}

class SafGameImporter(
    private val contentResolver: ContentResolver,
    private val workspaceManager: WorkspaceManager,
    private val onProgress: (String) -> Unit = {},
) {
    companion object {
        private const val TAG = "RunestoneImport"
    }

    fun importTree(treeUri: Uri, requestedStorageName: String? = null): SafImportResult {
        Log.i(TAG, "importTree: uri=$treeUri requested=$requestedStorageName")
        val rootDocumentUri = resolveDocumentUri(treeUri)
        val gameName = requestedStorageName ?: queryDocument(rootDocumentUri).name
        Log.i(TAG, "importTree: gameName=$gameName")
        val sanitized = sanitizeName(gameName)
        val gameDir: File = if (requestedStorageName != null && workspaceManager.isInstalled(requestedStorageName)) {
            workspaceManager.gameDir(requestedStorageName)
        } else {
            workspaceManager.allocateGameDir(sanitized)
        }

        val incoming = File(gameDir, "incoming")
        val original = File(gameDir, "original")

        onProgress("Preparing workspace...")
        incoming.deleteRecursively()
        incoming.mkdirs()

        return runCatching {
            Log.i(TAG, "importTree: rootDocumentUri=$rootDocumentUri")

            onProgress("Copying game files...")
            val fileCount = copyDocumentTree(rootDocumentUri, incoming)

            if (fileCount == 0) {
                incoming.deleteRecursively()
                gameDir.deleteRecursively()
                return SafImportResult.Failure("No files found in selected folder")
            }

            onProgress("Checking game files...")
            val engineType = EngineDetector.detect(incoming)
            if (engineType == EngineType.UNKNOWN) {
                incoming.deleteRecursively()
                gameDir.deleteRecursively()
                return SafImportResult.Failure("Could not detect a supported game engine in this folder")
            }

            onProgress("Freezing clean original...")
            original.deleteRecursively()
            require(incoming.renameTo(original)) { "Could not move imported files into workspace" }

            onProgress("Building workspace...")
            workspaceManager.rebuildActiveWorkspace(gameDir.name)

            onProgress("Generating manifest...")
            File(gameDir, "manifest.json").writeText(JSONObject().apply {
                put("storageName", gameDir.name)
                put("engineType", engineType.name)
                put("engineLabel", engineType.label)
                put("fileCount", fileCount)
                put("importedAt", System.currentTimeMillis())
            }.toString(2))

            onProgress("Import complete: $fileCount files")
            Log.d(TAG, "Import complete: $fileCount files, engine=$engineType")

            SafImportResult.Success(gameDir.name, engineType, fileCount)
        }.getOrElse { error ->
            incoming.deleteRecursively()
            Log.e(TAG, "Import failed", error)
            SafImportResult.Failure(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun copyDocumentTree(documentUri: Uri, target: File, fileCount: MutableList<Int> = mutableListOf(0)): Int {
        val meta = queryDocument(documentUri)
        if (meta.isDirectory) {
            onProgress("Copying game files...")
            target.mkdirs()
            listChildren(documentUri).forEach { child ->
                val safeName = sanitizeName(child.name)
                copyDocumentTree(child.uri, File(target, safeName), fileCount)
            }
            return fileCount[0]
        } else {
            target.parentFile?.mkdirs()
            contentResolver.openInputStream(documentUri).use { input ->
                requireNotNull(input) { "Could not open ${meta.name}" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            fileCount[0]++
            onProgress("Copying ${meta.name}")
            return fileCount[0]
        }
    }

    private fun listChildren(parentUri: Uri): List<DocumentMeta> {
        val parentId = DocumentsContract.getDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
        val children = mutableListOf<DocumentMeta>()

        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                children.add(DocumentMeta(childUri, cursor.getString(nameCol), cursor.getString(mimeCol)))
            }
        }
        return children
    }

    private fun queryDocument(documentUri: Uri): DocumentMeta {
        contentResolver.query(
            documentUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                return DocumentMeta(documentUri, name, mime)
            }
        }
        return DocumentMeta(documentUri, "document", "")
    }

    private fun resolveDocumentUri(uri: Uri): Uri {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(uri) }
        return DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
    }

    private fun sanitizeName(name: String): String {
        val cleaned = name.replace('/', '_').replace('\\', '_').trim()
        if (cleaned.isEmpty()) throw IllegalArgumentException("Empty file name")
        if (cleaned == "." || cleaned == "..") throw IllegalArgumentException("Unsafe name: $name")
        return cleaned
    }

    private data class DocumentMeta(
        val uri: Uri,
        val name: String,
        val mimeType: String,
    ) {
        val isDirectory: Boolean = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }
}
