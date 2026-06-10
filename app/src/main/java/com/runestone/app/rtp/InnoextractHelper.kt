/* Copyright (C) 2026 Gerson (KleirRampage45) */
package com.runestone.app.rtp

import android.util.Log
import java.io.File

/**
 * JNI bridge to the bundled innoextract native library.
 *
 * innoextract is compiled as a shared library with all dependencies
 * statically linked. The .so lives in jniLibs/arm64-v8a/ and is loaded
 * into the app process via System.loadLibrary.
 */
class InnoextractHelper {

    companion object {
        private const val TAG = "Innoextract"

        private var nativeLoaded = false

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

    val isReady: Boolean get() = nativeLoaded

    fun ensureInstalled() { ensureLoaded() }

    fun extract(setupExe: File, outputDir: File): Int {
        ensureLoaded()
        outputDir.mkdirs()
        Log.i(TAG, "Extracting ${setupExe.name} to ${outputDir.absolutePath}")
        return nativeExtract(outputDir.absolutePath, setupExe.absolutePath)
    }

    private external fun nativeExtract(outputDir: String, setupExe: String): Int
}
