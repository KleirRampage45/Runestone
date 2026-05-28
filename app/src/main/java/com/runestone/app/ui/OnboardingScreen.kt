/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * First-time onboarding welcome screen.
 */

package com.runestone.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class OnboardingScreen(context: Context, private val onDismiss: () -> Unit) : FrameLayout(context) {

    init {
        setBackgroundColor(Color.argb(220, 3, 3, 4))

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        addView(panel, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        panel.addView(text("Welcome to Runestone", 24f, Color.rgb(207, 174, 126),
            Typeface.create("serif", Typeface.BOLD)))
        panel.addView(space(dp(8)))
        panel.addView(text("Multi-engine game launcher for Android", 14f, Color.rgb(160, 150, 130)))
        panel.addView(space(dp(24)))

        val cards = listOf(
            "\uD83D\uDCC1" to "Import games from your device storage using the + button",
            "\uD83C\uDFAE" to "Supports RPG Maker (XP/VX/VX Ace/MV/MZ), Ren'Py, and more",
            "\uD83D\uDD8A\uFE0F" to "Customize controls per game — drag to reposition, pinch to resize",
            "\uD83C\uDFA7" to "Screen filters, speed controls, cheats, and controller mapping",
            "\uD83D\uDCBE" to "Your saves are protected in a separate folder — no data loss",
        )

        for ((icon, desc) in cards) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(TextView(context).apply {
                text = icon; textSize = 28f
                layoutParams = LinearLayout.LayoutParams(dp(40), WRAP)
            })
            row.addView(TextView(context).apply {
                text = desc; setTextColor(Color.rgb(200, 195, 180))
                textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            panel.addView(row)
        }

        panel.addView(space(dp(28)))

        val getStarted = TextView(context).apply {
            text = "GET STARTED"
            setTextColor(Color.rgb(220, 200, 160)); textSize = 15f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(100, 200, 170, 130)); cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.argb(120, 220, 190, 140))
            }
            setOnClickListener { onDismiss() }
        }
        panel.addView(getStarted)
    }

    private fun text(t: String, s: Float, c: Int, tf: Typeface = Typeface.DEFAULT) =
        TextView(context).apply { text = t; setTextColor(c); textSize = s; typeface = tf; gravity = Gravity.CENTER }

    private fun space(h: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(MATCH, h) }
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    private companion object { val MATCH = ViewGroup.LayoutParams.MATCH_PARENT; val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT }
}
