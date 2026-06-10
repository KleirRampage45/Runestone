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
import android.content.DialogInterface
import android.content.Context
import android.util.Log
import java.io.File

/**
 * Orchestrates the full RTP install flow for one pack:
 *
 *   1. EULA dialog (first-time only, per pack)
 *   2. Download ZIP from archive.org
 *   3. Extract with top-level prefix stripping
 *   4. Mark as installed
 *
 * Each pack is installed once and shared across all games that need it.
 */
class RtpInstaller(val context: Context) {

    companion object {
        private const val TAG = "RtpInstall"
        private const val PREFS = "runestone_rtp"
        private const val TEMP_EXT = ".rtp_download"

        // archive.org terms summary shown in the EULA dialog
        private val ARCHIVE_EULA_TEXT = """
RPG Maker VX Ace RTP — Run-Time Package

This software is the property of KADOKAWA / Gotcha Gotcha Games.
Runestone downloads this pack from archive.org, a digital library.
You may only use this pack if you legally own RPG Maker VX Ace.

By continuing, you confirm:
• You own a legal copy of RPG Maker VX Ace
• This RTP will be used only with compatible games
• Install size: ~195 MB
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
    private val extractor = RtpExtractor()
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
            .setMessage(ARCHIVE_EULA_TEXT)
            .setPositiveButton("I Agree") { _: DialogInterface, _: Int ->
                acceptEula(pack)
                onAccepted()
            }
            .setNegativeButton("Cancel") { _: DialogInterface, _: Int -> onRejected() }
            .setCancelable(false)
            .show()
    }

    /**
     * Install a pack: download ZIP, extract, mark installed.
     * Reports progress via [callback] on the calling thread's handler.
     */
    fun install(pack: RtpPack, callback: InstallCallback) {
        val tempFile = File(manager.rtpRoot, "${pack.slug}$TEMP_EXT")
        val targetDir = manager.packDir(pack)

        // Clean any partial state
        if (tempFile.exists()) tempFile.delete()
        if (!targetDir.exists()) targetDir.mkdirs()
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

                // ── Step 2: Extract ──
                callback.onProgress(InstallProgress(state = InstallState.EXTRACTING))
                extractor.extract(
                    zipFile = file,
                    outputDir = targetDir.absolutePath,
                    prefixToStrip = pack.zipPrefix,
                    object : RtpExtractor.Callback {
                        override fun onProgress(filesExtracted: Int, currentFile: String) {
                            callback.onProgress(InstallProgress(
                                state = InstallState.EXTRACTING,
                                filesExtracted = filesExtracted,
                                currentFile = currentFile,
                            ))
                        }

                        override fun onComplete(fileCount: Int) {
                            // Clean up temp file
                            file.delete()

                            // ── Step 3: Verify + complete ──
                            if (manager.isInstalled(pack)) {
                                Log.i(TAG, "${pack.slug} installed successfully ($fileCount files)")
                                callback.onProgress(InstallProgress(
                                    state = InstallState.COMPLETED,
                                    filesExtracted = fileCount,
                                ))
                                callback.onComplete()
                            } else {
                                val err = "Extraction completed but marker file not found (${pack.markerFile})"
                                Log.e(TAG, err)
                                callback.onError(err)
                            }
                        }

                        override fun onError(message: String) {
                            file.delete()
                            callback.onError("Extraction failed: $message")
                        }
                    },
                )
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
        extractor.cancel()
    }

    /**
     * Get the absolute RTP directory path for a pack, for use in mkxp.json.
     */
    fun getRtpPath(pack: RtpPack): String = manager.packDir(pack).absolutePath
}
