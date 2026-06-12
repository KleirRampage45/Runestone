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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Extracts an RTP ZIP archive into the RTP pack's target directory.
 * Hardened against:
 *   - path traversal (`../` segments blocked)
 *   - zip bombs (per-entry and aggregate size caps)
 *   - corrupt archives (propagates errors instead of silently truncating)
 */
class RtpExtractor {

    companion object {
        private const val TAG = "RtpExtractor"
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_ENTRY_BYTES = 2L * 1024 * 1024 * 1024   // 2 GB per file
        private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024   // 4 GB aggregate
    }

    sealed class Result {
        data class Success(val extractedBytes: Long, val fileCount: Int) : Result()
        data class Failure(val message: String, val cause: Throwable? = null) : Result()
    }

    /**
     * Extracts [archive] into [targetDir]. If [archive] contains a single
     * top-level folder (e.g. `RTP100/...`), its contents are flattened
     * into [targetDir] so mkxp-z sees `targetDir/Graphics`, `targetDir/Audio`.
     */
    fun extract(archive: File, targetDir: File): Result {
        if (!archive.isFile) return Result.Failure("Archive not found: ${archive.absolutePath}")
        targetDir.mkdirs()

        // Check if this is an Inno Setup installer (contains Setup.exe)
        if (peekForSetupExe(archive)) {
            return extractInnoSetup(archive, targetDir)
        }

        var totalBytes = 0L
        var fileCount = 0
        var topLevelDir: String? = null

        try {
            ZipInputStream(FileInputStream(archive).buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/').trim('/')
                    if (name.isEmpty() || name.contains("..") || name.startsWith("/")) {
                        Log.w(TAG, "Skipping unsafe entry: ${entry.name}")
                        entry = zis.nextEntry
                        continue
                    }

                    if (topLevelDir == null && !entry.isDirectory) {
                        topLevelDir = name.substringBefore('/', "")
                    }

                    val outName = if (topLevelDir != null && name.startsWith("$topLevelDir/")) {
                        name.removePrefix("$topLevelDir/")
                    } else if (topLevelDir != null && name == topLevelDir) {
                        // skip the top-level dir entry itself
                        entry = zis.nextEntry
                        continue
                    } else {
                        name
                    }
                    if (outName.isEmpty()) {
                        entry = zis.nextEntry
                        continue
                    }

                    val outFile = File(targetDir, outName)
                    if (!outFile.canonicalFile.toPath().startsWith(targetDir.canonicalFile.toPath())) {
                        Log.w(TAG, "Skipping path-traversal entry: ${entry.name}")
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                val n = zis.read(buffer)
                                if (n <= 0) break
                                entryBytes += n
                                if (entryBytes > MAX_ENTRY_BYTES) {
                                    fos.close()
                                    outFile.delete()
                                    return Result.Failure("Entry too large: ${entry.name}")
                                }
                                totalBytes += n
                                if (totalBytes > MAX_TOTAL_BYTES) {
                                    fos.close()
                                    outFile.delete()
                                    return Result.Failure("Archive exceeds ${MAX_TOTAL_BYTES} bytes after extraction")
                                }
                                fos.write(buffer, 0, n)
                            }
                        }
                        fileCount++
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (t: Throwable) {
            return Result.Failure("Extraction failed: ${t.message}", t)
        }

        if (fileCount == 0) {
            return Result.Failure("Archive contained no files")
        }

        Log.i(TAG, "Extracted $fileCount files (${totalBytes} bytes) to ${targetDir.absolutePath}")
        return Result.Success(totalBytes, fileCount)
    }

    // ── Inno Setup installer extraction ──

    private fun peekForSetupExe(archive: File): Boolean {
        return try {
            ZipInputStream(FileInputStream(archive).buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.equals("Setup.exe", ignoreCase = true) ||
                        name.endsWith("/Setup.exe", ignoreCase = true)) {
                        return@use true
                    }
                    entry = zis.nextEntry
                }
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to peek archive", e)
            false
        }
    }

    private fun extractInnoSetup(archive: File, targetDir: File): Result {
        val workDir = File(targetDir.parentFile, "${targetDir.name}_work")
        try {
            workDir.mkdirs()

            // Step 1: Extract ZIP to get Setup.exe
            val zipResult = extractZipToDir(archive, workDir)
            if (zipResult is Result.Failure) return zipResult

            // Step 2: Find Setup.exe
            val setupExe = findSetupExe(workDir)
            if (setupExe == null) {
                return Result.Failure("Setup.exe not found after ZIP extraction")
            }

            // Step 3: Run innoextract
            val innoDir = File(workDir, "inno_output")
            val exitCode = try {
                InnoextractHelper().extract(setupExe, innoDir)
            } catch (e: Exception) {
                return Result.Failure("innoextract failed: ${e.message}", e)
            }
            if (exitCode != 0) {
                return Result.Failure("innoextract exited with code $exitCode")
            }

            // Step 4: Move app/* to targetDir
            val appDir = File(innoDir, "app")
            if (!appDir.exists()) {
                return Result.Failure("innoextract output missing 'app/' directory")
            }
            val fileCount = appDir.walkTopDown().filter { it.isFile }.count()
            val totalBytes = appDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            appDir.copyRecursively(targetDir, overwrite = true)

            Log.i(TAG, "Extracted $fileCount files ($totalBytes bytes) via innoextract")
            return Result.Success(totalBytes, fileCount)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun extractZipToDir(archive: File, targetDir: File): Result {
        return try {
            ZipInputStream(FileInputStream(archive).buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val outFile = File(targetDir, name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            Result.Success(0, 0) // dummy — caller only checks for Failure
        } catch (t: Throwable) {
            Result.Failure("ZIP extraction failed: ${t.message}", t)
        }
    }

    private fun findSetupExe(dir: File): File? {
        val queue = ArrayDeque(listOf(dir))
        while (queue.isNotEmpty()) {
            val files = queue.removeFirst().listFiles() ?: continue
            for (f in files) {
                if (f.isDirectory) queue.addLast(f)
                else if (f.name.equals("Setup.exe", ignoreCase = true)) return f
            }
        }
        return null
    }
}
