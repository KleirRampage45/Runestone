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

/**
 * JNI bridge to the bundled innoextract native library.
 *
 * innoextract is compiled as a shared library (.so) and linked
 * together with its dependencies (Boost, liblzma, libiconv). All
 * .so files live in jniLibs/arm64-v8a/ and are loaded into the
 * app process via System.loadLibrary — no exec() needed, which
 * means it works on ALL Android devices regardless of OEM noexec
 * restrictions (OPPO, Huawei, etc.).
 *
 * The library is licensed under the ZLIB license.
 */
class InnoextractHelper {

    companion object {
        private const val TAG = "Innoextract"

        /** Whether the native library has been successfully loaded. */
        private var nativeLoaded = false

        /** Try to load the native library once. */
        fun ensureLoaded() {
            if (nativeLoaded) return
            try {
                System.loadLibrary("innoextract_jni")
                nativeLoaded = true
                Log.i(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
                throw RuntimeException("innoextract JNI library not available: ${e.message}")
            }
        }
    }

    /** Whether the native library is loaded and ready. */
    val isReady: Boolean get() = nativeLoaded

    /**
     * Ensure the native library is loaded. Safe to call multiple times.
     */
    fun ensureInstalled() {
        ensureLoaded()
    }

    /**
     * Run innoextract on [setupExe] and extract assets to [outputDir].
     *
     * @return 0 on success, non-zero on failure
     */
    fun extract(setupExe: File, outputDir: File): Int {
        ensureLoaded()
        outputDir.mkdirs()
        Log.i(TAG, "Extracting ${setupExe.name} to ${outputDir.absolutePath}")
        return nativeExtract(outputDir.absolutePath, setupExe.absolutePath)
    }

    // ── Native JNI method ──

    /**
     * Native implementation of innoextract extraction.
     *
     * @param outputDir absolute path to write extracted files into
     * @param setupExe  absolute path to the Inno Setup .exe installer
     * @return 0 on success, non-zero on failure
     */
    private external fun nativeExtract(outputDir: String, setupExe: String): Int
}
