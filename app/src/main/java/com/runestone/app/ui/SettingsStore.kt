/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.ui

import android.content.Context
import com.runestone.app.data.ControllerShortcut
import com.runestone.app.data.DisplayCutoutMode
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings
import com.runestone.app.data.UIMode

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("runestone-settings-v1", Context.MODE_PRIVATE)
    private val defaults = RunnerSettings()

    fun load(): RunnerSettings {
        val storedLayout = LayoutMode.parse(prefs.getString("layoutMode", defaults.layoutMode.name), defaults.layoutMode)
        val migratedGamepad = storedLayout == LayoutMode.GAMEPAD
        return RunnerSettings(
            layoutMode = storedLayout.normalized(),
            uiMode = runCatching {
                UIMode.valueOf(prefs.getString("uiMode", defaults.uiMode.name).orEmpty())
            }.getOrDefault(defaults.uiMode),
            integerScaling = prefs.getBoolean("integerScaling", defaults.integerScaling),
            smoothScaling = prefs.getBoolean("smoothScaling", defaults.smoothScaling),
            textScale = prefs.getFloat("textScale", defaults.textScale),
            keepScreenOn = prefs.getBoolean("keepScreenOn", defaults.keepScreenOn),
            displayCutoutMode = runCatching {
                DisplayCutoutMode.valueOf(prefs.getString("displayCutoutMode", defaults.displayCutoutMode.name).orEmpty())
            }.getOrDefault(defaults.displayCutoutMode),
            touchOpacity = prefs.getFloat("touchOpacity", defaults.touchOpacity),
            touchScale = prefs.getFloat("touchScale", defaults.touchScale),
            hapticsEnabled = prefs.getBoolean("hapticsEnabled", defaults.hapticsEnabled),
            hapticIntensity = prefs.getFloat("hapticIntensity", defaults.hapticIntensity),
            showExtraButtons = prefs.getBoolean("showExtraButtons", defaults.showExtraButtons),
            hideVirtualGamepad = prefs.getBoolean("hideVirtualGamepad", if (migratedGamepad) true else defaults.hideVirtualGamepad),
            diagonalMovement = prefs.getBoolean("diagonalMovement", defaults.diagonalMovement),
            leftButtonKey = prefs.getString("leftButtonKey", defaults.leftButtonKey) ?: defaults.leftButtonKey,
            rightButtonKey = prefs.getString("rightButtonKey", defaults.rightButtonKey) ?: defaults.rightButtonKey,
            leftMButtonKey = prefs.getString("leftMButtonKey", defaults.leftMButtonKey) ?: defaults.leftMButtonKey,
            rightMButtonKey = prefs.getString("rightMButtonKey", defaults.rightMButtonKey) ?: defaults.rightMButtonKey,
            firstButtonKey = prefs.getString("firstButtonKey", defaults.firstButtonKey) ?: defaults.firstButtonKey,
            secondButtonKey = prefs.getString("secondButtonKey", defaults.secondButtonKey) ?: defaults.secondButtonKey,
            thirdButtonKey = prefs.getString("thirdButtonKey", defaults.thirdButtonKey) ?: defaults.thirdButtonKey,
            fourthButtonKey = prefs.getString("fourthButtonKey", defaults.fourthButtonKey) ?: defaults.fourthButtonKey,
            fifthButtonKey = prefs.getString("fifthButtonKey", defaults.fifthButtonKey) ?: defaults.fifthButtonKey,
            sixthButtonKey = prefs.getString("sixthButtonKey", defaults.sixthButtonKey) ?: defaults.sixthButtonKey,
            controllerHomeShortcut = shortcut("controllerHomeShortcut", defaults.controllerHomeShortcut),
            controllerKeyboardShortcut = shortcut("controllerKeyboardShortcut", defaults.controllerKeyboardShortcut),
            controllerRuntimeMenuShortcut = shortcut("controllerRuntimeMenuShortcut", defaults.controllerRuntimeMenuShortcut),
            controllerResumeShortcut = shortcut("controllerResumeShortcut", defaults.controllerResumeShortcut),
            forceAudioExt = prefs.getString("forceAudioExt", defaults.forceAudioExt) ?: defaults.forceAudioExt,
            disableAudioEmulation = prefs.getBoolean("disableAudioEmulation", defaults.disableAudioEmulation),
            dialogLogs = prefs.getBoolean("dialogLogs", defaults.dialogLogs),
            useRuby18 = prefs.getBoolean("useRuby18", defaults.useRuby18),
            customFont = prefs.getString("customFont", defaults.customFont) ?: defaults.customFont,
            vsync = prefs.getBoolean("vsync", defaults.vsync),
            frameSkip = prefs.getBoolean("frameSkip", defaults.frameSkip),
            shaders = prefs.getBoolean("shaders", defaults.shaders),
            pathCache = prefs.getBoolean("pathCache", defaults.pathCache),
            reachPathDistance = prefs.getBoolean("reachPathDistance", defaults.reachPathDistance),
            enablePreloadScripts = prefs.getBoolean("enablePreloadScripts", defaults.enablePreloadScripts),
            windowWidth = prefs.getInt("windowWidth", defaults.windowWidth),
            windowHeight = prefs.getInt("windowHeight", defaults.windowHeight),
            virtualScreenAlignment = prefs.getString("virtualScreenAlignment", defaults.virtualScreenAlignment) ?: defaults.virtualScreenAlignment,
            updateGraphics = prefs.getBoolean("updateGraphics", defaults.updateGraphics),
            pixelFormatSpeed = prefs.getString("pixelFormatSpeed", defaults.pixelFormatSpeed) ?: defaults.pixelFormatSpeed,
            cropLeftY = prefs.getBoolean("cropLeftY", defaults.cropLeftY),
            useWebgl2 = prefs.getBoolean("useWebgl2", defaults.useWebgl2),
            decrypterAndReadfiles = prefs.getBoolean("decrypterAndReadfiles", defaults.decrypterAndReadfiles),
            usePreloadJs = prefs.getBoolean("usePreloadJs", defaults.usePreloadJs),
            useHttpServer = prefs.getBoolean("useHttpServer", defaults.useHttpServer),
            preload = prefs.getBoolean("preload", defaults.preload),
            webgl = prefs.getBoolean("webgl", defaults.webgl),
            desktopMode = prefs.getBoolean("desktopMode", defaults.desktopMode),
            allowExternalModules = prefs.getBoolean("allowExternalModules", defaults.allowExternalModules),
            autoSave = prefs.getBoolean("autoSave", defaults.autoSave),
            hwVideo = prefs.getBoolean("hwVideo", defaults.hwVideo),
            usePrescaledVariant = prefs.getBoolean("usePrescaledVariant", defaults.usePrescaledVariant),
            renpyVsync = prefs.getBoolean("renpyVsync", defaults.renpyVsync),
            useLowMemory = prefs.getBoolean("useLowMemory", defaults.useLowMemory),
            lowQuality = prefs.getBoolean("lowQuality", defaults.lowQuality),
            multiPixelReduction = prefs.getBoolean("multiPixelReduction", defaults.multiPixelReduction),
            recordsSkip = prefs.getBoolean("recordsSkip", defaults.recordsSkip),
            rendererBackend = prefs.getString("rendererBackend", defaults.rendererBackend) ?: defaults.rendererBackend,
            ruffleQuality = prefs.getString("ruffleQuality", defaults.ruffleQuality) ?: defaults.ruffleQuality,
            scaleMode = prefs.getString("scaleMode", defaults.scaleMode) ?: defaults.scaleMode,
            letterbox = prefs.getBoolean("letterbox", defaults.letterbox),
            loadBehavior = prefs.getString("loadBehavior", defaults.loadBehavior) ?: defaults.loadBehavior,
            defaultGameFolder = prefs.getString("defaultGameFolder", defaults.defaultGameFolder) ?: defaults.defaultGameFolder,
            theme = prefs.getString("theme", defaults.theme) ?: defaults.theme,
            colorPalette = prefs.getString("colorPalette", defaults.colorPalette) ?: defaults.colorPalette,
            primaryColor = prefs.getInt("primaryColor", defaults.primaryColor),
            animationFrames = prefs.getString("animationFrames", defaults.animationFrames) ?: defaults.animationFrames,
            enableCheats = prefs.getBoolean("enableCheats", defaults.enableCheats),
            lockScreen = prefs.getBoolean("lockScreen", defaults.lockScreen),
            experimentalFeatures = prefs.getBoolean("experimentalFeatures", defaults.experimentalFeatures),
            showGameName = prefs.getBoolean("showGameName", defaults.showGameName),
            contextFix = prefs.getBoolean("contextFix", defaults.contextFix),
            preserveFiles = prefs.getBoolean("preserveFiles", defaults.preserveFiles),
            inputOverrides = prefs.getBoolean("inputOverrides", defaults.inputOverrides),
            timersTiedToInput = prefs.getBoolean("timersTiedToInput", defaults.timersTiedToInput),
            rawgApiKey = prefs.getString("rawgApiKey", defaults.rawgApiKey) ?: defaults.rawgApiKey,
            reduceMotion = prefs.getBoolean("reduceMotion", defaults.reduceMotion),
        )
    }

    fun save(settings: RunnerSettings) {
        prefs.edit()
            .putString("layoutMode", settings.layoutMode.name)
            .putString("uiMode", settings.uiMode.name)
            .putBoolean("integerScaling", settings.integerScaling)
            .putBoolean("smoothScaling", settings.smoothScaling)
            .putFloat("textScale", settings.textScale)
            .putBoolean("keepScreenOn", settings.keepScreenOn)
            .putString("displayCutoutMode", settings.displayCutoutMode.name)
            .putFloat("touchOpacity", settings.touchOpacity)
            .putFloat("touchScale", settings.touchScale)
            .putBoolean("hapticsEnabled", settings.hapticsEnabled)
            .putFloat("hapticIntensity", settings.hapticIntensity)
            .putBoolean("showExtraButtons", settings.showExtraButtons)
            .putBoolean("hideVirtualGamepad", settings.hideVirtualGamepad)
            .putBoolean("diagonalMovement", settings.diagonalMovement)
            .putString("leftButtonKey", settings.leftButtonKey)
            .putString("rightButtonKey", settings.rightButtonKey)
            .putString("leftMButtonKey", settings.leftMButtonKey)
            .putString("rightMButtonKey", settings.rightMButtonKey)
            .putString("firstButtonKey", settings.firstButtonKey)
            .putString("secondButtonKey", settings.secondButtonKey)
            .putString("thirdButtonKey", settings.thirdButtonKey)
            .putString("fourthButtonKey", settings.fourthButtonKey)
            .putString("fifthButtonKey", settings.fifthButtonKey)
            .putString("sixthButtonKey", settings.sixthButtonKey)
            .putString("controllerHomeShortcut", settings.controllerHomeShortcut.name)
            .putString("controllerKeyboardShortcut", settings.controllerKeyboardShortcut.name)
            .putString("controllerRuntimeMenuShortcut", settings.controllerRuntimeMenuShortcut.name)
            .putString("controllerResumeShortcut", settings.controllerResumeShortcut.name)
            .putString("forceAudioExt", settings.forceAudioExt)
            .putBoolean("disableAudioEmulation", settings.disableAudioEmulation)
            .putBoolean("dialogLogs", settings.dialogLogs)
            .putBoolean("useRuby18", settings.useRuby18)
            .putString("customFont", settings.customFont)
            .putBoolean("vsync", settings.vsync)
            .putBoolean("frameSkip", settings.frameSkip)
            .putBoolean("shaders", settings.shaders)
            .putBoolean("pathCache", settings.pathCache)
            .putBoolean("reachPathDistance", settings.reachPathDistance)
            .putBoolean("enablePreloadScripts", settings.enablePreloadScripts)
            .putInt("windowWidth", settings.windowWidth)
            .putInt("windowHeight", settings.windowHeight)
            .putString("virtualScreenAlignment", settings.virtualScreenAlignment)
            .putBoolean("updateGraphics", settings.updateGraphics)
            .putString("pixelFormatSpeed", settings.pixelFormatSpeed)
            .putBoolean("cropLeftY", settings.cropLeftY)
            .putBoolean("useWebgl2", settings.useWebgl2)
            .putBoolean("decrypterAndReadfiles", settings.decrypterAndReadfiles)
            .putBoolean("usePreloadJs", settings.usePreloadJs)
            .putBoolean("useHttpServer", settings.useHttpServer)
            .putBoolean("preload", settings.preload)
            .putBoolean("webgl", settings.webgl)
            .putBoolean("desktopMode", settings.desktopMode)
            .putBoolean("allowExternalModules", settings.allowExternalModules)
            .putBoolean("autoSave", settings.autoSave)
            .putBoolean("hwVideo", settings.hwVideo)
            .putBoolean("usePrescaledVariant", settings.usePrescaledVariant)
            .putBoolean("renpyVsync", settings.renpyVsync)
            .putBoolean("useLowMemory", settings.useLowMemory)
            .putBoolean("lowQuality", settings.lowQuality)
            .putBoolean("multiPixelReduction", settings.multiPixelReduction)
            .putBoolean("recordsSkip", settings.recordsSkip)
            .putString("rendererBackend", settings.rendererBackend)
            .putString("ruffleQuality", settings.ruffleQuality)
            .putString("scaleMode", settings.scaleMode)
            .putBoolean("letterbox", settings.letterbox)
            .putString("loadBehavior", settings.loadBehavior)
            .putString("defaultGameFolder", settings.defaultGameFolder)
            .putString("theme", settings.theme)
            .putString("colorPalette", settings.colorPalette)
            .putInt("primaryColor", settings.primaryColor)
            .putString("animationFrames", settings.animationFrames)
            .putBoolean("enableCheats", settings.enableCheats)
            .putBoolean("lockScreen", settings.lockScreen)
            .putBoolean("experimentalFeatures", settings.experimentalFeatures)
            .putBoolean("showGameName", settings.showGameName)
            .putBoolean("contextFix", settings.contextFix)
            .putBoolean("preserveFiles", settings.preserveFiles)
            .putBoolean("inputOverrides", settings.inputOverrides)
            .putBoolean("timersTiedToInput", settings.timersTiedToInput)
            .putString("rawgApiKey", settings.rawgApiKey)
            .putBoolean("reduceMotion", settings.reduceMotion)
            .apply()
    }

    private fun shortcut(key: String, default: ControllerShortcut): ControllerShortcut =
        runCatching {
            ControllerShortcut.valueOf(prefs.getString(key, default.name).orEmpty())
        }.getOrDefault(default)
}
