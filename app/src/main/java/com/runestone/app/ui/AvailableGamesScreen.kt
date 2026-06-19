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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.EditText
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
import com.runestone.app.provider.SourcesManager

class AvailableGamesScreen(private val context: Context) {

    fun create(
        games: List<AvailableGame>,
        isLoading: Boolean,
        isMetadataLoading: Boolean = false,
        errorMessage: String?,
        downloadStates: Map<String, DownloadManager.DownloadProgress> = emptyMap(),
        installStates: Map<String, MainActivity.InstallProgress> = emptyMap(),
        installedGameTitles: Set<String> = emptySet(),
        gridColumns: Int = 2,
        initialScrollY: Int = 0,
        onScrollYChanged: (Int) -> Unit = {},
        onGridColumnsChanged: (Int) -> Unit = {},
        onRefresh: () -> Unit,
        onManageSources: () -> Unit,
        onProviderSettings: () -> Unit,
        onDownload: (AvailableGame) -> Unit,
        onPauseDownload: (String) -> Unit,
        onBack: () -> Unit,
        onOpenDetail: (AvailableGame) -> Unit = {},
    ): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(252, 3, 3, 4))
            isClickable = true
            isFocusable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(248, 3, 3, 4))
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(mainLayout)

        mainLayout.addView(makeTopBar(onBack, onManageSources, onProviderSettings, gridColumns, onGridColumnsChanged))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                onScrollYChanged(scrollY)
            }
        }
        mainLayout.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(34))
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
                    (
                        searchQuery.isBlank() ||
                            game.title.contains(searchQuery, ignoreCase = true) ||
                            game.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                            game.description?.contains(searchQuery, ignoreCase = true) == true
                    ) &&
                        (engineFilter == null || game.engine.equals(engineFilter, ignoreCase = true))
                }
                renderGameList(gamesContainer, filtered, downloadStates, installStates, onDownload, onPauseDownload, installedGameTitles, onOpenDetail, gridColumns)
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
            if (isMetadataLoading) {
                content.addView(spacer(dp(8)))
                content.addView(metadataLoadingStrip())
            }
            content.addView(spacer(dp(10)))

            content.addView(gamesContainer, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            renderFilteredGames()
        }

        if (initialScrollY > 0) {
            scroll.post { scroll.scrollTo(0, initialScrollY) }
        }

        return root
    }

    private fun makeTopBar(
        onBack: () -> Unit,
        onManageSources: () -> Unit,
        onProviderSettings: () -> Unit,
        gridColumns: Int,
        onGridColumnsChanged: (Int) -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(8))
        setBackgroundColor(Color.TRANSPARENT)

        addView(TextView(context).apply {
            text = "Store"
            setTextColor(TEXT); textSize = 22f
            letterSpacing = 0f; gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        addView(TextView(context).apply {
            text = "\u2699"
            contentDescription = "Provider settings"
            setTextColor(TEXT); textSize = 21f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = glassBg(dp(22), alpha = 55)
            setOnClickListener { onProviderSettings() }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            marginEnd = dp(8)
        })

        addView(GridDensityIcon(context, gridColumns).apply {
            contentDescription = "Store grid density"
            background = glassBg(dp(22), alpha = 55)
            setOnClickListener {
                animTap(this)
                onGridColumnsChanged(if (gridColumns >= 4) 1 else gridColumns + 1)
            }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            marginEnd = dp(8)
        })

        addView(TextView(context).apply {
            text = "SOURCES"
            setTextColor(MUTED); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            background = glassBg(dp(22), alpha = 55)
            setOnClickListener { onManageSources() }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)))
    }

    private fun renderGameList(
        container: LinearLayout,
        games: List<AvailableGame>,
        downloadStates: Map<String, DownloadManager.DownloadProgress>,
        installStates: Map<String, MainActivity.InstallProgress>,
        onDownload: (AvailableGame) -> Unit,
        onPauseDownload: (String) -> Unit,
        installedGameTitles: Set<String> = emptySet(),
        onOpenDetail: (AvailableGame) -> Unit = {},
        gridColumns: Int = 2,
    ) {
        container.removeAllViews()
        val screenW = context.resources.displayMetrics.widthPixels
        val columns = gridColumns.coerceIn(1, 4)
        val horizontalPadding = dp(58)
        val gap = dp(if (columns >= 3) 8 else 10)
        val cardW = ((screenW - horizontalPadding - gap * (columns - 1)) / columns)
            .coerceAtLeast(dp(if (columns >= 4) 74 else 100))
        val cardH = when (columns) {
            1 -> dp(244)
            2 -> dp(186)
            3 -> dp(152)
            else -> dp(132)
        }
        games.chunked(columns).forEach { rowGames ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowGames.forEachIndexed { index, game ->
                row.addView(
                    gameCard(
                        game,
                        downloadStates[game.id],
                        installStates[game.id],
                        onDownload,
                        onPauseDownload,
                        installedGameTitles,
                        onOpenDetail,
                        cardW,
                        columns,
                    ),
                    LinearLayout.LayoutParams(cardW, cardH).apply {
                        if (index < columns - 1) marginEnd = gap
                    },
                )
            }
            repeat(columns - rowGames.size) { emptyIndex ->
                row.addView(View(context), LinearLayout.LayoutParams(cardW, 1).apply {
                    if (rowGames.size + emptyIndex < columns - 1) marginEnd = gap
                })
            }
            container.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = gap
            })
        }
    }

    private fun makeSearchBar(
        onSearchChanged: (String) -> Unit,
    ): LinearLayout {
        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(225, 5, 5, 7)); cornerRadius = dp(25).toFloat()
                setStroke(dp(1), Color.argb(70, 150, 140, 125))
            }
        }
        val searchInput = EditText(context).apply {
            hint = "Search games..."; setHintTextColor(Color.argb(155, 204, 186, 148))
            setTextColor(TEXT); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT; maxLines = 1; background = null
            setPadding(0, dp(7), 0, dp(7))
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val clearBtn = TextView(context).apply {
            text = "X"; setTextColor(MUTED_DIM); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            minimumWidth = dp(44); minimumHeight = dp(44)
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
                button.setTextColor(if (isSelected) Color.rgb(18, 17, 14) else MUTED)
                button.background = glassBg(dp(20), alpha = if (isSelected) 210 else 42, accent = isSelected)
            }
            onFilterChanged(selected)
        }

        listOf<String?>(null).plus(engines).forEach { engine ->
            val button = TextView(context).apply {
                text = engine?.let(::engineLabel) ?: "ALL"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(9), dp(15), dp(9))
                minimumHeight = dp(42)
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
                marginEnd = dp(7)
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

    private fun metadataLoadingStrip(): TextView =
        TextView(context).apply {
            text = "Fetching store art and details"
            setTextColor(Color.rgb(206, 184, 146))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = glassBg(dp(8), alpha = 75, accent = true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun gameCard(
        game: AvailableGame,
        progress: DownloadManager.DownloadProgress?,
        installProgress: MainActivity.InstallProgress?,
        onDownload: (AvailableGame) -> Unit,
        onPauseDownload: (String) -> Unit,
        installedGameTitles: Set<String> = emptySet(),
        onOpenDetail: (AvailableGame) -> Unit = {},
        forcedCardWidth: Int? = null,
        gridColumns: Int = 2,
    ): LinearLayout {
        val screenW = context.resources.displayMetrics.widthPixels
        val cardW = forcedCardWidth ?: (screenW * 0.92f).toInt()
        val columns = gridColumns.coerceIn(1, 4)
        val cardH = when (columns) {
            1 -> dp(244)
            2 -> dp(186)
            3 -> dp(152)
            else -> dp(132)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(cardW, cardH)
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 12, 11, 16))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.argb(70, 145, 124, 100))
            }
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setOnClickListener { onOpenDetail(game) }
        }

        val coverHeight = when (columns) {
            1 -> dp(154)
            2 -> dp(98)
            3 -> dp(76)
            else -> dp(58)
        }
        if (game.coverUrl != null) {
            val cover = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(Color.argb(50, 255, 255, 255))
                    cornerRadius = dp(8).toFloat()
                }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                contentDescription = game.title
            }
            card.addView(cover, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight,
                ))
            Thread {
                runCatching {
                    val bitmap = if (game.coverUrl.startsWith("local:")) {
                        android.graphics.BitmapFactory.decodeFile(game.coverUrl.removePrefix("local:"))
                    } else {
                        android.graphics.BitmapFactory.decodeStream(java.net.URL(game.coverUrl).openStream())
                    }
                    cover.post { cover.setImageBitmap(bitmap) }
                }
            }.start()
        } else {
            card.addView(TextView(context).apply {
                text = engineLabel(game.engine).take(2)
                setTextColor(Color.rgb(238, 207, 158)); textSize = when (columns) {
                    1 -> 40f
                    2 -> 34f
                    3 -> 25f
                    else -> 20f
                }
                letterSpacing = 0.12f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.argb(120, 38, 32, 28))
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, coverHeight))
        }

        val topSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(if (columns >= 3) 7 else 10),
                dp(if (columns >= 3) 6 else 8),
                dp(if (columns >= 3) 7 else 10),
                dp(if (columns >= 3) 5 else 7),
            )
        }

        topSection.addView(TextView(context).apply {
            text = game.title
            setTextColor(TEXT); textSize = when (columns) {
                1 -> 18f
                2 -> 15f
                3 -> 12.5f
                else -> 10.5f
            }
            typeface = Typeface.DEFAULT_BOLD
            maxLines = if (columns >= 4) 1 else 2
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        })

        topSection.addView(TextView(context).apply {
            text = cardMeta(game)
            setTextColor(MUTED); textSize = when (columns) {
                1 -> 12.5f
                2 -> 12f
                3 -> 9.5f
                else -> 8.5f
            }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(0, dp(if (columns >= 3) 4 else 6), 0, 0)
        })

        card.addView(topSection, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Progress bar (if downloading/installing) ──
        if (installProgress != null) {
            val progressContainer = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(30, 255, 255, 255)); cornerRadius = dp(4).toFloat()
                }
                setPadding(dp(16), 0, dp(16), 0)
            }
            val progressBar = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.argb(160, 126, 190, 207)); cornerRadius = dp(4).toFloat()
                }
            }
            val percent = if (installProgress.totalFiles > 0) {
                (installProgress.filesExtracted * 100 / installProgress.totalFiles).coerceIn(0, 100)
            } else 0
            val containerWidth = (cardW - dp(32))
            val barWidth = (containerWidth * percent / 100f).toInt().coerceAtLeast(dp(2))

            progressContainer.addView(progressBar, FrameLayout.LayoutParams(barWidth, dp(6)))
            card.addView(progressContainer, LinearLayout.LayoutParams(cardW, dp(6)))

            card.addView(TextView(context).apply {
                text = "Installing  |  ${installProgress.filesExtracted}/${installProgress.totalFiles} files"
                setTextColor(MUTED_DIM); textSize = 10f; gravity = Gravity.CENTER
                setPadding(dp(16), dp(4), dp(16), 0)
            })
        } else if (progress != null && progress.state == DownloadManager.DownloadState.DOWNLOADING) {
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
                text = "$percent%  |  ${formatBytes(progress.bytesDownloaded)} / ${formatBytes(progress.totalBytes)}  |  ${formatBytes(progress.speed.toLong())}/s"
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

        val state = progress?.state
        when {
            installProgress != null -> {
                card.addView(compactStatus("INSTALLING", Color.rgb(170, 210, 230)))
            }
            state == DownloadManager.DownloadState.DOWNLOADING -> {
                card.addView(compactStatus("DOWNLOADING", Color.rgb(220, 200, 160)).apply {
                    setOnClickListener { animTap(this); onPauseDownload(game.id) }
                    makeLiquid(this)
                })
            }
            state == DownloadManager.DownloadState.PAUSED -> {
                card.addView(compactStatus("PAUSED", Color.rgb(200, 200, 160)).apply {
                    setOnClickListener { animTap(this); onDownload(game) }
                    makeLiquid(this)
                })
            }
            state == DownloadManager.DownloadState.COMPLETED -> {
                card.addView(compactStatus("INSTALLING", Color.rgb(170, 210, 230)))
            }
            state == DownloadManager.DownloadState.FAILED -> {
                card.addView(compactStatus("FAILED", Color.rgb(220, 160, 140)).apply {
                    setOnClickListener { animTap(this); onDownload(game) }
                    makeLiquid(this)
                })
            }
        }

        return card
    }

    private fun compactStatus(label: String, textColor: Int): TextView =
        TextView(context).apply {
            text = label; setTextColor(textColor); textSize = 10f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.argb(80, 12, 11, 16))
                setStroke(dp(1), Color.argb(45, 160, 140, 110))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22))
        }

    private fun cardMeta(game: AvailableGame): String {
        val engine = engineLabel(game.engine)
        val size = game.fileSize?.let(::formatBytes)
        return listOf(engine, size).filterNotNull().joinToString("  |  ")
    }

    private class GridDensityIcon(context: Context, private val columns: Int) : View(context) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 1.6f
            color = Color.rgb(232, 229, 220)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(55, 232, 229, 220)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 229, 220)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = context.resources.displayMetrics.density * 10f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val count = columns.coerceIn(1, 4)
            val left = width * 0.24f
            val top = height * 0.28f
            val right = width * 0.76f
            val bottom = height * 0.66f
            val gap = density * 2.4f
            val cellW = (right - left - gap * (count - 1)) / count
            repeat(count) { index ->
                val x = left + index * (cellW + gap)
                val rect = RectF(x, top, x + cellW, bottom)
                canvas.drawRoundRect(rect, density * 2f, density * 2f, fill)
                canvas.drawRoundRect(rect, density * 2f, density * 2f, stroke)
            }
            canvas.drawText(count.toString(), width / 2f, height * 0.86f, textPaint)
        }
    }

    private fun makeActionBtn(label: String, textColor: Int, bgColor: Int, onClick: (View) -> Unit): TextView =
        TextView(context).apply {
            text = label; setTextColor(textColor); textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(bgColor); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(50, 160, 140, 110))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { onClick(this) }
            makeLiquid(this)
        }

    private fun openPage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha,
                if (accent) 202 else 22, if (accent) 174 else 20, if (accent) 126 else 26))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(if (accent) 80 else 45,
                if (accent) 230 else 100, if (accent) 196 else 90, if (accent) 145 else 80))
        }

    private fun makeLiquid(view: View) { if (Theme.isReducedMotion(context)) return
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

    private fun animTap(v: View) { if (Theme.isReducedMotion(context)) return
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
        addView(TextView(context).apply {
            text = "Loading catalogue"
            setTextColor(Color.rgb(206, 184, 146))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = glassBg(dp(8), alpha = 75, accent = true)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(spacer(dp(10)))
        val screenW = context.resources.displayMetrics.widthPixels
        val cardW = ((screenW - dp(44)) / 2).coerceAtLeast(dp(144))
        repeat(3) { rowIndex ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            repeat(2) { col ->
                row.addView(skeletonCard(cardW, rowIndex * 2 + col), LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(4), 0, dp(4), dp(10))
                })
            }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun skeletonCard(cardW: Int, index: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(cardW, dp(112))
        background = glassBg(dp(10), alpha = 145)
        gravity = Gravity.CENTER_HORIZONTAL

        // Title placeholder bar
        addView(spacer(dp(12)))
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((cardW * 0.58f).toInt(), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255))
                cornerRadius = dp(4).toFloat()
            }
        })

        // Subtitle placeholder bar
        addView(spacer(dp(8)))
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams((cardW * 0.38f).toInt(), dp(8))
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
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(10).toFloat(), dp(10).toFloat(), dp(10).toFloat(), dp(10).toFloat())
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

    private fun isInstalled(game: AvailableGame, installedGameTitles: Set<String>): Boolean {
        val installedKeys = installedGameTitles.map { normalizeInstallKey(it) }.toSet()
        return normalizeInstallKey(game.title) in installedKeys ||
            normalizeInstallKey(game.id) in installedKeys ||
            game.title in installedGameTitles ||
            game.id in installedGameTitles
    }

    private fun normalizeInstallKey(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9\\-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(120, 112, 104)
        val ACCENT: Int get() = Theme.active.accent
    }
}
