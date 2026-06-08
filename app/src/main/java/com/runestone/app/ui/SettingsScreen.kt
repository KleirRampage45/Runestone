/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.runestone.app.data.ControllerShortcut
import com.runestone.app.data.DisplayCutoutMode
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings
import com.runestone.app.data.UIMode

class SettingsScreen(private val context: Context) {

    fun create(
        settings: RunnerSettings,
        onSettingsChanged: (RunnerSettings) -> Unit,
        onBack: () -> Unit,
        onResetDefaults: () -> Unit,
        onClearRuntimeCache: () -> Unit = {},
    ): LinearLayout {
        var current = settings.copy()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        root.addView(makeTopBar(onBack))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        root.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll.addView(content)
        content.alpha = 0f

        fun upd(transform: RunnerSettings.() -> RunnerSettings) {
            current = current.transform()
            onSettingsChanged(current)
        }

        // ────────────────────────────────────────────────
        //  1. DISPLAY & LAYOUT
        // ────────────────────────────────────────────────
        accordion(content, "DISPLAY & LAYOUT", "Screen mode, scaling, and UI options.") { panel ->
            panel.addView(
                layoutSelector(current.layoutMode) { upd { copy(layoutMode = it) } },
            )
            panel.addView(spacer(8))
            panel.addView(
                uiModeSelector(current.uiMode) { upd { copy(uiMode = it) } },
            )
            panel.addView(spacer(8))
            panel.addView(switchPanel("Smooth Scaling", "Bilinear filtering for scaled sprites.", current.smoothScaling) {
                upd { copy(smoothScaling = it) }
            })
            panel.addView(spacer(6))
            panel.addView(switchPanel("Integer Scaling", "Pixel-perfect scaling (no blur).", current.integerScaling) {
                upd { copy(integerScaling = it) }
            })
            panel.addView(spacer(6))
            panel.addView(sliderPanel("Text Scale", "${(current.textScale * 100).toInt()}%") { label ->
                slider(100, ((current.textScale - 0.5f) * 200).toInt().coerceIn(0, 100)) { progress ->
                    val v = 0.5f + (progress / 200f)
                    upd { copy(textScale = v) }
                    label.text = "${(v * 100).toInt()}%"
                }
            })
            panel.addView(spacer(6))
            panel.addView(switchPanel("Keep Screen On", "Prevent device sleep while playing.", current.keepScreenOn) {
                upd { copy(keepScreenOn = it) }
            })
            panel.addView(spacer(6))
            panel.addView(dropdownRow(
                "Display Cutout",
                current.displayCutoutMode.label,
                DisplayCutoutMode.values().map { it.label },
            ) { label ->
                val mode = DisplayCutoutMode.values().firstOrNull { it.label == label } ?: DisplayCutoutMode.SAFE_AREA
                upd { copy(displayCutoutMode = mode) }
            })
            panel.addView(spacer(6))
            panel.addView(switchPanel("Reduce Motion", "Disable UI animations for accessibility.", current.reduceMotion) {
                upd { copy(reduceMotion = it) }
            })
        }

        // ────────────────────────────────────────────────
        //  2. GAMEPAD & INPUT
        // ────────────────────────────────────────────────
        accordion(content, "GAMEPAD & INPUT", "Virtual controller, haptics, and button mapping.") { panel ->
            panel.addView(sliderPanel("Button Opacity", "${(current.touchOpacity * 100).toInt()}%") { label ->
                slider(100, (current.touchOpacity * 100).toInt().coerceIn(0, 100)) { progress ->
                    val v = progress / 100f
                    upd { copy(touchOpacity = v) }
                    label.text = "${(v * 100).toInt()}%"
                }
            })
            panel.addView(spacer(6))
            panel.addView(sliderPanel("Button Size", "${(current.touchScale * 100).toInt()}%") { label ->
                slider(100, ((current.touchScale - 0.5f) * 200).toInt().coerceIn(0, 100)) { progress ->
                    val v = 0.5f + (progress / 200f)
                    upd { copy(touchScale = v) }
                    label.text = "${(v * 100).toInt()}%"
                }
            })
            panel.addView(spacer(6))
            panel.addView(switchPanel("Hide Virtual Gamepad", "Hide on-screen controls entirely.", current.hideVirtualGamepad) {
                upd { copy(hideVirtualGamepad = it) }
            })
            panel.addView(spacer(6))
            panel.addView(switchPanel("Diagonal Movement", "Enable 8-direction D-pad input.", current.diagonalMovement) {
                upd { copy(diagonalMovement = it) }
            })

            // Haptic
            val hapticRow = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                if (!current.hapticsEnabled) visibility = View.GONE
            }
            hapticRow.addView(spacerAfter(6))
            hapticRow.addView(sliderPanel("Haptic Intensity", "${(current.hapticIntensity * 100).toInt()}%") { label ->
                slider(100, (current.hapticIntensity * 100).toInt().coerceIn(0, 100)) { progress ->
                    val v = progress / 100f
                    upd { copy(hapticIntensity = v) }
                    label.text = "${(v * 100).toInt()}%"
                }
            })
            panel.addView(switchPanel("Haptic Feedback", "Vibrate when virtual controls are pressed.", current.hapticsEnabled) { checked ->
                upd { copy(hapticsEnabled = checked) }
                hapticRow.visibility = if (checked) View.VISIBLE else View.GONE
            })
            panel.addView(hapticRow)
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Show X/Y Buttons", "Extra RPG Maker keys on the overlay.", current.showExtraButtons) {
                upd { copy(showExtraButtons = it) }
            })
            panel.addView(spacerAfter(6))

            // Button mapping sub-accordion
            subAccordion(panel, "BUTTON MAPPING") { btnPanel ->
                val keyOptions = listOf("ENTER", "ESCAPE", "SPACE", "TAB", "Z", "X", "Q", "B", "A", "S", "D", "W", "V", "C",
                    "F2", "F8", "CTRL_LEFT", "SHIFT_LEFT", "ALT_LEFT")
                btnPanel.addView(compactDropdown("Left Button", current.leftButtonKey, keyOptions) { upd { copy(leftButtonKey = it) } })
                btnPanel.addView(compactDropdown("Right Button", current.rightButtonKey, keyOptions) { upd { copy(rightButtonKey = it) } })
                btnPanel.addView(compactDropdown("Left M Button", current.leftMButtonKey, keyOptions) { upd { copy(leftMButtonKey = it) } })
                btnPanel.addView(compactDropdown("Right M Button", current.rightMButtonKey, keyOptions) { upd { copy(rightMButtonKey = it) } })
                btnPanel.addView(compactDropdown("First Button", current.firstButtonKey, keyOptions) { upd { copy(firstButtonKey = it) } })
                btnPanel.addView(compactDropdown("Second Button", current.secondButtonKey, keyOptions) { upd { copy(secondButtonKey = it) } })
                btnPanel.addView(compactDropdown("Third Button", current.thirdButtonKey, keyOptions) { upd { copy(thirdButtonKey = it) } })
                btnPanel.addView(compactDropdown("Fourth Button", current.fourthButtonKey, keyOptions) { upd { copy(fourthButtonKey = it) } })
                btnPanel.addView(compactDropdown("Fifth Button", current.fifthButtonKey, keyOptions) { upd { copy(fifthButtonKey = it) } })
                btnPanel.addView(compactDropdown("Sixth Button", current.sixthButtonKey, keyOptions) { upd { copy(sixthButtonKey = it) } })
            }
            panel.addView(spacerAfter(6))
            subAccordion(panel, "CONTROLLER SHORTCUTS") { shortcutPanel ->
                val shortcuts = ControllerShortcut.values().map { it.label }
                fun shortcutFrom(label: String): ControllerShortcut =
                    ControllerShortcut.values().firstOrNull { it.label == label } ?: ControllerShortcut.OFF
                shortcutPanel.addView(compactDropdown("Minimize Game", current.controllerHomeShortcut.label, shortcuts) {
                    upd { copy(controllerHomeShortcut = shortcutFrom(it)) }
                })
                shortcutPanel.addView(compactDropdown("Runtime Keyboard", current.controllerKeyboardShortcut.label, shortcuts) {
                    upd { copy(controllerKeyboardShortcut = shortcutFrom(it)) }
                })
                shortcutPanel.addView(compactDropdown("Runtime Menu", current.controllerRuntimeMenuShortcut.label, shortcuts) {
                    upd { copy(controllerRuntimeMenuShortcut = shortcutFrom(it)) }
                })
                shortcutPanel.addView(compactDropdown("Resume Game", current.controllerResumeShortcut.label, shortcuts) {
                    upd { copy(controllerResumeShortcut = shortcutFrom(it)) }
                })
            }
        }

        // ────────────────────────────────────────────────
        //  3. AUDIO
        // ────────────────────────────────────────────────
        accordion(content, "AUDIO", "Audio format and emulation settings.") { panel ->
            panel.addView(
                audioSelector(current.forceAudioExt) { ext -> upd { copy(forceAudioExt = ext) } },
            )
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Disable Audio Emulation", "Use native audio path for compatibility.", current.disableAudioEmulation) {
                upd { copy(disableAudioEmulation = it) }
            })
        }

        // ────────────────────────────────────────────────
        //  4. RPG MAKER — RGSS (XP/VX/VX Ace)
        // ────────────────────────────────────────────────
        accordion(content, "RPG MAKER (RGSS)", "mkxp-z for XP/VX/VX Ace. Dialog logs wired; rest stored-only.") { panel ->
            panel.addView(switchPanel("Dialog Logs", "Capture and display message history.", current.dialogLogs) {
                upd { copy(dialogLogs = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Use Ruby 1.8", "Compatibility mode for older RGSS scripts.", current.useRuby18) {
                upd { copy(useRuby18 = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("VSync", "Sync rendering to display refresh rate.", current.vsync) {
                upd { copy(vsync = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Frame Skip", "Skip frames to maintain target FPS.", current.frameSkip) {
                upd { copy(frameSkip = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Shaders", "Enable shader/filter effects.", current.shaders) {
                upd { copy(shaders = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Path Cache", "Cache file/resource path lookups.", current.pathCache) {
                upd { copy(pathCache = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Reach Path Distance", "Extended pathfinding range support.", current.reachPathDistance) {
                upd { copy(reachPathDistance = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Enable Preload Scripts", "Load compat scripts before boot.", current.enablePreloadScripts) {
                upd { copy(enablePreloadScripts = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Update Graphics", "Compat toggle for graphics update loop.", current.updateGraphics) {
                upd { copy(updateGraphics = it) }
            })
            panel.addView(spacerAfter(6))
            val pfOptions = listOf("Normal", "Fast", "Slow")
            panel.addView(compactDropdown("Pixel Format Speed", current.pixelFormatSpeed, pfOptions) {
                upd { copy(pixelFormatSpeed = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Crop Left Y", "Crop left Y axis in rendering.", current.cropLeftY) {
                upd { copy(cropLeftY = it) }
            })
            panel.addView(spacerAfter(6))

            // Window size sub-accordion
            subAccordion(panel, "WINDOW & DISPLAY (stub)") { wPanel ->
                val widthOptions = listOf("320", "480", "640", "800", "1024", "1280")
                val heightOptions = listOf("240", "360", "480", "600", "768", "720")
                val alignOptions = listOf("CENTER", "TOP", "BOTTOM", "CENTER_TOP_HALF")
                wPanel.addView(compactDropdown("Window Width", current.windowWidth.toString(), widthOptions) {
                    upd { copy(windowWidth = it.toIntOrNull() ?: 640) }
                })
                wPanel.addView(compactDropdown("Window Height", current.windowHeight.toString(), heightOptions) {
                    upd { copy(windowHeight = it.toIntOrNull() ?: 480) }
                })
                wPanel.addView(compactDropdown("Screen Alignment", current.virtualScreenAlignment, alignOptions) {
                    upd { copy(virtualScreenAlignment = it) }
                })
            }
        }

        // ────────────────────────────────────────────────
        //  5. RPG MAKER — MV/MZ (WebView)
        // ────────────────────────────────────────────────
        accordion(content, "RPG MAKER (MV/MZ)", "WebView settings for MV/MZ games.") { panel ->
            panel.addView(switchPanel("Use WebGL2", "Enable WebGL2 rendering context.", current.useWebgl2) {
                upd { copy(useWebgl2 = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Decrypter & Readfiles", "Support encrypted RPG Maker assets.", current.decrypterAndReadfiles) {
                upd { copy(decrypterAndReadfiles = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Use Preload JS", "Inject preload scripts before game boot.", current.usePreloadJs) {
                upd { copy(usePreloadJs = it) }
            })
        }

        // ────────────────────────────────────────────────
        //  6. REN'PY
        // ────────────────────────────────────────────────
        stubAccordion(content, "REN'PY", "Visual novel engine (runtime not yet integrated).") { panel ->
            panel.addView(switchPanel("Auto Save", "Enable automatic save points.", current.autoSave) {
                upd { copy(autoSave = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("HW Video", "Hardware-accelerated video decoding.", current.hwVideo) {
                upd { copy(hwVideo = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Use Prescaled Variant", "Use pre-scaled assets for performance.", current.usePrescaledVariant) {
                upd { copy(usePrescaledVariant = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("VSync", "Sync rendering to display refresh.", current.renpyVsync) {
                upd { copy(renpyVsync = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Use Low Memory", "Reduce memory usage at cost of speed.", current.useLowMemory) {
                upd { copy(useLowMemory = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Low Quality", "Lower rendering quality for performance.", current.lowQuality) {
                upd { copy(lowQuality = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Multi Pixel Reduction", "Reduce pixel processing load.", current.multiPixelReduction) {
                upd { copy(multiPixelReduction = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Records Skip", "Enable skip-unseen text feature.", current.recordsSkip) {
                upd { copy(recordsSkip = it) }
            })
            panel.addView(spacer(6))
            panel.addView(TextView(context).apply {
                text = "Ren'Py runtime is bundled. Some games may still need device testing."
                setTextColor(Color.rgb(130, 170, 150))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(4))
            })
        }

        // ────────────────────────────────────────────────
        //  7. HTML GAMES (WebView)
        // ────────────────────────────────────────────────
        accordion(content, "HTML GAMES", "WebView settings for HTML5/Tyrano/Construct games.") { panel ->
            panel.addView(stubSwitchPanel("Use HTTP Server", "Serve games via local HTTP instead of file://.", current.useHttpServer) {
                upd { copy(useHttpServer = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(stubSwitchPanel("Preload", "Preload HTML resources for faster startup.", current.preload) {
                upd { copy(preload = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("WebGL", "Enable WebGL in WebView.", current.webgl) {
                upd { copy(webgl = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Desktop Mode", "Use desktop user agent and viewport.", current.desktopMode) {
                upd { copy(desktopMode = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Allow External Modules", "Allow loading external JS/assets. Security risk.", current.allowExternalModules) {
                upd { copy(allowExternalModules = it) }
            })
        }

        // ────────────────────────────────────────────────
        //  8. RUFFLE (Flash)
        // ────────────────────────────────────────────────
        stubAccordion(content, "RUFFLE (Flash)", "Flash/SWF engine (runtime not yet integrated).") { panel ->
            val backendOptions = listOf("OpenGL", "Vulkan", "Software")
            val qualityOptions = listOf("Low", "Medium", "High")
            val scaleModeOptions = listOf("Show All", "No Border", "Exact Fit")
            val loadBehaviorOptions = listOf("Streaming", "Download", "Local")
            panel.addView(compactDropdown("Renderer Backend", current.rendererBackend, backendOptions) {
                upd { copy(rendererBackend = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(compactDropdown("Quality", current.ruffleQuality, qualityOptions) {
                upd { copy(ruffleQuality = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(compactDropdown("Scale Mode", current.scaleMode, scaleModeOptions) {
                upd { copy(scaleMode = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Letterbox", "Preserve aspect ratio with black bars.", current.letterbox) {
                upd { copy(letterbox = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(compactDropdown("Load Behavior", current.loadBehavior, loadBehaviorOptions) {
                upd { copy(loadBehavior = it) }
            })
        }

        // ────────────────────────────────────────────────
        //  9. APPLICATION
        // ────────────────────────────────────────────────
        accordion(content, "APPLICATION", "App-wide preferences and features.") { panel ->
            val themeOptions = listOf("Dark", "Light", "Wallpaper")
            val animFrameOptions = listOf("None", "Low", "Medium", "High")
            panel.addView(compactDropdown("Theme", current.theme, themeOptions) { upd { copy(theme = it) } })
            panel.addView(spacerAfter(6))
            panel.addView(compactDropdown("Animation Frames", current.animationFrames, animFrameOptions) { upd { copy(animationFrames = it) } })
            panel.addView(spacerAfter(6))
            // Color Palette picker
            val paletteNames = Theme.palettes.map { it.name }
            panel.addView(paletteSelector(current.colorPalette, paletteNames) { name ->
                upd { copy(colorPalette = name) }
                Theme.active = Theme.byName(name)
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Show Game Names", "Display game title under hero cards.", current.showGameName) {
                upd { copy(showGameName = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Enable Cheats", "Allow cheat/debug injection in games.", current.enableCheats) {
                upd { copy(enableCheats = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Lock Screen", "Lock orientation/screen state during gameplay.", current.lockScreen) {
                upd { copy(lockScreen = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Experimental Features", "Enable unstable/experimental options.", current.experimentalFeatures) {
                upd { copy(experimentalFeatures = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Context Fix", "Fix WebView context-loss issues.", current.contextFix) {
                upd { copy(contextFix = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(TextView(context).apply {
                text = "RAWG API Key"
                setTextColor(TEXT); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(6), 0, dp(2))
            })
            val apiKeyInput = android.widget.EditText(context).apply {
                setText(current.rawgApiKey); setTextColor(TEXT); setHint("Paste API key here")
                setHintTextColor(Color.argb(80, 200, 200, 200))
                setBackgroundColor(Color.argb(120, 10, 10, 14))
                textSize = 13f; setPadding(dp(10), dp(8), dp(10), dp(8))
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setOnEditorActionListener { _, _, _ -> upd { copy(rawgApiKey = text.toString()) }; true }
                setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) upd { copy(rawgApiKey = text.toString()) } }
            }
            panel.addView(apiKeyInput, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            panel.addView(TextView(context).apply {
                text = "Get a free key at rawg.io/apidocs"
                setTextColor(MUTED); textSize = 10f; setPadding(0, dp(2), 0, 0)
            })
        }

        // ────────────────────────────────────────────────
        //  10. ESSENTIALS
        // ────────────────────────────────────────────────
        stubAccordion(content, "ESSENTIALS", "Compatibility toggles (not yet implemented).") { panel ->
            panel.addView(switchPanel("Keep Downloaded ZIPs", "Keep store ZIP files after install instead of deleting them.", current.preserveFiles) {
                upd { copy(preserveFiles = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Input Overrides", "Enable per-game input override system.", current.inputOverrides) {
                upd { copy(inputOverrides = it) }
            })
            panel.addView(spacerAfter(6))
            panel.addView(switchPanel("Timers Tied to Input", "Sync game timers to input timing.", current.timersTiedToInput) {
                upd { copy(timersTiedToInput = it) }
            })
        }

        // ────────────────────────────────────────────────
        //  11. HELP & ABOUT
        // ────────────────────────────────────────────────
        accordion(content, "HELP & ABOUT", "Quick guide, runtimes, and project info.") { panel ->
            panel.addView(TextView(context).apply {
                text = """
QUICK START

1. Import — Tap +, choose a game folder, then let Runestone detect the engine.
2. Play — Tap a game card. Runestone launches the matching runtime.
3. Store — Use the store tab for public catalogue entries and user-added sources.
4. Files — Use the folder tab for per-game settings, saves, engine overrides, patches, and removal.
5. Runtime menu — In game, use the small runtime controls or controller shortcut for Home, Keyboard, and settings.
6. Saves — Saves are kept separately where supported, so reimports and repairs can preserve progress.
7. Layouts — Portrait and landscape are game settings. Controller users can hide touch controls.
8. Backups — Patch/mod flows back up touched files instead of duplicating the entire game.
                """.trimIndent()
                setTextColor(MUTED); textSize = 11f
                setLineSpacing(2f, 1f)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
            panel.addView(spacer(6))
            panel.addView(TextView(context).apply {
                text = """
ABOUT RUNESTONE

Runestone is a multi-engine Android launcher for imported games.
Bring your own legally owned game files. The APK does not include commercial game data.

Supported runtime families include:
• RPG Maker XP/VX/VX Ace through mkxp-z
• RPG Maker 2000/2003 through EasyRPG
• MV/MZ and HTML engines through WebView
• Ren'Py and other runtimes where available or experimental

Open source: GPLv2+
Repository: github.com/KleirRampage45/Runestone
UI: Kotlin, programmatic glass interface
Core runtimes: SDL2, mkxp-z, EasyRPG, Ruby, OpenAL, WebView
                """.trimIndent()
                setTextColor(Color.rgb(120, 110, 90)); textSize = 10f
                setLineSpacing(1.5f, 1f)
                setPadding(dp(4), dp(2), dp(4), dp(4))
            })
        }

        // ────────────────────────────────────────────────
        //  BOTTOM ACTIONS
        // ────────────────────────────────────────────────
        content.addView(spacer(height = 14))

        // Clear Runtime Packages
        content.addView(
            TextView(context).apply {
                text = "CLEAR RUNTIME PACKAGES"
                setTextColor(Color.rgb(200, 160, 100))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(11), dp(16), dp(11))
                background = GradientDrawable().apply {
                    setColor(Color.argb(50, 180, 140, 80))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.argb(70, 200, 160, 100))
                }
                makeLiquid(this)
                setOnClickListener {
                    animTap(this)
                    onClearRuntimeCache()
                    android.widget.Toast.makeText(context, "Runtime cache cleared.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
        content.addView(spacer(10))

        // Reset to Default
        content.addView(
            TextView(context).apply {
                text = "RESET TO DEFAULT"
                setTextColor(Color.rgb(240, 120, 120))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(11), dp(16), dp(11))
                background = GradientDrawable().apply {
                    setColor(Color.argb(60, 200, 80, 80))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.argb(80, 200, 100, 100))
                }
                makeLiquid(this)
                setOnClickListener {
                    animTap(this)
                    onResetDefaults()
                }
            },
        )

        content.animate().alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.1f)).start()
        return root
    }

    // ============================================================
    //  Accordion Sections
    // ============================================================

    private fun accordion(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        build: (LinearLayout) -> Unit,
    ) {
        accordion(parent, title, subtitle, isStub = false, build)
    }

    private fun stubAccordion(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        build: (LinearLayout) -> Unit,
    ) {
        accordion(parent, title, subtitle, isStub = true, build)
    }

    private fun accordion(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        isStub: Boolean,
        build: (LinearLayout) -> Unit,
    ) {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val contentArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = glassBg(14, alpha = 160)
        }
        build(contentArea)

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val alpha = if (isStub) 100 else 180
            val strokeAlpha = if (isStub) 25 else 50
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(alpha, 18, 16, 22))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(strokeAlpha, 207, 174, 126))
            }
            makeLiquid(this)
            setOnClickListener {
                animTap(this)
                if (contentArea.visibility == View.GONE) {
                    contentArea.visibility = View.VISIBLE
                    contentArea.alpha = 0f
                    contentArea.animate().alpha(1f).setDuration(200).start()
                } else {
                    contentArea.animate().alpha(0f).setDuration(120)
                        .withEndAction { contentArea.visibility = View.GONE }.start()
                }
            }
        }
        header.addView(TextView(context).apply {
            text = if (isStub) "$title (pending)" else title
            setTextColor(if (isStub) Color.argb(180, 140, 110, 80) else ACCENT)
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(context).apply {
            text = subtitle; setTextColor(if (isStub) Color.argb(100, 120, 100, 80) else MUTED); textSize = 11f; setPadding(0, dp(2), 0, 0)
        })
        container.addView(header)
        container.addView(spacerAfter(8))
        container.addView(contentArea)

        parent.addView(container)
        parent.addView(spacer(8))
    }

    private fun subAccordion(parent: LinearLayout, title: String, build: (LinearLayout) -> Unit) {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val contentArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        build(contentArea)

        val header = TextView(context).apply {
            text = title; setTextColor(ACCENT); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), dp(8), dp(10), dp(8))
            background = glassBg(10, alpha = 140)
            makeLiquid(this)
            setOnClickListener {
                animTap(this)
                if (contentArea.visibility == View.GONE) {
                    contentArea.visibility = View.VISIBLE
                    contentArea.animate().alpha(1f).setDuration(150).start()
                } else {
                    contentArea.animate().alpha(0f).setDuration(100)
                        .withEndAction { contentArea.visibility = View.GONE }.start()
                }
            }
        }
        container.addView(header)
        container.addView(contentArea)
        parent.addView(container)
    }

    // ============================================================
    //  Top Bar
    // ============================================================

    private fun makeTopBar(onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.argb(180, 3, 3, 4))

            addView(
                TextView(context).apply {
                    text = "\u2190 Back"
                    setTextColor(Theme.active.accent)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = GradientDrawable().apply {
                        setColor(Theme.active.accentBg)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.argb(60,
                            Color.red(Theme.active.accent),
                            Color.green(Theme.active.accent),
                            Color.blue(Theme.active.accent)))
                    }
                    setOnClickListener { onBack() }
                },
                LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            addView(
                TextView(context).apply {
                    text = "Settings"
                    setTextColor(TEXT)
                    textSize = 18f
                    letterSpacing = 0.04f
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("serif", Typeface.BOLD)
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(View(context), LinearLayout.LayoutParams(dp(80), 1))
        }

    // ============================================================
    //  Layout Mode Selector
    // ============================================================

    private fun layoutSelector(selected: LayoutMode, onSelect: (LayoutMode) -> Unit): LinearLayout {
        lateinit var cards: List<Pair<LayoutMode, LinearLayout>>
        val select: (LayoutMode) -> Unit = { mode ->
            onSelect(mode)
            cards.forEach { (cardMode, card) ->
                card.background = selectorCardBackground(cardMode == mode)
            }
        }
        val normalizedSelected = selected.normalized()
        val portrait = layoutCard(LayoutMode.PORTRAIT_CONSOLE, normalizedSelected, "Portrait", "Game above, controls below", select)
        val landscape = layoutCard(LayoutMode.LANDSCAPE, normalizedSelected, "Landscape", "Game fills wide screen", select)
        cards = listOf(
            LayoutMode.PORTRAIT_CONSOLE to portrait,
            LayoutMode.LANDSCAPE to landscape,
        )
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(twoColumn(portrait, landscape))
        }
    }

    private fun layoutCard(
        mode: LayoutMode,
        selected: LayoutMode,
        title: String,
        detail: String,
        onSelect: (LayoutMode) -> Unit,
    ): LinearLayout =
        settingsPanel {
            setOnClickListener {
                animTap(this)
                onSelect(mode)
            }
            background = selectorCardBackground(selected == mode)
            makeLiquid(this)
            addView(LayoutPreviewView(context, mode), LinearLayout.LayoutParams(MATCH_PARENT, dp(84)))
            addView(TextView(context).apply {
                text = title; setTextColor(TEXT); textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            addView(TextView(context).apply {
                text = detail; setTextColor(MUTED); textSize = 10.5f
                gravity = Gravity.CENTER; setPadding(dp(2), dp(3), dp(2), 0)
            })
        }

    // ============================================================
    //  UI Mode Selector
    // ============================================================

    private fun uiModeSelector(selected: UIMode, onSelect: (UIMode) -> Unit): LinearLayout {
        lateinit var cards: List<Pair<UIMode, LinearLayout>>
        val select: (UIMode) -> Unit = { mode ->
            onSelect(mode)
            cards.forEach { (cardMode, card) ->
                card.background = selectorCardBackground(cardMode == mode)
            }
        }
        val grid = uiModeCard(UIMode.GRID, selected, select)
        val carousel = uiModeCard(UIMode.CAROUSEL_3D, selected, select)
        val list = uiModeCard(UIMode.LIST, selected, select)
        val tiles = uiModeCard(UIMode.TILES, selected, select)
        cards = listOf(UIMode.GRID to grid, UIMode.CAROUSEL_3D to carousel, UIMode.LIST to list, UIMode.TILES to tiles)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(twoColumn(grid, carousel))
            addView(spacer(10))
            addView(twoColumn(list, tiles))
        }
    }

    private fun uiModeCard(mode: UIMode, selected: UIMode, onSelect: (UIMode) -> Unit): LinearLayout =
        settingsPanel {
            setOnClickListener { animTap(this); onSelect(mode) }
            background = selectorCardBackground(selected == mode)
            makeLiquid(this)
            addView(TextView(context).apply {
                text = mode.label; setTextColor(TEXT); textSize = 14f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })
            addView(TextView(context).apply {
                text = mode.description; setTextColor(MUTED); textSize = 10.5f
                gravity = Gravity.CENTER; setPadding(dp(4), dp(3), dp(4), dp(10))
            })
        }

    private fun selectorCardBackground(selected: Boolean): GradientDrawable =
        panelBackground(
            if (selected) Color.argb(200, 33, 28, 27) else Color.argb(190, 12, 11, 16),
            stroke = if (selected) ACCENT else Color.argb(60, 207, 174, 126),
            corner = 16,
        )

    // ============================================================
    //  Audio Selector
    // ============================================================

    private fun audioSelector(currentExt: String, onSelect: (String) -> Unit): LinearLayout =
        settingsPanel {
            addView(twoColumn(
                audioCard(".ogg", "Opus audio", currentExt == ".ogg") { onSelect(".ogg") },
                audioCard(".m4a", "AAC audio", currentExt == ".m4a") { onSelect(".m4a") },
            ))
        }

    private fun audioCard(label: String, detail: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = "$label\n$detail"
            setTextColor(TEXT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; minHeight = dp(60)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = panelBackground(
                if (selected) Color.argb(200, 33, 28, 27) else Color.argb(190, 12, 11, 16),
                stroke = if (selected) ACCENT else Color.argb(60, 207, 174, 126),
                corner = 16,
            )
            makeLiquid(this)
            setOnClickListener { animTap(this); onClick() }
        }

    // ============================================================
    //  Reusable UI Components
    // ============================================================

    private fun settingsPanel(build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = glassBg(14, alpha = 190)
            makeLiquid(this)
            build()
        }

    private fun twoColumn(left: View, right: View): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(spacer(width = 10))
            addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun sliderPanel(title: String, value: String, sliderFactory: (TextView) -> GlassSlider): LinearLayout =
        settingsPanel {
            val label = TextView(context).apply {
                text = value; setTextColor(ACCENT); textSize = 13f; gravity = Gravity.END
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = title; setTextColor(TEXT); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(label)
            }
            addView(row)
            val slider = sliderFactory(label)
            slider.contentDescription = "$title — ${label.text}"
            addView(slider)
        }

    private fun switchPanel(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout =
        switchPanelImpl(title, detail, checked, onChange, isStub = false)

    private fun stubSwitchPanel(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout =
        switchPanelImpl(title, detail, checked, onChange, isStub = true)

    private fun switchPanelImpl(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, isStub: Boolean): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val copy = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = if (isStub) "$title (stub)" else title
                    setTextColor(if (isStub) Color.argb(160, 140, 110, 80) else TEXT)
                    textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(context).apply {
                    text = detail; setTextColor(if (isStub) Color.argb(100, 120, 100, 80) else MUTED)
                    textSize = 11f; setPadding(0, dp(3), dp(10), 0)
                })
            }
            addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = checked
                isEnabled = !isStub
                setOnCheckedChangeListener { _, value -> if (!isStub) onChange(value) }
            })
        }

    private fun compactDropdown(title: String, currentValue: String, options: List<String>, onSelect: (String) -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            addView(TextView(context).apply {
                text = title; setTextColor(TEXT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = currentValue; setTextColor(ACCENT); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = glassBg(6, alpha = 80)
                makeLiquid(this)
                setOnClickListener {
                    animTap(this)
                    val idx = options.indexOf(currentValue)
                    val nextIdx = (idx + 1) % options.size
                    text = options[nextIdx]
                    onSelect(options[nextIdx])
                }
            })
        }

    private fun dropdownRow(title: String, currentValue: String, options: List<String>, onSelect: (String) -> Unit): LinearLayout =
        settingsPanel {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(context).apply {
                text = title; setTextColor(TEXT); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(context).apply {
                text = currentValue; setTextColor(ACCENT); textSize = 13f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = glassBg(8, alpha = 80)
                makeLiquid(this)
                setOnClickListener {
                    animTap(this)
                    val currentIndex = options.indexOf(currentValue)
                    val nextIndex = (currentIndex + 1) % options.size
                    text = options[nextIndex]
                    onSelect(options[nextIndex])
                }
            })
            addView(row)
        }

    private fun paletteSelector(currentPalette: String, paletteNames: List<String>, onSelect: (String) -> Unit): LinearLayout =
        settingsPanel {
            addView(TextView(context).apply {
                text = "Color Palette"; setTextColor(TEXT); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            })
            addView(spacer(height = 8))
            val swatchRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            }
            paletteNames.forEach { name ->
                val palette = Theme.byName(name)
                val isActive = name == currentPalette
                val swatch = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                    val inner = View(context).apply {
                        layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                        background = GradientDrawable().apply {
                            setColor(palette.accent)
                            cornerRadius = dp(14).toFloat()
                            if (isActive) {
                                setStroke(dp(3), Color.argb(200, 255, 255, 255))
                            }
                        }
                    }
                    addView(inner)
                    val label = TextView(context).apply {
                        text = name.first().toString() // first letter
                        setTextColor(Color.rgb(232, 229, 220)); textSize = 9f
                        gravity = Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP, Gravity.BOTTOM)
                        setPadding(0, 0, 0, dp(2))
                    }
                    addView(label)
                    setOnClickListener {
                        onSelect(name)
                        // Refresh the parent to show updated selection
                        swatchRow.removeAllViews()
                        // Rebuild
                        paletteNames.forEach { n ->
                            val p = Theme.byName(n)
                            val active = n == name
                            swatchRow.addView(FrameLayout(context).apply {
                                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                                    setMargins(dp(3), 0, dp(3), 0)
                                }
                                addView(View(context).apply {
                                    layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
                                    background = GradientDrawable().apply {
                                        setColor(p.accent)
                                        cornerRadius = dp(14).toFloat()
                                        if (active) setStroke(dp(3), Color.argb(200, 255, 255, 255))
                                    }
                                })
                                addView(TextView(context).apply {
                                    text = n.first().toString()
                                    setTextColor(Color.rgb(232, 229, 220)); textSize = 9f
                                    gravity = Gravity.CENTER
                                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP, Gravity.BOTTOM)
                                    setPadding(0, 0, 0, dp(2))
                                })
                            })
                        }
                    }
                }
                swatchRow.addView(swatch)
            }
            addView(spacer(height = 6))
            addView(TextView(context).apply {
                text = "Current: $currentPalette"
                setTextColor(Theme.byName(currentPalette).accent); textSize = 11f
                gravity = Gravity.CENTER
            })
            addView(swatchRow)
        }

    private fun slider(max: Int, progress: Int, onChange: (Int) -> Unit): GlassSlider =
        GlassSlider(context, max, progress, onChange)

    private fun panelBackground(color: Int, stroke: Int = Color.TRANSPARENT, corner: Int = 16): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(corner).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun spacer(height: Int = 0, width: Int = 0): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
        }

    private fun spacerAfter(height: Int): View {
        val v = View(context)
        v.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(height))
        return v
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun makeLiquid(view: View) { if (Theme.isReducedMotion(context)) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f; val cy = v.height / 2f
                    v.translationX = (event.x - cx) * 0.06f
                    v.translationY = (event.y - cy) * 0.06f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().translationX(0f).translationY(0f)
                        .setDuration(200).setInterpolator(OvershootInterpolator(1.4f)).start()
                }
                else -> {}
            }
            false
        }
    }

    private fun animTap(v: View) { if (Theme.isReducedMotion(context)) return
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            }.start()
    }

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha,
                if (accent) Color.red(Theme.active.accent) / 4 else 22,
                if (accent) Color.green(Theme.active.accent) / 4 else 20,
                if (accent) Color.blue(Theme.active.accent) / 4 else 26))
            cornerRadius = dp(radius).toFloat()
            if (accent) {
                setStroke(dp(1), Color.argb(80,
                    Color.red(Theme.active.accent),
                    Color.green(Theme.active.accent),
                    Color.blue(Theme.active.accent)))
            } else {
                setStroke(dp(1), Color.argb(45, 100, 90, 80))
            }
        }

    // ============================================================
    //  Layout Preview
    // ============================================================

    private class LayoutPreviewView(context: Context, private val mode: LayoutMode) : View(context) {
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126); style = Paint.Style.STROKE; strokeWidth = 3f
        }
        private val phone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(9, 9, 11); style = Paint.Style.FILL
        }
        private val game = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 68, 58); style = Paint.Style.FILL
        }
        private val controls = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 32, 36); style = Paint.Style.FILL
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126); style = Paint.Style.FILL
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val landscape = mode != LayoutMode.PORTRAIT_CONSOLE
            val pw = if (landscape) w * 0.88f else w * 0.50f
            val ph = if (landscape) h * 0.58f else h * 0.92f
            val left = (w - pw) / 2f; val top = (h - ph) / 2f
            rect.set(left, top, left + pw, top + ph)
            canvas.drawRoundRect(rect, 22f, 22f, phone)
            canvas.drawRoundRect(rect, 22f, 22f, stroke)
            val inset = if (landscape) ph * 0.10f else pw * 0.09f

            if (mode == LayoutMode.PORTRAIT_CONSOLE) {
                canvas.drawRoundRect(left + inset, top + ph * 0.08f, left + pw - inset, top + ph * 0.46f, 8f, 8f, game)
                val scInset = inset * 1.3f
                canvas.drawRoundRect(left + scInset, top + ph * 0.11f, left + pw - scInset, top + ph * 0.43f, 4f, 4f, phone)
                canvas.drawRoundRect(left + inset, top + ph * 0.53f, left + pw - inset, top + ph * 0.88f, 8f, 8f, controls)
                canvas.drawCircle(left + pw * 0.28f, top + ph * 0.70f, ph * 0.06f, game)
                drawButtonDots(canvas, left + pw * 0.72f, top + ph * 0.70f, pw * 0.05f)
            } else if (mode == LayoutMode.LANDSCAPE) {
                canvas.drawRoundRect(left + inset, top + inset, left + pw - inset, top + ph - inset, 8f, 8f, game)
                val scInset = inset * 1.4f
                canvas.drawRoundRect(left + scInset, top + scInset, left + pw - scInset, top + ph - scInset, 6f, 6f, phone)
                canvas.drawCircle(left + pw * 0.22f, top + ph * 0.58f, ph * 0.07f, controls)
                drawButtonDots(canvas, left + pw * 0.78f, top + ph * 0.58f, ph * 0.04f)
            } else {
                canvas.drawRoundRect(left + inset, top + inset, left + pw - inset, top + ph - inset, 8f, 8f, game)
                val scInset = inset * 1.4f
                canvas.drawRoundRect(left + scInset, top + scInset, left + pw - scInset, top + ph - scInset, 6f, 6f, phone)
                val cx = left + pw / 2f; val cy = top + ph / 2f
                val cw = pw * 0.35f; val ch = ph * 0.20f
                val bodyRect = RectF(cx - cw, cy - ch * 0.5f, cx + cw, cy + ch * 0.5f)
                canvas.drawRoundRect(bodyRect, ch * 0.5f, ch * 0.5f, controls)
                canvas.drawCircle(cx - cw * 0.45f, cy, ch * 0.25f, game)
                canvas.drawCircle(cx + cw * 0.45f, cy, ch * 0.25f, game)
                val dpx = cx - cw * 0.18f; val dpy = cy; val ds = ch * 0.08f
                canvas.drawRoundRect(dpx - ds, dpy - ds * 2.2f, dpx + ds, dpy + ds * 2.2f, 2f, 2f, accentPaint)
                canvas.drawRoundRect(dpx - ds * 2.2f, dpy - ds, dpx + ds * 2.2f, dpy + ds, 2f, 2f, accentPaint)
                val fcx = cx + cw * 0.18f
                canvas.drawCircle(fcx + ds * 1.6f, cy - ds * 1.6f, ds * 0.6f, accentPaint)
                canvas.drawCircle(fcx - ds * 1.6f, cy - ds * 1.6f, ds * 0.6f, accentPaint)
                canvas.drawCircle(fcx, cy - ds * 3f, ds * 0.6f, accentPaint)
                canvas.drawCircle(fcx, cy + ds * 0.4f, ds * 0.6f, accentPaint)
            }
        }

        private fun drawButtonDots(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            canvas.drawCircle(cx - r * 1.6f, cy, r, controls)
            canvas.drawCircle(cx + r * 1.6f, cy, r, controls)
            canvas.drawCircle(cx, cy - r * 1.6f, r, controls)
            canvas.drawCircle(cx, cy + r * 1.6f, r, controls)
        }
    }

    // ============================================================
    //  Glass Slider
    // ============================================================

    private inner class GlassSlider(
        context: Context,
        private val maxVal: Int,
        private var currentProgress: Int,
        private val onChanged: (Int) -> Unit,
    ) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255); style = Paint.Style.FILL
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126); style = Paint.Style.FILL
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 229, 220); style = Paint.Style.FILL
        }
        private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private var isTracking = false
        private val trackH = dp(6)
        private val thumbR = dp(12)

        init {
            minimumHeight = dp(40)
            isClickable = true
            isFocusable = true
        }

        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = android.widget.SeekBar::class.java.name
            info.contentDescription = contentDescription ?: "Slider — ${currentProgress} of $maxVal"
            info.rangeInfo = android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(
                android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, maxVal.toFloat(), currentProgress.toFloat()
            )
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        }

        override fun performAccessibilityAction(action: Int, args: Bundle?): Boolean {
            if (action == android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id) {
                val value = args?.getFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, currentProgress.toFloat())
                    ?: return false
                currentProgress = value.toInt().coerceIn(0, maxVal)
                onChanged(currentProgress)
                invalidate()
                return true
            }
            return super.performAccessibilityAction(action, args)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val cy = h / 2f
            val trackL = thumbR.toFloat(); val trackR = w - thumbR
            val frac = currentProgress.toFloat() / maxVal.coerceAtLeast(1)
            val thumbX = trackL + (trackR - trackL) * frac
            canvas.drawRoundRect(trackL, cy - trackH / 2f, trackR, cy + trackH / 2f, trackH / 2f, trackH / 2f, bgPaint)
            canvas.drawRoundRect(trackL, cy - trackH / 2f, thumbX, cy + trackH / 2f, trackH / 2f, trackH / 2f, fillPaint)
            val scale = if (isTracking) 1.25f else 1f
            canvas.drawCircle(thumbX, cy, thumbR * scale, thumbPaint)
            canvas.drawCircle(thumbX, cy, thumbR * scale, thumbStroke)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = when (event.action) {
            MotionEvent.ACTION_DOWN -> { isTracking = true; updateProgress(event.x); true }
            MotionEvent.ACTION_MOVE -> { updateProgress(event.x); true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isTracking = false; invalidate(); true }
            else -> false
        }

        private fun updateProgress(x: Float) {
            val w = width.toFloat()
            val trackL = thumbR.toFloat(); val trackR = w - thumbR
            val frac = ((x - trackL) / (trackR - trackL)).coerceIn(0f, 1f)
            currentProgress = (frac * maxVal).toInt().coerceIn(0, maxVal)
            onChanged(currentProgress)
            invalidate()
        }
    }

    private companion object {
        val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val TEXT: Int = Color.rgb(232, 229, 220)
        val MUTED: Int = Color.rgb(140, 130, 112)
        val ACCENT: Int get() = Theme.active.accent
    }
}
