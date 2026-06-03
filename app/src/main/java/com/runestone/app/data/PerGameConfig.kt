/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Per-game configuration stored as runestone.json in each game's workspace.
 * Uses Android's built-in org.json — no extra dependencies.
 */

package com.runestone.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PerGameConfig(
    val version: Int = 1,
    val game: GameSection = GameSection(),
    val input: InputSection = InputSection(),
    val video: VideoSection = VideoSection(),
    val audio: AudioSection = AudioSection(),
    val performance: PerformanceSection = PerformanceSection(),
    val cheats: CheatSection = CheatSection(),
    val fonts: FontSection = FontSection(),
    val metadata: MetadataSection = MetadataSection(),
) {
    companion object {
        fun load(file: File): PerGameConfig {
            if (!file.exists()) return PerGameConfig()
            return try { fromJson(JSONObject(file.readText())) }
            catch (e: Exception) { PerGameConfig() }
        }

        fun save(file: File, config: PerGameConfig) {
            file.writeText(config.toJson().toString(2))
        }

        fun fromJson(json: JSONObject): PerGameConfig {
            return PerGameConfig(
                version = json.optInt("version", 1),
                game = GameSection.fromJson(json.optJSONObject("game")),
                input = InputSection.fromJson(json.optJSONObject("input")),
                video = VideoSection.fromJson(json.optJSONObject("video")),
                audio = AudioSection.fromJson(json.optJSONObject("audio")),
                performance = PerformanceSection.fromJson(json.optJSONObject("performance")),
                cheats = CheatSection.fromJson(json.optJSONObject("cheats")),
                fonts = FontSection.fromJson(json.optJSONObject("fonts")),
                metadata = MetadataSection.fromJson(json.optJSONObject("metadata")),
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version)
        put("game", game.toJson())
        put("input", input.toJson())
        put("video", video.toJson())
        put("audio", audio.toJson())
        put("performance", performance.toJson())
        put("cheats", cheats.toJson())
        put("fonts", fonts.toJson())
        put("metadata", metadata.toJson())
    }
}

// ── Sections ───────────────────────────────────────────────────────

data class GameSection(
    val title: String = "",
    val engine: String = "",
    val engineOverride: String? = null,
    val customCoverPath: String? = null,
) {
    companion object {
        fun fromJson(j: JSONObject?): GameSection {
            if (j == null) return GameSection()
            return GameSection(
                title = j.optString("title", ""),
                engine = j.optString("engine", ""),
                engineOverride = j.optString("engineOverride", null),
                customCoverPath = j.optString("customCoverPath", null),
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("title", title)
        put("engine", engine)
        putOpt("engineOverride", engineOverride)
        putOpt("customCoverPath", customCoverPath)
    }
}

data class InputSection(
    val layoutMode: String = "portrait_console",
    val buttonOpacity: Float = 0.72f,
    val buttonScale: Float = 1.0f,
    val showExtraButtons: Boolean = false,
    val showL1R1: Boolean = false,
    val showL2R2: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val hapticIntensity: Float = 0.55f,
    val controllerPreset: String = "auto",
    val invertAxisX: Boolean = false,
    val invertAxisY: Boolean = false,
    val deadZone: Float = 0.3f,
    val buttonLayout: ButtonLayout? = null,
    val controllerMapping: Map<String, String> = emptyMap(),
) {
    companion object {
        fun fromJson(j: JSONObject?): InputSection {
            if (j == null) return InputSection()
            return InputSection(
                layoutMode = j.optString("layoutMode", "portrait_console"),
                buttonOpacity = j.optDouble("buttonOpacity", 0.72).toFloat(),
                buttonScale = j.optDouble("buttonScale", 1.0).toFloat(),
                showExtraButtons = j.optBoolean("showExtraButtons", false),
                showL1R1 = j.optBoolean("showL1R1", false),
                showL2R2 = j.optBoolean("showL2R2", false),
                hapticsEnabled = j.optBoolean("hapticsEnabled", true),
                hapticIntensity = j.optDouble("hapticIntensity", 0.55).toFloat(),
                controllerPreset = j.optString("controllerPreset", "auto"),
                invertAxisX = j.optBoolean("invertAxisX", false),
                invertAxisY = j.optBoolean("invertAxisY", false),
                deadZone = j.optDouble("deadZone", 0.3).toFloat(),
                buttonLayout = ButtonLayout.fromJson(j.optJSONObject("buttonLayout")),
                controllerMapping = jsonToStringMap(j.optJSONObject("controllerMapping")),
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("layoutMode", layoutMode)
        put("buttonOpacity", buttonOpacity.toDouble())
        put("buttonScale", buttonScale.toDouble())
        put("showExtraButtons", showExtraButtons)
        put("showL1R1", showL1R1)
        put("showL2R2", showL2R2)
        put("hapticsEnabled", hapticsEnabled)
        put("hapticIntensity", hapticIntensity.toDouble())
        put("controllerPreset", controllerPreset)
        put("invertAxisX", invertAxisX)
        put("invertAxisY", invertAxisY)
        put("deadZone", deadZone.toDouble())
        if (buttonLayout != null) put("buttonLayout", buttonLayout.toJson())
        if (controllerMapping.isNotEmpty()) put("controllerMapping", JSONObject(controllerMapping))
    }
}

data class ButtonLayout(
    val dpad: ButtonPos = ButtonPos(0.22, 0.50, 1.0),
    val btnA: ButtonPos = ButtonPos(0.78, 0.70, 1.0),
    val btnB: ButtonPos = ButtonPos(0.85, 0.50, 1.0),
    val btnX: ButtonPos? = null,
    val btnY: ButtonPos? = null,
    val select: ButtonPos = ButtonPos(0.20, 0.90, 1.0),
    val start: ButtonPos = ButtonPos(0.50, 0.90, 1.0),
    val menu: ButtonPos = ButtonPos(0.80, 0.90, 1.0),
) {
    companion object {
        fun fromJson(j: JSONObject?): ButtonLayout? {
            if (j == null) return null
            return ButtonLayout(
                dpad = posOrDef(j, "dpad", 0.22, 0.50),
                btnA = posOrDef(j, "btnA", 0.78, 0.70),
                btnB = posOrDef(j, "btnB", 0.85, 0.50),
                btnX = posOrNull(j, "btnX"),
                btnY = posOrNull(j, "btnY"),
                select = posOrDef(j, "select", 0.20, 0.90),
                start = posOrDef(j, "start", 0.50, 0.90),
                menu = posOrDef(j, "menu", 0.80, 0.90),
            )
        }
        private fun posOrDef(j: JSONObject, key: String, dx: Double, dy: Double): ButtonPos {
            val p = j.optJSONObject(key) ?: return ButtonPos(dx, dy, 1.0)
            return ButtonPos(p.optDouble("x", dx), p.optDouble("y", dy), p.optDouble("size", 1.0))
        }
        private fun posOrNull(j: JSONObject, key: String): ButtonPos? {
            val p = j.optJSONObject(key) ?: return null
            return ButtonPos(p.optDouble("x"), p.optDouble("y"), p.optDouble("size", 1.0))
        }
    }
    fun toJson() = JSONObject().apply {
        fun putPos(key: String, pos: ButtonPos?) {
            if (pos != null) put(key, JSONObject().apply {
                put("x", pos.x); put("y", pos.y); put("size", pos.size)
            })
        }
        putPos("dpad", dpad)
        putPos("btnA", btnA)
        putPos("btnB", btnB)
        putPos("btnX", btnX)
        putPos("btnY", btnY)
        putPos("select", select)
        putPos("start", start)
        putPos("menu", menu)
    }
}

data class ButtonPos(val x: Double, val y: Double, val size: Double = 1.0)

data class VideoSection(
    val screenFilter: String = "none",
    val integerScaling: Boolean = false,
    val smoothScaling: Boolean = false,
    val showFps: Boolean = true,
    val vsync: Boolean = true,
    val resolutionScale: Float = 1.0f,
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
) {
    companion object {
        fun fromJson(j: JSONObject?): VideoSection {
            if (j == null) return VideoSection()
            return VideoSection(
                screenFilter = j.optString("screenFilter", "none"),
                integerScaling = j.optBoolean("integerScaling", false),
                smoothScaling = j.optBoolean("smoothScaling", false),
                showFps = j.optBoolean("showFps", true),
                vsync = j.optBoolean("vsync", true),
                resolutionScale = j.optDouble("resolutionScale", 1.0).toFloat(),
                brightness = j.optDouble("brightness", 1.0).toFloat(),
                contrast = j.optDouble("contrast", 1.0).toFloat(),
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("screenFilter", screenFilter)
        put("integerScaling", integerScaling)
        put("smoothScaling", smoothScaling)
        put("showFps", showFps)
        put("vsync", vsync)
        put("resolutionScale", resolutionScale.toDouble())
        put("brightness", brightness.toDouble())
        put("contrast", contrast.toDouble())
    }
}

data class AudioSection(
    val forceAudioExt: String = ".ogg",
    val audioBufferSize: Int = 2048,
    val muteMusic: Boolean = false,
    val muteSfx: Boolean = false,
    val muteVideo: Boolean = false,
    val volume: Float = 1.0f,
    val volumeMusic: Float = 1.0f,
    val volumeSfx: Float = 1.0f,
) {
    companion object {
        fun fromJson(j: JSONObject?): AudioSection {
            if (j == null) return AudioSection()
            return AudioSection(
                forceAudioExt = j.optString("forceAudioExt", ".ogg"),
                audioBufferSize = j.optInt("audioBufferSize", 2048),
                muteMusic = j.optBoolean("muteMusic", false),
                muteSfx = j.optBoolean("muteSfx", false),
                muteVideo = j.optBoolean("muteVideo", false),
                volume = j.optDouble("volume", 1.0).toFloat(),
                volumeMusic = j.optDouble("volumeMusic", 1.0).toFloat(),
                volumeSfx = j.optDouble("volumeSfx", 1.0).toFloat(),
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("forceAudioExt", forceAudioExt)
        put("audioBufferSize", audioBufferSize)
        put("muteMusic", muteMusic)
        put("muteSfx", muteSfx)
        put("muteVideo", muteVideo)
        put("volume", volume.toDouble())
        put("volumeMusic", volumeMusic.toDouble())
        put("volumeSfx", volumeSfx.toDouble())
    }
}

data class PerformanceSection(
    val speedMultiplier: Float = 1.0f,
    val frameSkip: Int = 0,
    val threadedRendering: Boolean = true,
    val forceMiniz: Boolean = false,
    val optimizeMaps: Boolean = false,
    val textureCacheSize: Int = 64,
    val reduceShadows: Boolean = false,
    val reduceParticles: Boolean = false,
    val backgroundLoading: Boolean = true,
) {
    companion object {
        fun fromJson(j: JSONObject?): PerformanceSection {
            if (j == null) return PerformanceSection()
            return PerformanceSection(
                speedMultiplier = j.optDouble("speedMultiplier", 1.0).toFloat(),
                frameSkip = j.optInt("frameSkip", 0),
                threadedRendering = j.optBoolean("threadedRendering", true),
                forceMiniz = j.optBoolean("forceMiniz", false),
                optimizeMaps = j.optBoolean("optimizeMaps", false),
                textureCacheSize = j.optInt("textureCacheSize", 64),
                reduceShadows = j.optBoolean("reduceShadows", false),
                reduceParticles = j.optBoolean("reduceParticles", false),
                backgroundLoading = j.optBoolean("backgroundLoading", true),
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("speedMultiplier", speedMultiplier.toDouble())
        put("frameSkip", frameSkip)
        put("threadedRendering", threadedRendering)
        put("forceMiniz", forceMiniz)
        put("optimizeMaps", optimizeMaps)
        put("textureCacheSize", textureCacheSize)
        put("reduceShadows", reduceShadows)
        put("reduceParticles", reduceParticles)
        put("backgroundLoading", backgroundLoading)
    }
}

data class CheatSection(
    val enabled: Boolean = false,
    val postLoadScripts: List<String> = emptyList(),
) {
    companion object {
        fun fromJson(j: JSONObject?): CheatSection {
            if (j == null) return CheatSection()
            val arr = j.optJSONArray("postLoadScripts")
            val scripts = mutableListOf<String>()
            if (arr != null) for (i in 0 until arr.length()) scripts.add(arr.optString(i, ""))
            return CheatSection(
                enabled = j.optBoolean("enabled", false),
                postLoadScripts = scripts,
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("enabled", enabled)
        if (postLoadScripts.isNotEmpty()) {
            val arr = JSONArray()
            postLoadScripts.forEach { arr.put(it) }
            put("postLoadScripts", arr)
        }
    }
}

data class FontSection(
    val fontScale: Float = 1.0f,
    val boldText: Boolean = false,
    val italicText: Boolean = false,
    val fallbackFont: String? = null,
    val useGameFonts: Boolean = true,
    val textOutline: Int = 0,
    val lineSpacing: Float = 1.0f,
) {
    companion object {
        fun fromJson(j: JSONObject?): FontSection {
            if (j == null) return FontSection()
            return FontSection(
                fontScale = j.optDouble("fontScale", 1.0).toFloat(),
                boldText = j.optBoolean("boldText", false),
                italicText = j.optBoolean("italicText", false),
                fallbackFont = j.optString("fallbackFont", null),
                useGameFonts = j.optBoolean("useGameFonts", true),
                textOutline = j.optInt("textOutline", 0),
                lineSpacing = j.optDouble("lineSpacing", 1.0).toFloat(),
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("fontScale", fontScale.toDouble())
        put("boldText", boldText)
        put("italicText", italicText)
        putOpt("fallbackFont", fallbackFont)
        put("useGameFonts", useGameFonts)
        put("textOutline", textOutline)
        put("lineSpacing", lineSpacing.toDouble())
    }
}

data class MetadataSection(
    val gameTitle: String = "",
    val description: String = "",
    val developer: String = "",
    val publisher: String = "",
    val genres: String = "",
    val releaseYear: String = "",
    val coverUrl: String = "",
    val localCoverPath: String = "",
    val metadataSource: String = "",
) {
    companion object {
        fun fromJson(j: JSONObject?): MetadataSection {
            if (j == null) return MetadataSection()
            return MetadataSection(
                gameTitle = j.optString("gameTitle", ""),
                description = j.optString("description", ""),
                developer = j.optString("developer", ""),
                publisher = j.optString("publisher", ""),
                genres = j.optString("genres", ""),
                releaseYear = j.optString("releaseYear", ""),
                coverUrl = j.optString("coverUrl", ""),
                localCoverPath = j.optString("localCoverPath", ""),
                metadataSource = j.optString("metadataSource", ""),
            )
        }
    }
    fun toJson() = JSONObject().apply {
        put("gameTitle", gameTitle)
        put("description", description)
        put("developer", developer)
        put("publisher", publisher)
        put("genres", genres)
        put("releaseYear", releaseYear)
        put("coverUrl", coverUrl)
        put("localCoverPath", localCoverPath)
        put("metadataSource", metadataSource)
    }
}

// ── Helper ─────────────────────────────────────────────────────────

private fun jsonToStringMap(j: JSONObject?): Map<String, String> {
    if (j == null) return emptyMap()
    val map = mutableMapOf<String, String>()
    j.keys().forEach { key -> map[key] = j.optString(key, "") }
    return map
}
