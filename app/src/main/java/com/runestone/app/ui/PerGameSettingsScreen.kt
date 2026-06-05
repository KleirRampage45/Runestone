/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Per-game settings screen with glassmorphism UI.
 */

package com.runestone.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.runestone.app.data.*
import com.runestone.app.workspace.PatchManager
import java.io.File

class PerGameSettingsScreen(private val context: Context) {

    fun create(
        gameTitle: String,
        config: PerGameConfig,
        storageName: String = "",
        onConfigChanged: (PerGameConfig) -> Unit,
        onBack: () -> Unit,
        onPickCover: ((pathCallback: (String) -> Unit) -> Unit) = {},
        onFetchMetadata: ((Boolean) -> Unit) -> Unit = {},
        onInstallPatch: ((zipCallback: (String) -> Unit) -> Unit) = {},
    ): LinearLayout {
        var current = config

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(makeTopBar(gameTitle, onBack))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll.addView(content)
        content.alpha = 0f

        // ── Hero Card Cover ──
        content.addView(sectionTitle("Hero Card", "Custom cover image for game card"))
        content.addView(coverPicker(current.game.customCoverPath,
            onPick = { setPath ->
                onPickCover { pickedPath ->
                    if (pickedPath.isNotEmpty()) {
                        current = current.copy(game = current.game.copy(customCoverPath = pickedPath))
                        onConfigChanged(current)
                        setPath(pickedPath)
                        (context as? android.app.Activity)?.runOnUiThread {
                            android.widget.Toast.makeText(context, "Cover image set!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onClear = { clearPath ->
                current = current.copy(game = current.game.copy(customCoverPath = null))
                onConfigChanged(current)
                clearPath(current.game.customCoverPath.orEmpty())
            }
        ))
        content.addView(spacer(h = 14))

        // ── Metadata ──
        content.addView(sectionTitle("Metadata", "Game info from RAWG. Fetch or edit manually."))
        val meta = current.metadata

        content.addView(metadataEditRow("Title", meta.gameTitle) { v ->
            current = current.copy(metadata = current.metadata.copy(gameTitle = v)); onConfigChanged(current) })
        content.addView(metadataEditRow("Developer", meta.developer) { v ->
            current = current.copy(metadata = current.metadata.copy(developer = v)); onConfigChanged(current) })
        content.addView(metadataEditRow("Publisher", meta.publisher) { v ->
            current = current.copy(metadata = current.metadata.copy(publisher = v)); onConfigChanged(current) })
        content.addView(metadataEditRow("Genres", meta.genres) { v ->
            current = current.copy(metadata = current.metadata.copy(genres = v)); onConfigChanged(current) })
        content.addView(metadataEditRow("Year", meta.releaseYear) { v ->
            current = current.copy(metadata = current.metadata.copy(releaseYear = v)); onConfigChanged(current) })
        content.addView(metadataEditRow("Description", meta.description) { v ->
            current = current.copy(metadata = current.metadata.copy(description = v)); onConfigChanged(current) })
        content.addView(spacer(h = 6))

        val fetchBtn = TextView(context).apply {
            val hasMetadata = meta.metadataSource.isNotEmpty()
            text = if (hasMetadata) "REFETCH FROM RAWG (\u2192)" else "FETCH FROM RAWG (\u2192)"
            setTextColor(Theme.active.accent); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Theme.active.accentBg)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(80,
                    Color.red(Theme.active.accent),
                    Color.green(Theme.active.accent),
                    Color.blue(Theme.active.accent)))
            }
            makeLiquid(this)
            setOnClickListener {
                text = "FETCHING..."
                isEnabled = false
                onFetchMetadata { ok ->
                    post {
                        text = if (ok) "DONE" else "RETRY"
                        isEnabled = true
                    }
                }
            }
        }
        content.addView(fetchBtn)
        if (meta.metadataSource.isNotEmpty()) {
            content.addView(TextView(context).apply {
                text = "Source: ${meta.metadataSource}"
                setTextColor(MUTED); textSize = 10f; setPadding(dp(4), dp(3), 0, 0)
            })
        }
        content.addView(spacer(h = 14))

        // ── Input Section ──
        content.addView(sectionTitle("Input", "Touch controls and haptics"))

        content.addView(switchPanel("Haptic Feedback", "Vibrate when controls are pressed",
            current.input.hapticsEnabled) { checked ->
            current = current.copy(input = current.input.copy(hapticsEnabled = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Haptic Intensity",
            "${(current.input.hapticIntensity * 100).toInt()}%") { label ->
            slider(100, (current.input.hapticIntensity * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(input = current.input.copy(hapticIntensity = progress / 100f))
                label.text = "${(current.input.hapticIntensity * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Button Opacity",
            "${(current.input.buttonOpacity * 100).toInt()}%") { label ->
            slider(100, (current.input.buttonOpacity * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(input = current.input.copy(buttonOpacity = progress / 100f))
                label.text = "${(current.input.buttonOpacity * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Button Scale",
            "${(current.input.buttonScale * 100).toInt()}%") { label ->
            slider(100, ((current.input.buttonScale - 0.5f) * 200).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(input = current.input.copy(buttonScale = 0.5f + progress / 200f))
                label.text = "${(current.input.buttonScale * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Video Section ──
        content.addView(sectionTitle("Video", "Display and rendering"))

        content.addView(switchPanel("Show FPS", "Display frame rate counter",
            current.video.showFps) { checked ->
            current = current.copy(video = current.video.copy(showFps = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("VSync", "Synchronize frame rate with display",
            current.video.vsync) { checked ->
            current = current.copy(video = current.video.copy(vsync = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Integer Scaling", "Pixel-perfect scaling (may add black bars)",
            current.video.integerScaling) { checked ->
            current = current.copy(video = current.video.copy(integerScaling = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Smooth Scaling", "Bilinear filtering for smoother image",
            current.video.smoothScaling) { checked ->
            current = current.copy(video = current.video.copy(smoothScaling = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Brightness",
            "${(current.video.brightness * 100).toInt()}%") { label ->
            slider(200, (current.video.brightness * 100).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(video = current.video.copy(brightness = progress / 100f))
                label.text = "${(current.video.brightness * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Contrast",
            "${(current.video.contrast * 100).toInt()}%") { label ->
            slider(200, (current.video.contrast * 100).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(video = current.video.copy(contrast = progress / 100f))
                label.text = "${(current.video.contrast * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Audio Section ──
        content.addView(sectionTitle("Audio", "Sound and music"))

        content.addView(switchPanel("Mute Music", "Disable background music",
            current.audio.muteMusic) { checked ->
            current = current.copy(audio = current.audio.copy(muteMusic = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Mute Sound Effects", "Disable sound effects",
            current.audio.muteSfx) { checked ->
            current = current.copy(audio = current.audio.copy(muteSfx = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Mute Video Audio", "Disable video sound",
            current.audio.muteVideo) { checked ->
            current = current.copy(audio = current.audio.copy(muteVideo = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Master Volume",
            "${(current.audio.volume * 100).toInt()}%") { label ->
            slider(100, (current.audio.volume * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(audio = current.audio.copy(volume = progress / 100f))
                label.text = "${(current.audio.volume * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Music Volume",
            "${(current.audio.volumeMusic * 100).toInt()}%") { label ->
            slider(100, (current.audio.volumeMusic * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(audio = current.audio.copy(volumeMusic = progress / 100f))
                label.text = "${(current.audio.volumeMusic * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("SFX Volume",
            "${(current.audio.volumeSfx * 100).toInt()}%") { label ->
            slider(100, (current.audio.volumeSfx * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(audio = current.audio.copy(volumeSfx = progress / 100f))
                label.text = "${(current.audio.volumeSfx * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Performance Section ──
        content.addView(sectionTitle("Performance", "Optimization settings"))

        content.addView(switchPanel("Threaded Rendering", "Use multiple threads for rendering",
            current.performance.threadedRendering) { checked ->
            current = current.copy(performance = current.performance.copy(threadedRendering = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Background Loading", "Load assets in background",
            current.performance.backgroundLoading) { checked ->
            current = current.copy(performance = current.performance.copy(backgroundLoading = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Reduce Shadows", "Lower shadow quality for better performance",
            current.performance.reduceShadows) { checked ->
            current = current.copy(performance = current.performance.copy(reduceShadows = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Reduce Particles", "Lower particle effects for better performance",
            current.performance.reduceParticles) { checked ->
            current = current.copy(performance = current.performance.copy(reduceParticles = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Frame Skip",
            "${current.performance.frameSkip}") { label ->
            slider(5, current.performance.frameSkip.coerceIn(0, 5)) { progress ->
                current = current.copy(performance = current.performance.copy(frameSkip = progress))
                label.text = "$progress"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Texture Cache Size",
            "${current.performance.textureCacheSize} MB") { label ->
            slider(256, (current.performance.textureCacheSize / 4).coerceIn(8, 64)) { progress ->
                val size = progress * 4
                current = current.copy(performance = current.performance.copy(textureCacheSize = size))
                label.text = "$size MB"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Fonts Section ──
        content.addView(sectionTitle("Fonts", "Text rendering"))

        content.addView(switchPanel("Use Game Fonts", "Prefer fonts bundled with the game",
            current.fonts.useGameFonts) { checked ->
            current = current.copy(fonts = current.fonts.copy(useGameFonts = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Bold Text", "Make all text bold",
            current.fonts.boldText) { checked ->
            current = current.copy(fonts = current.fonts.copy(boldText = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Italic Text", "Make all text italic",
            current.fonts.italicText) { checked ->
            current = current.copy(fonts = current.fonts.copy(italicText = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Font Scale",
            "${(current.fonts.fontScale * 100).toInt()}%") { label ->
            slider(200, ((current.fonts.fontScale - 0.5f) * 200).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(fonts = current.fonts.copy(fontScale = 0.5f + progress / 200f))
                label.text = "${(current.fonts.fontScale * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Line Spacing",
            "${(current.fonts.lineSpacing * 100).toInt()}%") { label ->
            slider(200, ((current.fonts.lineSpacing - 0.5f) * 200).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(fonts = current.fonts.copy(lineSpacing = 0.5f + progress / 200f))
                label.text = "${(current.fonts.lineSpacing * 100).toInt()}%"
                onConfigChanged(current)
            }
        })

        // ── Patches & Mods Section ──
        content.addView(sectionTitle("Patches & Mods", "Translations, +18 patches, mods, and extra content"))
        val patchManager = PatchManager(context, com.runestone.app.workspace.WorkspaceManager(context))
        var refreshPatchList: (() -> Unit)? = null

        fun installZipPanel(title: String, buttonText: String, isTranslation: Boolean): View = settingsPanel {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(context).apply {
                text = title
                setTextColor(TEXT); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            val installBtn = TextView(context).apply {
                text = buttonText
                setTextColor(ACCENT); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; setPadding(dp(12), dp(6), dp(12), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT)))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.argb(60, Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT)))
                }
                makeLiquid(this)
	                setOnClickListener {
	                    onInstallPatch { zipPath ->
	                        if (zipPath.isNotEmpty() && storageName.isNotEmpty()) {
	                            val zipFile = File(zipPath)
	                            val patchName = zipFile.nameWithoutExtension
                                val preflight = patchManager.preflightPatch(storageName, zipFile)
                                (context as? android.app.Activity)?.runOnUiThread {
                                    if (!preflight.success) {
                                        android.app.AlertDialog.Builder(context)
                                            .setTitle("Patch blocked")
                                            .setMessage(preflight.summary())
                                            .setPositiveButton("OK", null)
                                            .show()
                                        return@runOnUiThread
                                    }

                                    android.app.AlertDialog.Builder(context)
                                        .setTitle("Install $patchName?")
                                        .setMessage(preflight.summary())
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton("Install") { _, _ ->
                                            val result = patchManager.installPatch(
                                                storageName = storageName,
                                                zipFile = zipFile,
                                                patchName = patchName,
                                                description = if (isTranslation) "User-installed translation overlay" else "User-installed patch or mod",
                                                isTranslation = isTranslation,
                                            )
                                            android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                            if (result.success) {
                                                val fresh = com.runestone.app.data.GameConfigService(
                                                    context, com.runestone.app.workspace.WorkspaceManager(context)
                                                ).loadPerGame(storageName)
                                                current = current.copy(patches = fresh.patches)
                                                onConfigChanged(current)
                                                refreshPatchList?.invoke()
                                            }
                                        }
                                        .show()
                                }
	                        }
	                    }
	                }
            }
            row.addView(installBtn)
            addView(row)
        }

        content.addView(installZipPanel("Install Translation ZIP", "TRANSLATION", isTranslation = true))
        content.addView(spacer(8))
        content.addView(installZipPanel("Install Mod/Patch ZIP", "MOD/PATCH", isTranslation = false))
        content.addView(spacer(10))

        // Patch list — rebuild on each update
        fun buildPatchList() {
            // Remove old list views
            val existingTags = mutableListOf<View>()
            for (i in 0 until content.childCount) {
                val v = content.getChildAt(i)
                if (v.tag == "patch_list_item" || v.tag == "patch_list_header" || v.tag == "patch_all_btn") {
                    existingTags.add(v)
                }
            }
            existingTags.forEach { content.removeView(it) }

            val patches = current.patches.installedPatches
                .sortedByDescending { it.installedAtMillis }
            if (patches.isEmpty()) {
                val emptyLabel = TextView(context).apply {
                    tag = "patch_list_header"
                    text = "No patches installed"
                    setTextColor(MUTED); textSize = 12f
                    setPadding(dp(4), dp(4), 0, dp(8))
                }
                content.addView(emptyLabel, content.childCount - 1)
                return
            }

            val activePatches = patches.filter { it.isActive }
            val translations = patches.filter { it.isTranslation }
            val mods = patches.filter { !it.isTranslation }

            fun addPatchListHeader(label: String) {
                content.addView(TextView(context).apply {
                    tag = "patch_list_header"
                    text = label
                    setTextColor(TEXT); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(4), dp(8), 0, dp(4))
                }, content.childCount - 1)
            }

            fun addPatchCard(p: InstalledPatch) {
                val card = settingsPanel {
                    tag = "patch_list_item"

                    val titleRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val infoCol = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    }

                    infoCol.addView(TextView(context).apply {
                        text = p.name
                        setTextColor(TEXT); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                    })

                    infoCol.addView(TextView(context).apply {
                        val desc = buildString {
                            append(if (p.isTranslation) "Translation / " else "Mod/Patch / ")
                            if (p.isActive) append("Active") else append("Reverted")
                            if (p.overwrittenCount > 0) append(" · ${p.overwrittenCount} overwritten")
                            if (p.addedCount > 0) append(" · ${p.addedCount} added")
                        }
                        text = desc
                        setTextColor(MUTED); textSize = 11f
                        setPadding(0, dp(2), 0, 0)
                    })

                    val dateText = TextView(context).apply {
                        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        text = sdf.format(java.util.Date(p.installedAtMillis))
                        setTextColor(MUTED); textSize = 10f
                        setPadding(0, dp(1), 0, 0)
                    }
                    infoCol.addView(dateText)

                    titleRow.addView(infoCol)

                    if (p.isActive) {
                        // Revert button — only show if this is the most recent active patch
                        val isNewest = (activePatches.maxByOrNull { it.installedAtMillis }?.patchId == p.patchId)
                        if (isNewest && storageName.isNotEmpty()) {
                            val revertBtn = TextView(context).apply {
                                text = "REVERT"
                                setTextColor(Color.rgb(200, 120, 120)); textSize = 10f
                                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                                setPadding(dp(8), dp(4), dp(8), dp(4))
                                background = GradientDrawable().apply {
                                    setColor(Color.argb(40, 200, 80, 80))
                                    cornerRadius = dp(6).toFloat()
                                    setStroke(dp(1), Color.argb(60, 200, 80, 80))
                                }
                                makeLiquid(this)
                                setOnClickListener {
                                    val result = patchManager.revertPatch(storageName, p.patchId)
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                        if (result.success) {
                                            val fresh = com.runestone.app.data.GameConfigService(
                                                context, com.runestone.app.workspace.WorkspaceManager(context)
                                            ).loadPerGame(storageName)
                                            current = current.copy(patches = fresh.patches)
                                            onConfigChanged(current)
                                            buildPatchList()
                                        }
                                    }
                                }
                            }
                            titleRow.addView(revertBtn)
                        }
                    } else {
                        val badge = TextView(context).apply {
                            text = "REVERTED"
                            setTextColor(MUTED); textSize = 10f; typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.CENTER
                            setPadding(dp(8), dp(4), dp(8), dp(4))
                            background = GradientDrawable().apply {
                                setColor(Color.argb(30, 140, 130, 112))
                                cornerRadius = dp(6).toFloat()
                            }
                        }
                        titleRow.addView(badge)
                    }

                    addView(titleRow)
                }
                content.addView(card, content.childCount - 1)
            }

            if (translations.isNotEmpty()) {
                addPatchListHeader("Translations (${translations.size})")
                translations.forEach { addPatchCard(it) }
            }

            if (mods.isNotEmpty()) {
                addPatchListHeader("Patches & Mods (${mods.size})")
                mods.forEach { addPatchCard(it) }
            }

            // Revert All button (if any patches active)
            if (activePatches.isNotEmpty() && storageName.isNotEmpty()) {
                val revertAllBtn = TextView(context).apply {
                    tag = "patch_all_btn"
                    text = "REVERT ALL (${activePatches.size} active)"
                    setTextColor(Color.rgb(200, 120, 120)); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER; setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = GradientDrawable().apply {
                        setColor(Color.argb(30, 200, 80, 80))
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), Color.argb(40, 200, 80, 80))
                    }
                    makeLiquid(this)
                    setOnClickListener {
                        val result = patchManager.revertAll(storageName)
                        (context as? android.app.Activity)?.runOnUiThread {
                            android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                            if (result.success) {
                                val fresh = com.runestone.app.data.GameConfigService(
                                    context, com.runestone.app.workspace.WorkspaceManager(context)
                                ).loadPerGame(storageName)
                                current = current.copy(patches = fresh.patches)
                                onConfigChanged(current)
                                buildPatchList()
                            }
                        }
                    }
                }
                content.addView(revertAllBtn, content.childCount - 1)
            }
        }
        buildPatchList()
        refreshPatchList = { buildPatchList() }
        content.addView(spacer(h = 14))

        content.animate().alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.1f)).start()
        return root
    }

    private fun makeTopBar(gameTitle: String, onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.argb(180, 3, 3, 4))

            addView(
                TextView(context).apply {
                    text = "Back"
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
                    text = gameTitle
                    setTextColor(TEXT)
                    textSize = 16f
                    letterSpacing = 0.2f
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("serif", Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(View(context), LinearLayout.LayoutParams(dp(80), 1))
        }

    private fun sectionTitle(title: String, detail: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(TEXT)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                },
            )
            addView(
                TextView(context).apply {
                    text = detail
                    setTextColor(MUTED)
                    textSize = 12f
                    setPadding(0, dp(3), 0, 0)
                },
            )
        }

    private fun settingsPanel(build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = glassBg(16, alpha = 200)
            makeLiquid(this)
            build()
        }

    private fun sliderPanel(title: String, value: String, sliderFactory: (TextView) -> GlassSlider): LinearLayout =
        settingsPanel {
            val label = TextView(context).apply {
                text = value
                setTextColor(ACCENT)
                textSize = 13f
                gravity = Gravity.END
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(context).apply {
                        text = title
                        setTextColor(TEXT)
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(label)
            }
            addView(row)
            addView(sliderFactory(label))
        }

    private fun switchPanel(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout =
        settingsPanel {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val copy = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(context).apply {
                        text = title
                        setTextColor(TEXT)
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                    },
                )
                addView(
                    TextView(context).apply {
                        text = detail
                        setTextColor(MUTED)
                        textSize = 11f
                        setPadding(0, dp(3), dp(10), 0)
                    },
                )
            }
            row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(
                Switch(context).apply {
                    isChecked = checked
                    setOnCheckedChangeListener { _, value -> onChange(value) }
                },
            )
            addView(row)
        }

    private fun metadataEditRow(label: String, value: String, onChange: (String) -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(TextView(context).apply {
                text = label; setTextColor(MUTED); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            val valView = TextView(context).apply {
                text = if (value.isEmpty()) "(tap to edit)" else value
                setTextColor(if (value.isEmpty()) Color.argb(120, 140, 130, 112) else TEXT)
                textSize = 13f; setPadding(dp(6), dp(3), dp(6), dp(3))
                maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                background = glassBg(8, alpha = 80)
                makeLiquid(this)
            }
            valView.setOnClickListener {
                showMetadataEditOverlay(label, value) { newValue ->
                    onChange(newValue)
                    valView.text = if (newValue.isEmpty()) "(tap to edit)" else newValue
                    valView.setTextColor(if (newValue.isEmpty()) Color.argb(120, 140, 130, 112) else TEXT)
                }
            }
            addView(valView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun showMetadataEditOverlay(label: String, currentValue: String, onSave: (String) -> Unit) {
        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val rootView = (context as? android.app.Activity)?.window?.decorView
            ?.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Backdrop
        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            alpha = 0f
            animate().alpha(1f).setDuration(250).start()
        }

        // Glass panel
        val panelW = (screenW * 0.82f).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.argb(230, 12, 11, 16))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.argb(70, 160, 140, 110))
            }
            translationY = 100f; alpha = 0f
            animate().translationY(0f).alpha(1f).setDuration(350)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
        }

        // Title
        panel.addView(TextView(context).apply {
            text = "Edit $label"
            setTextColor(ACCENT); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(12))
        })

        // EditText with glass styling
        val editText = android.widget.EditText(context).apply {
            setText(currentValue); setTextColor(TEXT); setHint("Enter $label...")
            setHintTextColor(Color.argb(80, 200, 200, 200))
            setBackgroundColor(Color.argb(30, 255, 255, 255))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            textSize = 15f
            setSelection(text?.length ?: 0)
        }
        // Rounded corner background for EditText
        editText.background = GradientDrawable().apply {
            setColor(Color.argb(30, 255, 255, 255))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.argb(40, 200, 180, 150))
        }
        panel.addView(editText)
        panel.addView(spacer(h = 18))

        // Button row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        // Cancel
        btnRow.addView(TextView(context).apply {
            text = "Cancel"; setTextColor(MUTED); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 200, 180, 150))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(40, 200, 180, 150))
            }
            setOnClickListener {
                overlay.animate().alpha(0f).translationY(60f).setDuration(180)
                    .withEndAction { rootView.removeView(overlay) }.start()
            }
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, dp(6), 0) })
        // Save
        btnRow.addView(TextView(context).apply {
            text = "Save"; setTextColor(Theme.active.accentBright); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 200, 170, 130))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(80, 200, 170, 130))
            }
            setOnClickListener {
                val newVal = editText.text.toString()
                onSave(newVal)
                overlay.animate().alpha(0f).translationY(60f).setDuration(180)
                    .withEndAction { rootView.removeView(overlay) }.start()
            }
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        panel.addView(btnRow)

        // Mount
        overlay.addView(panel, FrameLayout.LayoutParams(panelW, WRAP, Gravity.CENTER))
        rootView.addView(overlay)

        // Backdrop dismiss
        overlay.setOnClickListener { v ->
            v.animate().alpha(0f).translationY(60f).setDuration(180)
                .withEndAction { rootView.removeView(v) }.start()
        }
        // Allow text interaction without dismissing
        panel.setOnClickListener { /* consume tap */ }
    }

    private fun coverPicker(
        currentPath: String?,
        onPick: (setPath: (String) -> Unit) -> Unit,
        onClear: (clearPath: (String) -> Unit) -> Unit,
    ): LinearLayout = settingsPanel {
        var previewImage: ImageView? = null
        lateinit var clearBtn: TextView

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }

        // Thumbnail preview
        val thumb = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(96))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.argb(40, 255, 255, 255))
            if (!currentPath.isNullOrEmpty()) {
                val bmp = loadCoverBitmap(currentPath)
                if (bmp != null) setImageBitmap(bmp)
            }
        }
        previewImage = thumb
        row.addView(thumb)
        row.addView(spacer(h = 0, w = 12))

        // Buttons column
        val btnCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pickBtn = TextView(context).apply {
            text = "CHOOSE IMAGE"
            setTextColor(Theme.active.accent); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Theme.active.accentBg)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(80,
                    Color.red(Theme.active.accent),
                    Color.green(Theme.active.accent),
                    Color.blue(Theme.active.accent)))
            }
            makeLiquid(this)
            setOnClickListener {
                onPick { pickedPath ->
                    runCatching {
                        val bmp = loadCoverBitmap(pickedPath)
                        if (bmp != null) previewImage?.setImageBitmap(bmp)
                    }
                    clearBtn.visibility = View.VISIBLE
                }
            }
        }
        btnCol.addView(pickBtn)
        btnCol.addView(spacer(h = 6))

        clearBtn = TextView(context).apply {
            text = "REMOVE"
            setTextColor(Color.rgb(200, 120, 120)); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(50, 180, 80, 80))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(80, 180, 80, 80))
            }
            visibility = if (currentPath.isNullOrEmpty()) View.GONE else View.VISIBLE
            makeLiquid(this)
            setOnClickListener {
                previewImage?.setImageDrawable(null)
                previewImage?.setBackgroundColor(Color.argb(40, 255, 255, 255))
                visibility = View.GONE
                onClear { cleared ->
                    // no-op, handled by caller
                }
            }
        }
        btnCol.addView(clearBtn)

        row.addView(btnCol)
        addView(row)
    }

    private fun loadCoverBitmap(path: String): Bitmap? {
        return runCatching {
            val file = File(path)
            if (!file.exists()) return@runCatching null
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bmp = BitmapFactory.decodeFile(path, opts)
            // Scale to fit the thumbnail area maintaining aspect ratio
            if (bmp != null) {
                val maxW = dp(64).toFloat(); val maxH = dp(96).toFloat()
                val scale = minOf(maxW / bmp.width, maxH / bmp.height)
                if (scale < 1f) {
                    val w = (bmp.width * scale).toInt()
                    val h = (bmp.height * scale).toInt()
                    Bitmap.createScaledBitmap(bmp, w, h, true)
                } else bmp
            } else null
        }.getOrNull()
    }

    private fun slider(max: Int, progress: Int, onChange: (Int) -> Unit): GlassSlider =
        GlassSlider(context, max, progress, onChange)

    private fun spacer(h: Int = 0, w: Int = 0): View {
        val lp = LinearLayout.LayoutParams(
            if (w > 0) dp(w) else ViewGroup.LayoutParams.MATCH_PARENT,
            if (h > 0) dp(h) else ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return View(context).apply { layoutParams = lp }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun makeLiquid(view: View) { if (Theme.isReducedMotion(context)) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f
                    val cy = v.height / 2f
                    val dx = (event.x - cx) * 0.06f
                    val dy = (event.y - cy) * 0.06f
                    v.translationX = dx
                    v.translationY = dy
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .translationX(0f).translationY(0f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()
                }
                else -> {}
            }
            false
        }
    }

    private fun glassBg(radius: Int, alpha: Int = 200): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha, 18, 18, 24))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(40,
                Color.red(Theme.active.accent),
                Color.green(Theme.active.accent),
                Color.blue(Theme.active.accent)))
        }

    private inner class GlassSlider(
        context: Context,
        private val maxValue: Int,
        initialValue: Int,
        private val onChange: (Int) -> Unit,
    ) : View(context) {

        private var value = initialValue.coerceIn(0, maxValue)
        private val trackPaint = android.graphics.Paint().apply {
            color = Color.argb(60, 255, 255, 255)
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        private val progressPaint = android.graphics.Paint().apply {
            color = ACCENT
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        private val thumbPaint = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }

        init {
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val newValue = ((event.x / width) * maxValue).toInt().coerceIn(0, maxValue)
                        if (newValue != value) {
                            value = newValue
                            onChange(value)
                            invalidate()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val trackHeight = dp(6)
            val thumbRadius = dp(10)
            val centerY = height / 2f
            val trackLeft = thumbRadius.toFloat()
            val trackRight = width - thumbRadius.toFloat()

            // Track
            canvas.drawRoundRect(
                trackLeft, centerY - trackHeight / 2f,
                trackRight, centerY + trackHeight / 2f,
                trackHeight / 2f, trackHeight / 2f,
                trackPaint
            )

            // Progress
            val progressWidth = trackLeft + (trackRight - trackLeft) * (value.toFloat() / maxValue)
            canvas.drawRoundRect(
                trackLeft, centerY - trackHeight / 2f,
                progressWidth, centerY + trackHeight / 2f,
                trackHeight / 2f, trackHeight / 2f,
                progressPaint
            )

            // Thumb
            canvas.drawCircle(progressWidth, centerY, thumbRadius.toFloat(), thumbPaint)
        }
    }

    companion object {
        private val TEXT = Color.rgb(232, 229, 220)
        private val MUTED = Color.rgb(140, 130, 112)
        private val ACCENT: Int get() = Theme.active.accent
        private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
