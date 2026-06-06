/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Layered settings resolver: runtime > per-game > global > defaults.
 */

package com.runestone.app.data

import android.content.Context
import com.runestone.app.ui.SettingsStore
import com.runestone.app.workspace.WorkspaceManager
import org.json.JSONObject

class GameConfigService(
    private val context: Context,
    private val workspaceManager: WorkspaceManager,
) {
    private val globalStore = SettingsStore(context)

    /** Compute effective config by merging all layers. */
    fun resolve(storageName: String? = null): PerGameConfig {
        val defaults = PerGameConfig()
        val global = globalToConfig(globalStore.load())
        val perGame = if (storageName != null) loadPerGame(storageName) else null
        return merge(defaults, global, perGame)
    }

    /** Load config from runestone.json beside the installed game files. */
    fun loadPerGame(storageName: String): PerGameConfig {
        val configFile = java.io.File(workspaceManager.originalDir(storageName), "runestone.json")
        return PerGameConfig.load(configFile)
    }

    /** Save config to runestone.json beside the installed game files. */
    fun savePerGame(storageName: String, config: PerGameConfig) {
        val configFile = java.io.File(workspaceManager.originalDir(storageName), "runestone.json")
        PerGameConfig.save(configFile, config)
    }

    fun resolveRunnerSettings(storageName: String? = null): RunnerSettings {
        var result = globalStore.load()
        if (storageName == null) return result

        val configFile = java.io.File(workspaceManager.originalDir(storageName), "runestone.json")
        if (!configFile.isFile) return result

        val json = runCatching { JSONObject(configFile.readText()) }.getOrNull() ?: return result
        json.optJSONObject("input")?.let { input ->
            result = result.copy(
                layoutMode = if (input.has("layoutMode")) {
                    parseLayoutMode(input.optString("layoutMode"), result.layoutMode)
                } else result.layoutMode,
                touchOpacity = if (input.has("buttonOpacity")) input.optDouble("buttonOpacity", result.touchOpacity.toDouble()).toFloat() else result.touchOpacity,
                touchScale = if (input.has("buttonScale")) input.optDouble("buttonScale", result.touchScale.toDouble()).toFloat() else result.touchScale,
                hapticsEnabled = if (input.has("hapticsEnabled")) input.optBoolean("hapticsEnabled", result.hapticsEnabled) else result.hapticsEnabled,
                hapticIntensity = if (input.has("hapticIntensity")) input.optDouble("hapticIntensity", result.hapticIntensity.toDouble()).toFloat() else result.hapticIntensity,
                showExtraButtons = if (input.has("showExtraButtons")) input.optBoolean("showExtraButtons", result.showExtraButtons) else result.showExtraButtons,
                hideVirtualGamepad = if (input.has("hideVirtualGamepad")) input.optBoolean("hideVirtualGamepad", result.hideVirtualGamepad) else result.hideVirtualGamepad,
                diagonalMovement = if (input.has("diagonalMovement")) input.optBoolean("diagonalMovement", result.diagonalMovement) else result.diagonalMovement,
            )
        }
        json.optJSONObject("video")?.let { video ->
            result = result.copy(
                integerScaling = if (video.has("integerScaling")) video.optBoolean("integerScaling", result.integerScaling) else result.integerScaling,
                smoothScaling = if (video.has("smoothScaling")) video.optBoolean("smoothScaling", result.smoothScaling) else result.smoothScaling,
                vsync = if (video.has("vsync")) video.optBoolean("vsync", result.vsync) else result.vsync,
            )
        }
        json.optJSONObject("audio")?.let { audio ->
            result = result.copy(
                forceAudioExt = if (audio.has("forceAudioExt")) audio.optString("forceAudioExt", result.forceAudioExt) else result.forceAudioExt,
            )
        }
        json.optJSONObject("performance")?.let { perf ->
            result = result.copy(
                frameSkip = if (perf.has("frameSkip")) perf.optInt("frameSkip", if (result.frameSkip) 1 else 0) > 0 else result.frameSkip,
            )
        }
        json.optJSONObject("fonts")?.let { fonts ->
            result = result.copy(
                textScale = if (fonts.has("fontScale")) fonts.optDouble("fontScale", result.textScale.toDouble()).toFloat() else result.textScale,
            )
        }
        return result
    }

    // ---------- merge --------------------------------------------------

    private fun merge(vararg layers: PerGameConfig?): PerGameConfig {
        var result = PerGameConfig()
        for (layer in layers) {
            if (layer == null) continue
            result = result.copy(
                game = mergeGame(result.game, layer.game),
                input = mergeInput(result.input, layer.input),
                video = mergeVideo(result.video, layer.video),
                audio = mergeAudio(result.audio, layer.audio),
                performance = mergePerf(result.performance, layer.performance),
                cheats = mergeCheats(result.cheats, layer.cheats),
                fonts = mergeFonts(result.fonts, layer.fonts),
                patches = layer.patches,  // patches are per-game only
            )
        }
        return result
    }

    private fun mergeGame(b: GameSection, o: GameSection) = b.copy(
        title = o.title.ifEmpty { b.title },
        engine = o.engine.ifEmpty { b.engine },
        engineOverride = o.engineOverride ?: b.engineOverride,
    )

    private fun mergeInput(b: InputSection, o: InputSection): InputSection {
        fun <T> override(a: T, def: T, bVal: T): T = if (a != def) a else bVal
        return b.copy(
            layoutMode = override(o.layoutMode, "portrait_console", b.layoutMode),
            buttonOpacity = override(o.buttonOpacity, 0.72f, b.buttonOpacity),
            buttonScale = override(o.buttonScale, 1.0f, b.buttonScale),
            showExtraButtons = o.showExtraButtons,
            hideVirtualGamepad = o.hideVirtualGamepad,
            diagonalMovement = o.diagonalMovement,
            showL1R1 = o.showL1R1,
            showL2R2 = o.showL2R2,
            hapticsEnabled = o.hapticsEnabled,
            hapticIntensity = override(o.hapticIntensity, 0.55f, b.hapticIntensity),
            controllerPreset = override(o.controllerPreset, "auto", b.controllerPreset),
            invertAxisX = o.invertAxisX,
            invertAxisY = o.invertAxisY,
            deadZone = override(o.deadZone, 0.3f, b.deadZone),
            buttonLayout = o.buttonLayout ?: b.buttonLayout,
            controllerMapping = if (o.controllerMapping.isNotEmpty()) o.controllerMapping else b.controllerMapping,
        )
    }

    private fun mergeVideo(b: VideoSection, o: VideoSection): VideoSection {
        fun <T> override(a: T, def: T, bVal: T): T = if (a != def) a else bVal
        return b.copy(
            screenFilter = override(o.screenFilter, "none", b.screenFilter),
            integerScaling = o.integerScaling,
            smoothScaling = o.smoothScaling,
            showFps = o.showFps,
            vsync = o.vsync,
            resolutionScale = override(o.resolutionScale, 1.0f, b.resolutionScale),
            brightness = override(o.brightness, 1.0f, b.brightness),
            contrast = override(o.contrast, 1.0f, b.contrast),
        )
    }

    private fun mergeAudio(b: AudioSection, o: AudioSection): AudioSection {
        fun <T> override(a: T, def: T, bVal: T): T = if (a != def) a else bVal
        return b.copy(
            forceAudioExt = override(o.forceAudioExt, ".ogg", b.forceAudioExt),
            audioBufferSize = override(o.audioBufferSize, 2048, b.audioBufferSize),
            muteMusic = o.muteMusic,
            muteSfx = o.muteSfx,
            muteVideo = o.muteVideo,
            volume = override(o.volume, 1.0f, b.volume),
            volumeMusic = override(o.volumeMusic, 1.0f, b.volumeMusic),
            volumeSfx = override(o.volumeSfx, 1.0f, b.volumeSfx),
        )
    }

    private fun mergePerf(b: PerformanceSection, o: PerformanceSection): PerformanceSection {
        fun <T> override(a: T, def: T, bVal: T): T = if (a != def) a else bVal
        return b.copy(
            speedMultiplier = override(o.speedMultiplier, 1.0f, b.speedMultiplier),
            frameSkip = override(o.frameSkip, 0, b.frameSkip),
            threadedRendering = o.threadedRendering,
            forceMiniz = o.forceMiniz,
            optimizeMaps = o.optimizeMaps,
            textureCacheSize = override(o.textureCacheSize, 64, b.textureCacheSize),
            reduceShadows = o.reduceShadows,
            reduceParticles = o.reduceParticles,
            backgroundLoading = o.backgroundLoading,
        )
    }

    private fun mergeCheats(b: CheatSection, o: CheatSection) = b.copy(
        enabled = o.enabled,
        postLoadScripts = if (o.postLoadScripts.isNotEmpty()) o.postLoadScripts else b.postLoadScripts,
    )

    private fun mergeFonts(b: FontSection, o: FontSection): FontSection {
        fun <T> override(a: T, def: T, bVal: T): T = if (a != def) a else bVal
        return b.copy(
            fontScale = override(o.fontScale, 1.0f, b.fontScale),
            boldText = o.boldText,
            italicText = o.italicText,
            fallbackFont = o.fallbackFont ?: b.fallbackFont,
            useGameFonts = o.useGameFonts,
            textOutline = override(o.textOutline, 0, b.textOutline),
            lineSpacing = override(o.lineSpacing, 1.0f, b.lineSpacing),
        )
    }

    // ---------- global bridge -------------------------------------------

    private fun globalToConfig(settings: RunnerSettings): PerGameConfig {
        return PerGameConfig(
            input = InputSection(
                layoutMode = settings.layoutMode.name.lowercase(),
                buttonOpacity = settings.touchOpacity,
                buttonScale = settings.touchScale,
                showExtraButtons = settings.showExtraButtons,
                hideVirtualGamepad = settings.hideVirtualGamepad,
                diagonalMovement = settings.diagonalMovement,
                hapticsEnabled = settings.hapticsEnabled,
                hapticIntensity = settings.hapticIntensity,
            ),
            video = VideoSection(
                integerScaling = settings.integerScaling,
                smoothScaling = settings.smoothScaling,
            ),
            fonts = FontSection(
                fontScale = settings.textScale,
            ),
        )
    }

    private fun parseLayoutMode(value: String, fallback: LayoutMode): LayoutMode {
        val normalized = value.trim().replace('-', '_')
        return LayoutMode.values().firstOrNull {
            it.name.equals(normalized, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
        } ?: fallback
    }
}
