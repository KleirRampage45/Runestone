/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Patch/translation/mod manager for Runestone games.
 *
 * Installs patches directly into the original/ game directory.
 * Only overwritten files get backed up; only added files get path-tracked.
 * Zero duplication for clean games; space-efficient for patched ones.
 *
 * Directory layout (per game):
 *   original/             <- game files (engine reads from here)
 *   patches/
 *     backups/{patchId}/  <- original files before they got overwritten
 *     manifests/{patchId}.json  -> {"addedFiles": [...]}
 *     zips/{patchId}.zip         <- source ZIP stored for re-apply
 */

package com.runestone.app.workspace

import android.content.Context
import com.runestone.app.data.GameConfigService
import com.runestone.app.data.InstalledPatch
import com.runestone.app.data.PatchesSection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PatchManager(
    private val context: Context,
    private val workspaceManager: WorkspaceManager,
    private val configService: GameConfigService = GameConfigService(context, workspaceManager),
) {

    data class PatchResult(
        val success: Boolean,
        val message: String,
        val overwrittenFiles: List<String> = emptyList(),
        val addedFiles: List<String> = emptyList(),
        val patchId: String? = null,
    )

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Install a ZIP patch onto the game's original/ directory.
     *
     * Steps:
     *  1. Validate ZIP
     *  2. Extract to temp, walking each entry
     *  3. For each file: check if it exists in original/
     *     - YES (overwrite): backup original to patches/backups/, then copy new version
     *     - NO  (new file):  copy to original/, track path in manifest
     *  4. Store source ZIP in patches/zips/
     *  5. Write manifest JSON
     *  6. Save InstalledPatch entry to PerGameConfig
     */
    fun installPatch(
        storageName: String,
        zipFile: File,
        patchName: String,
        description: String = "",
        isTranslation: Boolean = false,
    ): PatchResult {
        val gameDir = workspaceManager.gameDir(storageName)
        val originalDir = workspaceManager.originalDir(storageName)
        if (!originalDir.isDirectory) {
            return PatchResult(false, "Game directory not found: $storageName")
        }

        // --- Validate ZIP ---
        if (!zipFile.isFile || !zipFile.name.endsWith(".zip", ignoreCase = true)) {
            return PatchResult(false, "Selected file is not a ZIP archive")
        }
        val magic = runCatching {
            FileInputStream(zipFile).use { input ->
                val header = ByteArray(4)
                if (input.read(header) == 4) {
                    header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                } else false
            }
        }.getOrElse { false }
        if (!magic) {
            return PatchResult(false, "File is not a valid ZIP (bad magic bytes)")
        }

        val patchId = UUID.randomUUID().toString().take(12)
        val patchDir = patchBaseDir(gameDir, patchId).also { it.mkdirs() }
        val backupDir = backupDir(gameDir, patchId).also { it.mkdirs() }
        val manifestFile = manifestFile(gameDir, patchId)
        val zipArchive = zipArchiveFile(gameDir, patchId)

        val overwritten = mutableListOf<String>()
        val added = mutableListOf<String>()

        // --- Extract and install ---
        val result = runCatching {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val relPath = entry.name
                        val targetFile = File(originalDir, relPath)
                        val targetParent = targetFile.parentFile
                        if (targetParent != null) targetParent.mkdirs()

                        if (targetFile.exists()) {
                            // OVERWRITE: backup original first
                            val backupFile = File(backupDir, relPath)
                            backupFile.parentFile?.mkdirs()
                            targetFile.copyTo(backupFile, overwrite = true)
                            overwritten.add(relPath)
                        } else {
                            // NEW FILE: just track it
                            added.add(relPath)
                        }

                        // Write the patched file into original/
                        FileOutputStream(targetFile).use { out ->
                            zis.copyTo(out)
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            // Store source ZIP
            zipFile.copyTo(zipArchive, overwrite = true)

            // Write manifest
            manifestFile.writeText(JSONObject().apply {
                put("addedFiles", JSONArray(added))
            }.toString(2))

            // Save config entry
            addPatchToConfig(storageName, InstalledPatch(
                patchId = patchId,
                name = patchName,
                description = description,
                installedAtMillis = System.currentTimeMillis(),
                sourceFileName = zipFile.name,
                isTranslation = isTranslation,
                isActive = true,
                overwrittenCount = overwritten.size,
                addedCount = added.size,
            ))

            PatchResult(
                success = true,
                message = "Installed '$patchName': ${overwritten.size} files overwritten, ${added.size} new files added",
                overwrittenFiles = overwritten,
                addedFiles = added,
                patchId = patchId,
            )
        }.getOrElse { error ->
            // Cleanup on failure
            patchDir.deleteRecursively()
            PatchResult(false, "Install failed: ${error.message}")
        }

        return result
    }

    /**
     * Revert a SINGLE patch.
     *
     * Enforces LIFO order: only the newest active patch can be reverted.
     * Returns an error message listing newer patches that must be reverted first.
     */
    fun revertPatch(storageName: String, targetPatchId: String): PatchResult {
        val gameDir = workspaceManager.gameDir(storageName)
        val config = loadConfig(storageName)
        val allPatches = config.patches.installedPatches
            .filter { it.isActive }
            .sortedBy { it.installedAtMillis }

        val targetIdx = allPatches.indexOfFirst { it.patchId == targetPatchId }
        if (targetIdx == -1) {
            return PatchResult(false, "Patch not found or already inactive")
        }

        // Check LIFO: any newer patches still active?
        val newerActive = allPatches.drop(targetIdx + 1)
        if (newerActive.isNotEmpty()) {
            val names = newerActive.joinToString(", ") { "'${it.name}'" }
            return PatchResult(
                false,
                "Cannot revert '${allPatches[targetIdx].name}' — $names must be reverted first"
            )
        }

        return doRevert(gameDir, config, listOf(allPatches[targetIdx]))
    }

    /**
     * Revert ALL patches for a game.
     * Restores every backup, deletes every added file, clears config.
     */
    fun revertAll(storageName: String): PatchResult {
        val gameDir = workspaceManager.gameDir(storageName)
        val config = loadConfig(storageName)
        val activePatches = config.patches.installedPatches
            .filter { it.isActive }
            .sortedBy { it.installedAtMillis }

        if (activePatches.isEmpty()) {
            return PatchResult(false, "No active patches to revert")
        }

        return doRevert(gameDir, config, activePatches)
    }

    /** List currently active patches for a game, sorted by install time. */
    fun listActivePatches(storageName: String): List<InstalledPatch> {
        val config = loadConfig(storageName)
        return config.patches.installedPatches
            .filter { it.isActive }
            .sortedBy { it.installedAtMillis }
    }

    /** List ALL patches (active + inactive) for a game, sorted by install time. */
    fun listAllPatches(storageName: String): List<InstalledPatch> {
        val config = loadConfig(storageName)
        return config.patches.installedPatches
            .sortedBy { it.installedAtMillis }
    }

    // ── Internal revert ─────────────────────────────────────────────

    private fun doRevert(
        gameDir: File,
        config: com.runestone.app.data.PerGameConfig,
        patches: List<InstalledPatch>,
    ): PatchResult {
        val originalDir = File(gameDir, "original")
        var restoredCount = 0
        var removedCount = 0
        val errors = mutableListOf<String>()

        // Revert in reverse order (newest first)
        for (patch in patches.reversed()) {
            val patchBackupDir = backupDir(gameDir, patch.patchId)
            val patchManifestFile = manifestFile(gameDir, patch.patchId)

            // Restore backed-up originals
            if (patchBackupDir.isDirectory) {
                patchBackupDir.walkTopDown().forEach { backupFile ->
                    if (backupFile.isFile) {
                        val relPath = backupFile.relativeTo(patchBackupDir).path
                        val target = File(originalDir, relPath)
                        try {
                            target.parentFile?.mkdirs()
                            backupFile.copyTo(target, overwrite = true)
                            restoredCount++
                        } catch (e: Exception) {
                            errors.add("Failed to restore $relPath: ${e.message}")
                        }
                    }
                }
            }

            // Remove added files
            if (patchManifestFile.isFile) {
                try {
                    val manifest = JSONObject(patchManifestFile.readText())
                    val addedArr = manifest.optJSONArray("addedFiles")
                    if (addedArr != null) {
                        for (i in 0 until addedArr.length()) {
                            val relPath = addedArr.optString(i, "")
                            if (relPath.isNotBlank()) {
                                val addedFile = File(originalDir, relPath)
                                if (addedFile.exists()) {
                                    addedFile.delete()
                                    // Try to clean up empty parent dirs
                                    var parent = addedFile.parentFile
                                    while (parent != null && parent != originalDir &&
                                        parent.isDirectory && parent.listFiles()?.isEmpty() == true
                                    ) {
                                        parent.delete()
                                        parent = parent.parentFile
                                    }
                                    removedCount++
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Failed to read manifest for ${patch.name}: ${e.message}")
                }
            }

            // Cleanup patch data (backups, manifest, zip)
            patchBackupDir.deleteRecursively()
            patchManifestFile.delete()
            zipArchiveFile(gameDir, patch.patchId).delete()
            // Clean empty parent dirs
            backupDir(gameDir, patch.patchId).parentFile?.let { cleanEmptyDirs(it) }
            manifestFile(gameDir, patch.patchId).parentFile?.let { cleanEmptyDirs(it) }
            zipArchiveFile(gameDir, patch.patchId).parentFile?.let { cleanEmptyDirs(it) }
        }

        // Remove all reverted patches from config
        val revertedIds = patches.map { it.patchId }.toSet()
        val updatedPatches = loadConfig(gameDir.name).patches.installedPatches
            .filter { it.patchId !in revertedIds }
        saveConfig(
            gameDir.name,
            config.copy(patches = PatchesSection(installedPatches = updatedPatches))
        )

        val names = patches.joinToString(", ") { "'${it.name}'" }
        val errorMsg = if (errors.isNotEmpty()) " (with ${errors.size} errors)" else ""
        return PatchResult(
            success = true,
            message = "Reverted $names: $restoredCount files restored, $removedCount files removed$errorMsg",
        )
    }

    // ── Config helpers ──────────────────────────────────────────────

    private fun loadConfig(storageName: String): com.runestone.app.data.PerGameConfig {
        return configService.loadPerGame(storageName)
    }

    private fun saveConfig(storageName: String, config: com.runestone.app.data.PerGameConfig) {
        configService.savePerGame(storageName, config)
    }

    private fun addPatchToConfig(storageName: String, patch: InstalledPatch) {
        val config = loadConfig(storageName)
        val updated = config.copy(
            patches = PatchesSection(
                installedPatches = config.patches.installedPatches + patch
            )
        )
        saveConfig(storageName, updated)
    }

    // ── Path helpers ────────────────────────────────────────────────

    private fun patchBaseDir(gameDir: File, patchId: String): File =
        File(gameDir, "patches/$patchId")

    private fun backupDir(gameDir: File, patchId: String): File =
        File(gameDir, "patches/backups/$patchId")

    private fun manifestFile(gameDir: File, patchId: String): File =
        File(gameDir, "patches/manifests/$patchId.json")

    private fun zipArchiveFile(gameDir: File, patchId: String): File =
        File(gameDir, "patches/zips/$patchId.zip")

    private fun cleanEmptyDirs(dir: File) {
        if (dir.isDirectory && dir.listFiles()?.isEmpty() == true) {
            dir.delete()
        }
    }
}
