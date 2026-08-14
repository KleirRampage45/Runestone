/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright 2026 Gerson (KleirRampage45)
 *
 * Central filter manager: holds preset definitions, resolves overrides,
 * produces ResolvedFilterConfig for the native renderer.
 */

package com.runestone.app.filters

import com.runestone.app.data.VideoSection

object FilterManager {

    private val presets = mutableMapOf<String, FilterPreset>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        register(FilterPreset(
            id = "off",
            displayName = "Off / Original",
            description = "No extra filtering. Shows the game as-is.",
            tags = listOf("Fast"),
            performanceTier = PerfTier.FREE,
            passes = emptyList(),
            defaultParams = emptyMap(),
        ))

        register(FilterPreset(
            id = "clean_sharp",
            displayName = "Clean Sharp",
            description = "Recommended for most games. Sharp pixels without harsh edges. Improves text readability on phone screens.",
            tags = listOf("Recommended", "Fast", "Readable"),
            performanceTier = PerfTier.LOW,
            passes = listOf(
                FilterPassTemplate(
                    shader = "sharp_bilinear",
                    params = mapOf("sharpness" to 0.15f),
                ),
                FilterPassTemplate(
                    shader = "brightness_contrast",
                    params = mapOf(
                        "brightness" to 0.0f,
                        "contrast" to 1.05f,
                        "gamma" to 1.0f,
                        "saturation" to 1.0f,
                    ),
                ),
                FilterPassTemplate(
                    shader = "sharpen",
                    params = mapOf("strength" to 0.15f),
                ),
            ),
            defaultParams = mapOf(
                "brightness" to 1.0f,
                "contrast" to 1.05f,
                "gamma" to 1.0f,
                "saturation" to 1.0f,
                "sharpness" to 0.15f,
            ),
        ))
    }

    fun register(preset: FilterPreset) {
        presets[preset.id] = preset
    }

    fun getPreset(id: String): FilterPreset? = presets[id]

    fun getAllPresets(): List<FilterPreset> = presets.values.toList()

    fun getPresetsForEngine(engineId: String): List<FilterPreset> {
        return presets.values.filter {
            it.compatibleEngines.isEmpty() || it.compatibleEngines.contains(engineId)
        }
    }

    /**
     * Resolve a preset + per-game video overrides into a final config
     * that the native renderer can consume directly.
     *
     * Override semantics: user slider values are absolute.
     * If a slider differs from the preset default, it overrides.
     * The preset default is used for parameters the user hasn't touched.
     */
    fun resolve(video: VideoSection): ResolvedFilterConfig {
        val presetId = video.screenFilter
        if (presetId == "none" || presetId == "off") {
            return ResolvedFilterConfig.DISABLED
        }

        val preset = presets[presetId] ?: return ResolvedFilterConfig.DISABLED

        if (preset.passes.isEmpty()) {
            return ResolvedFilterConfig(
                enabled = true,
                preset = presetId,
                aspectMode = video.aspectMode,
                passes = emptyList(),
            )
        }

        // Build override map from video section.
        // Only include values that differ from neutral/identity defaults.
        val overrides = mutableMapOf<String, Float>()
        if (video.brightness != 1.0f) overrides["brightness"] = video.brightness
        if (video.contrast != 1.0f) overrides["contrast"] = video.contrast
        if (video.gamma != 1.0f) overrides["gamma"] = video.gamma
        if (video.saturation != 1.0f) overrides["saturation"] = video.saturation
        if (video.sharpness != 0.0f) overrides["sharpness"] = video.sharpness

        // Resolve each pass: merge preset defaults with overrides.
        val resolvedPasses = preset.passes.map { template ->
            val mergedParams = template.params.toMutableMap()

            // Apply overrides that are relevant to this pass's shader.
            for ((key, value) in overrides) {
                if (mergedParams.containsKey(key)) {
                    mergedParams[key] = value
                }
            }

            ResolvedPass(
                shader = template.shader,
                params = mergedParams,
            )
        }

        return ResolvedFilterConfig(
            enabled = true,
            preset = presetId,
            aspectMode = video.aspectMode,
            passes = resolvedPasses,
        )
    }

    /** Default preset ID for new installs. */
    const val DEFAULT_PRESET = "clean_sharp"
}
