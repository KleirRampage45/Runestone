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
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.runestone.app.ui.GameCardInfo

class InspectOverlay(
    context: Context,
    private val game: GameCardInfo,
    private val onPlay: (String) -> Unit,
    private val onSettings: (String) -> Unit,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    init {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // Dim background — tap to dismiss
        val dim = View(context).apply {
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        addView(dim, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        dim.animate().alpha(1f).setDuration(250).start()

        // Action panel — glass card at bottom-center
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.argb(230, 12, 11, 16))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(207, 174, 126))
            }
            translationY = 200f
            alpha = 0f
        }

        // Game title
        panel.addView(TextView(context).apply {
            text = game.displayName
            setTextColor(Color.rgb(232, 229, 220))
            textSize = 22f
            typeface = Typeface.create("serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        // Engine badge
        val badge = TextView(context).apply {
            text = game.engineType.label
            setTextColor(Color.rgb(207, 174, 126))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 200, 170, 130))
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), Color.argb(50, 200, 170, 130))
            }
        }
        panel.addView(badge.apply { setPadding(0, dp(10), 0, dp(4)) })

        // File count
        panel.addView(TextView(context).apply {
            text = "${game.fileCount} files"
            setTextColor(Color.argb(140, 180, 160, 130))
            textSize = 12f
            setPadding(0, dp(10), 0, dp(4))
        })

        // Spacer
        panel.addView(spacer(context, dp(16)))

        // Action buttons
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        btnRow.addView(makeActionBtn(
            ctx = context,
            label = "▶ PLAY",
            textColor = Color.rgb(140, 220, 140),
            bgColor = Color.argb(40, 80, 160, 80),
        ) {
            onPlay(game.storageName)
            dismiss()
        })

        btnRow.addView(spacer(context, 0, dp(12)))

        btnRow.addView(makeActionBtn(
            ctx = context,
            label = "⚙ SETTINGS",
            textColor = Color.rgb(200, 180, 150),
            bgColor = Color.argb(40, 160, 140, 100),
        ) {
            onSettings(game.storageName)
            dismiss()
        })

        panel.addView(btnRow)

        // Spacer
        panel.addView(spacer(context, dp(6)))

        // Cancel hint
        panel.addView(TextView(context).apply {
            text = "Tap anywhere to close"
            setTextColor(Color.argb(80, 180, 160, 130))
            textSize = 11f
            gravity = Gravity.CENTER
        })

        // Add panel centered horizontally, at ~40% from bottom
        val panelLp = LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.85f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
        )
        panelLp.bottomMargin = dp(120)
        addView(panel, panelLp)

        // Animate panel in — slide up with overshoot
        panel.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    private fun dismiss() {
        animate().alpha(0f).setDuration(200).withEndAction {
            val parent = parent as? ViewGroup
            parent?.removeView(this)
            onDismiss()
        }.start()
    }

    companion object {
        private fun dp(v: Int): Int =
            (v * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

        private fun spacer(ctx: Context, h: Int = 0, w: Int = 0): View =
            View(ctx).apply {
                layoutParams = LayoutParams(dp(w), dp(h))
            }

        private fun makeActionBtn(
            ctx: Context,
            label: String,
            textColor: Int,
            bgColor: Int,
            onClick: () -> Unit,
        ): TextView = TextView(ctx).apply {
            text = label
            setTextColor(textColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.argb(60, 160, 140, 110))
            }
            layoutParams = LayoutParams(dp(150), dp(44))
            setOnClickListener { onClick() }
        }
    }
}
