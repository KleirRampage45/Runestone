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
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.MainActivity
import com.runestone.app.provider.AvailableGame
import com.runestone.app.provider.DownloadManager
import com.runestone.app.provider.DownloadOption
import com.runestone.app.provider.HosterResolver

class GameDetailOverlay(
    context: Context,
    initialGame: AvailableGame,
    initialProgress: DownloadManager.DownloadProgress?,
    initialInstallProgress: MainActivity.InstallProgress?,
    initialInstalledGameTitles: Set<String>,
    private val onDownload: (AvailableGame) -> Unit,
    private val onPauseDownload: (String) -> Unit,
    private val onClose: (AvailableGame) -> Unit,
) {

    enum class State { INFO, SOURCES, PROGRESS }

    private val context: Context = context.applicationContext
    private val root: FrameLayout
    private val panel: LinearLayout
    private val contentHost: LinearLayout
    private val actionBar: LinearLayout

    private var game: AvailableGame = initialGame
    private var progress: DownloadManager.DownloadProgress? = initialProgress
    private var installProgress: MainActivity.InstallProgress? = initialInstallProgress
    private var installedGameTitles: Set<String> = initialInstalledGameTitles
    private var currentState: State = State.INFO

    init {
        root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.argb(220, 4, 4, 6))
            alpha = 0f
            animate().alpha(1f).setDuration(220).start()
        }
        root.setOnClickListener { dismiss() }

        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val panelW = (screenW * 0.94f).toInt()
        val panelMaxH = (screenH * 0.86f).toInt()

        panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                panelW, panelMaxH, Gravity.CENTER,
            ).apply { setMargins(0, dp(20), 0, dp(20)) }
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 14, 13, 18))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(80, 207, 174, 126))
            }
            translationY = 40f
            alpha = 0f
            animate().translationY(0f).alpha(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
            clipChildren = true
        }
        panel.setOnClickListener { /* swallow */ }
        root.addView(panel)

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        contentHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        scroll.addView(contentHost)
        panel.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))

        actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.argb(140, 8, 7, 12))
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    dp(18).toFloat(), dp(18).toFloat(),
                    dp(18).toFloat(), dp(18).toFloat(),
                )
            }
        }
        panel.addView(actionBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        render()
    }

    fun view(): View = root

    fun update(
        game: AvailableGame,
        progress: DownloadManager.DownloadProgress?,
        installProgress: MainActivity.InstallProgress?,
        installedGameTitles: Set<String>,
    ) {
        val stateChanged = this.game.id != game.id
        val wasComplete = this.progress?.state == DownloadManager.DownloadState.COMPLETED
        this.game = game
        this.progress = progress
        this.installProgress = installProgress
        this.installedGameTitles = installedGameTitles

        if (stateChanged) {
            currentState = State.INFO
        } else if (currentState == State.PROGRESS) {
            val isNowDone = installProgress == null &&
                progress?.state == DownloadManager.DownloadState.COMPLETED
            if (isNowDone && !wasComplete) {
                currentState = State.INFO
            }
        }
        render()
    }

    private fun dismiss() {
        root.animate().alpha(0f).setDuration(180).withEndAction {
            (root.parent as? ViewGroup)?.removeView(root)
            onClose(game)
        }.start()
    }

    private fun render() {
        contentHost.removeAllViews()
        actionBar.removeAllViews()
        when (currentState) {
            State.INFO -> renderInfo()
            State.SOURCES -> renderSources()
            State.PROGRESS -> renderProgress()
        }
    }

    // ──────────────────── INFO state ────────────────────

    private fun renderInfo() {
        // Cover art block
        val coverFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180),
            )
            background = GradientDrawable().apply {
                setColor(Color.argb(180, 35, 28, 22))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(60, 207, 174, 126))
            }
        }
        if (game.coverUrl != null) {
            val cover = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = game.title
            }
            coverFrame.addView(cover, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            game.coverUrl?.let { com.runestone.app.util.ImageLoader.load(it, cover, maxWidthPx = 720) }
        } else {
            val monogram = engineLabel(game.engine).take(3)
            coverFrame.addView(TextView(context).apply {
                text = monogram
                setTextColor(Color.rgb(207, 174, 126))
                textSize = 56f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.1f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }

        contentHost.addView(coverFrame)
        contentHost.addView(spacer(dp(14)))

        // Title
        contentHost.addView(TextView(context).apply {
            text = game.title
            setTextColor(TEXT); textSize = 22f
            typeface = Typeface.create("serif", Typeface.BOLD)
            letterSpacing = 0.02f
        })
        contentHost.addView(spacer(dp(10)))

        // Metadata grid (2 columns)
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addMetaRow(left, "Engine", engineLabel(game.engine))
        addMetaRow(left, "Size", game.fileSize?.let(::formatBytes) ?: "Unknown")
        addMetaRow(left, "Language", game.language ?: "Unknown")
        addMetaRow(left, "License", game.license ?: "Unknown")
        addMetaRow(right, "Source", game.sourceName)
        addMetaRow(right, "ID", game.id)
        addMetaRow(right, "Options", "${game.downloadOptions.size} available")
        addMetaRow(right, "Status", if (isInstalled(game)) "INSTALLED" else "Available")
        grid.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, dp(8), 0)
        })
        grid.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(8), 0, 0, 0)
        })
        contentHost.addView(grid)
        contentHost.addView(spacer(dp(14)))

        // Description
        if (!game.description.isNullOrBlank()) {
            contentHost.addView(TextView(context).apply {
                text = game.description
                setTextColor(TEXT); textSize = 13f
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.2f)
            })
            contentHost.addView(spacer(dp(14)))
        }

        // Screenshots placeholder (horizontal carousel of gradient placeholders)
        val screenshotsLabel = TextView(context).apply {
            text = "SCREENSHOTS"
            setTextColor(MUTED); textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }
        contentHost.addView(screenshotsLabel)
        contentHost.addView(spacer(dp(8)))

        val ssScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = HorizontalScrollView.OVER_SCROLL_NEVER
        }
        val ssRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(4) { i ->
            val ss = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(140), dp(80)).apply {
                    setMargins(if (i == 0) 0 else dp(8), 0, 0, 0)
                }
                background = GradientDrawable().apply {
                    setColor(Color.argb(30 + i * 15, 207, 174, 126))
                    cornerRadius = dp(8).toFloat()
                }
            }
            ssRow.addView(ss)
        }
        ssScroll.addView(ssRow)
        contentHost.addView(ssScroll)

        // Action bar
        renderInfoActionBar()
    }

    private fun renderInfoActionBar() {
        val installed = isInstalled(game)
        val state = progress?.state

        val (label, enabled, click) = when {
            installProgress != null -> Triple("INSTALLING…", false, null as (() -> Unit)?)
            state == DownloadManager.DownloadState.DOWNLOADING -> Triple("DOWNLOADING…", false, null as (() -> Unit)?)
            state == DownloadManager.DownloadState.PAUSED -> Triple("RESUME", true, ({ onDownload(game) }))
            state == DownloadManager.DownloadState.FAILED -> Triple("RETRY", true, ({ onDownload(game) }))
            installed -> Triple("INSTALLED", false, null as (() -> Unit)?)
            game.downloadOptions.isNotEmpty() -> Triple(
                if (game.downloadOptions.size > 1) "CHOOSE SOURCE (${game.downloadOptions.size})" else "GET",
                true,
                if (game.downloadOptions.size > 1) ({ currentState = State.SOURCES; render() }) else ({ onDownload(game) }),
            )
            game.pageUrl != null -> Triple("OPEN PAGE", true, ({ openPage(game.pageUrl!!) }))
            else -> Triple("NO SOURCES", false, null as (() -> Unit)?)
        }

        val primaryBg: Int
        val primaryFg: Int
        if (!enabled) {
            primaryBg = Color.argb(60, 80, 70, 55)
            primaryFg = Color.rgb(160, 150, 130)
        } else when {
            installed -> { primaryBg = Color.argb(80, 60, 130, 60); primaryFg = Color.rgb(140, 220, 140) }
            state == DownloadManager.DownloadState.PAUSED || state == DownloadManager.DownloadState.FAILED -> {
                primaryBg = Color.argb(200, 207, 174, 126); primaryFg = Color.rgb(20, 18, 14)
            }
            game.pageUrl != null && game.downloadOptions.isEmpty() -> {
                primaryBg = Color.argb(120, 60, 90, 140); primaryFg = Color.rgb(190, 210, 230)
            }
            else -> { primaryBg = Color.argb(200, 207, 174, 126); primaryFg = Color.rgb(20, 18, 14) }
        }

        val primary = TextView(context).apply {
            text = label
            setTextColor(primaryFg); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = GradientDrawable().apply {
                setColor(primaryBg)
                cornerRadius = dp(12).toFloat()
                val r = Color.red(primaryFg); val g = Color.green(primaryFg); val b = Color.blue(primaryFg)
                setStroke(dp(1), Color.argb(80, r, g, b))
            }
        }
        if (click != null) {
            primary.isClickable = true
            primary.isFocusable = true
            primary.setOnClickListener { animTap(it); click() }
            makeLiquid(primary)
        }
        actionBar.addView(primary, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val cancel = TextView(context).apply {
            text = "CLOSE"
            setTextColor(MUTED); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 207, 174, 126))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.argb(45, 207, 174, 126))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { animTap(it); dismiss() }
            makeLiquid(this)
        }
        actionBar.addView(cancel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(dp(10), 0, 0, 0) })
    }

    // ──────────────────── SOURCES state ────────────────────

    private fun renderSources() {
        contentHost.addView(TextView(context).apply {
            text = "CHOOSE SOURCE"
            setTextColor(ACCENT); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        })
        contentHost.addView(spacer(dp(2)))
        contentHost.addView(TextView(context).apply {
            text = game.title
            setTextColor(TEXT); textSize = 16f
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        contentHost.addView(spacer(dp(10)))

        if (game.downloadOptions.isEmpty()) {
            contentHost.addView(TextView(context).apply {
                text = "No direct sources available."
                setTextColor(MUTED); textSize = 12f
            })
        }

        game.downloadOptions.forEach { option ->
            val hostStatus = HosterResolver.isSupported(option.url)
            val isSupported = hostStatus.supported

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                alpha = if (isSupported) 1f else 0.5f
                background = GradientDrawable().apply {
                    setColor(Color.argb(if (isSupported) 50 else 22, 207, 174, 126))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.argb(if (isSupported) 70 else 25, 207, 174, 126))
                }
                if (isSupported) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        animTap(it)
                        val singleOptionGame = game.copy(downloadOptions = listOf(option))
                        onDownload(singleOptionGame)
                        currentState = State.PROGRESS
                        render()
                    }
                    makeLiquid(this)
                }
            }

            val infoCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            infoCol.addView(TextView(context).apply {
                text = option.name
                setTextColor(TEXT); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            })
            if (!isSupported) {
                infoCol.addView(TextView(context).apply {
                    text = "Not available on Android"
                    setTextColor(Color.rgb(200, 120, 100)); textSize = 9f
                    setPadding(0, dp(1), 0, 0)
                })
            }
            row.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(TextView(context).apply {
                text = option.host
                setTextColor(if (isSupported) ACCENT else Color.rgb(140, 100, 90))
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = GradientDrawable().apply {
                    setColor(Color.argb(if (isSupported) 40 else 15, 200, 170, 130))
                    cornerRadius = dp(4).toFloat()
                    setStroke(dp(1), Color.argb(if (isSupported) 50 else 15, 200, 170, 130))
                }
            })

            if (option.fileSize != null) {
                row.addView(spacer(dp(6)))
                row.addView(TextView(context).apply {
                    text = formatBytes(option.fileSize)
                    setTextColor(MUTED_DIM); textSize = 10f
                })
            }

            contentHost.addView(row)
            contentHost.addView(spacer(dp(4)))
        }

        // Cancel/back
        val back = TextView(context).apply {
            text = "BACK"
            setTextColor(MUTED); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(10), dp(24), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 207, 174, 126))
                cornerRadius = dp(10).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animTap(it)
                currentState = State.INFO
                render()
            }
            makeLiquid(this)
        }
        contentHost.addView(spacer(dp(8)))
        contentHost.addView(back, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    // ──────────────────── PROGRESS state ────────────────────

    private fun renderProgress() {
        contentHost.addView(TextView(context).apply {
            text = "INSTALLING"
            setTextColor(ACCENT); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        })
        contentHost.addView(spacer(dp(4)))
        contentHost.addView(TextView(context).apply {
            text = game.title
            setTextColor(TEXT); textSize = 20f
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        contentHost.addView(spacer(dp(20)))

        val phase = computePhase()
        contentHost.addView(TextView(context).apply {
            text = phase.headline
            setTextColor(TEXT); textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        contentHost.addView(spacer(dp(8)))
        contentHost.addView(TextView(context).apply {
            text = phase.detail
            setTextColor(MUTED); textSize = 12f
            gravity = Gravity.CENTER
        })
        contentHost.addView(spacer(dp(18)))

        // Progress bar
        val percent = phase.percent.coerceIn(0, 100)
        val trackW = (context.resources.displayMetrics.widthPixels * 0.94f - dp(36)).toInt()
        val barTrack = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 255, 255, 255))
                cornerRadius = dp(4).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(trackW, dp(10))
        }
        val barFill = View(context).apply {
            background = GradientDrawable().apply {
                setColor(phase.barColor)
                cornerRadius = dp(4).toFloat()
            }
        }
        barTrack.addView(barFill, FrameLayout.LayoutParams(
            (trackW * percent / 100f).toInt().coerceAtLeast(dp(2)), dp(10),
        ))
        contentHost.addView(barTrack, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })

        contentHost.addView(spacer(dp(8)))
        contentHost.addView(TextView(context).apply {
            text = "$percent%"
            setTextColor(MUTED); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        contentHost.addView(spacer(dp(20)))

        // Action bar
        val cancel = TextView(context).apply {
            text = "MINIMIZE"
            setTextColor(MUTED); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 207, 174, 126))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.argb(45, 207, 174, 126))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { animTap(it); dismiss() }
            makeLiquid(this)
        }
        actionBar.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val secondary = when {
            progress?.state == DownloadManager.DownloadState.DOWNLOADING -> TextView(context).apply {
                text = "PAUSE"
                setTextColor(Color.rgb(220, 200, 160)); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.argb(80, 200, 170, 130))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.argb(120, 220, 200, 160))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { animTap(it); onPauseDownload(game.id) }
                makeLiquid(this)
            }
            progress?.state == DownloadManager.DownloadState.PAUSED -> TextView(context).apply {
                text = "RESUME"
                setTextColor(Color.rgb(200, 200, 160)); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.05f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.argb(80, 200, 170, 80))
                    cornerRadius = dp(12).toFloat()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { animTap(it); onDownload(game) }
                makeLiquid(this)
            }
            else -> null
        }
        if (secondary != null) {
            actionBar.addView(secondary, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f,
            ).apply { setMargins(dp(10), 0, 0, 0) })
        }
    }

    private data class Phase(
        val headline: String,
        val detail: String,
        val percent: Int,
        val barColor: Int,
    )

    private fun computePhase(): Phase {
        val p = progress
        val ip = installProgress
        val state = p?.state
        return when {
            ip != null -> {
                val pct = if (ip.totalFiles > 0)
                    (ip.filesExtracted * 100 / ip.totalFiles).coerceIn(0, 100)
                else 0
                Phase(
                    headline = "Installing…",
                    detail = "${ip.filesExtracted} / ${ip.totalFiles} files",
                    percent = pct,
                    barColor = Color.argb(200, 126, 190, 207),
                )
            }
            state == DownloadManager.DownloadState.DOWNLOADING && p != null -> {
                val pct = if (p.totalBytes > 0)
                    (p.bytesDownloaded * 100 / p.totalBytes).toInt()
                else 0
                Phase(
                    headline = "Downloading…",
                    detail = "${formatBytes(p.bytesDownloaded)} / ${formatBytes(p.totalBytes)}  ·  ${formatBytes(p.speed.toLong())}/s",
                    percent = pct,
                    barColor = Color.argb(200, 207, 174, 126),
                )
            }
            state == DownloadManager.DownloadState.PAUSED && p != null -> {
                val pct = if (p.totalBytes > 0) (p.bytesDownloaded * 100 / p.totalBytes).toInt() else 0
                Phase(
                    headline = "Paused",
                    detail = "Tap RESUME to continue",
                    percent = pct,
                    barColor = Color.argb(140, 200, 170, 80),
                )
            }
            state == DownloadManager.DownloadState.FAILED -> Phase(
                headline = "Download failed",
                detail = p?.error ?: "Unknown error",
                percent = 0,
                barColor = Color.argb(180, 200, 100, 80),
            )
            state == DownloadManager.DownloadState.COMPLETED -> Phase(
                headline = "Finalizing…",
                detail = "Starting install",
                percent = 100,
                barColor = Color.argb(200, 126, 190, 207),
            )
            else -> Phase("Preparing…", "", 0, Color.argb(120, 207, 174, 126))
        }
    }

    private fun engineLabel(engine: String?): String = when (engine?.lowercase()) {
        "mv", "rpgmaker_mv" -> "MV"
        "mz", "rpgmaker_mz" -> "MZ"
        "vx", "rgss_vx" -> "VX"
        "vxace", "rgss_vx_ace", "rgss3" -> "VX ACE"
        "xp", "rgss", "rgss_xp" -> "XP"
        "2000", "2003", "easyrpg" -> "2K"
        "renpy" -> "REN'PY"
        "wolf", "wolfrpg", "wolf_rpg", "wolf_rpg_editor" -> "WOLF"
        "kirikiri", "kirikiri2", "kirikiri_z", "kag", "xp3" -> "KAG"
        "unity", "unity3d" -> "UNITY"
        "unreal", "ue4", "ue5", "unreal_engine" -> "UNREAL"
        "gamemaker", "game_maker", "gms", "gms2" -> "GMS"
        "ags", "adventure_game_studio" -> "AGS"
        "nscripter", "onscripter" -> "ONS"
        "rpgmaker" -> "RPGM"
        "html", "html5" -> "HTML"
        null, "" -> "OTHER"
        else -> engine.orEmpty().take(8).uppercase()
    }

    private fun formatBytes(bytes: Long): String {
        val gb = 1024.0 * 1024.0 * 1024.0; val mb = 1024.0 * 1024.0
        return if (bytes >= gb) String.format("%.2f GB", bytes / gb) else String.format("%.1f MB", bytes / mb)
    }

    // ──────────────────── helpers ────────────────────

    private fun addMetaRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(MUTED); textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
        }, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(TextView(context).apply {
            text = value
            setTextColor(TEXT); textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row)
    }

    private fun isInstalled(game: AvailableGame): Boolean {
        val keys = installedGameTitles.map { normalizeKey(it) }.toSet()
        return normalizeKey(game.title) in keys ||
            normalizeKey(game.id) in keys ||
            game.title in installedGameTitles ||
            game.id in installedGameTitles
    }

    private fun openPage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun spacer(h: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (h > 0) h else 1)
    }

    private fun animTap(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            }.start()
    }

    private fun makeLiquid(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(250)
                        .setInterpolator(OvershootInterpolator(1.6f)).start()
                }
            }
            false
        }
    }

    private fun normalizeKey(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9\\-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

    companion object {
        private val TEXT = Color.rgb(232, 229, 220)
        private val MUTED = Color.rgb(140, 130, 112)
        private val MUTED_DIM = Color.rgb(120, 112, 104)
        private val ACCENT: Int get() = Theme.active.accent

        fun show(
            context: Context,
            game: AvailableGame,
            progress: DownloadManager.DownloadProgress?,
            installProgress: MainActivity.InstallProgress?,
            installedGameTitles: Set<String>,
            onDownload: (AvailableGame) -> Unit,
            onPauseDownload: (String) -> Unit,
            onClose: (AvailableGame) -> Unit,
        ): GameDetailOverlay {
            val overlay = GameDetailOverlay(
                context, game, progress, installProgress, installedGameTitles,
                onDownload, onPauseDownload, onClose,
            )
            val rootView = (context as? android.app.Activity)?.window?.decorView
                ?.findViewById<ViewGroup>(android.R.id.content)
            rootView?.addView(overlay.view(), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            return overlay
        }
    }
}
