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
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.runestone.app.data.EngineType
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings
import com.runestone.app.engine.EngineDetector
import com.runestone.app.engine.UnavailableEngine
import com.runestone.app.engine.WebViewEngine
import com.runestone.app.input.TouchOverlayView
import java.io.File

class GameActivity : Activity() {

    private var webViewEngine: WebViewEngine? = null
    private var engineType: EngineType = EngineType.UNKNOWN
    private var gamePath: String = ""
    private var settings = RunnerSettings()
    private var overlayView: TouchOverlayView? = null

    companion object {
        private const val TAG = "Runestone"
        private const val EXTRA_GAME_PATH = "game_path"
        private const val EXTRA_ENGINE_TYPE = "engine_type"
        private const val EXTRA_LAYOUT_MODE = "layout_mode"
        private const val EXTRA_TOUCH_OPACITY = "touch_opacity"
        private const val EXTRA_TOUCH_SCALE = "touch_scale"
        private const val EXTRA_HAPTICS = "haptics"
        private const val EXTRA_HAPTIC_INTENSITY = "haptic_intensity"
        private const val EXTRA_SHOW_EXTRA_BTNS = "show_extra_btns"
        private const val EXTRA_AUDIO_EXT = "audio_ext"

        fun start(activity: Activity, gamePath: String, engineType: String? = null, settings: RunnerSettings = RunnerSettings()) {
            val intent = Intent(activity, GameActivity::class.java).apply {
                putExtra(EXTRA_GAME_PATH, gamePath)
                if (engineType != null) putExtra(EXTRA_ENGINE_TYPE, engineType)
                putExtra(EXTRA_LAYOUT_MODE, settings.layoutMode.name)
                putExtra(EXTRA_TOUCH_OPACITY, settings.touchOpacity)
                putExtra(EXTRA_TOUCH_SCALE, settings.touchScale)
                putExtra(EXTRA_HAPTICS, settings.hapticsEnabled)
                putExtra(EXTRA_HAPTIC_INTENSITY, settings.hapticIntensity)
                putExtra(EXTRA_SHOW_EXTRA_BTNS, settings.showExtraButtons)
                putExtra(EXTRA_AUDIO_EXT, settings.forceAudioExt)
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

        // Load settings from extras
        settings = RunnerSettings(
            layoutMode = runCatching {
                LayoutMode.valueOf(intent.getStringExtra(EXTRA_LAYOUT_MODE) ?: LayoutMode.PORTRAIT_CONSOLE.name)
            }.getOrDefault(LayoutMode.PORTRAIT_CONSOLE),
            touchOpacity = intent.getFloatExtra(EXTRA_TOUCH_OPACITY, RunnerSettings().touchOpacity),
            touchScale = intent.getFloatExtra(EXTRA_TOUCH_SCALE, RunnerSettings().touchScale),
            hapticsEnabled = intent.getBooleanExtra(EXTRA_HAPTICS, RunnerSettings().hapticsEnabled),
            hapticIntensity = intent.getFloatExtra(EXTRA_HAPTIC_INTENSITY, RunnerSettings().hapticIntensity),
            showExtraButtons = intent.getBooleanExtra(EXTRA_SHOW_EXTRA_BTNS, RunnerSettings().showExtraButtons),
            forceAudioExt = intent.getStringExtra(EXTRA_AUDIO_EXT) ?: RunnerSettings().forceAudioExt,
        )

        // Force orientation based on layout mode
        if (settings.layoutMode == LayoutMode.LANDSCAPE || settings.layoutMode == LayoutMode.GAMEPAD) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        // Debug: show received settings
        android.util.Log.d("Runestone", "GameActivity: layoutMode=${settings.layoutMode}, path=$gamePath")

        val typeStr = intent.getStringExtra(EXTRA_ENGINE_TYPE)
        engineType = if (typeStr != null) {
            try { EngineType.valueOf(typeStr) } catch (e: Exception) { EngineDetector.detect(gameDir) }
        } else {
            EngineDetector.detect(gameDir)
        }

        when (engineType) {
            // WebView engines (bundled)
            EngineType.MV, EngineType.MZ,
            EngineType.TYRANO, EngineType.CONSTRUCT,
            EngineType.HTML, EngineType.TWINE, EngineType.VNMAKER,
            EngineType.RUFFLE -> launchWebViewGame(gameDir)

            // Native engines (bundled)
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> launchRgssGame(gameDir)
            EngineType.RGSS_2000, EngineType.RGSS_2003, EngineType.EASYRPG -> launchEasyRpgGame(gameDir)

            // Bundlable engines (native-first, fallback download)
            EngineType.RENPY -> launchRenpyGame(gameDir)
            EngineType.GODOT, EngineType.GODOT3, EngineType.GODOT4 -> launchGodotGame(gameDir)
            EngineType.NSCRIPTER -> launchNScripterGame(gameDir)

            // Legacy / unsupported
            EngineType.RM95, EngineType.DANTE98 -> showLegacyDialog(engineType)
            EngineType.ELECTRON -> showElectronDialog()
            EngineType.UNKNOWN -> {
                Toast.makeText(this, "Unknown engine, trying WebView", Toast.LENGTH_SHORT).show()
                launchWebViewGame(gameDir)
            }
        }
    }

    private fun launchWebViewGame(gameDir: File) {
        val isLandscape = settings.layoutMode == LayoutMode.LANDSCAPE
        val isPortraitConsole = settings.layoutMode == LayoutMode.PORTRAIT_CONSOLE
        val isGamepad = settings.layoutMode == LayoutMode.GAMEPAD

        // Debug
        android.util.Log.d("Runestone", "launchWebViewGame: layoutMode=${settings.layoutMode.name} landscape=$isLandscape portraitConsole=$isPortraitConsole")

        // ── Root: FrameLayout ──
        val root = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)

        // ── Game area (fills all space for landscape/gamepad, split for portrait console) ──
        if (isPortraitConsole) {
            // Portrait Console: game above (52%), controls below (48%)
            val splitLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            root.addView(splitLayout)

            val gameArea = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    0.52f,
                )
            }
            splitLayout.addView(gameArea)

            val engine = WebViewEngine(this)
            webViewEngine = engine
            gameArea.addView(engine, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))

