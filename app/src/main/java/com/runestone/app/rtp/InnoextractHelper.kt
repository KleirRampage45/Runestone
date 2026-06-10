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

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages the bundled innoextract native binary and its shared libraries.
 *
 * innoextract is a tool that unpacks installers created by Inno Setup,
 * licensed under the ZLIB license. It's bundled as an ARM64 binary
 * compiled against Android bionic, with RPATH set to `$ORIGIN/../lib`.
 *
 * The binary + .so files live under [ASSET_BASE] in the APK assets and
 * are extracted to internal storage on first use.
 */
class InnoextractHelper(private val context: Context) {

    companion object {
        private const val TAG = "Innoextract"
        private const val ASSET_BASE = "innoextract"
        private const val DIR_NAME = "innoextract"
        private const val BINARY_NAME = "innoextract"
        private const val LIB_DIR = "lib"

        /** Libraries the binary needs (in order — greedy regex). */
        private val NEEDED_LIBS = listOf(
            "libboost_filesystem.so",
            "libboost_iostreams.so",
            "libboost_program_options.so",
            "libiconv.so",
            "liblzma.so",
            "liblzma.so.5",
        )
    }

    /** Directory where the binary + libs are (or will be) extracted. */
    private val baseDir: File get() = File(context.filesDir, DIR_NAME)

    /** The innoextract binary. */
    val binaryFile: File get() = File(baseDir, BINARY_NAME)

    /** Whether the binary is installed and executable. */
    val isReady: Boolean
        get() = binaryFile.exists() && binaryFile.canExecute()

    /**
     * Ensure the binary and libraries are extracted from assets.
     * Safe to call multiple times — no-ops if already installed.
     *
     * @throws RuntimeException if asset extraction fails
     */
    fun ensureInstalled() {
        if (isReady) return

        val dir = baseDir
        val libDir = File(dir, LIB_DIR)
        dir.mkdirs()
        libDir.mkdirs()

        // Extract the binary
        val bin = binaryFile
        extractAsset("$ASSET_BASE/$BINARY_NAME", bin)
        bin.setExecutable(true)

        // Extract libraries
        for (libName in NEEDED_LIBS) {
            val libFile = File(libDir, libName)
            if (!libFile.exists()) {
                extractAsset("$ASSET_BASE/$LIB_DIR/$libName", libFile)
            }
        }

        if (!isReady) {
            throw RuntimeException("Failed to install innoextract binary")
        }
        Log.i(TAG, "innoextract installed at ${bin.absolutePath}")
    }

    /**
     * Run innoextract on [setupExe] and extract assets to [outputDir].
     *
     * The [setupExe] must be an Inno Setup installer exe.
     * [setupExe]'s parent directory must also contain [Setup-1.bin].
     * Files are extracted under `[outputDir]/app/...`.
     *
     * @return the exit code (0 = success)
     */
    fun extract(setupExe: File, outputDir: File): Int {
        ensureInstalled()

        outputDir.mkdirs()

        val pb = ProcessBuilder(
            binaryFile.absolutePath,
            "-d", outputDir.absolutePath,
            setupExe.absolutePath,
        )
        pb.environment()["LD_LIBRARY_PATH"] =
            File(baseDir, LIB_DIR).absolutePath
        pb.directory(outputDir)

        // Merge stderr into stdout for logging
        pb.redirectErrorStream(true)

        Log.i(TAG, "Running: ${pb.command().joinToString(" ")}")

        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (output.isNotBlank()) {
            Log.i(TAG, "innoextract output:\n$output")
        }
        Log.i(TAG, "innoextract exited with code $exitCode")

        return exitCode
    }

    /**
     * Extract a single file from the APK assets to [dest].
     */
    private fun extractAsset(assetPath: String, dest: File) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Extracted asset $assetPath -> ${dest.absolutePath} " +
                "(${dest.length()} bytes)")
    }
}
