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
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("runestone-settings-v1", Context.MODE_PRIVATE)
    private val defaults = RunnerSettings()

    fun load(): RunnerSettings =
        RunnerSettings(
            layoutMode = runCatching {
                LayoutMode.valueOf(prefs.getString("layoutMode", defaults.layoutMode.name).orEmpty())
            }.getOrDefault(defaults.layoutMode),
            touchOpacity = prefs.getFloat("touchOpacity", defaults.touchOpacity),
            touchScale = prefs.getFloat("touchScale", defaults.touchScale),
            hapticsEnabled = prefs.getBoolean("hapticsEnabled", defaults.hapticsEnabled),
            hapticIntensity = prefs.getFloat("hapticIntensity", defaults.hapticIntensity),
            showExtraButtons = prefs.getBoolean("showExtraButtons", defaults.showExtraButtons),
            integerScaling = prefs.getBoolean("integerScaling", defaults.integerScaling),
            smoothScaling = prefs.getBoolean("smoothScaling", defaults.smoothScaling),
            textScale = defaults.textScale,
            forceAudioExt = prefs.getString("forceAudioExt", defaults.forceAudioExt) ?: defaults.forceAudioExt,
        )

    fun save(settings: RunnerSettings) {
        prefs.edit()
            .putString("layoutMode", settings.layoutMode.name)
            .putFloat("touchOpacity", settings.touchOpacity)
            .putFloat("touchScale", settings.touchScale)
            .putBoolean("hapticsEnabled", settings.hapticsEnabled)
            .putFloat("hapticIntensity", settings.hapticIntensity)
            .putBoolean("showExtraButtons", settings.showExtraButtons)
            .putBoolean("integerScaling", settings.integerScaling)
            .putBoolean("smoothScaling", settings.smoothScaling)
            .putFloat("textScale", 1.0f)
            .putString("forceAudioExt", settings.forceAudioExt)
            .apply()
    }
}
