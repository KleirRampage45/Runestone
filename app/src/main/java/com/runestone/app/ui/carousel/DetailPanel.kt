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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.runestone.app.ui.GameCardInfo

class DetailPanel(context: Context) : LinearLayout(context) {

    private val titleView: TextView
    private val engineBadge: TextView
    private val playButton: TextView
    private val settingsButton: TextView
    private var currentGame: GameCardInfo? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20), dp(12), dp(20), dp(12))

        // Title
        titleView = TextView(context).apply {
            textSize = 20f
            setTextColor(Color.rgb(232, 229, 220))
            typeface = Typeface.create("serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
        }
        addView(titleView)

        // Spacer
        addView(spacer(dp(6)))

        // Engine badge
        engineBadge = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.rgb(207, 174, 126))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 200, 170, 130))
                cornerRadius = dp(5).toFloat()
                setStroke(dp(1), Color.argb(50, 200, 170, 130))
            }
        }
        addView(engineBadge)

        // Spacer
        addView(spacer(dp(14)))

        // Action buttons row
        val actionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }

        playButton = makeButton("▶ PLAY", Color.rgb(140, 220, 140), Color.argb(40, 80, 160, 80))
        actionRow.addView(playButton)

        actionRow.addView(spacer(dp(12), 0))

        settingsButton = makeButton("⚙️ SETTINGS", Color.rgb(200, 180, 150), Color.argb(40, 160, 140, 100))
        actionRow.addView(settingsButton)

        addView(actionRow)

        visibility = GONE
    }

    fun bind(game: GameCardInfo) {
        currentGame = game
        titleView.text = game.displayName
        engineBadge.text = game.engineType.label
        visibility = VISIBLE
    }

    fun setOnPlayListener(callback: (String) -> Unit) {
        playButton.setOnClickListener {
            currentGame?.let { callback(it.storageName) }
        }
    }

    fun setOnSettingsListener(callback: (String) -> Unit) {
        settingsButton.setOnClickListener {
            currentGame?.let { callback(it.storageName) }
        }
    }

    private fun makeButton(label: String, textColor: Int, bgColor: Int): TextView =
        TextView(context).apply {
            text = label; setTextColor(textColor); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                setColor(bgColor); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(50, 160, 140, 110))
            }
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun spacer(h: Int, w: Int = 0): View =
        View(context).apply { layoutParams = LayoutParams(dp(w), dp(h)) }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
