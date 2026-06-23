/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.runtime

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
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
import com.runestone.app.R
import com.runestone.app.data.ControllerShortcut
import com.runestone.app.data.DisplayCutoutMode
import com.runestone.app.data.EngineType
import com.runestone.app.data.GameConfigService
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings
import com.runestone.app.engine.WebViewEngine
import com.runestone.app.engine.WebglConfigBuilder
import com.runestone.app.input.ControlButtonProfile
import com.runestone.app.input.ControlProfile
import com.runestone.app.input.ControlProfileScope
import com.runestone.app.input.ControlProfileStore
import com.runestone.app.input.InputDispatcher
import com.runestone.app.input.RunestoneKeyboardView
import com.runestone.app.input.TouchOverlayView
import com.runestone.app.input.VirtualKeyboardOverlay
import com.runestone.app.workspace.WorkspaceManager
import java.io.File

class WebViewGameSession(
    private val activity: Activity,
    private val gameDir: File,
    private val settings: RunnerSettings,
    private val engineType: EngineType,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onGoHomePaused()
        fun onOpenSettings()
        fun onToggleKeyboard()
        fun onPersistInputSettings(layoutMode: LayoutMode, hideGamepad: Boolean)
        fun onPersistControlProfile(buttons: List<ControlButtonProfile>)
    }

    var webViewEngine: WebViewEngine? = null
    var overlayView: TouchOverlayView? = null
    var overlayContainer: ViewGroup? = null
    var rootView: FrameLayout? = null
    var keyboardView: RunestoneKeyboardView? = null
    var virtualKeyboardView: VirtualKeyboardOverlay? = null
    var keyboardVisible: Boolean = false
    var runtimeActionsOverlay: View? = null
    var menuBtn: TextView? = null
    var recoveryBtn: View? = null

    private var inputDispatcher: InputDispatcher? = null
    private var immersiveDecorConfigured = false
    private var lastImmersiveApplyAt = 0L
    private var lastAppliedCutoutMode: DisplayCutoutMode? = null

    private var storageName: String? = null

    fun launch() {
        val root = FrameLayout(activity).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.BLACK)
        }
        rootView = root
        installSafeAreaInsets(root)
        activity.setContentView(root)

        val engine = WebViewEngine(activity)
        webViewEngine = engine
        createInputDispatcher()
        rebuildLayout()
        engine.loadGame(gameDir.absolutePath, WebViewEngine.WebViewGameConfig(
            title = gameDir.name,
            addGamepad = false,
            fakeGreenworks = true,
            showFps = true,
            forceAudioExt = settings.forceAudioExt,
            smoothScaling = settings.smoothScaling,
            integerScaling = settings.integerScaling,
            textScale = settings.textScale,
            webgl = settings.webgl,
            useWebgl2 = settings.useWebgl2,
            forceCanvas = settings.forceCanvas,
            useHttpServer = settings.useHttpServer,
            useAsmjsEffekseer = settings.useAsmjsEffekseer,
            engineFamily = engineTypeToFamily(engineType),
            desktopMode = settings.desktopMode,
            allowExternalModules = settings.allowExternalModules,
            dialogLogs = settings.dialogLogs,
        ))
        engine.isFocusable = true
        engine.isFocusableInTouchMode = true
    }

    fun rebuildLayout() {
        val root = rootView ?: return
        val engine = webViewEngine ?: return
        (engine.parent as? ViewGroup)?.removeView(engine)
        root.removeAllViews()
        overlayView = null
        overlayContainer = null

        val isLandscape = settings.layoutMode == LayoutMode.LANDSCAPE
        val isPortraitConsole = settings.layoutMode == LayoutMode.PORTRAIT_CONSOLE
        val hideOverlay = settings.hideVirtualGamepad

        if (isPortraitConsole && !hideOverlay) {
            val splitLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            root.addView(splitLayout)

            val gameArea = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    0.52f,
                )
            }
            splitLayout.addView(gameArea)

            gameArea.addView(engine, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))

            val controlPanel = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    0.48f,
                )
                setBackgroundColor(Color.rgb(10, 10, 12))
            }
            splitLayout.addView(controlPanel)

            setupTouchOverlay(controlPanel, engine, 0f, 0f, 0f, 0f)
        } else if (isPortraitConsole && hideOverlay) {
            root.addView(engine, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        } else {
            if (isLandscape) {
                val targetGameRatio = 4f / 3f
                val screenW = root.width.coerceAtLeast(1)
                val screenH = root.height.coerceAtLeast(1)
                val gameH = screenH
                val gameW = minOf(screenW, (gameH * targetGameRatio).toInt())
                val marginLeft = (screenW - gameW) / 2
                val marginRight = marginLeft

                root.addView(engine, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    leftMargin = marginLeft
                    rightMargin = marginRight
                })

                if (!hideOverlay) {
                    val overlayContainer = FrameLayout(activity).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                    root.addView(overlayContainer)
                    setupTouchOverlay(overlayContainer, engine, marginLeft.toFloat(), 0f, (screenW - marginRight).toFloat(), screenH.toFloat())
                }
            } else {
                root.addView(engine, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))

                if (!hideOverlay) {
                    val overlayContainer = FrameLayout(activity).apply {
                        setBackgroundColor(Color.TRANSPARENT)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                    root.addView(overlayContainer)
                    setupTouchOverlay(overlayContainer, engine, 0f, 0f, 0f, 0f)
                }
            }
        }

        addChrome(root)
    }

    fun addChrome(root: FrameLayout) {
        addRecoveryButton(root)
    }

    private fun addRecoveryButton(root: FrameLayout) {
        val btn = TextView(activity).apply {
            text = "\u2022\u2022\u2022"
            textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(220, 210, 190))
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 8, 8, 10))
                setStroke(dp(1), Color.argb(80, 160, 140, 110))
                cornerRadius = dp(22).toFloat()
            }
            minimumWidth = dp(44)
            minimumHeight = dp(44)
            setOnClickListener { showOverlayMenu() }
            val p = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            p.gravity = Gravity.TOP or Gravity.END
            p.topMargin = dp(8); p.rightMargin = dp(8)
            layoutParams = p
            tag = "recovery_btn"
        }
        root.addView(btn)
        recoveryBtn = btn
    }

    fun setupTouchOverlay(container: ViewGroup, engine: WebViewEngine, gameLeft: Float, gameTop: Float, gameRight: Float, gameBottom: Float) {
        val overlay = TouchOverlayView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            alpha = settings.touchOpacity
            scale = settings.touchScale
            hapticsEnabled = settings.hapticsEnabled
            hapticIntensity = settings.hapticIntensity
            controllerPreset = runCatching {
                TouchOverlayView.ControllerPreset.valueOf(settings.controllerPreset)
            }.getOrDefault(TouchOverlayView.ControllerPreset.SIMPLIFIED)
            diagonalMovement = settings.diagonalMovement
            controlsOnly = (settings.layoutMode == LayoutMode.PORTRAIT_CONSOLE)
            gameViewportLeft = gameLeft
            gameViewportTop = gameTop
            gameViewportRight = gameRight
            gameViewportBottom = gameBottom
            onToggleControls = { setVirtualControlsVisible(false) }
            onRotateLayout = { rotateLayout() }
            onProfileLayoutChanged = { buttons ->
                persistControlProfile(buttons)
            }

            onInput = inputHandler@{ zone, pressed ->
                if (zone == TouchOverlayView.Zone.SETTINGS && pressed) {
                    callbacks.onOpenSettings()
                    return@inputHandler
                }
                if (zone == TouchOverlayView.Zone.HOME && pressed) {
                    callbacks.onGoHomePaused()
                    return@inputHandler
                }
                val keyCode = InputDispatcher.zoneToKeyCode(zone)
                val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                engine.dispatchKeyEvent(KeyEvent(action, keyCode))

                val js = when {
                    pressed && zone == TouchOverlayView.Zone.DPAD_UP -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('up');"
                    pressed && zone == TouchOverlayView.Zone.DPAD_DOWN -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('down');"
                    pressed && zone == TouchOverlayView.Zone.DPAD_LEFT -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('left');"
                    pressed && zone == TouchOverlayView.Zone.DPAD_RIGHT -> "if(TouchInput&&TouchInput._onDown)TouchInput._onDown('right');"
                    pressed && zone == TouchOverlayView.Zone.BTN_CONFIRM -> "if(TouchInput&&TouchInput._onOk)TouchInput._onOk();"
                    pressed && zone == TouchOverlayView.Zone.BTN_BACK -> "if(TouchInput&&TouchInput._onCancel)TouchInput._onCancel();"
                    pressed && zone == TouchOverlayView.Zone.BTN_DASH -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:16});"
                    pressed && zone == TouchOverlayView.Zone.BTN_EXTRA_A -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:65});"
                    pressed && zone == TouchOverlayView.Zone.BTN_EXTRA_S -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:83});"
                    pressed && zone == TouchOverlayView.Zone.BTN_EXTRA_D -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:68});"
                    pressed && zone == TouchOverlayView.Zone.BTN_EXTRA_Z -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:90});"
                    pressed && zone == TouchOverlayView.Zone.BTN_EXTRA_X -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:88});"
                    pressed && zone == TouchOverlayView.Zone.BTN_EXTRA_C -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:67});"
                    pressed && zone == TouchOverlayView.Zone.BTN_CTRL -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:17});"
                    pressed && zone == TouchOverlayView.Zone.BTN_ALT -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:18});"
                    pressed && zone == TouchOverlayView.Zone.BTN_SHIFT -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:16});"
                    pressed && zone == TouchOverlayView.Zone.SELECT -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:27});"
                    pressed && zone == TouchOverlayView.Zone.START -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:13});"
                    pressed && zone == TouchOverlayView.Zone.L1 -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:33});"
                    pressed && zone == TouchOverlayView.Zone.R1 -> "if(Input&&Input._onKeyDown)Input._onKeyDown({which:34});"
                    else -> ""
                }
                if (js.isNotEmpty()) {
                    engine.evaluateJavascript("(function(){try{$js}catch(e){}})();", null)
                }
            }
        }
        this@WebViewGameSession.overlayView = overlay
        this@WebViewGameSession.overlayContainer = container
        container.addView(overlay)
    }

    fun showRuntimeActions() {
        val root = rootView ?: return
        runtimeActionsOverlay?.let {
            root.removeView(it)
            runtimeActionsOverlay = null
            menuBtn?.rotation = 0f
            return
        }
        menuBtn?.rotation = 180f

        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(95, 0, 0, 0))
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
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(222, 12, 11, 16))
                setStroke(dp(1), Color.argb(85, 200, 180, 140))
                cornerRadius = dp(12).toFloat()
            }
            isClickable = true
        }
        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        topRow.addView(runtimeActionButton("RESUME", R.drawable.ic_runtime_resume) { dismissRuntimeActions() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        topRow.addView(runtimeActionButton("HOME", R.drawable.ic_runtime_home) {
            dismissRuntimeActions()
            callbacks.onGoHomePaused()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        panel.addView(topRow)

        panel.addView(runtimeToggleButton(!settings.hideVirtualGamepad) {
            setVirtualControlsVisible(settings.hideVirtualGamepad)
            dismissRuntimeActions()
        })

        val modeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val isFull = overlayView?.controllerPreset == TouchOverlayView.ControllerPreset.FULL
        modeRow.addView(runtimeActionButton(
            if (isFull) "BASIC" else "FULL",
            R.drawable.ic_runtime_controls,
        ) {
            dismissRuntimeActions()
            toggleControllerPreset()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
        })
        modeRow.addView(runtimeActionButton(
            if (settings.layoutMode == LayoutMode.LANDSCAPE) "PORTRAIT" else "LANDSCAPE",
            R.drawable.ic_runtime_rotate,
        ) {
            dismissRuntimeActions()
            rotateLayout()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6); rightMargin = dp(6) })
        modeRow.addView(runtimeActionButton("EDIT", R.drawable.ic_runtime_edit) {
            dismissRuntimeActions()
            openControlLayoutEditor()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6); rightMargin = dp(6) })
        modeRow.addView(runtimeActionButton("KEYBOARD", R.drawable.ic_runtime_keyboard) {
            dismissRuntimeActions()
            callbacks.onToggleKeyboard()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        panel.addView(modeRow)

        overlay.addView(panel, FrameLayout.LayoutParams(
            (activity.resources.displayMetrics.widthPixels * 0.72f).toInt().coerceIn(dp(260), dp(480)),
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

    fun dismissRuntimeActions() {
        val overlay = runtimeActionsOverlay ?: return
        rootView?.removeView(overlay)
        runtimeActionsOverlay = null
        menuBtn?.rotation = 0f
    }

    fun toggleControllerPreset() {
        val overlay = overlayView
        if (overlay == null) return
        val next = if (overlay.controllerPreset == TouchOverlayView.ControllerPreset.SIMPLIFIED)
            TouchOverlayView.ControllerPreset.FULL
        else
            TouchOverlayView.ControllerPreset.SIMPLIFIED
        overlay.setPreset(next)
        callbacks.onPersistInputSettings(settings.layoutMode, settings.hideVirtualGamepad)
        Toast.makeText(activity, "Controller: ${next.name}", Toast.LENGTH_SHORT).show()
    }

    fun setVirtualControlsVisible(visible: Boolean) {
        val overlay = overlayView
        if (overlay != null) {
            overlay.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            callbacks.onPersistInputSettings(settings.layoutMode, !visible)
            Toast.makeText(activity, if (visible) "Controls shown" else "Controls hidden", Toast.LENGTH_SHORT).show()
            return
        }
        webViewEngine?.let { engine ->
            rebuildLayout()
            callbacks.onPersistInputSettings(settings.layoutMode, !visible)
            Toast.makeText(activity, if (visible) "Controls shown" else "Controls hidden", Toast.LENGTH_SHORT).show()
            return
        }
        callbacks.onPersistInputSettings(settings.layoutMode, !visible)
        Toast.makeText(activity, "Controls will update next launch", Toast.LENGTH_SHORT).show()
    }

    fun rotateLayout() {
        val overlay = overlayView
        webViewEngine?.let { engine ->
            rootView?.post { rebuildLayout() }
        }
        callbacks.onPersistInputSettings(settings.layoutMode, settings.hideVirtualGamepad)
        val note = if (webViewEngine != null) {
            "Layout rotated"
        } else {
            "Saved. Native runtime applies it next launch."
        }
        Toast.makeText(activity, note, Toast.LENGTH_SHORT).show()
    }

    fun openControlLayoutEditor() {
        val overlay = overlayView
        if (overlay != null) {
            overlay.openLayoutEditor()
            return
        }
        if (webViewEngine != null) {
            setVirtualControlsVisible(true)
            rootView?.post {
                overlayView?.openLayoutEditor()
            }
        } else {
            Toast.makeText(activity, "Control editor opens in WebView sessions for now", Toast.LENGTH_SHORT).show()
        }
    }

    fun persistInputSettings() {
        val name = storageName ?: return
        runCatching {
            val service = GameConfigService(activity, WorkspaceManager(activity))
            val current = service.loadPerGame(name)
            service.savePerGame(
                name,
                current.copy(
                    input = current.input.copy(
                        layoutMode = settings.layoutMode.name.lowercase(),
                        hideVirtualGamepad = settings.hideVirtualGamepad,
                    ),
                ),
            )
        }.onFailure {
            Log.w("Runestone", "Failed to persist runtime input settings", it)
        }
    }

    fun persistControlProfile(buttons: List<ControlButtonProfile>) {
        if (buttons.isEmpty()) return
        runCatching {
            val store = ControlProfileStore(activity)
            val existing = store.loadEffective(engineType, storageName, settings)
            val editedLayout = buttons.first().layout
            val mergedButtons = existing.buttons.filterNot { it.layout == editedLayout } + buttons
            val name = storageName
            val scope = if (name != null) ControlProfileScope.GAME else ControlProfileScope.ENGINE
            store.save(
                ControlProfile(
                    id = if (name != null) "custom-$name" else "custom-${engineType.name.lowercase()}",
                    name = "Custom Layout",
                    scope = scope,
                    engineType = engineType,
                    storageName = name,
                    buttons = mergedButtons,
                ),
            )
            Toast.makeText(activity, "Control layout saved", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Log.w("Runestone", "Failed to persist control profile", it)
        }
    }

    private fun runtimeActionButton(label: String, iconRes: Int, action: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            setTextColor(Color.rgb(230, 220, 200))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0)
            compoundDrawablePadding = dp(4)
            background = GradientDrawable().apply {
                setColor(Color.argb(70, 200, 170, 130))
                setStroke(dp(1), Color.argb(85, 210, 185, 145))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun runtimeToggleButton(enabled: Boolean, action: () -> Unit): TextView =
        TextView(activity).apply {
            text = if (enabled) "CONTROLS ON" else "CONTROLS OFF"
            setTextColor(if (enabled) Color.rgb(245, 228, 190) else Color.rgb(170, 160, 145))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_runtime_controls, 0, 0)
            compoundDrawablePadding = dp(4)
            background = GradientDrawable().apply {
                setColor(if (enabled) Color.argb(105, 120, 95, 62) else Color.argb(55, 80, 75, 70))
                setStroke(dp(1), if (enabled) Color.argb(120, 225, 195, 140) else Color.argb(70, 160, 150, 130))
                cornerRadius = dp(11).toFloat()
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, dp(10), 0, dp(10))
            }
        }

    fun showOverlayMenu() {
        val overlay = overlayView
        if (overlay != null) {
            overlay.toggleMenuOverlay()
            return
        }
        showRuntimeActions()
    }

    fun handleBack(): Boolean {
        val engine = webViewEngine
        if (engine != null) {
            val shouldQuit = engine.handleBack()
            if (shouldQuit) {
                activity.getSharedPreferences("runestone", Activity.MODE_PRIVATE).edit()
                    .remove("paused_game")
                    .remove("game_minimized")
                    .apply()
                return true
            }
            return false
        }
        activity.getSharedPreferences("runestone", Activity.MODE_PRIVATE).edit()
            .remove("paused_game")
            .remove("game_minimized")
            .apply()
        return true
    }

    fun onPause() {
        inputDispatcher?.releaseControllerAxes()
        webViewEngine?.onPause()
    }

    fun onResume() {
        applyImmersiveMode()
        webViewEngine?.resumeTimers()
        webViewEngine?.onResume()
    }

    fun onDestroy() {
        webViewEngine?.destroy()
        webViewEngine = null
    }

    fun toggleKeyboard() {
        val root = rootView ?: return
        val existing = virtualKeyboardView
        if (existing != null) {
            root.removeView(existing)
            virtualKeyboardView = null
            keyboardVisible = false
            return
        }

        val kb = VirtualKeyboardOverlay(activity)
        kb.kbOpacity = settings.touchOpacity
        kb.kbScale = settings.touchScale
        kb.landscapeKeys = (settings.layoutMode == LayoutMode.LANDSCAPE)
        kb.dockMode = VirtualKeyboardOverlay.DockMode.BOTTOM
        kb.onKeyDown = { code -> inputDispatcher?.sendKeyboardKey(code) }
        kb.onKeyUp = { code -> inputDispatcher?.sendKeyboardKeyUp(code) }
        kb.onDockModeChanged = { mode ->
            toggleKeyboard()
            toggleKeyboard()
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        root.addView(kb, lp)
        virtualKeyboardView = kb
        keyboardVisible = true
    }

    private fun createInputDispatcher(): InputDispatcher {
        val dispatcher = InputDispatcher(webViewEngine)
        inputDispatcher = dispatcher
        return dispatcher
    }

    private fun applyImmersiveMode(force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val cutoutChanged = lastAppliedCutoutMode != settings.displayCutoutMode
        if (!force && !cutoutChanged && now - lastImmersiveApplyAt < 350L) return
        lastImmersiveApplyAt = now

        if (!immersiveDecorConfigured) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            immersiveDecorConfigured = true
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())

        if (android.os.Build.VERSION.SDK_INT >= 28 && cutoutChanged) {
            activity.window.attributes = activity.window.attributes.apply {
                layoutInDisplayCutoutMode = if (settings.displayCutoutMode == DisplayCutoutMode.EDGE_TO_EDGE) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
        lastAppliedCutoutMode = settings.displayCutoutMode
    }

    private fun installSafeAreaInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (settings.displayCutoutMode == DisplayCutoutMode.SAFE_AREA) {
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                val left = maxOf(bars.left, cutout.left)
                val top = maxOf(bars.top, cutout.top)
                val right = maxOf(bars.right, cutout.right)
                val bottom = maxOf(0, cutout.bottom)
                if (
                    view.paddingLeft != left ||
                    view.paddingTop != top ||
                    view.paddingRight != right ||
                    view.paddingBottom != bottom
                ) {
                    view.setPadding(left, top, right, bottom)
                }
            } else {
                if (
                    view.paddingLeft != 0 ||
                    view.paddingTop != 0 ||
                    view.paddingRight != 0 ||
                    view.paddingBottom != 0
                ) {
                    view.setPadding(0, 0, 0, 0)
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun engineTypeToFamily(type: EngineType): WebglConfigBuilder.EngineFamily = when (type) {
        EngineType.MV -> WebglConfigBuilder.EngineFamily.MV
        EngineType.MZ -> WebglConfigBuilder.EngineFamily.MZ
        else -> WebglConfigBuilder.EngineFamily.HTML
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
