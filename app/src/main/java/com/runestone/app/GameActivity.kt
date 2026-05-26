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

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class GameActivity : Activity() {

    companion object {
        private const val EXTRA_GAME_PATH = "game_path"
        private const val EXTRA_ENGINE_TYPE = "engine_type"

        fun start(activity: Activity, gamePath: String) {
            val intent = Intent(activity, GameActivity::class.java).apply {
                putExtra(EXTRA_GAME_PATH, gamePath)
            }
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gamePath = intent.getStringExtra(EXTRA_GAME_PATH) ?: run {
            finish()
            return
        }

        // TODO: Detect engine and launch appropriate runtime
        // - RGSS (XP/VX/VX Ace) -> mkxp-z native activity
        // - MV/MZ -> WebView pointing to www/index.html
        finish()
    }
}
