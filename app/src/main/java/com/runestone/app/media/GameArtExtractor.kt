/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Extracts game artwork from game folders for library display.
 * Supports: MV/MZ icon.png, RGSS Game.exe icons, Ren'Py icon.png.
 */

package com.runestone.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Detects and extracts game artwork for display in the library.
 *
 * Detection order (first found wins):
 *   1. MV/MZ:  www/icon/icon.png
 *   2. RGSS:   icon from Game.exe (PE parser — fallback to .ico)
 *   3. Ren'Py: game/icon.png or gui/window_icon.png
 *   4. Generic: icon.png, Icon.png, icon.ico at game root
 *   5. Fallback: null (UI shows engine-colored card)
 */
object GameArtExtractor {

    private const val TAG = "GameArtExtractor"

    data class ArtResult(
        val bitmap: Bitmap?,
        val source: String,  // where it was found, or "none"
    )

    /** Extract game art, returning a max 512px-wide bitmap (memory-efficient). */
    fun extract(gameDir: File): ArtResult {
        val sources = listOf(
            // MV/MZ
            File(gameDir, "www/icon/icon.png"),
            File(gameDir, "www/icon.png"),
            // Ren'Py
            File(gameDir, "game/icon.png"),
            File(gameDir, "game/gui/window_icon.png"),
            // Generic root-level
            File(gameDir, "icon.png"),
            File(gameDir, "Icon.png"),
            File(gameDir, "icon.ico"),
            File(gameDir, "Icon.ico"),
            File(gameDir, "graphics/system/icon.png"),
            File(gameDir, "Graphics/System/Icon.png"),
        )

        for (file in sources) {
            if (file.exists() && file.isFile && file.canRead()) {
                val bitmap = decodeBitmap(file)
                if (bitmap != null) {
                    Log.d(TAG, "Found art: ${file.name} (${bitmap.width}x${bitmap.height})")
                    return ArtResult(bitmap, file.name)
                }
            }
        }

        // Check for Game.exe icon (RGSS games)
        val exeFile = File(gameDir, "Game.exe")
        if (exeFile.exists()) {
            // TODO: Extract icon from PE executable
            // For now, report as detected but no bitmap
            Log.d(TAG, "Game.exe found — PE icon extraction not yet implemented")
        }

        return ArtResult(null, "none")
    }

    /** Decode and downscale to max 512px wide for memory efficiency. */
    private fun decodeBitmap(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            // Calculate sample size to get ~256-512px
            val maxDim = 512
            var sampleSize = 1
            while (w / sampleSize > maxDim || h / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode ${file.name}", e)
            null
        }
    }
}
