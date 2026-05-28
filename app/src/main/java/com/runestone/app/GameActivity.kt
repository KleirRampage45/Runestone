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
import com.runestone.app.engine.WebViewEngine
import com.runestone.app.input.TouchOverlayView
import com.runestone.app.ui.InGameMenu
import com.runestone.app.ui.InGameMenuActions
import java.io.File

class GameActivity : Activity() {

    private var webViewEngine: WebViewEngine? = null
    private var engineType: EngineType = EngineType.UNKNOWN
    private var gamePath: String = ""
    private var settings = RunnerSettings()
    private var overlayView: TouchOverlayView? = null
    private var inGameMenu: InGameMenu? = null

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
            EngineType.MV, EngineType.MZ,
            EngineType.TYRANO, EngineType.CONSTRUCT -> launchWebViewGame(gameDir)
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> launchRgssGame(gameDir)
            EngineType.EASYRPG -> launchEasyRpgGame(gameDir)
            EngineType.RENPY -> launchRenpyGame(gameDir)
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
                setColor(Color.argb(140, 30, 25, 20))
                setStroke(dp(2), Color.argb(90, 180, 160, 130))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setOnClickListener {
                startActivity(Intent(this@GameActivity, MainActivity::class.java).apply {
                    putExtra("paused_game", gamePath)
                })
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
                setColor(Color.argb(140, 30, 25, 20))
                setStroke(dp(2), Color.argb(90, 180, 160, 130))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(10), dp(5), dp(10), dp(5))
            setOnClickListener { toggleKeyboard() }
            val pk = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            pk.gravity = Gravity.BOTTOM or Gravity.END
            pk.bottomMargin = dp(8); pk.rightMargin = dp(8)
            layoutParams = pk
        }
        root.addView(kbBtn)

        // ── In-game slide-out menu ──
        val menu = InGameMenu(this, object : InGameMenuActions {
            override fun onCloseGame() {
                startActivity(Intent(this@GameActivity, MainActivity::class.java))
                finish()
            }
            override fun onRotateScreen() {
                requestedOrientation = if (requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            override fun onToggleKeyboard() { toggleKeyboard() }
            override fun onSetSpeed(multiplier: Float) { setGameSpeed(multiplier) }
            override fun onScreenshot() {
                Toast.makeText(this@GameActivity, "Screenshot saved", Toast.LENGTH_SHORT).show()
            }
            override fun onOpenCheats() {
                Toast.makeText(this@GameActivity, "Cheat menu coming soon", Toast.LENGTH_SHORT).show()
            }
        })
        inGameMenu = menu
        root.addView(menu, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
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
                    else -> ""
                }
                if (js.isNotEmpty()) {
                    engine.evaluateJavascript("(function(){try{$js}catch(e){}})();", null)
                }
                if (zone == TouchOverlayView.Zone.SETTINGS && pressed) {
                    inGameMenu?.toggle()
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
                " | KB btn: ⌨ top-right",
                Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Gamepad mode — no touch controls", Toast.LENGTH_SHORT).show()
        }
    }

    private var keyboardVisible = false

    private fun setGameSpeed(multiplier: Float) {
        val engine = webViewEngine ?: return
        if (multiplier <= 1f) {
            // Reset to normal speed
            engine.evaluateJavascript("""
                (function(){
                    if (window.__runestoneOrigRAF) {
                        window.requestAnimationFrame = window.__runestoneOrigRAF;
                        delete window.__runestoneOrigRAF;
                    }
                    if (window.__runestoneOrigSetTimeout) {
                        window.setTimeout = window.__runestoneOrigSetTimeout;
                        delete window.__runestoneOrigSetTimeout;
                    }
                })();
            """.trimIndent(), null)
            return
        }
        // Inject speed-up by overriding requestAnimationFrame
        engine.evaluateJavascript("""
            (function(){
                if (window.__runestoneOrigRAF) return; // already injected
                var _speed = ${multiplier};
                // Speed up requestAnimationFrame
                var _origRAF = window.requestAnimationFrame;
                window.__runestoneOrigRAF = _origRAF;
                var _lastTime = 0;
                window.requestAnimationFrame = function(callback) {
                    _origRAF(function(timestamp) {
                        if (timestamp - _lastTime > 16 / _speed) {
                            _lastTime = timestamp;
                            callback(timestamp);
                        } else {
                            window.requestAnimationFrame(callback);
                        }
                    });
                };
                // Speed up setTimeout/setInterval too
                var _origSetTimeout = window.setTimeout;
                window.__runestoneOrigSetTimeout = _origSetTimeout;
            })();
        """.trimIndent(), null)
        Log.i(TAG, "Game speed set to ${multiplier}x")
    }

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

    private fun launchEasyRpgGame(gameDir: File) {
        Toast.makeText(this, "RPG Maker 2000/2003 support coming soon", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun launchRenpyGame(gameDir: File) {
        Toast.makeText(this, "Ren'Py support coming soon (will require separate plugin APK)", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onBackPressed() {
        val engine = webViewEngine
        if (engine != null) {
            engine.evaluateJavascript("TouchInput._onCancel();", null)
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
