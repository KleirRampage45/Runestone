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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.runestone.app.ui.GameCardInfo

class CarouselGameCard(context: Context) : FrameLayout(context) {

    private val titleView: TextView
    private val engineBadge: TextView

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        // Card background — glass style matching existing UI
        background = GradientDrawable().apply {
            setColor(Color.argb(220, 12, 11, 16))
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.argb(60, 207, 174, 126))
        }

        // Content layout
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        // Game title
        titleView = TextView(context).apply {
            textSize = 18f
            setTextColor(Color.rgb(220, 210, 200))
            typeface = Typeface.create("serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 3
        }
        inner.addView(titleView)

        // Spacer
        inner.addView(TextView(context).apply {
            height = dp(12)
        })

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
        inner.addView(engineBadge)

        addView(inner)
    }

    fun bind(game: GameCardInfo) {
        titleView.text = game.displayName
        engineBadge.text = game.engineType.label
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
