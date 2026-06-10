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

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Extracts an RTP ZIP archive to the target install directory.
 *
 * Handles top-level prefix stripping (e.g. "RTP100/") so the assets end up
 * directly under `Graphics/`, `Audio/`, etc. rather than nested inside a
 * versioned folder.
 */
class RtpExtractor {

    companion object {
        private const val TAG = "RtpExtract"
        private const val BUFFER_SIZE = 8192
        private const val MAX_FILES = 20_000
        private const val MAX_BYTES = 1L * 1024 * 1024 * 1024 // 1 GiB
    }

    /** Callback for extraction progress. */
    interface Callback {
        fun onProgress(filesExtracted: Int, currentFile: String)
        fun onComplete(fileCount: Int)
        fun onError(message: String)
    }

    private var cancelFlag = false

    fun cancel() {
        cancelFlag = true
    }

    /**
     * Extract [zipFile] into [outputDir], stripping [prefixToStrip] from entry paths.
     * Runs on a background thread.
     */
    fun extract(zipFile: File, outputDir: String, prefixToStrip: String, callback: Callback) {
        Thread {
            try {
                doExtract(zipFile, outputDir, prefixToStrip, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
                callback.onError(e.message ?: "Extraction failed")
            }
        }.start()
    }

    private fun doExtract(
        zipFile: File,
        outputDir: String,
        prefixToStrip: String,
        callback: Callback,
    ) {
        val outDir = File(outputDir)
        if (!outDir.exists()) outDir.mkdirs()

        var extracted = 0
        var totalBytes = 0L

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry

            while (entry != null) {
                if (cancelFlag) {
                    Log.i(TAG, "Extraction cancelled")
                    return@use
                }

                val rawName = entry.name
                val stripped = stripPrefix(rawName, prefixToStrip)

                if (stripped == null || shouldSkip(stripped)) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                require(extracted < MAX_FILES) { "Archive contains more than $MAX_FILES entries" }

                val outFile = File(outDir, stripped)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            totalBytes += len
                            require(totalBytes <= MAX_BYTES) { "Archive exceeds max size" }
                            fos.write(buffer, 0, len)
                        }
                    }
                }

                extracted++
                callback.onProgress(extracted, stripped)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (cancelFlag) return

        require(extracted > 0) { "Archive contained no files after stripping prefix" }

        Log.i(TAG, "Extraction complete: $extracted files")
        callback.onComplete(extracted)
    }

    /**
     * Remove [prefix] from the start of [path] if present.
     * Returns null if the path IS the prefix (a directory entry for the top folder).
     */
    private fun stripPrefix(path: String, prefix: String): String? {
        val normalized = path.replace("\\", "/")
        if (!normalized.startsWith(prefix)) return normalized
        val stripped = normalized.removePrefix(prefix)
        if (stripped.isEmpty()) return null // the prefix dir itself
        return stripped
    }

    private fun shouldSkip(name: String): Boolean {
        val base = name.lowercase()
        return base.startsWith("__macosx") ||
            base == ".ds_store" ||
            base == "thumbs.db"
    }
}
