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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    val bgColor = Color.rgb(15, 14, 16)
    val panel = Color.rgb(34, 32, 36)
    val panelAlt = Color.rgb(44, 39, 43)
    val textColor = Color.rgb(237, 233, 224)
    val mutedTextColor = Color.rgb(170, 164, 154)
    val accent = Color.rgb(145, 31, 43)

    val homeBg = Color.rgb(3, 3, 4)
    val homeText = Color.rgb(232, 229, 220)
    val homeMuted = Color.rgb(151, 143, 132)
    val homeAccent: Int get() = Theme.active.accent

    fun vertical(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(bgColor)
        }

    fun title(context: Context, value: String): TextView =
        TextView(context).apply {
            text = value
            setTextColor(textColor)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }

    fun label(context: Context, value: String): TextView =
        TextView(context).apply {
            text = value
            setTextColor(mutedTextColor)
            textSize = 15f
        }

    fun button(context: Context, value: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = value
            setTextColor(textColor)
            setBackgroundColor(accent)
            setOnClickListener { onClick() }
        }

    fun spacer(context: Context, height: Int = 18): View =
        View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
            )
        }

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
