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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

data class ImportProgressView(
    val root: LinearLayout,
    val phaseView: TextView,
    val fileView: TextView,
    val countView: TextView,
)

class ImportProgressScreen(private val context: Context) {

    fun create(title: String): ImportProgressView {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(3, 3, 4))
        }

        // Glass card
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 12, 11, 16))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.argb(70, 160, 140, 110))
            }
            alpha = 0f
            animate().alpha(1f).setDuration(350)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
        }
        root.addView(card, LinearLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.85f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT))

        // Title
        card.addView(TextView(context).apply {
            text = title
            setTextColor(Color.rgb(232, 229, 220)); textSize = 22f
            typeface = Typeface.create("serif", Typeface.BOLD); gravity = Gravity.CENTER
        })

        card.addView(spacer(20))

        // Indeterminate progress bar
        card.addView(ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        card.addView(spacer(24))

        // Phase text
        val phaseView = TextView(context).apply {
            text = "Starting..."
            setTextColor(Color.rgb(207, 174, 126)); textSize = 16f
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(phaseView)

        card.addView(spacer(10))

        // Current file
        val fileView = TextView(context).apply {
            text = ""
            setTextColor(Color.argb(160, 180, 175, 160)); textSize = 12f
            gravity = Gravity.CENTER; maxLines = 1
        }
        card.addView(fileView)

        card.addView(spacer(6))

        // File count
        val countView = TextView(context).apply {
            text = ""
            setTextColor(Color.rgb(140, 130, 112)); textSize = 13f
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(countView)

        card.addView(spacer(16))

        // Hint
        card.addView(TextView(context).apply {
            text = "Keep the app open while files are copied."
            setTextColor(Color.argb(100, 100, 95, 85)); textSize = 11f; gravity = Gravity.CENTER
        })

        return ImportProgressView(root, phaseView, fileView, countView)
    }

    private fun spacer(h: Int): View = com.runestone.app.ui.UiKit.spacer(context, h)

    private fun dp(v: Int): Int = com.runestone.app.ui.UiKit.dp(context, v)
}
