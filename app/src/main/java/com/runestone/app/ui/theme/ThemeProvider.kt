package com.runestone.app.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup

class ThemeProvider private constructor(appContext: Context) {

    private val prefs = appContext.getSharedPreferences("runestone-settings-v1", Context.MODE_PRIVATE)

    var currentMode: ThemeMode = parseMode(prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name)
        private set

    val colors: ThemeColors
        get() = when (resolveMode()) {
            ThemeMode.LIGHT -> ThemeColors.LIGHT
            else -> ThemeColors.DARK
        }

    val background: Int get() = colors.background
    val surface: Int get() = colors.surface
    val cardBackground: Int get() = colors.cardBackground
    val text: Int get() = colors.text
    val textSecondary: Int get() = colors.textSecondary
    val accent: Int get() = colors.accent
    val accentMuted: Int get() = colors.accentMuted
    val muted: Int get() = colors.muted
    val mutedDim: Int get() = colors.mutedDim
    val overlayDim: Int get() = colors.overlayDim
    val error: Int get() = colors.error
    val success: Int get() = colors.success

    fun setMode(mode: ThemeMode) {
        currentMode = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun toggle(): ThemeMode {
        val next = if (resolveMode() == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        setMode(next)
        return next
    }

    fun resolveMode(): ThemeMode {
        if (currentMode != ThemeMode.SYSTEM) return currentMode
        return if (isSystemDark()) ThemeMode.DARK else ThemeMode.LIGHT
    }

    fun isDark(): Boolean = resolveMode() == ThemeMode.DARK

    fun glassBg(radiusDp: Int, alpha: Int = 200, useAccent: Boolean = false): GradientDrawable {
        val base = if (useAccent) accent else surface
        return GradientDrawable().apply {
            setColor(Color.argb(alpha.coerceIn(0, 255), Color.red(base), Color.green(base), Color.blue(base)))
            cornerRadius = dp(radiusDp).toFloat()
            val stroke = if (useAccent) accent else mutedDim
            setStroke(dpInt(1), Color.argb((alpha / 2).coerceIn(0, 255), Color.red(stroke), Color.green(stroke), Color.blue(stroke)))
        }
    }

    private fun isSystemDark(): Boolean {
        val uiMode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        return uiMode
    }

    companion object {
        @Volatile
        private var instance: ThemeProvider? = null

        fun getInstance(context: Context): ThemeProvider {
            return instance ?: synchronized(this) {
                instance ?: ThemeProvider(context.applicationContext).also { instance = it }
            }
        }

        private var displayDensity = 1f

        fun init(context: Context) {
            displayDensity = context.resources.displayMetrics.density
            getInstance(context)
        }

        fun dp(value: Int): Int = (value * displayDensity).toInt()
        private fun dpInt(value: Int): Int = (value * displayDensity).toInt()
    }

    private fun parseMode(name: String): ThemeMode = runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.DARK)
}
