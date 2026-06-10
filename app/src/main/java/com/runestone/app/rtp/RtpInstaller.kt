/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.rtp

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Orchestrates the full RTP install flow for one pack:
 *
 *   1. EULA dialog (first-time only, per pack)
 *   2. Download official installer ZIP from the vendor
 *   3. Extract the ZIP → get Setup.exe + Setup-1.bin
 *   4. Run bundled innoextract on Setup.exe → get raw assets
 *   5. Move assets to the install directory
 *   6. Mark as installed
 *
 * Each pack is installed once and shared across all games that need it.
 */
class RtpInstaller(val context: Context) {

    companion object {
        private const val TAG = "RtpInstall"
        private const val PREFS = "runestone_rtp"
        private const val TEMP_EXT = ".rtp_download"

        private val EULA_TEXT = """
RPG Maker VX Ace RTP — Run-Time Package

This software is the property of KADOKAWA / Gotcha Gotcha Games.
Runestone downloads this pack from a public archive mirror.
You may only use this pack if you legally own RPG Maker VX Ace.

By continuing, you confirm:
- You own a legal copy of RPG Maker VX Ace
- This RTP will be used only with compatible games
- Install size: ~195 MB
        """.trimIndent()
    }

    enum class InstallState { IDLE, DOWNLOADING, EXTRACTING, COMPLETED, FAILED }

    data class InstallProgress(
        val state: InstallState,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0,
        val filesExtracted: Int = 0,
        val currentFile: String = "",
        val error: String? = null,
    )

    interface InstallCallback {
        fun onProgress(progress: InstallProgress)
        fun onComplete()
        fun onError(message: String)
    }

    private val manager = RtpManager(context)
    private val downloader = RtpDownloader()
    private val innoextract = InnoextractHelper()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether the user has accepted the EULA for a given pack. */
    fun hasAcceptedEula(pack: RtpPack): Boolean =
        prefs.getBoolean("eula_${pack.slug}", false)

    /** Mark the EULA as accepted for a pack. */
    fun acceptEula(pack: RtpPack) {
        prefs.edit().putBoolean("eula_${pack.slug}", true).apply()
    }

    /** Whether a pack is fully installed and ready. */
    fun isInstalled(pack: RtpPack): Boolean = manager.isInstalled(pack)

    /**
     * Show the EULA dialog. Calls [onAccepted] if user accepts, [onRejected] if they decline.
     */
    fun showEulaDialog(pack: RtpPack, onAccepted: () -> Unit, onRejected: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(pack.label)
            .setMessage(EULA_TEXT)
            .setPositiveButton("I Agree") { _: DialogInterface, _: Int ->
                acceptEula(pack)
                onAccepted()
            }
            .setNegativeButton("Cancel") { _: DialogInterface, _: Int -> onRejected() }
            .setCancelable(false)
            .show()
    }

