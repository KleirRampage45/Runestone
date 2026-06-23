package com.runestone.app.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

object CoverExtractor {
    private const val TAG = "CoverExtractor"
    private const val COVERS_DIR = "game_covers"

    fun extractFallbackCover(context: Context, storageName: String, gameDir: File): String? {
        val coverDir = File(context.filesDir, COVERS_DIR).apply { mkdirs() }
        val fallbackFile = File(coverDir, "${storageName}_fallback.jpg")
        if (fallbackFile.exists()) return fallbackFile.absolutePath

        val bitmap = extractFromGameFiles(gameDir) ?: return null

        try {
            fallbackFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            Log.i(TAG, "Fallback cover saved: ${fallbackFile.absolutePath}")
            return fallbackFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save fallback cover", e)
            bitmap.recycle()
            return null
        }
    }

    private fun extractFromGameFiles(gameDir: File): Bitmap? {
        val wwwDir = File(gameDir, "www")

        // MV/MZ: www/img/titles1/ or www/img/titles2/
        if (wwwDir.isDirectory) {
            val titlesDirs = listOf(
                File(wwwDir, "img/titles1"),
                File(wwwDir, "img/titles2"),
                File(wwwDir, "img/titles"),
                File(wwwDir, "img/system"),
            )
            for (dir in titlesDirs) {
                if (!dir.isDirectory) continue
                val files = dir.listFiles { f -> f.extension in listOf("png", "jpg", "jpeg", "rpgmvp") }
                    ?: continue
                val best = files.minByOrNull { it.name.length } ?: continue
                if (best.extension == "rpgmvp") {
                    val decoded = decodeRpgmvp(best) ?: continue
                    return scaleToThumbnail(decoded)
                }
                val bmp = BitmapFactory.decodeFile(best.absolutePath) ?: continue
                return scaleToThumbnail(bmp)
            }
        }

        // RGSS (XP/VX/VX Ace): look for titles in Game.ini or common file names
        val rgssCandidates = listOf(
            File(gameDir, "Title.png"),
            File(gameDir, "title.png"),
            File(gameDir, "Title.jpg"),
            File(gameDir, "title.jpg"),
            File(gameDir, "GameOver.png"),
        )
        for (f in rgssCandidates) {
            if (f.isFile) {
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: continue
                return scaleToThumbnail(bmp)
            }
        }

        return null
    }

    private fun decodeRpgmvp(file: File): Bitmap? {
        return try {
            val data = file.readBytes()
            val header = "RPGMV\u0000".toByteArray()
            val headerOffset = data.indexOfFirst { it == header[0] }
            if (headerOffset < 0) return null
            val imgStart = headerOffset + header.size + 8
            if (imgStart >= data.size) return null
            BitmapFactory.decodeByteArray(data, imgStart, data.size - imgStart)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode RPGMVP: ${file.name}", e)
            null
        }
    }

    private fun scaleToThumbnail(bitmap: Bitmap): Bitmap {
        val maxSize = 480
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val scale = maxOf(w, h).toFloat() / maxSize
        val newW = (w / scale).toInt().coerceAtLeast(1)
        val newH = (h / scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
