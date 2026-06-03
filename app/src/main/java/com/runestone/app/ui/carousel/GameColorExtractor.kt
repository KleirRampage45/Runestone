package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.palette.graphics.Palette
import com.runestone.app.data.EngineType
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class GameColorExtractor(private val context: Context) {

    private val cache = ConcurrentHashMap<String, Int>()

    fun getColor(gameTitle: String, coverUrl: String?, engineType: EngineType, onColor: (Int) -> Unit) {
        val cached = cache[gameTitle]
        if (cached != null) {
            onColor(cached)
            return
        }

        if (coverUrl.isNullOrBlank()) {
            val fallback = metadataColor(gameTitle, engineType)
            cache[gameTitle] = fallback
            onColor(fallback)
            return
        }

        Thread {
            try {
                val bitmap = if (coverUrl.startsWith("local:")) {
                    BitmapFactory.decodeFile(coverUrl.removePrefix("local:"))
                } else {
                    BitmapFactory.decodeStream(URL(coverUrl).openStream())
                }
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val color = palette.getVibrantColor(palette.getMutedColor(metadataColor(gameTitle, engineType)))
                    cache[gameTitle] = color
                    bitmap.recycle()
                    Handler(Looper.getMainLooper()).post { onColor(color) }
                } else {
                    val fallback = metadataColor(gameTitle, engineType)
                    cache[gameTitle] = fallback
                    Handler(Looper.getMainLooper()).post { onColor(fallback) }
                }
            } catch (e: Exception) {
                val fallback = metadataColor(gameTitle, engineType)
                cache[gameTitle] = fallback
                Handler(Looper.getMainLooper()).post { onColor(fallback) }
            }
        }.start()
    }

    private fun metadataColor(gameTitle: String, engineType: EngineType): Int {
        val base = engineColor(engineType)
        val r = android.graphics.Color.red(base)
        val g = android.graphics.Color.green(base)
        val b = android.graphics.Color.blue(base)
        val hash = gameTitle.hashCode()
        val dr = ((hash and 0xFF) - 128) / 6
        val dg = (((hash shr 8) and 0xFF) - 128) / 6
        val db = (((hash shr 16) and 0xFF) - 128) / 6
        return android.graphics.Color.argb(
            android.graphics.Color.alpha(base),
            (r + dr).coerceIn(0, 255),
            (g + dg).coerceIn(0, 255),
            (b + db).coerceIn(0, 255),
        )
    }

    companion object {
        fun engineColor(engine: EngineType): Int = when (engine) {
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> Color.argb(80, 180, 120, 60)
            EngineType.MV, EngineType.MZ -> Color.argb(80, 100, 160, 200)
            EngineType.EASYRPG -> Color.argb(80, 120, 170, 120)
            EngineType.RENPY -> Color.argb(80, 180, 130, 160)
            EngineType.GODOT -> Color.argb(80, 80, 170, 170)
            EngineType.RUFFLE -> Color.argb(80, 160, 120, 180)
            else -> Color.argb(80, 207, 174, 126)
        }
    }
}
