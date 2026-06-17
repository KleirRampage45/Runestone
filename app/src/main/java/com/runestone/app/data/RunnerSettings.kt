/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.data

enum class UIMode(val label: String, val description: String) {
    GRID("Grid", "Default card grid"),
    CAROUSEL_3D("3D Shelf", "Perspective carousel"),
    LIST("Compact List", "Vertical text list"),
    TILES("Tiles", "Smaller cards in rows"),
}

enum class DisplayCutoutMode(val label: String) {
    SAFE_AREA("Safe area"),
    EDGE_TO_EDGE("Edge to edge"),
}

enum class ControllerShortcut(val label: String) {
    OFF("Off"),
    L2_R2("L2 + R2"),
    L1_R1("L1 + R1"),
    START_SELECT("Start + Select"),
    L2_START("L2 + Start"),
    R2_START("R2 + Start"),
}

data class RunnerSettings(
    // Display / Layout
    val layoutMode: LayoutMode = LayoutMode.PORTRAIT_CONSOLE,
    val uiMode: UIMode = UIMode.GRID,
    val integerScaling: Boolean = false,
    val smoothScaling: Boolean = false,
    val textScale: Float = 1.0f,
    val keepScreenOn: Boolean = false,
    val displayCutoutMode: DisplayCutoutMode = DisplayCutoutMode.SAFE_AREA,

    // Gamepad / Input
    val touchOpacity: Float = 0.72f,
    val touchScale: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
    val hapticIntensity: Float = 0.55f,
    val showExtraButtons: Boolean = false,
    val controllerPreset: String = "SIMPLIFIED",
    val hideVirtualGamepad: Boolean = false,
    val diagonalMovement: Boolean = false,
    val leftButtonKey: String = "ENTER",
    val rightButtonKey: String = "ESCAPE",
    val leftMButtonKey: String = "F2",
    val rightMButtonKey: String = "F8",
    val firstButtonKey: String = "Z",
    val secondButtonKey: String = "CTRL_LEFT",
    val thirdButtonKey: String = "Q",
    val fourthButtonKey: String = "X",
    val fifthButtonKey: String = "SHIFT_LEFT",
    val sixthButtonKey: String = "B",
    val controllerHomeShortcut: ControllerShortcut = ControllerShortcut.L2_R2,
    val controllerKeyboardShortcut: ControllerShortcut = ControllerShortcut.START_SELECT,
    val controllerRuntimeMenuShortcut: ControllerShortcut = ControllerShortcut.L1_R1,
    val controllerResumeShortcut: ControllerShortcut = ControllerShortcut.L2_R2,

    // Audio
    val forceAudioExt: String = ".ogg",
    val disableAudioEmulation: Boolean = false,

    // RPG / RGSS (mkxp-z)
    val dialogLogs: Boolean = false,
    val useRuby18: Boolean = true,
    val customFont: String = "",
    val vsync: Boolean = false,
    val frameSkip: Boolean = false,
    val shaders: Boolean = false,
    val pathCache: Boolean = false,
    val reachPathDistance: Boolean = true,
    val enablePreloadScripts: Boolean = true,
    val windowWidth: Int = 640,
    val windowHeight: Int = 480,
    val virtualScreenAlignment: String = "CENTER_TOP_HALF",
    val updateGraphics: Boolean = false,
    val pixelFormatSpeed: String = "Normal",
    val cropLeftY: Boolean = false,

    // RPG / MV-MZ (WebView)
    val useWebgl2: Boolean = true,
    val forceCanvas: Boolean = false,
    val decrypterAndReadfiles: Boolean = true,
    val usePreloadJs: Boolean = false,

    // HTML (WebView)
    val useHttpServer: Boolean = true,
    val preload: Boolean = true,
    val webgl: Boolean = true,
    val desktopMode: Boolean = false,
    val allowExternalModules: Boolean = false,

    // Ren'Py
    val autoSave: Boolean = false,
    val hwVideo: Boolean = true,
    val usePrescaledVariant: Boolean = false,
    val renpyVsync: Boolean = false,
    val useLowMemory: Boolean = false,
    val lowQuality: Boolean = false,
    val multiPixelReduction: Boolean = true,
    val recordsSkip: Boolean = false,

    // Ruffle (Flash)
    val rendererBackend: String = "OpenGL",
    val ruffleQuality: String = "High",
    val scaleMode: String = "Show All",
    val letterbox: Boolean = true,
    val loadBehavior: String = "Streaming",

    // App
    val defaultGameFolder: String = "",
    val theme: String = "Dark",
    val colorPalette: String = "Amber",
    val primaryColor: Int = 0xFFCFAE7E.toInt(),
    val animationFrames: String = "None",
    val enableCheats: Boolean = false,
    val lockScreen: Boolean = false,
    val experimentalFeatures: Boolean = false,
    val showGameName: Boolean = true,
    val contextFix: Boolean = false,

    // Essentials
    val preserveFiles: Boolean = true,
    val inputOverrides: Boolean = false,
    val timersTiedToInput: Boolean = true,

    // Metadata
    val rawgApiKey: String = "",

    // Accessibility
    val reduceMotion: Boolean = false,
)
