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
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.runestone.app.data.EngineType
import com.runestone.app.engine.EngineDetector
import com.runestone.app.engine.WebViewEngine
import java.io.File

class GameActivity : Activity() {

    private var webViewEngine: WebViewEngine? = null
    private var engineType: EngineType = EngineType.UNKNOWN
    private var gamePath: String = ""

    companion object {
        private const val EXTRA_GAME_PATH = "game_path"
        private const val EXTRA_ENGINE_TYPE = "engine_type"

        fun start(activity: Activity, gamePath: String, engineType: String? = null) {
            val intent = Intent(activity, GameActivity::class.java).apply {
                putExtra(EXTRA_GAME_PATH, gamePath)
                if (engineType != null) putExtra(EXTRA_ENGINE_TYPE, engineType)
            }
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gamePath = intent.getStringExtra(EXTRA_GAME_PATH) ?: run {
            Toast.makeText(this, "No game path provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val gameDir = File(gamePath)
        if (!gameDir.exists() || !gameDir.isDirectory) {
            Toast.makeText(this, "Game directory not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Detect engine type
        val typeStr = intent.getStringExtra(EXTRA_ENGINE_TYPE)
        engineType = if (typeStr != null) {
            try { EngineType.valueOf(typeStr) } catch (e: Exception) { EngineDetector.detect(gameDir) }
        } else {
            EngineDetector.detect(gameDir)
        }

        when (engineType) {
            EngineType.MV, EngineType.MZ,
            EngineType.TYRANO, EngineType.CONSTRUCT -> launchWebViewGame(gameDir)
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> launchRgssGame(gameDir)
            EngineType.EASYRPG -> launchEasyRpgGame(gameDir)
            EngineType.RENPY -> launchRenpyGame(gameDir)
            EngineType.UNKNOWN -> {
                // Try anyway - maybe it's an MV game with weird structure
                Toast.makeText(this, "Unknown engine type, trying WebView", Toast.LENGTH_SHORT).show()
                launchWebViewGame(gameDir)
            }
        }
    }

    private fun launchWebViewGame(gameDir: File) {
        val layout = FrameLayout(this).apply {
            id = View.generateViewId()
        }
        setContentView(layout)

        val engine = WebViewEngine(this)
        webViewEngine = engine

        layout.addView(engine, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        engine.loadGame(gameDir.absolutePath, WebViewEngine.WebViewGameConfig(
            title = gameDir.name,
            addGamepad = true,
            fakeGreenworks = true,
            showFps = true,
        ))
    }

    private fun launchRgssGame(gameDir: File) {
        // RGSS games (XP/VX/VX Ace) need the mkxp-z native runtime.
        // For now, show a message since mkxp-z integration is pending.
        Toast.makeText(this, "RGSS engine (${engineType.label}) support coming soon", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun launchEasyRpgGame(gameDir: File) {
        // RPG Maker 2000/2003 games need the EasyRPG native runtime.
        // Phase 1: show a message since EasyRPG integration is pending.
        Toast.makeText(this, "RPG Maker 2000/2003 support coming soon", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun launchRenpyGame(gameDir: File) {
        // Ren'Py games need the Ren'Py plugin APK.
        // Phase 2: show a message since Ren'Py integration is pending.
        Toast.makeText(this, "Ren'Py support coming soon (will require separate plugin APK)", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onBackPressed() {
        val engine = webViewEngine
        if (engine != null) {
            val shouldQuit = engine.handleBack()
            if (shouldQuit) {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        webViewEngine?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webViewEngine?.resumeTimers()
        webViewEngine?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewEngine?.destroy()
        webViewEngine = null
    }
}
