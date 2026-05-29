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
            val fallback = engineColor(engineType)
            cache[gameTitle] = fallback
            onColor(fallback)
            return
        }

        Thread {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(coverUrl).openStream())
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val color = palette.getVibrantColor(palette.getMutedColor(engineColor(engineType)))
                    cache[gameTitle] = color
                    bitmap.recycle()
                    Handler(Looper.getMainLooper()).post { onColor(color) }
                } else {
                    Handler(Looper.getMainLooper()).post { onColor(engineColor(engineType)) }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { onColor(engineColor(engineType)) }
            }
        }.start()
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