            val controlPanel = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    0.48f,
                )
                setBackgroundColor(Color.rgb(10, 10, 12))
            }
            splitLayout.addView(controlPanel)

            setupTouchOverlay(controlPanel, engine)
        } else {
            // Landscape or Gamepad: game fills the whole screen
            val engine = WebViewEngine(this)
            webViewEngine = engine
            root.addView(engine, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))

            if (isLandscape) {
                // Landscape: overlay controls on top of game
                val overlayContainer = FrameLayout(this).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                root.addView(overlayContainer)
                setupTouchOverlay(overlayContainer, engine)
            }
            // Gamepad: no touch overlay, just the game
        }

        // ── Load game with settings ──
        webViewEngine?.let { eng ->
            eng.loadGame(gameDir.absolutePath, WebViewEngine.WebViewGameConfig(
                title = gameDir.name,
                addGamepad = false, // Using native overlay
                fakeGreenworks = true,
                showFps = true,
                forceAudioExt = settings.forceAudioExt,
            ))
            // Make WebView focusable for keyboard input
            eng.isFocusable = true
            eng.isFocusableInTouchMode = true
        }

        // ── HOME & Keyboard buttons ──
        val homeBtn = TextView(this).apply {
            text = "HOME"
            textSize = 11f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 210, 190))
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 12, 11, 16))
                setStroke(dp(1), Color.argb(70, 160, 140, 110))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                // Save paused state and go home
                getSharedPreferences("runestone", MODE_PRIVATE).edit()
                    .putString("paused_game", gamePath).apply()
                startActivity(Intent(this@GameActivity, MainActivity::class.java))
            }
            val ph = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            ph.gravity = Gravity.BOTTOM or Gravity.START
            ph.bottomMargin = dp(8); ph.leftMargin = dp(8)
            layoutParams = ph
        }
        root.addView(homeBtn)

        // Keyboard toggle
        val kbBtn = TextView(this).apply {
            text = "KBD"
            textSize = 11f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 210, 190))
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 12, 11, 16))
                setStroke(dp(1), Color.argb(70, 160, 140, 110))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { toggleKeyboard() }
            val pk = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            pk.gravity = Gravity.BOTTOM or Gravity.END
            pk.bottomMargin = dp(8); pk.rightMargin = dp(8)
            layoutParams = pk
        }
        root.addView(kbBtn)
    }

    private fun setupTouchOverlay(container: ViewGroup, engine: WebViewEngine) {
        val overlay = TouchOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            alpha = settings.touchOpacity
            scale = settings.touchScale
            hapticsEnabled = settings.hapticsEnabled
            hapticIntensity = settings.hapticIntensity
            showExtraButtons = settings.showExtraButtons
            controlsOnly = (settings.layoutMode == LayoutMode.PORTRAIT_CONSOLE)

            onInput = { zone, pressed ->
                val keyCode = zoneToKeyCode(zone)
                val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                engine.dispatchKeyEvent(KeyEvent(action, keyCode))

                val js = when {
                    pressed && zone == TouchOverlayView.Zone.DPAD_UP -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('up');"
                    pressed && zone == TouchOverlayView.Zone.DPAD_DOWN -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('down');"
                    pressed && zone == TouchOverlayView.Zone.DPAD_LEFT -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('left');"
                    pressed && zone == TouchOverlayView.Zone.DPAD_RIGHT -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('right');"
                    pressed && zone == TouchOverlayView.Zone.BTN_A -> "if(TouchInput&&TouchInput._onOk)TouchInput._onOk();"
                    pressed && zone == TouchOverlayView.Zone.BTN_B -> "if(TouchInput&&TouchInput._onCancel)TouchInput._onCancel();"
                    pressed && zone == TouchOverlayView.Zone.BTN_X -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:33});"
                    pressed && zone == TouchOverlayView.Zone.BTN_Y -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:34});"
                    pressed && zone == TouchOverlayView.Zone.SELECT -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:27});"
                    pressed && zone == TouchOverlayView.Zone.START -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:13});"
                    pressed && zone == TouchOverlayView.Zone.L1 -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:81});"
                    pressed && zone == TouchOverlayView.Zone.R1 -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:87});"
                    else -> ""
                }
                if (js.isNotEmpty()) {
                    engine.evaluateJavascript("(function(){try{$js}catch(e){}})();", null)
                }
                if (zone == TouchOverlayView.Zone.SETTINGS && pressed) {
                    openSettings()
                }
            }
        }
        this@GameActivity.overlayView = overlay
        container.addView(overlay)
    }

    private fun openSettings() {
        val overlay = overlayView
        if (overlay != null) {
            Toast.makeText(this,
                "Layout: ${settings.layoutMode.displayName} | Vib: ${if (overlay.hapticsEnabled) "ON" else "OFF"}" +
                " | KB btn: KBD top-right",
                Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Gamepad mode — no touch controls", Toast.LENGTH_SHORT).show()
        }
    }

    private var keyboardVisible = false

    private fun toggleKeyboard() {
        val engine = webViewEngine ?: return
        keyboardVisible = !keyboardVisible
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (keyboardVisible) {
            engine.requestFocus()
            imm.showSoftInput(engine, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm.hideSoftInputFromWindow(engine.windowToken, 0)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun zoneToKeyCode(zone: TouchOverlayView.Zone): Int = when (zone) {
        TouchOverlayView.Zone.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
        TouchOverlayView.Zone.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        TouchOverlayView.Zone.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        TouchOverlayView.Zone.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        TouchOverlayView.Zone.BTN_A -> KeyEvent.KEYCODE_Z
        TouchOverlayView.Zone.BTN_B -> KeyEvent.KEYCODE_X
        TouchOverlayView.Zone.BTN_X -> KeyEvent.KEYCODE_Q
        TouchOverlayView.Zone.BTN_Y -> KeyEvent.KEYCODE_W
        TouchOverlayView.Zone.SELECT -> KeyEvent.KEYCODE_ESCAPE
        TouchOverlayView.Zone.START -> KeyEvent.KEYCODE_ENTER
        TouchOverlayView.Zone.MENU, TouchOverlayView.Zone.SETTINGS -> KeyEvent.KEYCODE_M
        TouchOverlayView.Zone.HOME -> KeyEvent.KEYCODE_HOME
        TouchOverlayView.Zone.L1 -> KeyEvent.KEYCODE_BUTTON_L1
        TouchOverlayView.Zone.R1 -> KeyEvent.KEYCODE_BUTTON_R1
    }

    private fun launchRgssGame(gameDir: File) {
        Log.i(TAG, "launchRgssGame: $gameDir (engine=$engineType)")
        val intent = Intent().apply {
            setClassName(this@GameActivity, "com.hatkid.mkxpz.MainActivity")
            putExtra("com.grimmobile.runner.extra.GAME_PATH", gameDir.absolutePath)
            putExtra("com.grimmobile.runner.extra.LAYOUT_MODE", settings.layoutMode.name)
            putExtra("com.grimmobile.runner.extra.TOUCH_OPACITY", settings.touchOpacity)
            putExtra("com.grimmobile.runner.extra.TOUCH_SCALE", settings.touchScale)
            putExtra("com.grimmobile.runner.extra.HAPTICS_ENABLED", settings.hapticsEnabled)
            putExtra("com.grimmobile.runner.extra.HAPTIC_INTENSITY", settings.hapticIntensity)
        }
        startActivity(intent)
    }

    // ── EasyRPG (GPLv3 — bundled native, no download needed) ─────

    private fun launchEasyRpgGame(gameDir: File) {
        Log.i(TAG, "EasyRPG bundled: launching ${gameDir.name}")
        val configDir = File(filesDir, "easyrpg").apply { mkdirs() }
        val saveDir = File(configDir, "saves").apply { mkdirs() }
        val logFile = File(configDir, "easyrpg-player.log")
        val commandLine = arrayOf(
            "--project-path", gameDir.absolutePath,
            "--config-path", configDir.absolutePath,
            "--save-path", saveDir.absolutePath,
            "--log-file", logFile.absolutePath,
        )
        val intent = Intent().apply {
            setClassName(packageName, "org.easyrpg.player.player.EasyRpgPlayerActivity")
            putExtra("project_path", gameDir.absolutePath)
            putExtra("command_line", commandLine)
            putExtra("save_path", saveDir.absolutePath)
            putExtra("log_file", logFile.absolutePath)
            putExtra("com.grimmobile.runner.extra.GAME_PATH", gameDir.absolutePath)
            putExtra("com.grimmobile.runner.extra.LAYOUT_MODE", settings.layoutMode.name)
            putExtra("com.grimmobile.runner.extra.TOUCH_OPACITY", settings.touchOpacity)
            putExtra("com.grimmobile.runner.extra.TOUCH_SCALE", settings.touchScale)
            putExtra("com.grimmobile.runner.extra.HAPTICS_ENABLED", settings.hapticsEnabled)
            putExtra("com.grimmobile.runner.extra.HAPTIC_INTENSITY", settings.hapticIntensity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // ── Godot (MIT — native wrapper not integrated) ──────────────

    private fun launchGodotGame(gameDir: File) {
        Log.i(TAG, "Godot unavailable: ${gameDir.name}")
        UnavailableEngine.show(this, "Godot")
    }

    // ── NScripter / ONScripter (GPLv2+ — wrapper not integrated) ─

    private fun launchNScripterGame(gameDir: File) {
        Log.i(TAG, "ONScripter unavailable: ${gameDir.name}")
        UnavailableEngine.show(this, "ONScripter")
    }

    // ── Ren'Py (MIT — native wrapper not integrated) ─────────────

    private fun launchRenpyGame(gameDir: File) {
        Log.i(TAG, "Ren'Py unavailable: ${gameDir.name}")
        UnavailableEngine.show(this, "Ren'Py")
    }

    private fun showLegacyDialog(type: EngineType) {
        AlertDialog.Builder(this)
            .setTitle("Legacy Engine — ${type.label}")
            .setMessage("This is a legacy engine from ${if (type == EngineType.DANTE98) "1992" else "1997"}.\n\nNo open-source runtime exists. These games require the original PC software.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showElectronDialog() {
        AlertDialog.Builder(this)
            .setTitle("Electron Not Supported")
            .setMessage("Electron apps bundle a full Chromium browser.\n\nThey cannot run on Android and require a desktop PC.")
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onBackPressed() {
        val engine = webViewEngine
        if (engine != null) {
            val shouldQuit = engine.handleBack()
            if (shouldQuit) {
                // Clear paused state — game is done
                getSharedPreferences("runestone", MODE_PRIVATE).edit().remove("paused_game").apply()
                super.onBackPressed()
            }
        } else {
            getSharedPreferences("runestone", MODE_PRIVATE).edit().remove("paused_game").apply()
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        webViewEngine?.onPause()
    }

    override fun onResume() {
        super.onResume()
        // Check if we should self-destruct from STOP dialog
        val killPath = getSharedPreferences("runestone", MODE_PRIVATE)
            .getString("kill_game", null)
        if (killPath != null && gamePath != null &&
            (killPath == gamePath || killPath == gamePath.substringAfterLast("/"))) {
            getSharedPreferences("runestone", MODE_PRIVATE).edit().remove("kill_game").apply()
            Log.i(TAG, "kill_game signal received for $killPath — finishing")
            finish()
            return
        }
        webViewEngine?.resumeTimers()
        webViewEngine?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewEngine?.destroy()
        webViewEngine = null
    }
}
