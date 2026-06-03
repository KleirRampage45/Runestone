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

class SafStorageBrowser(private val contentResolver: ContentResolver) {

    data class StorageRoot(
        val treeUri: Uri,
        val documentUri: Uri,
        val name: String,
    )

    data class Folder(
        val uri: Uri,
        val name: String,
        val childFolderCount: Int,
        val fileCount: Int,
        val gameHint: String?,
    )

    fun listRoots(): List<StorageRoot> =
        contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapNotNull { permission ->
                runCatching {
                    val documentUri = resolveDocumentUri(permission.uri)
                    StorageRoot(permission.uri, documentUri, queryName(documentUri))
                }.getOrNull()
            }
            .distinctBy { it.documentUri.toString() }
            .sortedBy { it.name.lowercase() }

    fun rootFromTreeUri(treeUri: Uri): StorageRoot {
        val documentUri = resolveDocumentUri(treeUri)
        return StorageRoot(treeUri, documentUri, queryName(documentUri))
    }

    fun listFolders(parentUri: Uri): List<Folder> {
        val children = queryChildren(parentUri)
        return children
            .filter { it.isDirectory }
            .map { child ->
                val nested = runCatching { queryChildren(child.uri) }.getOrDefault(emptyList())
                Folder(
                    uri = child.uri,
                    name = child.name,
                    childFolderCount = nested.count { it.isDirectory },
                    fileCount = nested.count { !it.isDirectory },
                    gameHint = detectGameHint(nested),
                )
            }
            .sortedWith(
                compareByDescending<Folder> { it.gameHint != null }
                    .thenBy { it.name.lowercase() },
            )
    }

    fun describeFolder(folderUri: Uri): Folder {
        val children = queryChildren(folderUri)
        return Folder(
            uri = folderUri,
            name = queryName(folderUri),
            childFolderCount = children.count { it.isDirectory },
            fileCount = children.count { !it.isDirectory },
            gameHint = detectGameHint(children),
        )
    }

    private fun resolveDocumentUri(uri: Uri): Uri {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(uri) }
        return DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
    }

    private fun queryName(documentUri: Uri): String {
        contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return "Storage"
    }

    private fun queryChildren(parentUri: Uri): List<DocumentEntry> {
        val parentId = DocumentsContract.getDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
        val entries = mutableListOf<DocumentEntry>()
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                entries += DocumentEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(idCol)),
                    name = cursor.getString(nameCol),
                    mimeType = cursor.getString(mimeCol),
                )
            }
        }
        return entries
    }

    private fun detectGameHint(entries: List<DocumentEntry>): String? {
        val names = entries.map { it.name.lowercase() }.toSet()
        return when {
            "game.ini" in names && "rgss3a.dll" in names -> "RPG MAKER VX ACE"
            "game.ini" in names && "rgss2a.dll" in names -> "RPG MAKER VX"
            "game.ini" in names && "rgss102e.dll" in names -> "RPG MAKER XP"
            "rpg_rt.ldb" in names -> "RPG MAKER 2000/2003"
            "renpy" in names || "game" in names && entries.any { it.name.endsWith(".py", ignoreCase = true) } -> "REN'PY"
            "index.html" in names && "www" in names -> "RPG MAKER MV/MZ"
            "index.html" in names -> "HTML GAME"
            entries.any { it.name.endsWith(".nsa", ignoreCase = true) } -> "NSCRIPTER"
            else -> null
        }
    }

    private data class DocumentEntry(
        val uri: Uri,
        val name: String,
        val mimeType: String,
    ) {
        val isDirectory: Boolean = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }
}
