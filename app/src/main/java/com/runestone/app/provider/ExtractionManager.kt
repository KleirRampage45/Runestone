/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.provider

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ExtractionManager(private val context: Context) {

    companion object {
        private const val TAG = "EXTRACT"
        private const val BUFFER_SIZE = 65536
        private const val MAX_FILES = 50_000
        private const val MAX_EXTRACTED_BYTES = 16L * 1024 * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 4L * 1024 * 1024 * 1024
        private val SKIP_PREFIXES = listOf("__MACOSX", ".DS_Store", "Thumbs.db", ".git")
    }

    data class ExtractionProgress(
        val filesExtracted: Int,
        val totalFiles: Int,
        val currentFile: String,
    )

    data class ExtractionResult(
        val outputDir: File,
        val gameRoot: File,
        val fileCount: Int,
    )

    interface ExtractionCallback {
        fun onProgress(progress: ExtractionProgress)
        fun onComplete(result: ExtractionResult)
        fun onError(message: String)
    }

    fun extract(zipPath: String, outputDir: File, callback: ExtractionCallback) {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            callback.onError("ZIP file not found: $zipPath")
            return
        }

        if (!outputDir.exists()) outputDir.mkdirs()
        ensureNoMedia(outputDir)

        Thread {
            try {
                doExtract(zipFile, outputDir, callback)
            } catch (e: Exception) {
                Log.e(TAG, "Extraction failed", e)
                callback.onError(e.message ?: "Extraction failed")
            }
        }.start()
    }

    private fun doExtract(zipFile: File, outputDir: File, callback: ExtractionCallback) {
        ensureNoMedia(outputDir)
        var extracted = 0
        var total = 0
        var extractedBytes = 0L

        val charset = selectZipCharset(zipFile)
        Log.i(TAG, "Using ZIP filename charset: ${charset.name()}")

        // Pass 1: count entries
        ZipInputStream(zipFile.inputStream().buffered(), charset).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                total++
                require(total <= MAX_FILES) { "Archive contains more than $MAX_FILES entries" }
                entry // touch
            }
        }
        require(total > 0) { "Archive contains no files" }

        // Pass 2: extract
        ZipInputStream(zipFile.inputStream().buffered(), charset).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name

                if (shouldSkip(name)) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                val outFile = sanitizePath(outputDir, name)
                if (outFile == null) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        var entryBytes = 0L
                        while (zis.read(buffer).also { len = it } > 0) {
                            entryBytes += len
                            extractedBytes += len
                            require(entryBytes <= MAX_ENTRY_BYTES) { "Archive entry is too large: $name" }
                            require(extractedBytes <= MAX_EXTRACTED_BYTES) { "Archive expands beyond the allowed size" }
                            fos.write(buffer, 0, len)
                        }
                    }
                }

                extracted++
                callback.onProgress(ExtractionProgress(
                    filesExtracted = extracted,
                    totalFiles = total,
                    currentFile = name,
                ))

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        require(extracted > 0) { "Archive did not extract any files" }

        val gameRoot = detectGameRoot(outputDir)
        Log.i(TAG, "Extraction complete: $extracted files, gameRoot=${gameRoot.absolutePath}")

        callback.onComplete(ExtractionResult(
            outputDir = outputDir,
            gameRoot = gameRoot,
            fileCount = extracted,
        ))
    }

    private fun shouldSkip(name: String): Boolean {
        val base = name.substringBefore("/").substringBefore("\\")
        return SKIP_PREFIXES.any { base.equals(it, ignoreCase = true) }
    }

    private fun sanitizePath(outputDir: File, entryName: String): File? {
        val normalized = entryName.replace("\\", "/")
        if (normalized.startsWith("/") || normalized.split("/").any { it == ".." }) {
            Log.w(TAG, "Path traversal detected: $entryName")
            return null
        }
        val parts = normalized.split("/").filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null

        var current = outputDir
        for (part in parts) {
            current = File(current, part)
        }

        if (!current.canonicalFile.toPath().startsWith(outputDir.canonicalFile.toPath())) {
            Log.w(TAG, "Path traversal detected: $entryName")
            return null
        }

        return current
    }

    private fun detectGameRoot(dir: File): File {
        findGameRoot(dir, maxDepth = 4)?.let { return it }
        return dir
    }

    private fun findGameRoot(dir: File, maxDepth: Int): File? {
        val directChildren = dir.listFiles() ?: return null

        if (hasGameData(directChildren)) {
            return dir
        }

        if (maxDepth <= 0) return null

        directChildren
            .filter { it.isDirectory }
            .sortedBy { it.name.length }
            .forEach { child ->
                findGameRoot(child, maxDepth - 1)?.let { return it }
            }

        return null
    }

    private fun hasGameData(children: Array<File>): Boolean {
        return children.any { child ->
            val name = child.name.lowercase()
            name == "www" ||
                name == "data" ||
                name == "game" ||
                name == "game.ini" ||
                name == "game.exe" ||
                name.endsWith(".exe") ||
                name == "rpg_rt.ldb" ||
                name == "rpg_rt.ini" ||
                name == "index.html" ||
                name == "project.json"
        }
    }

    private fun ensureNoMedia(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            if (dir.isDirectory) {
                val marker = File(dir, ".nomedia")
                if (!marker.exists()) marker.writeText("")
            }
        }
    }

    private fun selectZipCharset(zipFile: File): Charset {
        val candidates = listOf(
            Charsets.UTF_8,
            Charset.forName("windows-31j"),
            Charset.forName("Shift_JIS"),
            Charset.forName("ISO-8859-1"),
        ).distinctBy { it.name() }

        return candidates
            .mapNotNull { charset ->
                runCatching {
                    val names = readZipNames(zipFile, charset)
                    charset to scoreZipNames(names)
                }.getOrNull()
            }
            .maxByOrNull { it.second }
            ?.first
            ?: Charsets.UTF_8
    }

    private fun readZipNames(zipFile: File, charset: Charset): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(zipFile.inputStream().buffered(), charset).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                names += entry.name
                zis.closeEntry()
                if (names.size >= 300) break
            }
        }
        return names
    }

    private fun scoreZipNames(names: List<String>): Int {
        var score = 0
        for (name in names) {
            var hasJapanese = false
            var badControls = 0
            for (char in name) {
                when {
                    char.code in 0x80..0x9F -> badControls += 6
                    char == '\uFFFD' -> badControls += 10
                    isJapanese(char) -> hasJapanese = true
                }
            }
            score -= badControls
            if (hasJapanese) score += 8
            if (name.contains('/') || name.contains('\\')) score += 1
            if (name.any { it.isLetterOrDigit() }) score += 1
        }
        return score
    }

    private fun isJapanese(char: Char): Boolean {
        return char in '\u3040'..'\u30FF' ||
            char in '\u3400'..'\u4DBF' ||
            char in '\u4E00'..'\u9FFF' ||
            char in '\uF900'..'\uFAFF'
    }
}
