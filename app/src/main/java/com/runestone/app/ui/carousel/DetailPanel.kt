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
import android.widget.LinearLayout
import android.widget.TextView
import com.runestone.app.ui.GameCardInfo

class DetailPanel(context: Context) : LinearLayout(context) {

    private val titleView: TextView
    private val engineBadge: TextView
    private val fileCountView: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20), dp(6), dp(20), dp(8))

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
        addView(spacer(4))

        // Engine badge
        engineBadge = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.rgb(238, 207, 158))
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

        fileCountView = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.argb(160, 180, 160, 130))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        addView(fileCountView)

        visibility = GONE
    }

    fun bind(game: GameCardInfo) {
        titleView.text = game.displayName
        engineBadge.text = game.engineType.label
        fileCountView.text = "${game.fileCount} files"
        visibility = VISIBLE
    }

    private fun spacer(h: Int): View =
        View(context).apply { layoutParams = LayoutParams(0, dp(h)) }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
