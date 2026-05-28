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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
            setBackgroundColor(Color.rgb(8, 8, 10))
        }

        // Title
        root.addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE); textSize = 24f
            typeface = Typeface.create("serif", Typeface.BOLD); gravity = Gravity.CENTER
        })

        root.addView(spacer(20))

        // Indeterminate progress bar (spins)
        root.addView(ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        root.addView(spacer(28))

        // Phase text (e.g. "Copying game files...")
        val phaseView = TextView(context).apply {
            text = "Starting..."
            setTextColor(Color.argb(210, 200, 170, 130)); textSize = 17f
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(phaseView)

        root.addView(spacer(12))

        // Current file being copied
        val fileView = TextView(context).apply {
            text = ""
            setTextColor(Color.argb(160, 180, 175, 160)); textSize = 12f
            gravity = Gravity.CENTER; maxLines = 1
        }
        root.addView(fileView)

        root.addView(spacer(8))

        // File count
        val countView = TextView(context).apply {
            text = ""
            setTextColor(Color.argb(120, 200, 190, 170)); textSize = 13f
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(countView)

        root.addView(spacer(28))

        // Hint
        root.addView(TextView(context).apply {
            text = "Keep the app open while files are copied."
            setTextColor(Color.argb(70, 170, 164, 154)); textSize = 11f; gravity = Gravity.CENTER
        })

        return ImportProgressView(root, phaseView, fileView, countView)
    }

    private fun spacer(h: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
    }
}
