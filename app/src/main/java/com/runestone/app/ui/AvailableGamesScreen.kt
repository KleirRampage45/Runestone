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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.provider.AvailableGame
import com.runestone.app.provider.SourcesManager

class AvailableGamesScreen(private val context: Context) {

    fun create(
        games: List<AvailableGame>,
        isLoading: Boolean,
        errorMessage: String?,
        onRefresh: () -> Unit,
        onManageSources: () -> Unit,
        onProviderSettings: () -> Unit,
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
            content.addView(spacer(dp(48)))
            content.addView(TextView(context).apply {
                text = "Fetching games..."
                setTextColor(MUTED); textSize = 14f; gravity = Gravity.CENTER
            })
            content.addView(spacer(dp(8)))
            content.addView(TextView(context).apply {
                text = "Please wait"
                setTextColor(MUTED_DIM); textSize = 11f; gravity = Gravity.CENTER
            })
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
            content.addView(spacer(dp(48)))
            content.addView(TextView(context).apply {
                text = "No games available"
                setTextColor(MUTED); textSize = 16f; gravity = Gravity.CENTER
            })
            content.addView(TextView(context).apply {
                text = "Add sources or configure the API server"
                setTextColor(MUTED_DIM); textSize = 12f; gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            content.addView(spacer(dp(20)))
            content.addView(makeActionButton("MANAGE SOURCES", false) { onManageSources() })
        } else {
            content.addView(makeSearchBar(games, content, onManageSources, onProviderSettings, onRefresh))
            content.addView(spacer(dp(8)))
            games.forEach { game ->
                content.addView(gameCard(game))
                content.addView(spacer(dp(8)))
            }
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
        setBackgroundColor(Color.rgb(15, 14, 18))

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
            text = "CFG"
            setTextColor(MUTED); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = glassBg(dp(8))
            setOnClickListener { onProviderSettings() }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun makeSearchBar(
        allGames: List<AvailableGame>,
        content: LinearLayout,
        onManageSources: () -> Unit,
        onProviderSettings: () -> Unit,
        onRefresh: () -> Unit,
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
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, co: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, be: Int, co: Int) {}
        })

        return searchRow
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

    private fun gameCard(game: AvailableGame): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = glassBg(dp(12))
            makeLiquid(this)
        }

        // Icon placeholder (colored by engine)
        val engineColor = engineColor(game.engine)
        val iconBox = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(engineColor)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.argb(50, 200, 180, 150))
            }
        }
        iconBox.addView(TextView(context).apply {
            text = engineLabel(game.engine)
            setTextColor(Color.argb(120, 255, 255, 255))
            textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        card.addView(iconBox, LinearLayout.LayoutParams(dp(42), dp(42)))

        // Info column
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        info.addView(TextView(context).apply {
            text = game.title; setTextColor(TEXT); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; maxLines = 2
        })

        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, 0)
        }
        metaRow.addView(TextView(context).apply {
            text = game.engine ?: "Unknown"
            setTextColor(ACCENT); textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 200, 170, 130)); cornerRadius = dp(4).toFloat()
            }
        })
        if (game.fileSize != null) {
            metaRow.addView(spacer(dp(6)))
            metaRow.addView(TextView(context).apply {
                text = formatBytes(game.fileSize)
                setTextColor(MUTED_DIM); textSize = 10f
            })
        }
        metaRow.addView(spacer(dp(6)))
        metaRow.addView(TextView(context).apply {
            text = game.sourceName; setTextColor(MUTED_DIM); textSize = 10f
        })
        info.addView(metaRow)

        card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Download indicator
        if (game.downloadUrl != null) {
            card.addView(TextView(context).apply {
                text = "GET"; setTextColor(Color.rgb(140, 220, 140)); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 80, 160, 80)); cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), Color.argb(50, 80, 160, 80))
                }
            })
        }

        return card
    }

    private fun engineLabel(engine: String?): String = when (engine?.lowercase()) {
        "mv", "mz" -> "MV/MZ"
        "vx", "vxace", "rgss3" -> "VX/ACE"
        "xp", "rgss" -> "XP"
        "2000", "2003", "easyrpg" -> "2K"
        "renpy" -> "RNPY"
        else -> "???"
    }

    private fun engineColor(engine: String?): Int = when (engine?.lowercase()) {
        "mv", "mz" -> Color.rgb(30, 35, 28)
        "vx", "vxace", "rgss3" -> Color.rgb(35, 28, 32)
        "xp", "rgss" -> Color.rgb(28, 32, 35)
        "2000", "2003", "easyrpg" -> Color.rgb(28, 32, 35)
        "renpy" -> Color.rgb(32, 28, 35)
        else -> Color.rgb(28, 28, 28)
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

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(100, 95, 85)
        val ACCENT = Color.rgb(207, 174, 126)
    }
}
