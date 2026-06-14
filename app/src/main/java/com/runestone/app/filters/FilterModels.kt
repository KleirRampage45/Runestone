/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright 2026 Gerson (KleirRampage45)
 *
 * Visual filter data model: presets, passes, and parameter definitions.
 */

package com.runestone.app.filters

import org.json.JSONArray
import org.json.JSONObject

/**
 * A named collection of filter passes with default parameters.
 * Resolved by FilterManager into a concrete [ResolvedFilterConfig].
 */
data class FilterPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val performanceTier: Int = 1,
    val compatibleEngines: List<String> = emptyList(), // empty = all
    val passes: List<FilterPassTemplate> = emptyList(),
    val defaultParams: Map<String, Float> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("description", description)
        put("tags", JSONArray(tags))
        put("performanceTier", performanceTier)
        put("compatibleEngines", JSONArray(compatibleEngines))
        put("passes", JSONArray(passes.map { it.toJson() }))
        put("defaultParams", JSONObject().apply {
            defaultParams.forEach { (k, v) -> put(k, v.toDouble()) }
        })
    }

    companion object {
        fun fromJson(j: JSONObject): FilterPreset = FilterPreset(
            id = j.getString("id"),
            displayName = j.getString("displayName"),
            description = j.optString("description", ""),
            tags = j.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            performanceTier = j.optInt("performanceTier", 1),
            compatibleEngines = j.optJSONArray("compatibleEngines")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            passes = j.optJSONArray("passes")?.let { arr ->
                (0 until arr.length()).map { FilterPassTemplate.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            defaultParams = j.optJSONObject("defaultParams")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getDouble(it).toFloat() }
            } ?: emptyMap(),
        )
    }
}

/**
 * Template for a single shader pass within a preset.
 * Contains default parameter values that can be overridden per-game.
 */
data class FilterPassTemplate(
    val shader: String,
    val params: Map<String, Float> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shader", shader)
        put("params", JSONObject().apply {
            params.forEach { (k, v) -> put(k, v.toDouble()) }
        })
    }

    companion object {
        fun fromJson(j: JSONObject): FilterPassTemplate = FilterPassTemplate(
            shader = j.getString("shader"),
            params = j.optJSONObject("params")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getDouble(it).toFloat() }
            } ?: emptyMap(),
        )
    }
}

/**
 * A fully resolved filter configuration ready to be written as JSON
 * for the native renderer. All values are absolute — no presets or overrides.
 */
data class ResolvedFilterConfig(
    val enabled: Boolean,
    val preset: String,
    val aspectMode: String,
    val passes: List<ResolvedPass>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("preset", preset)
        put("aspectMode", aspectMode)
        put("passes", JSONArray(passes.map { it.toJson() }))
    }

    companion object {
        val DISABLED = ResolvedFilterConfig(
            enabled = false,
            preset = "off",
            aspectMode = "fit_4_3",
            passes = emptyList(),
        )
    }
}

/**
 * A single resolved pass with final parameter values.
 */
data class ResolvedPass(
    val shader: String,
    val params: Map<String, Float>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shader", shader)
        put("params", JSONObject().apply {
            params.forEach { (k, v) -> put(k, v.toDouble()) }
        })
    }
}

/** Aspect mode constants. */
object AspectMode {
    const val FIT_4_3 = "fit_4_3"
    const val FIT_ORIGINAL = "fit_original"
    const val FILL = "fill"
    const val CROP = "crop"
    const val STRETCH = "stretch"
    const val INTEGER_CENTER = "integer_center"
}

/** Performance tier constants. */
object PerfTier {
    const val FREE = 0      // passthrough
    const val LOW = 1       // single-pass color
    const val MEDIUM = 2    // multi-pass CRT Lite
    const val HIGH = 3      // heavy multi-pass
    const val EXPERIMENTAL = 4
}
