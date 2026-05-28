/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Locale manager — handles language switching at runtime.
 * Supports English, Spanish, and system default.
 */

package com.runestone.app

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleManager {

    private const val PREFS_NAME = "runestone-locale"
    private const val KEY_LANGUAGE = "language"

    /** Supported languages. */
    val SUPPORTED_LOCALES = mapOf(
        "en" to "English",
        "es" to "Español",
    )

    /** Get the saved language code, or "system" for device default. */
    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "system") ?: "system"
    }

    /** Save language preference and apply immediately. */
    fun setLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /**
     * Apply the saved locale to a context, returning a wrapped context
     * with the correct locale. Call this in Activity.attachBaseContext().
     */
    fun applyLocale(context: Context): Context {
        val langCode = getSavedLanguage(context)
        if (langCode == "system") return context

        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    /** Get the display name for the current language. */
    fun getCurrentLanguageName(context: Context): String {
        val code = getSavedLanguage(context)
        if (code == "system") return "System Default"
        return SUPPORTED_LOCALES[code] ?: code
    }
}
