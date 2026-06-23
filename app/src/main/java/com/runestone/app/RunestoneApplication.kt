/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app

import android.app.Application
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process
import android.util.Log
import android.content.Context
import com.runestone.app.engine.EngineRegistry
import com.runestone.app.util.I18n

/**
 * Application class for Runestone.
 * 
 * Initializes the engine plugin system at app startup.
 */
class RunestoneApplication : Application() {

    private var performanceHintSession: PerformanceHintManager.Session? = null

    companion object {
        private const val TAG = "Runestone"
    }

    override fun attachBaseContext(base: Context) {
        val locale = base.getSharedPreferences("runestone-settings-v1", MODE_PRIVATE)
            .getString("locale", "en") ?: "en"
        val localeObj = java.util.Locale(locale)
        java.util.Locale.setDefault(localeObj)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(localeObj)
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "Runestone starting up...")
        
        // Boost app startup performance on API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val manager = getSystemService(Context.PERFORMANCE_HINT_SERVICE) as PerformanceHintManager
                val tid = Process.myTid()
                val session = manager.createHintSession(intArrayOf(tid), 2_000_000_000L)
                session?.reportActualWorkDuration(1_000_000L)
                performanceHintSession = session
                Log.i(TAG, "PerformanceHintManager boost activated")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create PerformanceHintManager session: ${e.message}")
            }
        }
        
        // Initialize all built-in engine plugins
        EngineRegistry.initDefaults(this)
        com.runestone.app.ui.theme.ThemeProvider.init(this)
        com.runestone.app.ui.Theme.bind(com.runestone.app.ui.theme.ThemeProvider.getInstance(this))
        
        val engineCount = EngineRegistry.all().size
        Log.i(TAG, "Initialized $engineCount engine plugins")
    }
}
