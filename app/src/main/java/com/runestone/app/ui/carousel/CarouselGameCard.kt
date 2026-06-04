/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.runestone.app.ui.GameCardInfo
import java.net.URL

class CarouselGameCard(context: Context) : FrameLayout(context) {

    private val coverImage: ImageView
    private val engineBadge: TextView
    private val actionOverlay: FrameLayout
    private val playButton: TextView
    private val settingsButton: TextView

    init {
        layoutParams = RecyclerView.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.72f).toInt(),
            ((context.resources.displayMetrics.widthPixels * 0.72f) * 1.38f).toInt()
        ).apply {
            setMargins(dp(16), 0, dp(16), 0)
        }
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        // Card background — glass style
        background = GradientDrawable().apply {
            setColor(Color.argb(220, 12, 11, 16))
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.argb(60, 207, 174, 126))
        }

        // Cover image area (takes up top portion of card)
        coverImage = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            contentDescription = ""
        }
        addView(coverImage)

        // Bottom overlay for engine badge. Keep it translucent so cover art remains visible.
        val bottomOverlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(112, 3, 3, 4))
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,
                    dp(22).toFloat(), dp(22).toFloat(),
                    dp(22).toFloat(), dp(22).toFloat(),
                )
            }
        }

        engineBadge = TextView(context).apply {
            textSize = 10f
            setTextColor(Color.rgb(238, 207, 158))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 200, 170, 130))
                cornerRadius = dp(4).toFloat()
                setStroke(dp(1), Color.argb(40, 200, 170, 130))
            }
        }
        bottomOverlay.addView(engineBadge)

        addView(bottomOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))

        actionOverlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            visibility = View.GONE
        }
        val actionPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(60)) // Extra bottom padding to avoid badge overlap
        }
        playButton = makeButton("PLAY", Color.rgb(220, 240, 210), Color.argb(100, 80, 160, 80))
        settingsButton = makeButton("SETTINGS", Color.rgb(238, 218, 184), Color.argb(90, 160, 140, 100))
        actionPanel.addView(playButton)
        actionPanel.addView(View(context), LinearLayout.LayoutParams(0, dp(12)))
        actionPanel.addView(settingsButton)
        actionOverlay.addView(actionPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        addView(actionOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
    }

    fun bind(
        game: GameCardInfo,
        showActions: Boolean,
        onPlay: (GameCardInfo) -> Unit,
        onSettings: (GameCardInfo) -> Unit,
    ) {
        coverImage.contentDescription = game.displayName
        engineBadge.text = game.engineType.label
        actionOverlay.visibility = if (showActions) View.VISIBLE else View.GONE
        playButton.setOnClickListener { onPlay(game) }
        settingsButton.setOnClickListener { onSettings(game) }

        // Always set engine-themed gradient first as base
        setEngineGradient(game.engineType.label)

        // Try to load cover art in background
        val url = game.coverUrl
        if (!url.isNullOrBlank()) {
            Thread {
                try {
                    val bitmap = if (url.startsWith("local:")) {
                        BitmapFactory.decodeFile(url.removePrefix("local:"))
                    } else {
                        BitmapFactory.decodeStream(URL(url).openStream())
                    }
                    if (bitmap != null) {
                        post {
                            coverImage.setImageBitmap(bitmap)
                            coverImage.alpha = 0f
                            coverImage.animate().alpha(1f).setDuration(300).start()
                        }
                    }
                } catch (_: Exception) {
                    // Fallback: gradient stays as-is
                }
            }.start()
        }
    }

    private fun makeButton(label: String, textColor: Int, bgColor: Int): TextView =
        TextView(context).apply {
            text = label
            setTextColor(textColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(70, 200, 180, 140))
            }
            layoutParams = LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    private fun setEngineGradient(engine: String) {
        val colors = when {
            engine.contains("RGSS", ignoreCase = true) -> intArrayOf(
                Color.rgb(60, 40, 20), Color.rgb(20, 15, 25),
            )
            engine.contains("MV", ignoreCase = true) || engine.contains("MZ", ignoreCase = true) -> intArrayOf(
                Color.rgb(20, 40, 60), Color.rgb(15, 15, 25),
            )
            engine.contains("2K", ignoreCase = true) || engine.contains("2k", ignoreCase = true) -> intArrayOf(
                Color.rgb(30, 50, 30), Color.rgb(15, 20, 15),
            )
            else -> intArrayOf(
                Color.rgb(30, 25, 35), Color.rgb(15, 12, 18),
            )
        }
        val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        gradient.cornerRadius = dp(22).toFloat()
        coverImage.setImageDrawable(gradient)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
