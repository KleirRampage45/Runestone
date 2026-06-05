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
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import com.runestone.app.data.ControllerShortcut
import com.runestone.app.data.EngineType
import com.runestone.app.data.DisplayCutoutMode
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings
import com.runestone.app.engine.EngineDetector
import com.runestone.app.engine.UnavailableEngine
import com.runestone.app.engine.WebViewEngine
import com.runestone.app.input.ControllerMapper
import com.runestone.app.input.RunestoneKeyboardView
import com.runestone.app.input.TouchOverlayView
import org.json.JSONObject
import java.io.File

class GameActivity : Activity() {

    private var webViewEngine: WebViewEngine? = null
    private var engineType: EngineType = EngineType.UNKNOWN
    private var gamePath: String = ""
    private var settings = RunnerSettings()
    private var overlayView: TouchOverlayView? = null
    private var rootView: FrameLayout? = null
    private var keyboardView: RunestoneKeyboardView? = null
    private var controllerPresetId: String? = null
    private val activeControllerAxisButtons = mutableSetOf<ControllerMapper.GameButton>()
    private val pressedControllerKeys = mutableSetOf<Int>()
    private var triggerHomeComboDown = false
    private var runtimeActionsOverlay: View? = null

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
        private const val EXTRA_SMOOTH_SCALING = "smooth_scaling"
        private const val EXTRA_INTEGER_SCALING = "integer_scaling"
        private const val EXTRA_TEXT_SCALE = "text_scale"
        private const val EXTRA_HIDE_GAMEPAD = "hide_gamepad"
        private const val EXTRA_DIAGONAL = "diagonal_movement"
        private const val EXTRA_KEEP_SCREEN_ON = "keep_screen_on"
        private const val EXTRA_DISPLAY_CUTOUT_MODE = "display_cutout_mode"
        private const val EXTRA_USE_HTTP_SERVER = "use_http_server"
        private const val EXTRA_WEBGL = "webgl"
        private const val EXTRA_DESKTOP_MODE = "desktop_mode"
        private const val EXTRA_ALLOW_EXTERNAL = "allow_external"
        private const val EXTRA_DIALOG_LOGS = "dialog_logs"
        private const val EXTRA_USE_RUBY18 = "use_ruby18"
        private const val EXTRA_VSYNC = "vsync"
        private const val EXTRA_FRAME_SKIP = "frame_skip"
        private const val EXTRA_SHADERS = "shaders"
        private const val EXTRA_CONTROLLER_HOME_SHORTCUT = "controller_home_shortcut"
        private const val EXTRA_CONTROLLER_KEYBOARD_SHORTCUT = "controller_keyboard_shortcut"
        private const val EXTRA_CONTROLLER_RUNTIME_MENU_SHORTCUT = "controller_runtime_menu_shortcut"
        private const val EXTRA_CONTROLLER_RESUME_SHORTCUT = "controller_resume_shortcut"

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
                putExtra(EXTRA_SMOOTH_SCALING, settings.smoothScaling)
                putExtra(EXTRA_INTEGER_SCALING, settings.integerScaling)
                putExtra(EXTRA_TEXT_SCALE, settings.textScale)
                putExtra(EXTRA_HIDE_GAMEPAD, settings.hideVirtualGamepad)
                putExtra(EXTRA_DIAGONAL, settings.diagonalMovement)
                putExtra(EXTRA_KEEP_SCREEN_ON, settings.keepScreenOn)
                putExtra(EXTRA_DISPLAY_CUTOUT_MODE, settings.displayCutoutMode.name)
                putExtra(EXTRA_USE_HTTP_SERVER, settings.useHttpServer)
                putExtra(EXTRA_WEBGL, settings.webgl)
                putExtra(EXTRA_DESKTOP_MODE, settings.desktopMode)
                putExtra(EXTRA_ALLOW_EXTERNAL, settings.allowExternalModules)
                putExtra(EXTRA_DIALOG_LOGS, settings.dialogLogs)
                putExtra(EXTRA_USE_RUBY18, settings.useRuby18)
                putExtra(EXTRA_VSYNC, settings.vsync)
                putExtra(EXTRA_FRAME_SKIP, settings.frameSkip)
                putExtra(EXTRA_SHADERS, settings.shaders)
                putExtra(EXTRA_CONTROLLER_HOME_SHORTCUT, settings.controllerHomeShortcut.name)
                putExtra(EXTRA_CONTROLLER_KEYBOARD_SHORTCUT, settings.controllerKeyboardShortcut.name)
                putExtra(EXTRA_CONTROLLER_RUNTIME_MENU_SHORTCUT, settings.controllerRuntimeMenuShortcut.name)
                putExtra(EXTRA_CONTROLLER_RESUME_SHORTCUT, settings.controllerResumeShortcut.name)
            }
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()

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

        val typeStr = intent.getStringExtra(EXTRA_ENGINE_TYPE)
        engineType = if (typeStr != null) {
            try { EngineType.valueOf(typeStr) } catch (e: Exception) { EngineDetector.detect(gameDir) }
        } else {
            EngineDetector.detect(gameDir)
        }

        // Load settings from extras
        val defaults = RunnerSettings()
        settings = RunnerSettings(
            layoutMode = runCatching {
                LayoutMode.valueOf(intent.getStringExtra(EXTRA_LAYOUT_MODE) ?: LayoutMode.PORTRAIT_CONSOLE.name)
            }.getOrDefault(LayoutMode.PORTRAIT_CONSOLE),
            touchOpacity = intent.getFloatExtra(EXTRA_TOUCH_OPACITY, defaults.touchOpacity),
            touchScale = intent.getFloatExtra(EXTRA_TOUCH_SCALE, defaults.touchScale),
            hapticsEnabled = intent.getBooleanExtra(EXTRA_HAPTICS, defaults.hapticsEnabled),
            hapticIntensity = intent.getFloatExtra(EXTRA_HAPTIC_INTENSITY, defaults.hapticIntensity),
            showExtraButtons = intent.getBooleanExtra(EXTRA_SHOW_EXTRA_BTNS, defaults.showExtraButtons),
            forceAudioExt = intent.getStringExtra(EXTRA_AUDIO_EXT) ?: defaults.forceAudioExt,
            smoothScaling = intent.getBooleanExtra(EXTRA_SMOOTH_SCALING, defaults.smoothScaling),
            integerScaling = intent.getBooleanExtra(EXTRA_INTEGER_SCALING, defaults.integerScaling),
            textScale = intent.getFloatExtra(EXTRA_TEXT_SCALE, defaults.textScale),
            hideVirtualGamepad = intent.getBooleanExtra(EXTRA_HIDE_GAMEPAD, defaults.hideVirtualGamepad),
            diagonalMovement = intent.getBooleanExtra(EXTRA_DIAGONAL, defaults.diagonalMovement),
            keepScreenOn = intent.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, defaults.keepScreenOn),
            displayCutoutMode = runCatching {
                DisplayCutoutMode.valueOf(intent.getStringExtra(EXTRA_DISPLAY_CUTOUT_MODE) ?: defaults.displayCutoutMode.name)
            }.getOrDefault(defaults.displayCutoutMode),
            useHttpServer = intent.getBooleanExtra(EXTRA_USE_HTTP_SERVER, defaults.useHttpServer),
            webgl = intent.getBooleanExtra(EXTRA_WEBGL, defaults.webgl),
            desktopMode = intent.getBooleanExtra(EXTRA_DESKTOP_MODE, defaults.desktopMode),
            allowExternalModules = intent.getBooleanExtra(EXTRA_ALLOW_EXTERNAL, defaults.allowExternalModules),
            dialogLogs = intent.getBooleanExtra(EXTRA_DIALOG_LOGS, defaults.dialogLogs),
            useRuby18 = intent.getBooleanExtra(EXTRA_USE_RUBY18, defaults.useRuby18),
            vsync = intent.getBooleanExtra(EXTRA_VSYNC, defaults.vsync),
            frameSkip = intent.getBooleanExtra(EXTRA_FRAME_SKIP, defaults.frameSkip),
            shaders = intent.getBooleanExtra(EXTRA_SHADERS, defaults.shaders),
            controllerHomeShortcut = controllerShortcut(EXTRA_CONTROLLER_HOME_SHORTCUT, defaults.controllerHomeShortcut),
            controllerKeyboardShortcut = controllerShortcut(EXTRA_CONTROLLER_KEYBOARD_SHORTCUT, defaults.controllerKeyboardShortcut),
            controllerRuntimeMenuShortcut = controllerShortcut(EXTRA_CONTROLLER_RUNTIME_MENU_SHORTCUT, defaults.controllerRuntimeMenuShortcut),
            controllerResumeShortcut = controllerShortcut(EXTRA_CONTROLLER_RESUME_SHORTCUT, defaults.controllerResumeShortcut),
        )

        if (settings.keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        applyImmersiveMode()

        // Force orientation based on layout mode
        if (
            engineType == EngineType.RENPY ||
            settings.layoutMode == LayoutMode.LANDSCAPE ||
            settings.layoutMode == LayoutMode.GAMEPAD
        ) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        // Debug: show received settings
        android.util.Log.d("Runestone", "GameActivity: layoutMode=${settings.layoutMode}, path=$gamePath")

        if (engineType == EngineType.RENPY) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
            EngineType.RM95, EngineType.DANTE98,
            EngineType.WOLF, EngineType.KIRIKIRI, EngineType.UNITY,
            EngineType.UNREAL, EngineType.GAMEMAKER, EngineType.AGS -> showLegacyDialog(engineType)
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
        rootView = root
        installSafeAreaInsets(root)
        setContentView(root)

        // ── Game area (fills all space for landscape/gamepad, split for portrait console) ──
        val hideOverlay = settings.hideVirtualGamepad
        if (isPortraitConsole && !hideOverlay) {
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
        } else if (isPortraitConsole && hideOverlay) {
            // Portrait, no virtual gamepad — game fills screen
            val engine = WebViewEngine(this)
            webViewEngine = engine
            root.addView(engine, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        } else {
            // Landscape or Gamepad: game fills the whole screen
            val engine = WebViewEngine(this)
            webViewEngine = engine
            root.addView(engine, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))

            if (isLandscape && !hideOverlay) {
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
                smoothScaling = settings.smoothScaling,
                integerScaling = settings.integerScaling,
                textScale = settings.textScale,
                webgl = settings.webgl,
                desktopMode = settings.desktopMode,
                allowExternalModules = settings.allowExternalModules,
                dialogLogs = settings.dialogLogs,
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
                goHomePaused()
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
            diagonalMovement = settings.diagonalMovement
            controlsOnly = (settings.layoutMode == LayoutMode.PORTRAIT_CONSOLE)

            onInput = inputHandler@{ zone, pressed ->
                if (zone == TouchOverlayView.Zone.SETTINGS && pressed) {
                    openSettings()
                    return@inputHandler
                }
                if (zone == TouchOverlayView.Zone.HOME && pressed) {
                    goHomePaused()
                    return@inputHandler
                }
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
            }
        }
        this@GameActivity.overlayView = overlay
        container.addView(overlay)
    }

    private fun openSettings() {
        val overlay = overlayView
        if (overlay != null) {
            overlay.toggleQuickSettings()
        } else {
            showRuntimeActions()
        }
    }

    private fun showRuntimeActions() {
        val root = rootView ?: return
        runtimeActionsOverlay?.let {
            root.removeView(it)
            runtimeActionsOverlay = null
            return
        }

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            isClickable = true
            isFocusable = true
            setOnClickListener { dismissRuntimeActions() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                    dismissRuntimeActions()
                    true
                } else {
                    false
                }
            }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 12, 11, 16))
                setStroke(dp(1), Color.argb(80, 200, 180, 140))
                cornerRadius = dp(18).toFloat()
            }
            isClickable = true
        }
        panel.addView(TextView(this).apply {
            text = "RUNTIME"
            setTextColor(Color.rgb(220, 200, 160))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        })
        panel.addView(runtimeActionButton("RESUME") { dismissRuntimeActions() })
        panel.addView(runtimeActionButton("KEYBOARD") {
            dismissRuntimeActions()
            toggleKeyboard()
        })
        panel.addView(runtimeActionButton("HOME") {
            dismissRuntimeActions()
            goHomePaused()
        })
        overlay.addView(panel, FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.72f).toInt().coerceAtLeast(dp(260)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        runtimeActionsOverlay = overlay
        overlay.requestFocus()
    }

    private fun goHomePaused() {
        getSharedPreferences("runestone", MODE_PRIVATE).edit()
            .putBoolean("game_minimized", true)
            .putString("paused_game", gamePath)
            .apply()
        val intent = Intent(this@GameActivity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyboardVisible && event.isControllerEvent()) {
            keyboardView?.handleControllerKey(event)?.let { handled ->
                if (handled) return true
            }
        }
        if (webViewEngine != null && event.isControllerEvent()) {
            if (handleControllerCombo(event)) return true
            val mapped = mapControllerKey(event)
            if (mapped != null) {
                dispatchMappedGameKey(mapped, event.action)
                return true
            }
        }

        // Forward keyboard events to the game's JS input system
        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            val engine = webViewEngine
            if (engine != null) {
                val isDown = event.action == KeyEvent.ACTION_DOWN
                val keyChar = event.unicodeChar
                val keyCode = event.keyCode

                // Forward as Android key event for WebView
                engine.dispatchKeyEvent(event)

                // Also inject into RPG Maker's Input system via JS
                val jsAction = if (isDown) "_onKeyDown" else "_onKeyUp"
                val js = """(function(){
                    try {
                        if (window.Input && window.Input.$jsAction)
                            window.Input.$jsAction({which:$keyCode, keyCode:$keyCode});
                        if (window.TouchInput && window.TouchInput.$jsAction)
                            window.TouchInput.$jsAction({which:$keyCode, keyCode:$keyCode});
                        // Also forward character-based keys for chat mods
                        if ($isDown && $keyChar > 31) {
                            var c = String.fromCharCode($keyChar).toLowerCase();
                            window.dispatchEvent(new CustomEvent('rune_key', {detail:{key:c,code:$keyCode}}));
                        }
                    } catch(e){}
                })();""".trimIndent()
                engine.evaluateJavascript(js, null)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleControllerCombo(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            pressedControllerKeys.remove(event.keyCode)
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        pressedControllerKeys.add(event.keyCode)
        if (event.repeatCount > 0) return false

        return when {
            shortcutPressed(settings.controllerHomeShortcut) -> {
                goHomePaused()
                true
            }
            shortcutPressed(settings.controllerKeyboardShortcut) -> {
                toggleKeyboard()
                true
            }
            shortcutPressed(settings.controllerRuntimeMenuShortcut) -> {
                openSettings()
                true
            }
            else -> false
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (webViewEngine != null && event.isControllerEvent()) {
            if (handleTriggerHomeCombo(event)) return true
            if (keyboardVisible) return true
            val preset = controllerPresetFor(event.device)
            val activeButtons = ControllerMapper.mapAxisToButtons(event, preset).toSet()
            val released = activeControllerAxisButtons - activeButtons
            val pressed = activeButtons - activeControllerAxisButtons

            released.forEach { dispatchMappedGameButton(it, KeyEvent.ACTION_UP) }
            pressed.forEach { dispatchMappedGameButton(it, KeyEvent.ACTION_DOWN) }

            activeControllerAxisButtons.clear()
            activeControllerAxisButtons.addAll(activeButtons)
            if (pressed.isNotEmpty() || released.isNotEmpty()) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun mapControllerKey(event: KeyEvent): Int? {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return null
        if (event.repeatCount > 0 && event.action == KeyEvent.ACTION_DOWN) return null

        val directDpad = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> event.keyCode
            else -> null
        }
        if (directDpad != null) return directDpad

        val preset = controllerPresetFor(event.device)
        val button = ControllerMapper.mapKeyToButton(event, preset) ?: return null
        return ControllerMapper.toKeyCode(button)
    }

    private fun dispatchMappedGameButton(button: ControllerMapper.GameButton, action: Int) {
        dispatchMappedGameKey(ControllerMapper.toKeyCode(button), action)
    }

    private fun dispatchMappedGameKey(keyCode: Int, action: Int) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val engine = webViewEngine ?: return
        val keyEvent = KeyEvent(action, keyCode)
        engine.dispatchKeyEvent(keyEvent)
        val jsAction = if (action == KeyEvent.ACTION_DOWN) "_onKeyDown" else "_onKeyUp"
        val js = """(function(){
            try {
                var ev = {which:$keyCode, keyCode:$keyCode};
                if (window.Input && window.Input.$jsAction) window.Input.$jsAction(ev);
                if (window.TouchInput && window.TouchInput.$jsAction) window.TouchInput.$jsAction(ev);
                window.dispatchEvent(new KeyboardEvent('${if (action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"}', {
                    keyCode:$keyCode,
                    which:$keyCode,
                    bubbles:true
                }));
            } catch(e) {}
        })();""".trimIndent()
        engine.evaluateJavascript(js, null)
    }

    private fun controllerPresetFor(device: android.view.InputDevice?): ControllerMapper.ControllerPreset {
        if (device == null) return ControllerMapper.getPreset("generic")
        val current = controllerPresetId
        if (current != null) return ControllerMapper.getPreset(current)
        val detected = ControllerMapper.detectPreset(device)
        controllerPresetId = detected
        return ControllerMapper.getPreset(detected)
    }

    private fun android.view.InputEvent.isControllerEvent(): Boolean {
        val controllerSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
        return source and controllerSources != 0
    }

    private var keyboardVisible = false

    private fun toggleKeyboard() {
        val root = rootView ?: return
        val existing = keyboardView
        if (existing != null) {
            root.removeView(existing)
            keyboardView = null
            keyboardVisible = false
            return
        }

        keyboardView = RunestoneKeyboardView.attachTo(root).apply {
            onText = { text -> sendKeyboardText(text) }
            onKeyCode = { keyCode -> sendKeyboardKey(keyCode) }
            onHide = { toggleKeyboard() }
        }
        keyboardVisible = true
    }

    private fun dismissRuntimeActions() {
        val overlay = runtimeActionsOverlay ?: return
        rootView?.removeView(overlay)
        runtimeActionsOverlay = null
    }

    private fun runtimeActionButton(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.rgb(230, 220, 200))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(70, 200, 170, 130))
                setStroke(dp(1), Color.argb(85, 210, 185, 145))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { action() }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            lp.setMargins(0, dp(4), 0, dp(4))
            layoutParams = lp
        }

    private fun sendKeyboardText(text: String) {
        val engine = webViewEngine ?: return
        text.forEach { char ->
            val keyCode = keyCodeForChar(char)
            val js = """(function(){
                try {
                    var key = ${JSONObject.quote(char.toString())};
                    var code = $keyCode;
                    window.dispatchEvent(new KeyboardEvent('keydown', {key:key, keyCode:code, which:code, bubbles:true}));
                    if (window.Input && window.Input._onKeyDown) window.Input._onKeyDown({key:key, keyCode:code, which:code});
                    window.dispatchEvent(new KeyboardEvent('keypress', {key:key, keyCode:code, which:code, bubbles:true}));
                    window.dispatchEvent(new InputEvent('input', {data:key, inputType:'insertText', bubbles:true}));
                    window.dispatchEvent(new KeyboardEvent('keyup', {key:key, keyCode:code, which:code, bubbles:true}));
                    if (window.Input && window.Input._onKeyUp) window.Input._onKeyUp({key:key, keyCode:code, which:code});
                } catch(e) {}
            })();""".trimIndent()
            if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            engine.evaluateJavascript(js, null)
        }
    }

    private fun sendKeyboardKey(keyCode: Int) {
        val engine = webViewEngine ?: return
        engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        engine.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        val key = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> "Backspace"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            else -> ""
        }
        val inputType = if (keyCode == KeyEvent.KEYCODE_DEL) "deleteContentBackward" else "insertLineBreak"
        val js = """(function(){
            try {
                var key = ${JSONObject.quote(key)};
                var code = $keyCode;
                window.dispatchEvent(new KeyboardEvent('keydown', {key:key, keyCode:code, which:code, bubbles:true}));
                if (window.Input && window.Input._onKeyDown) window.Input._onKeyDown({key:key, keyCode:code, which:code});
                window.dispatchEvent(new InputEvent('input', {data:null, inputType:'$inputType', bubbles:true}));
                window.dispatchEvent(new KeyboardEvent('keyup', {key:key, keyCode:code, which:code, bubbles:true}));
                if (window.Input && window.Input._onKeyUp) window.Input._onKeyUp({key:key, keyCode:code, which:code});
            } catch(e) {}
        })();""".trimIndent()
        engine.evaluateJavascript(js, null)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun controllerShortcut(extra: String, default: ControllerShortcut): ControllerShortcut =
        runCatching {
            ControllerShortcut.valueOf(intent.getStringExtra(extra) ?: default.name)
        }.getOrDefault(default)

    private fun shortcutPressed(shortcut: ControllerShortcut): Boolean = when (shortcut) {
        ControllerShortcut.OFF -> false
        ControllerShortcut.L2_R2 ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2)
        ControllerShortcut.L1_R1 ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L1) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R1)
        ControllerShortcut.START_SELECT ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_SELECT)
        ControllerShortcut.L2_START ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START)
        ControllerShortcut.R2_START ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START)
    }

    private fun handleTriggerHomeCombo(event: MotionEvent): Boolean {
        if (settings.controllerHomeShortcut != ControllerShortcut.L2_R2) {
            triggerHomeComboDown = false
            return false
        }
        val left = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE),
        )
        val right = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS),
        )
        val bothPressed = left > 0.55f && right > 0.55f
        if (!bothPressed) {
            triggerHomeComboDown = false
            return false
        }
        if (triggerHomeComboDown) return true
        triggerHomeComboDown = true
        goHomePaused()
        return true
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (settings.displayCutoutMode == DisplayCutoutMode.EDGE_TO_EDGE) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    private fun installSafeAreaInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (settings.displayCutoutMode == DisplayCutoutMode.SAFE_AREA) {
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                view.setPadding(
                    maxOf(bars.left, cutout.left),
                    maxOf(bars.top, cutout.top),
                    maxOf(bars.right, cutout.right),
                    maxOf(0, cutout.bottom),
                )
            } else {
                view.setPadding(0, 0, 0, 0)
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun zoneToKeyCode(zone: TouchOverlayView.Zone): Int = when (zone) {
        TouchOverlayView.Zone.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
        TouchOverlayView.Zone.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        TouchOverlayView.Zone.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        TouchOverlayView.Zone.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        TouchOverlayView.Zone.BTN_A -> keyNameToCode(settings.firstButtonKey)
        TouchOverlayView.Zone.BTN_B -> keyNameToCode(settings.secondButtonKey)
        TouchOverlayView.Zone.BTN_X -> keyNameToCode(settings.thirdButtonKey)
        TouchOverlayView.Zone.BTN_Y -> keyNameToCode(settings.fourthButtonKey)
        TouchOverlayView.Zone.SELECT -> keyNameToCode(settings.leftButtonKey)
        TouchOverlayView.Zone.START -> keyNameToCode(settings.rightButtonKey)
        TouchOverlayView.Zone.MENU -> keyNameToCode(settings.leftMButtonKey)
        TouchOverlayView.Zone.SETTINGS -> keyNameToCode(settings.rightMButtonKey)
        TouchOverlayView.Zone.HOME -> KeyEvent.KEYCODE_HOME
        TouchOverlayView.Zone.L1 -> keyNameToCode(settings.fifthButtonKey)
        TouchOverlayView.Zone.R1 -> keyNameToCode(settings.sixthButtonKey)
    }

    private fun keyNameToCode(name: String): Int = when (name) {
        "ENTER" -> KeyEvent.KEYCODE_ENTER
        "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
        "SPACE" -> KeyEvent.KEYCODE_SPACE
        "TAB" -> KeyEvent.KEYCODE_TAB
        "Z" -> KeyEvent.KEYCODE_Z
        "X" -> KeyEvent.KEYCODE_X
        "Q" -> KeyEvent.KEYCODE_Q
        "B" -> KeyEvent.KEYCODE_B
        "A" -> KeyEvent.KEYCODE_A
        "S" -> KeyEvent.KEYCODE_S
        "D" -> KeyEvent.KEYCODE_D
        "W" -> KeyEvent.KEYCODE_W
        "V" -> KeyEvent.KEYCODE_V
        "C" -> KeyEvent.KEYCODE_C
        "F2" -> KeyEvent.KEYCODE_F2
        "F8" -> KeyEvent.KEYCODE_F8
        "CTRL_LEFT" -> KeyEvent.KEYCODE_CTRL_LEFT
        "SHIFT_LEFT" -> KeyEvent.KEYCODE_SHIFT_LEFT
        "ALT_LEFT" -> KeyEvent.KEYCODE_ALT_LEFT
        else -> KeyEvent.KEYCODE_UNKNOWN
    }

    private fun keyCodeForChar(char: Char): Int = when (char) {
        in 'a'..'z' -> KeyEvent.KEYCODE_A + (char - 'a')
        in 'A'..'Z' -> KeyEvent.KEYCODE_A + (char - 'A')
        in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
        ' ' -> KeyEvent.KEYCODE_SPACE
        else -> KeyEvent.KEYCODE_UNKNOWN
    }

    private fun launchRgssGame(gameDir: File) {
        Log.i(TAG, "launchRgssGame: $gameDir (engine=$engineType)")
        val intent = Intent().apply {
            setClassName(this@GameActivity, "com.hatkid.mkxpz.MainActivity")
            putExtra("com.runestone.app.extra.GAME_PATH", gameDir.absolutePath)
            putExtra("com.runestone.app.extra.LAYOUT_MODE", settings.layoutMode.name)
            putExtra("com.runestone.app.extra.TOUCH_OPACITY", settings.touchOpacity)
            putExtra("com.runestone.app.extra.TOUCH_SCALE", settings.touchScale)
            putExtra("com.runestone.app.extra.HAPTICS_ENABLED", settings.hapticsEnabled)
            putExtra("com.runestone.app.extra.HAPTIC_INTENSITY", settings.hapticIntensity)
            putExtra("com.runestone.app.extra.DISPLAY_CUTOUT_MODE", settings.displayCutoutMode.name)
            putExtra("com.runestone.app.extra.CONTROLLER_HOME_SHORTCUT", settings.controllerHomeShortcut.name)
        }
        startActivity(intent)
        finish()
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
            putExtra("com.runestone.app.extra.GAME_PATH", gameDir.absolutePath)
            putExtra("com.runestone.app.extra.LAYOUT_MODE", settings.layoutMode.name)
            putExtra("com.runestone.app.extra.TOUCH_OPACITY", settings.touchOpacity)
            putExtra("com.runestone.app.extra.TOUCH_SCALE", settings.touchScale)
            putExtra("com.runestone.app.extra.HAPTICS_ENABLED", settings.hapticsEnabled)
            putExtra("com.runestone.app.extra.HAPTIC_INTENSITY", settings.hapticIntensity)
            putExtra("com.runestone.app.extra.DISPLAY_CUTOUT_MODE", settings.displayCutoutMode.name)
            putExtra("com.runestone.app.extra.CONTROLLER_HOME_SHORTCUT", settings.controllerHomeShortcut.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    // ── Godot (MIT — native wrapper not integrated) ──────────────

    private fun launchGodotGame(gameDir: File) {
        Log.i(TAG, "Godot unavailable: ${gameDir.name}")
        UnavailableEngine.show(this, "Godot")
    }

    // ── NScripter / ONScripter (GPLv2+ — bundled native wrapper) ─

    private fun launchNScripterGame(gameDir: File) {
        Log.i(TAG, "ONScripter bundled: launching ${gameDir.name}")
        val saveDir = File(gameDir, "saves").apply { mkdirs() }
        val intent = Intent(this, com.runestone.app.engine.onscripter.OnscripterActivity::class.java).apply {
            putExtra("game_path", gameDir.absolutePath)
            putExtra("save_path", saveDir.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    // ── Ren'Py (MIT — bundled native wrapper) ────────────────────

    private fun launchRenpyGame(gameDir: File) {
        Log.i(TAG, "Ren'Py bundled: launching ${gameDir.name}")
        val saveDir = File(gameDir, "saves").apply { mkdirs() }
        val intent = Intent(this, org.renpy.android.PythonSDLActivity::class.java).apply {
            putExtra("game_path", gameDir.absolutePath)
            putExtra("save_path", saveDir.absolutePath)
            putExtra("engine_version", "8.3.4")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun showLegacyDialog(type: EngineType) {
        val title: String
        val message: String
        when (type) {
            EngineType.WOLF -> {
                title = "Unsupported Engine — ${type.label}"
                message = "This game uses Wolf RPG Editor.\n\nRunestone can detect these games, but it does not bundle a Wolf RPG runtime yet. The game files are installed correctly, but this engine cannot be played here yet."
            }
            EngineType.KIRIKIRI -> {
                title = "Unsupported Engine — ${type.label}"
                message = "This game uses KiriKiri/KAG.\n\nRunestone can detect these games, but it does not bundle a KiriKiri runtime. The game files are installed correctly, but this engine cannot be played here yet."
            }
            EngineType.UNITY, EngineType.UNREAL, EngineType.GAMEMAKER, EngineType.AGS -> {
                title = "Unsupported Engine — ${type.label}"
                message = "Runestone can identify this engine, but it does not bundle a compatible Android runtime for it. The game files are installed correctly, but this engine cannot be played here yet."
            }
            else -> {
                title = "Legacy Engine — ${type.label}"
                message = "This is a legacy engine from ${if (type == EngineType.DANTE98) "1992" else "1997"}.\n\nNo open-source runtime exists. These games require the original PC software."
            }
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
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
        releaseControllerAxes()
        webViewEngine?.onPause()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
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

    private fun releaseControllerAxes() {
        activeControllerAxisButtons.forEach { dispatchMappedGameButton(it, KeyEvent.ACTION_UP) }
        activeControllerAxisButtons.clear()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewEngine?.destroy()
        webViewEngine = null
    }
}