    /**
     * Install a pack: download, extract, move, mark installed.
     * Reports progress via [callback].
     */
    fun install(pack: RtpPack, callback: InstallCallback) {
        val tempFile = File(manager.rtpRoot, "${pack.slug}$TEMP_EXT")
        val targetDir = manager.packDir(pack)
        val workDir = File(manager.rtpRoot, "${pack.slug}_work") // temp working dir

        // Clean any partial state
        if (tempFile.exists()) tempFile.delete()
        if (workDir.exists()) workDir.deleteRecursively()
        workDir.mkdirs()
        manager.ensureNoMedia(manager.rtpRoot)

        // ── Step 1: Download ──
        callback.onProgress(InstallProgress(state = InstallState.DOWNLOADING))
        Log.i(TAG, "Downloading ${pack.slug} from ${pack.zipUrl}")

        downloader.download(pack.zipUrl, tempFile, object : RtpDownloader.Callback {
            override fun onProgress(bytesDownloaded: Long, totalBytes: Long) {
                callback.onProgress(InstallProgress(
                    state = InstallState.DOWNLOADING,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                ))
            }

            override fun onComplete(file: File) {
                Log.i(TAG, "Download complete: ${file.length()} bytes")

                // ── Step 2: Extract ZIP to working dir ──
                callback.onProgress(InstallProgress(
                    state = InstallState.EXTRACTING,
                    currentFile = "Unpacking installer archive…",
                ))
                try {
                    unzipToDir(file, workDir)
                } catch (e: Exception) {
                    file.delete()
                    workDir.deleteRecursively()
                    callback.onError("Failed to unpack installer ZIP: ${e.message}")
                    return
                }

                // ── Step 3: Find Setup.exe ──
                val setupExe = findSetupExe(workDir)
                if (setupExe == null) {
                    file.delete()
                    workDir.deleteRecursively()
                    callback.onError("Could not find Setup.exe in the downloaded archive")
                    return
                }
                Log.i(TAG, "Found Setup.exe at ${setupExe.absolutePath}")

                // ── Step 4: Run innoextract ──
                callback.onProgress(InstallProgress(
                    state = InstallState.EXTRACTING,
                    currentFile = "Extracting RTP assets…",
                ))
                val extractDir = File(workDir, "inno_output")
                val exitCode = try {
                    innoextract.extract(setupExe, extractDir)
                } catch (e: Exception) {
                    file.delete()
                    workDir.deleteRecursively()
                    callback.onError("innoextract failed: ${e.message}")
                    return
                }

                if (exitCode != 0) {
                    file.delete()
                    workDir.deleteRecursively()
                    callback.onError("innoextract exited with code $exitCode")
                    return
                }

                // ── Step 5: Move app/* to target RTP dir ──
                val appDir = File(extractDir, "app")
                if (!appDir.exists()) {
                    file.delete()
                    workDir.deleteRecursively()
                    callback.onError("innoextract output missing 'app/' directory")
                    return
                }

                try {
                    val movedCount = moveContents(appDir, targetDir)
                    Log.i(TAG, "Moved $movedCount files to ${targetDir.absolutePath}")
                } catch (e: Exception) {
                    file.delete()
                    workDir.deleteRecursively()
                    callback.onError("Failed to move RTP assets: ${e.message}")
                    return
                }

                // ── Clean up temp files ──
                file.delete()
                workDir.deleteRecursively()

                // ── Step 6: Verify + complete ──
                if (manager.isInstalled(pack)) {
                    Log.i(TAG, "${pack.slug} installed successfully")
                    callback.onProgress(InstallProgress(state = InstallState.COMPLETED))
                    callback.onComplete()
                } else {
                    val err = "Extraction completed but marker file not found (${pack.markerFile})"
                    Log.e(TAG, err)
                    callback.onError(err)
                }
            }

            override fun onError(message: String) {
                callback.onError("Download failed: $message")
            }
        })
    }

    /**
     * Cancel an in-progress install.
     */
    fun cancel() {
        downloader.cancel()
    }

    /**
     * Get the absolute RTP directory path for a pack, for use in mkxp.json.
     */
    fun getRtpPath(pack: RtpPack): String = manager.packDir(pack).absolutePath

    // ── Private helpers ──

    /**
     * Extract a ZIP file to [outputDir] without stripping any prefix.
     */
    private fun unzipToDir(zipFile: File, outputDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace("\\", "/")
                if (name.startsWith("__MACOSX") || name == ".DS_Store" || name == "thumbs.db") {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                val outFile = File(outputDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Recursively copy all files from [sourceDir] into [targetDir],
     * returning the number of files moved.
     */
    private fun moveContents(sourceDir: File, targetDir: File): Int {
        var count = 0
        targetDir.mkdirs()
        sourceDir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                count += moveContents(child, File(targetDir, child.name))
            } else {
                child.copyTo(File(targetDir, child.name), overwrite = true)
                child.delete()
                count++
            }
        }
        return count
    }

    /**
     * Search [dir] recursively for `Setup.exe` (case-insensitive) and return it.
     */
    private fun findSetupExe(dir: File): File? {
        val queue = ArrayDeque(listOf(dir))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val files = current.listFiles() ?: continue
            for (f in files) {
                if (f.isDirectory) {
                    queue.addLast(f)
                } else if (f.name.equals("Setup.exe", ignoreCase = true)) {
                    return f
                }
            }
        }
        return null
    }
}
