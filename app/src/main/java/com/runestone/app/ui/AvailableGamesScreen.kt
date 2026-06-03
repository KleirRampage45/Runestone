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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.provider.AvailableGame
import com.runestone.app.provider.DownloadManager
import com.runestone.app.provider.DownloadOption
import com.runestone.app.provider.HosterResolver
import com.runestone.app.provider.SourcesManager

class AvailableGamesScreen(private val context: Context) {

    fun create(
        games: List<AvailableGame>,
        isLoading: Boolean,
        errorMessage: String?,
        downloadStates: Map<String, DownloadManager.DownloadProgress> = emptyMap(),
        installedGameTitles: Set<String> = emptySet(),
        onRefresh: () -> Unit,
        onManageSources: () -> Unit,
        onProviderSettings: () -> Unit,
        onDownload: (AvailableGame) -> Unit,
        onPauseDownload: (String) -> Unit,
        onBack: () -> Unit,
    ): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(220, 8, 8, 10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(mainLayout)

        mainLayout.addView(makeTopBar(onBack, onManageSources, onProviderSettings))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        }
        mainLayout.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(18))
        }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        if (isLoading) {
            content.addView(spacer(dp(12)))
            content.addView(loadingSkeleton())
        } else if (errorMessage != null) {
            content.addView(spacer(dp(36)))
            content.addView(TextView(context).apply {
                text = errorMessage
                setTextColor(Color.rgb(200, 160, 120)); textSize = 14f
                gravity = Gravity.CENTER; setPadding(dp(16), 0, dp(16), 0)
            })
            content.addView(spacer(dp(16)))
            content.addView(makeActionButton("MANAGE SOURCES", false) { onManageSources() })
            content.addView(spacer(dp(8)))
            content.addView(makeActionButton("PROVIDER SETTINGS", false) { onProviderSettings() })
            content.addView(spacer(dp(8)))
            content.addView(makeActionButton("REFRESH", true) { onRefresh() })
        } else if (games.isEmpty()) {
            content.addView(spacer(dp(36)))
            content.addView(TextView(context).apply {
                text = "EMPTY"
                textSize = 18f; gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(MUTED_DIM)
            })
            content.addView(spacer(dp(12)))
            content.addView(TextView(context).apply {
                text = "No games available"
                setTextColor(MUTED); textSize = 16f; gravity = Gravity.CENTER
            })
            content.addView(TextView(context).apply {
                text = "Add a trusted JSON source to browse games"
                setTextColor(MUTED_DIM); textSize = 12f; gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            content.addView(spacer(dp(20)))
            content.addView(makeActionButton("MANAGE SOURCES", false) { onManageSources() })
        } else {
            val gamesContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            var searchQuery = ""
            var engineFilter: String? = null
            val renderFilteredGames = {
                val filtered = games.filter { game ->
                    (searchQuery.isBlank() || game.title.contains(searchQuery, ignoreCase = true)) &&
                        (engineFilter == null || game.engine.equals(engineFilter, ignoreCase = true))
                }
                renderGameList(gamesContainer, filtered, downloadStates, onDownload, onPauseDownload, installedGameTitles)
            }

            val searchRow = makeSearchBar { query ->
                searchQuery = query
                renderFilteredGames()
            }
            content.addView(searchRow)
            content.addView(spacer(dp(10)))
            content.addView(makeEngineFilters(games) { engine ->
                engineFilter = engine
                renderFilteredGames()
            })
            content.addView(spacer(dp(10)))

            content.addView(gamesContainer, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            renderFilteredGames()
        }

        return root
    }

    private fun makeTopBar(
        onBack: () -> Unit,
        onManageSources: () -> Unit,
        onProviderSettings: () -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(10))
        setBackgroundColor(Color.rgb(3, 3, 4))

        addView(TextView(context).apply {
            text = "Back"
            setTextColor(ACCENT); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 207, 174, 126))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.argb(60, 207, 174, 126))
            }
            setOnClickListener { onBack() }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(TextView(context).apply {
            text = "Available Games"
            setTextColor(TEXT); textSize = 19f
            letterSpacing = 0.4f; gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        addView(TextView(context).apply {
            text = "\u2699 OPTIONS"
            setTextColor(TEXT); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = glassBg(dp(12), alpha = 120, accent = true)
            setOnClickListener { onProviderSettings() }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun renderGameList(
        container: LinearLayout,
        games: List<AvailableGame>,
        downloadStates: Map<String, DownloadManager.DownloadProgress>,
        onDownload: (AvailableGame) -> Unit,
        onPauseDownload: (String) -> Unit,
        installedGameTitles: Set<String> = emptySet(),
    ) {
        container.removeAllViews()
        games.forEach { game ->
            container.addView(gameCard(game, downloadStates[game.id], onDownload, onPauseDownload, installedGameTitles))
            container.addView(spacer(dp(12)))
        }
    }

    private fun makeSearchBar(
        onSearchChanged: (String) -> Unit,
    ): LinearLayout {
        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(4), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255)); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(30, 200, 180, 150))
            }
        }
        val searchInput = EditText(context).apply {
            hint = "Search games..."; setHintTextColor(Color.argb(80, 200, 180, 130))
            setTextColor(TEXT); textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT; maxLines = 1; background = null
            setPadding(0, dp(6), 0, dp(6))
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val clearBtn = TextView(context).apply {
            text = "X"; setTextColor(MUTED_DIM); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.INVISIBLE
            setOnClickListener { searchInput.setText("") }
            makeLiquid(this)
        }
        searchRow.addView(clearBtn)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                clearBtn.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
                onSearchChanged(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, co: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, be: Int, co: Int) {}
        })

        return searchRow
    }

    private fun makeEngineFilters(
        games: List<AvailableGame>,
        onFilterChanged: (String?) -> Unit,
    ): HorizontalScrollView {
        val engines = games.mapNotNull { it.engine?.trim()?.ifEmpty { null } }
            .distinctBy { it.lowercase() }
            .sortedBy { engineLabel(it) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val buttons = mutableListOf<Pair<String?, TextView>>()

        fun updateSelection(selected: String?) {
            buttons.forEach { (engine, button) ->
                val isSelected = engine == selected
                button.setTextColor(if (isSelected) Color.rgb(238, 207, 158) else MUTED)
                button.background = glassBg(dp(8), alpha = if (isSelected) 120 else 60, accent = isSelected)
            }
            onFilterChanged(selected)
        }

        listOf<String?>(null).plus(engines).forEach { engine ->
            val button = TextView(context).apply {
                text = engine?.let(::engineLabel) ?: "ALL"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener {
                    animTap(this)
                    updateSelection(engine)
                }
                makeLiquid(this)
            }
            buttons.add(engine to button)
            row.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(6)
            })
        }

        updateSelection(null)
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = HorizontalScrollView.OVER_SCROLL_NEVER
            addView(row)
        }
    }

    private fun makeActionButton(label: String, accent: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label; textSize = 12f
            setTextColor(if (accent) Color.rgb(220, 200, 160) else MUTED)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = glassBg(dp(10), alpha = if (accent) 120 else 80, accent = accent)
            setOnClickListener { animTap(this); onClick() }
            makeLiquid(this)
            layoutParams = LinearLayout.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.7f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun gameCard(
        game: AvailableGame,
        progress: DownloadManager.DownloadProgress?,
        onDownload: (AvailableGame) -> Unit,
        onPauseDownload: (String) -> Unit,
        installedGameTitles: Set<String> = emptySet(),
    ): LinearLayout {
        val screenW = context.resources.displayMetrics.widthPixels
        val cardW = (screenW * 0.92f).toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 12, 11, 16))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(60, 207, 174, 126))
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // ── Top section: title + engine badge ──
        val topSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }

        topSection.addView(TextView(context).apply {
            text = game.title; setTextColor(TEXT); textSize = 19f
            typeface = Typeface.create("serif", Typeface.BOLD); maxLines = 2
        })

        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        metaRow.addView(TextView(context).apply {
            text = engineLabel(game.engine)
            setTextColor(Color.rgb(238, 207, 158)); textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 200, 170, 130)); cornerRadius = dp(6).toFloat()
                setStroke(dp(1), Color.argb(70, 200, 170, 130))
            }
        })
        metaRow.addView(spacer(dp(10)))
        metaRow.addView(TextView(context).apply {
            text = game.sourceName; setTextColor(MUTED); textSize = 11f
        })
        if (game.fileSize != null) {
            metaRow.addView(spacer(dp(10)))
            metaRow.addView(TextView(context).apply {
                text = formatBytes(game.fileSize)
                setTextColor(MUTED); textSize = 11f
            })
        }
        topSection.addView(metaRow)

        card.addView(topSection, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ── Progress bar (if downloading) ──
        if (progress != null && progress.state == DownloadManager.DownloadState.DOWNLOADING) {
            val progressContainer = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(30, 255, 255, 255)); cornerRadius = dp(4).toFloat()
                }
                setPadding(dp(16), 0, dp(16), 0)
            }
            val progressBar = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(160, 207, 174, 126)); cornerRadius = dp(4).toFloat()
                }
            }
            val percent = if (progress.totalBytes > 0) {
                (progress.bytesDownloaded * 100 / progress.totalBytes).toInt()
            } else 0
            val containerWidth = (cardW - dp(32))
            val barWidth = (containerWidth * percent / 100f).toInt().coerceAtLeast(dp(2))

            progressContainer.addView(progressBar, FrameLayout.LayoutParams(barWidth, dp(6)))
            card.addView(progressContainer, LinearLayout.LayoutParams(cardW, dp(6)))

            card.addView(TextView(context).apply {
                text = "$percent%  |  ${formatBytes(progress.bytesDownloaded)} / ${formatBytes(progress.totalBytes)}"
                setTextColor(MUTED_DIM); textSize = 10f; gravity = Gravity.CENTER
                setPadding(dp(16), dp(4), dp(16), 0)
            })
        }

        // ── Error message ──
        if (progress != null && progress.state == DownloadManager.DownloadState.FAILED) {
            card.addView(TextView(context).apply {
                text = progress.error ?: "Download failed"
                setTextColor(Color.rgb(200, 120, 100)); textSize = 10f
                setPadding(dp(16), dp(4), dp(16), 0)
            })
        }

        // ── Bottom action bar ──
        val actionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 12, 11, 16))
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat()
                )
            }
        }

        val state = progress?.state
        when {
            state == DownloadManager.DownloadState.DOWNLOADING -> {
                actionBar.addView(makeActionBtn("PAUSE", Color.rgb(220, 200, 160), Color.argb(40, 200, 170, 130)) {
                    animTap(it); onPauseDownload(game.id)
                })
            }
            state == DownloadManager.DownloadState.PAUSED -> {
                actionBar.addView(makeActionBtn("RESUME", Color.rgb(200, 200, 160), Color.argb(40, 200, 170, 80)) {
                    animTap(it); onDownload(game)
                })
            }
            state == DownloadManager.DownloadState.COMPLETED -> {
                actionBar.addView(makeActionBtn("INSTALLED", Color.rgb(140, 220, 140), Color.argb(40, 80, 160, 80)) {})
            }
            state == DownloadManager.DownloadState.FAILED -> {
                actionBar.addView(makeActionBtn("RETRY", Color.rgb(220, 160, 140), Color.argb(40, 200, 100, 80)) {
                    animTap(it); onDownload(game)
                })
            }
            game.title in installedGameTitles -> {
                actionBar.addView(makeActionBtn("INSTALLED", Color.rgb(140, 220, 140), Color.argb(40, 80, 160, 80)) {})
            }
            game.downloadOptions.size > 1 -> {
                actionBar.addView(makeActionBtn("GET (${game.downloadOptions.size})", Color.rgb(140, 220, 140), Color.argb(40, 80, 160, 80)) {
                    animTap(it); showDownloadOptionsDialog(game, onDownload)
                })
            }
            game.downloadOptions.isNotEmpty() -> {
                actionBar.addView(makeActionBtn("GET", Color.rgb(140, 220, 140), Color.argb(40, 80, 160, 80)) {
                    animTap(it); onDownload(game)
                })
            }
            else -> {
                actionBar.addView(makeActionBtn("NO DOWNLOADS", Color.rgb(160, 150, 130), Color.argb(40, 120, 110, 90)) {})
            }
        }

        card.addView(actionBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        return card
    }

    private fun makeActionBtn(label: String, textColor: Int, bgColor: Int, onClick: (View) -> Unit): TextView =
        TextView(context).apply {
            text = label; setTextColor(textColor); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(bgColor); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(50, 160, 140, 110))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { onClick(this) }
            makeLiquid(this)
        }

    private fun showDownloadOptionsDialog(game: AvailableGame, onDownload: (AvailableGame) -> Unit) {
        val screenW = context.resources.displayMetrics.widthPixels

        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            alpha = 0f
            animate().alpha(1f).setDuration(280).start()
        }

        val panelW = (screenW * 0.88f).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 12, 11, 16))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(70, 160, 140, 110))
            }
            translationY = 120f
            alpha = 0f
            animate().translationY(0f).alpha(1f).setDuration(350)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = false; overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        scroll.addView(panel)

        panel.addView(TextView(context).apply {
            text = "CHOOSE DOWNLOAD"; setTextColor(ACCENT); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(spacer(dp(4)))
        panel.addView(TextView(context).apply {
            text = game.title; setTextColor(TEXT); textSize = 16f
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        panel.addView(spacer(dp(14)))

        game.downloadOptions.forEach { option ->
            val hostStatus = HosterResolver.isSupported(option.url)
            val isSupported = hostStatus.supported

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                alpha = if (isSupported) 1f else 0.45f
                background = glassBg(dp(10), alpha = 60, accent = isSupported)
                if (isSupported) {
                    setOnClickListener {
                        animTap(this)
                        val singleOptionGame = game.copy(downloadOptions = listOf(option))
                        onDownload(singleOptionGame)
                        val rootView = (context as? android.app.Activity)?.window?.decorView
                            ?.findViewById<ViewGroup>(android.R.id.content)
                        overlay.animate().alpha(0f).translationY(60f).setDuration(200).withEndAction {
                            rootView?.removeView(overlay)
                        }.start()
                    }
                    makeLiquid(this)
                }
            }

            val infoCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            infoCol.addView(TextView(context).apply {
                text = option.name; setTextColor(TEXT); textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            })
            if (!isSupported) {
                infoCol.addView(TextView(context).apply {
                    text = "Not available on Android"
                    setTextColor(Color.rgb(200, 120, 100)); textSize = 10f
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(TextView(context).apply {
                text = option.host; setTextColor(if (isSupported) ACCENT else Color.rgb(140, 100, 90)); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.argb(if (isSupported) 40 else 20, 200, 170, 130)); cornerRadius = dp(5).toFloat()
                    setStroke(dp(1), Color.argb(if (isSupported) 50 else 20, 200, 170, 130))
                }
            })

            if (option.fileSize != null) {
                row.addView(spacer(dp(8)))
                row.addView(TextView(context).apply {
                    text = formatBytes(option.fileSize); setTextColor(MUTED_DIM); textSize = 11f
                })
            }

            panel.addView(row)
            panel.addView(spacer(dp(6)))
        }

        panel.addView(spacer(dp(8)))
        panel.addView(TextView(context).apply {
            text = "CANCEL"; setTextColor(MUTED); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(24), dp(8), dp(24), dp(8))
            background = glassBg(dp(8), alpha = 60)
            setOnClickListener {
                animTap(this)
                val rootView = (context as? android.app.Activity)?.window?.decorView
                    ?.findViewById<ViewGroup>(android.R.id.content)
                overlay.animate().alpha(0f).translationY(60f).setDuration(200).withEndAction {
                    rootView?.removeView(overlay)
                }.start()
            }
            makeLiquid(this)
        })

        val rootView = (context as? android.app.Activity)?.window?.decorView
            ?.findViewById<ViewGroup>(android.R.id.content)
        overlay.addView(scroll, FrameLayout.LayoutParams(panelW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(0, dp(30), 0, dp(30))
        })
        rootView?.addView(overlay)

        overlay.setOnClickListener {
            overlay.animate().alpha(0f).translationY(60f).setDuration(200).withEndAction {
                rootView?.removeView(overlay)
            }.start()
        }
        scroll.setOnTouchListener { _, _ -> false }
    }

    private fun engineLabel(engine: String?): String = when (engine?.lowercase()) {
        "mv", "mz" -> "MV/MZ"
        "vx", "vxace", "rgss3" -> "VX/ACE"
        "xp", "rgss" -> "XP"
        "2000", "2003", "easyrpg" -> "2K"
        "renpy" -> "RNPY"
        else -> "???"
    }

    private fun formatBytes(bytes: Long): String {
        val gb = 1024.0 * 1024.0 * 1024.0; val mb = 1024.0 * 1024.0
        return if (bytes >= gb) String.format("%.2f GB", bytes / gb) else String.format("%.1f MB", bytes / mb)
    }

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha,
                if (accent) 50 else 22, if (accent) 40 else 20, if (accent) 30 else 26))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(if (accent) 80 else 45,
                if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
        }

    private fun makeLiquid(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f; val cy = v.height / 2f
                    v.translationX = (event.x - cx) * 0.04f
                    v.translationY = (event.y - cy) * 0.04f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                        .setDuration(250).setInterpolator(OvershootInterpolator(1.6f)).start()
                }
            }
            false
        }
    }

    private fun animTap(v: View) {
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            }.start()
    }

    private fun spacer(h: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (h > 0) h else 1)
    }

    // ── Loading skeleton ──

    private fun loadingSkeleton(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val screenW = context.resources.displayMetrics.widthPixels
        val cardW = (screenW * 0.88f).toInt()
        repeat(3) { i ->
            if (i > 0) addView(spacer(dp(12)))
            addView(skeletonCard(cardW, i))
        }
    }

    private fun skeletonCard(cardW: Int, index: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(cardW, dp(120))
        background = glassBg(dp(14), alpha = 200)
        gravity = Gravity.CENTER_HORIZONTAL

        // Title placeholder bar
        addView(spacer(dp(16)))
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((cardW * 0.55f).toInt(), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255))
                cornerRadius = dp(4).toFloat()
            }
        })

        // Subtitle placeholder bar
        addView(spacer(dp(10)))
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((cardW * 0.35f).toInt(), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(20, 255, 255, 255))
                cornerRadius = dp(3).toFloat()
            }
        })

        // Flexible spacer to push button area to bottom
        addView(spacer(0), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Button area placeholder (bottom bar of the card)
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
            background = GradientDrawable().apply {
                setColor(Color.argb(20, 255, 255, 255))
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat()
                )
            }
        })

        // Staggered pulsing animation — each card starts with a delay
        postDelayed({
            startPulse(this)
        }, (index * 250L))
    }

    private fun startPulse(view: View) {
        if (!view.isAttachedToWindow) return
        view.animate().alpha(0.4f).setDuration(800).withEndAction {
            if (!view.isAttachedToWindow) return@withEndAction
            view.animate().alpha(0.8f).setDuration(800).withEndAction {
                startPulse(view)
            }.start()
        }.start()
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(100, 95, 85)
        val ACCENT: Int get() = Theme.active.accent
    }
}
