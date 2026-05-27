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
import android.util.Log
import com.runestone.app.engine.EngineRegistry

/**
 * Application class for Runestone.
 * 
 * Initializes the engine plugin system at app startup.
 */
class RunestoneApplication : Application() {

    companion object {
        private const val TAG = "Runestone"
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "Runestone starting up...")
        
        // Initialize all built-in engine plugins
        EngineRegistry.initDefaults()
        
        val engineCount = EngineRegistry.all().size
        Log.i(TAG, "Initialized $engineCount engine plugins")
    }
}
