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
import java.io.FileFilter
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveManager(private val workspaceManager: WorkspaceManager) {
    data class SaveBackupResult(
        val count: Int,
        val directory: File?,
    )

    data class SaveBackupInfo(
        val name: String,
        val fileCount: Int,
        val bytes: Long,
        val modifiedAtMillis: Long,
        val directory: File,
    )

    /** Backup saves from the game's active save locations into the protected saves/ dir. */
    fun syncFromActive(storageName: String): Int {
        val gameDir = workspaceManager.originalDir(storageName)
        if (!gameDir.isDirectory) return 0

        val savesDir = workspaceManager.savesDir(storageName)
        savesDir.mkdirs()

        var count = 0
        // Look in common save subdirectories for MV/VX Ace
        val saveDirs = saveLocations(gameDir)
        for (dir in saveDirs) {
            if (!dir.isDirectory) continue
            dir.listFiles(SAVE_FILTER)?.forEach { save ->
                val relativePath = save.relativeTo(gameDir)
                val target = File(savesDir, relativePath.path)
                target.parentFile?.mkdirs()
                save.copyTo(target, overwrite = true)
                count++
            }
        }
        return count
    }

    /** Restore backed-up saves from saves/ back into the game directory. */
    fun restoreToActive(storageName: String): Int {
        val gameDir = workspaceManager.originalDir(storageName)
        if (!gameDir.isDirectory) return 0

        val savesDir = workspaceManager.savesDir(storageName)
        if (!savesDir.exists()) return 0

        var count = 0
        savesDir.walkTopDown()
            .filter { it.isFile && it.name.isRpgMakerSaveName() }
            .forEach { save ->
                // Reconstruct the relative path within the game dir
                val relativePath = save.toRelativeString(savesDir)
                val target = File(gameDir, relativePath)
                target.parentFile?.mkdirs()
                save.copyTo(target, overwrite = true)
                count++
            }
        return count
    }

    /** List all backed-up save files AND live saves inside the game dir. */
    fun listSaves(storageName: String): List<File> {
        val savesDir = workspaceManager.savesDir(storageName)
        val gameDir = workspaceManager.originalDir(storageName)
        val result = mutableListOf<File>()

        // Backed-up saves
        if (savesDir.exists()) {
            result.addAll(
                savesDir.walkTopDown().filter { it.isFile && it.name.isRpgMakerSaveName() }.toList()
            )
        }

        // Live saves still inside the game directory
        if (gameDir.exists()) {
            for (saveSubdir in listOf(File(gameDir, "www/save"), File(gameDir, "save"), gameDir)) {
                if (!saveSubdir.isDirectory) continue
                result.addAll(
                    saveSubdir.listFiles(SAVE_FILTER)?.filter { it.name.isRpgMakerSaveName() }?.map {
                        // If the file is inside gameDir, return it as-is
                        it
                    }?.toList().orEmpty()
                )
            }
        }

        return result.distinctBy { it.name }.sortedBy { it.name }
    }

    companion object {
        private val SAVE_FILTER = FileFilter { file ->
            file.isFile && file.name.isRpgMakerSaveName()
        }

        /** Known save extensions for cross-platform detection. */
        val PC_SAVE_EXTENSIONS = setOf("rvdata2", "rvdata", "rxdata", "lsd", "save", "json", "dat", "rpgsave", "rpy-save")
    }

    // ── Import / Export ──────────────────────────────────────────

    /**
     * Import a save file from external storage into the game.
     * Detects the correct target directory based on engine type.
     *
     * @param storageName Game storage name
     * @param sourceFile The save file to import
     * @param slot Optional slot number (for auto-naming)
     * @return The imported file, or null on failure
     */
    fun importSave(storageName: String, sourceFile: File, slot: Int? = null): File? {
        val gameDir = workspaceManager.originalDir(storageName)
        if (!gameDir.isDirectory) return null

        // Detect target directory based on existing save locations
        val saveLocations = saveLocations(gameDir)

        val targetDir = saveLocations.firstOrNull { it.isDirectory } ?: saveLocations.first()
        targetDir.mkdirs()

        val targetName = if (slot != null && sourceFile.extension in PC_SAVE_EXTENSIONS) {
            "Save${slot.toString().padStart(2, '0')}.${sourceFile.extension}"
        } else {
            sourceFile.name
        }

        val target = File(targetDir, targetName)
        sourceFile.copyTo(target, overwrite = true)
        return target
    }

    /**
     * Export a save file to a destination directory.
     * @return The exported file, or null on failure.
     */
    fun exportSave(storageName: String, saveName: String, destDir: File): File? {
        val saves = listSaves(storageName)
        val source = saves.find { it.name == saveName } ?: return null
        val dest = File(destDir, source.name)
        source.copyTo(dest, overwrite = true)
        return dest
    }

    /**
     * Export every detected save into a ZIP stream.
     * The ZIP keeps relative save paths where possible, so MV/MZ and Ren'Py
     * saves do not flatten into ambiguous filenames.
     */
    fun exportAllSavesZip(storageName: String, outputStream: OutputStream): Int {
        val gameDir = workspaceManager.originalDir(storageName)
        val savesDir = workspaceManager.savesDir(storageName)
        val entries = mutableListOf<Pair<String, File>>()

        if (savesDir.exists()) {
            savesDir.walkTopDown()
                .filter { it.isFile && it.name.isRpgMakerSaveName() }
                .forEach { save ->
                    entries.add(save.toRelativeString(savesDir).normalizeZipPath() to save)
                }
        }

        if (gameDir.exists()) {
            saveLocations(gameDir).forEach { dir ->
                if (!dir.isDirectory) return@forEach
                dir.listFiles(SAVE_FILTER)?.forEach { save ->
                    entries.add(save.relativeTo(gameDir).path.normalizeZipPath() to save)
                }
            }
        }

        val uniqueEntries = entries
            .filter { it.first.isNotBlank() }
            .distinctBy { it.first.lowercase() }
            .sortedBy { it.first.lowercase() }

        ZipOutputStream(outputStream).use { zip ->
            uniqueEntries.forEach { (path, file) ->
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return uniqueEntries.size
    }

    /**
     * Capture detected live/protected saves into a timestamped backup folder.
     * Used before patch/translation operations that may affect compatibility.
     */
    fun backupSaves(storageName: String, reason: String): SaveBackupResult {
        syncFromActive(storageName)
        val saves = listSaves(storageName)
        if (saves.isEmpty()) return SaveBackupResult(0, null)

        val gameDir = workspaceManager.gameDir(storageName)
        val originalDir = workspaceManager.originalDir(storageName)
        val protectedSavesDir = workspaceManager.savesDir(storageName)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val safeReason = reason.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "backup" }
        val backupDir = File(workspaceManager.saveBackupsDir(storageName), "${stamp}_$safeReason")
        backupDir.mkdirs()
        File(backupDir, ".nomedia").writeText("")

        var count = 0
        saves.forEach { save ->
            val relPath = when {
                save.canonicalPath.startsWith(protectedSavesDir.canonicalPath + File.separator) ->
                    "protected/${save.toRelativeString(protectedSavesDir)}"
                save.canonicalPath.startsWith(originalDir.canonicalPath + File.separator) ->
                    "live/${save.toRelativeString(originalDir)}"
                save.canonicalPath.startsWith(gameDir.canonicalPath + File.separator) ->
                    "workspace/${save.toRelativeString(gameDir)}"
                else -> save.name
            }.replace(File.separatorChar, '/')
            val target = File(backupDir, relPath)
            target.parentFile?.mkdirs()
            save.copyTo(target, overwrite = true)
            count++
        }

        return SaveBackupResult(count, backupDir)
    }

    fun listSaveBackups(storageName: String): List<SaveBackupInfo> {
        val backupsDir = workspaceManager.saveBackupsDir(storageName)
        if (!backupsDir.isDirectory) return emptyList()

        return backupsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { dir ->
                val files = dir.walkTopDown()
                    .filter { it.isFile && it.name != ".nomedia" }
                    .toList()
                SaveBackupInfo(
                    name = dir.name,
                    fileCount = files.size,
                    bytes = files.sumOf { it.length() },
                    modifiedAtMillis = dir.lastModified(),
                    directory = dir,
                )
            }
            ?.sortedByDescending { it.modifiedAtMillis }
            ?: emptyList()
    }

    fun importSavesZip(storageName: String, zipFile: File): Int {
        val gameDir = workspaceManager.originalDir(storageName)
        if (!gameDir.isDirectory || !zipFile.isFile) return 0

        backupSaves(storageName, "before_save_import")

        val protectedSavesDir = workspaceManager.savesDir(storageName)
        protectedSavesDir.mkdirs()
        File(protectedSavesDir, ".nomedia").writeText("")

        var imported = 0
        ZipInputStream(FileInputStream(zipFile)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    if (entry.isDirectory) continue
                    val relPath = entry.name.safeSaveZipPath() ?: continue
                    if (!File(relPath).name.isRpgMakerSaveName()) continue

                    val target = File(protectedSavesDir, relPath)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                    imported++
                } finally {
                    zip.closeEntry()
                }
            }
        }

        if (imported > 0) {
            restoreToActive(storageName)
        }
        return imported
    }

    /**
     * Scan a directory for PC-compatible save files.
     * Useful for users who copied saves from their PC.
     */
    fun detectPcSaves(folder: File): List<File> {
        if (!folder.isDirectory) return emptyList()
        return folder.listFiles(SAVE_FILTER)?.filter {
            it.extension.lowercase() in PC_SAVE_EXTENSIONS
        }?.sortedBy { it.name } ?: emptyList()
    }

    /**
     * Import all detected PC saves from a folder into the game.
     * Auto-detects the target save directory.
     */
    fun importPcSaves(storageName: String, pcFolder: File): List<File> {
        val saves = detectPcSaves(pcFolder)
        return saves.mapNotNull { save ->
            val slot = Regex("""(\d+)""").find(save.name)?.groupValues?.get(1)?.toIntOrNull()
            importSave(storageName, save, slot)
        }
    }

    private fun saveLocations(gameDir: File): List<File> = listOf(
        gameDir,                 // RGSS root
        File(gameDir, "www/save"), // RPG Maker MV/MZ
        File(gameDir, "save"),   // Ren'Py and some HTML/VN engines
        File(gameDir, "saves"),  // Ren'Py wrapper path
    )

    private fun String.normalizeZipPath(): String =
        replace(File.separatorChar, '/').trimStart('/')

    private fun String.safeSaveZipPath(): String? {
        val normalized = replace('\\', '/').trimStart('/')
        if (normalized.isBlank()) return null
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.any { it == "." || it == ".." }) return null
        val stripped = when (parts.first().lowercase()) {
            "protected", "live", "workspace", "saves" -> parts.drop(1)
            else -> parts
        }
        if (stripped.isEmpty()) return null
        return stripped.joinToString("/")
    }
}

internal fun String.isRpgMakerSaveName(): Boolean =
    matches(Regex("""(?i)(save|file|game|global|persistent|auto|quick)?\d*[-\w]*\.(rvdata2|rvdata|rxdata|dat|json|lsd|lmu|lmt|rpgsave|save|rpy-save)"""))
