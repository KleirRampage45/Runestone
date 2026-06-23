package com.runestone.app.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object I18n {
    private const val PREFS = "runestone-settings-v1"
    private const val KEY_LOCALE = "locale"

    val supportedLocales = listOf("en", "es", "pt")

    fun get(context: Context, key: String): String {
        val localeCode = getLocale(context)
        val res = localizedResources(context, localeCode)
        val id = res.getIdentifier(key, "string", context.packageName)
        return if (id != 0) res.getString(id) else key
    }

    fun get(context: Context, key: String, vararg args: Any?): String {
        val localeCode = getLocale(context)
        val res = localizedResources(context, localeCode)
        val id = res.getIdentifier(key, "string", context.packageName)
        return if (id != 0) res.getString(id, *args) else key
    }

    fun getLocale(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LOCALE, "en") ?: "en"
    }

    fun setLocale(context: Context, localeCode: String) {
        if (localeCode !in supportedLocales) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, localeCode)
            .apply()
    }

    fun applyToContext(context: Context): Context {
        val localeCode = getLocale(context)
        val locale = Locale(localeCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun localizedResources(context: Context, localeCode: String): Resources {
        val locale = Locale(localeCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config).resources
    }
}
