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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
class ProviderSettingsScreen(private val context: Context) {

    fun create(
        onBack: () -> Unit,
        onClearAll: () -> Unit,
    ): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(makeTopBar(onBack))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        }
        root.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll.addView(content)

        content.alpha = 0f
        content.animate().alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.1f)).start()

        // How to get a catalogue
        content.addView(sectionTitle("Source Format", "Sources are added from the Game Sources screen."))
        content.addView(helpPanel())
        content.addView(spacer(dp(16)))

        // Danger zone
        content.addView(sectionTitle("Danger Zone", "Irreversible actions."))
        content.addView(dangerPanel(onClearAll))

        return root
    }

    private fun makeTopBar(onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(15, 14, 18))

            addView(TextView(context).apply {
                text = "Back"
                setTextColor(ACCENT); textSize = 15f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 207, 174, 126))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.argb(60, 207, 174, 126))
                }
                setOnClickListener { onBack() }
                makeLiquid(this)
            }, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT))

            addView(TextView(context).apply {
                text = "Provider Settings"
                setTextColor(TEXT); textSize = 21f
                letterSpacing = 0.02f; gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.BOLD)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(View(context), LinearLayout.LayoutParams(dp(84), 1))
        }

    private fun sectionTitle(title: String, detail: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(TextView(context).apply {
                text = title; setTextColor(TEXT); textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
            })
            if (detail.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = detail; setTextColor(MUTED); textSize = 12f
                    setPadding(0, dp(3), 0, 0)
                })
            }
        }

    private fun helpPanel(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassBg(dp(14))
        }

        panel.addView(TextView(context).apply {
            text = """
Runestone loads games from user-added HTTPS JSON sources.

- The app does not include or recommend source URLs
- Add sources from Available Games > Manage Sources
- Each source is a JSON file with a "games" array
- Use only files you are authorized to distribute or download

Required fields: id, title, engine, and either downloadUrl or downloadOptions.
Download URLs must use HTTPS.

Example:
{"games":[{"id":"demo","title":"Demo","engine":"mv","downloadUrl":"https://example.com/demo.zip"}]}
            """.trimIndent()
            setTextColor(MUTED); textSize = 11f
            setPadding(0, dp(2), 0, dp(2))
            setLineSpacing(2f, 1f)
        })

        return panel
    }

    private fun dangerPanel(onClearAll: () -> Unit): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 60, 20, 20)); cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(40, 200, 80, 80))
            }
        }

        panel.addView(TextView(context).apply {
            text = "Clear Sources"
            setTextColor(Color.rgb(200, 140, 140)); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(TextView(context).apply {
            text = "Remove every user-added source URL. This cannot be undone."
            setTextColor(MUTED); textSize = 11f
            setPadding(0, dp(4), 0, dp(10))
        })
        panel.addView(TextView(context).apply {
            text = "CLEAR ALL"; setTextColor(Color.rgb(220, 160, 160)); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 200, 80, 80)); cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.argb(50, 200, 80, 80))
            }
            setOnClickListener {
                animTap(this)
                onClearAll()
            }
            makeLiquid(this)
        })

        return panel
    }

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha,
                if (accent) 50 else 22, if (accent) 40 else 20, if (accent) 30 else 26))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(if (accent) 80 else 45,
                if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
        }

    private fun makeLiquid(view: View) { if (Theme.isReducedMotion(context)) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f; val cy = v.height / 2f
                    v.translationX = (event.x - cx) * 0.06f
                    v.translationY = (event.y - cy) * 0.06f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                        .setDuration(250).setInterpolator(OvershootInterpolator(1.6f)).start()
                }
            }
            false
        }
    }

    private fun animTap(v: View) { if (Theme.isReducedMotion(context)) return
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
        val MUTED_DIM = Color.rgb(120, 112, 104)
        val ACCENT: Int get() = Theme.active.accent
    }
}
