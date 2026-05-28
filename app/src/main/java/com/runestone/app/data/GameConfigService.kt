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

    /** Load config from original/runestone.json in the game's workspace. */
    fun loadPerGame(storageName: String): PerGameConfig {
        val configFile = java.io.File(workspaceManager.originalDir(storageName), "runestone.json")
        return PerGameConfig.load(configFile)
    }

    /** Save config to original/runestone.json. */
    fun savePerGame(storageName: String, config: PerGameConfig) {
        val configFile = java.io.File(workspaceManager.originalDir(storageName), "runestone.json")
        PerGameConfig.save(configFile, config)
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
}
