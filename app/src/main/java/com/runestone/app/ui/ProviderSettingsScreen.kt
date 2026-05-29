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
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.provider.SourcesManager

class ProviderSettingsScreen(private val context: Context) {

    fun create(
        sourcesManager: SourcesManager,
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

        // API URL section
        content.addView(sectionTitle("Provider API", "Backend server URL for fetching game catalogs."))
        content.addView(apiUrlPanel(sourcesManager))
        content.addView(spacer(dp(16)))

        // Default Sources section
        content.addView(sectionTitle("Default Sources", "Pre-configured game source URLs."))
        content.addView(defaultSourcesPanel(sourcesManager))
        content.addView(spacer(dp(16)))

        // Danger zone
        content.addView(sectionTitle("Danger Zone", "Irreversible actions."))
        content.addView(dangerPanel(onClearAll))
        content.addView(spacer(dp(16)))

        // Info
        content.addView(sectionTitle("How It Works", ""))
        content.addView(infoPanel())

        return root
    }

    private fun makeTopBar(onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
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
                letterSpacing = 0.5f; gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.BOLD)
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

    private fun apiUrlPanel(sourcesManager: SourcesManager): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassBg(dp(14))
        }

        val currentUrl = sourcesManager.getApiUrl()
        val urlInput = EditText(context).apply {
            hint = "https://api.example.com"
            setHintTextColor(Color.argb(80, 200, 180, 130))
            setTextColor(TEXT); textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1; background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setText(currentUrl)
        }

        val inputBg = GradientDrawable().apply {
            setColor(Color.argb(30, 255, 255, 255)); cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.argb(30, 200, 180, 150))
        }
        val inputWrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = inputBg
            setPadding(dp(6), dp(2), dp(6), dp(2))
            addView(urlInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        panel.addView(inputWrapper)
        panel.addView(spacer(dp(10)))

        panel.addView(TextView(context).apply {
            text = "SAVE URL"; setTextColor(Color.rgb(220, 200, 160)); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = glassBg(dp(8), alpha = 120, accent = true)
            setOnClickListener {
                animTap(this)
                sourcesManager.setApiUrl(urlInput.text.toString())
            }
            makeLiquid(this)
        })

        return panel
    }

    private fun defaultSourcesPanel(sourcesManager: SourcesManager): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassBg(dp(14))
        }

        val defaults = listOf(
            "Community RPG Maker Games" to "https://rpg-games.example.com",
            "Indie Game Catalog" to "https://indie-catalog.example.com",
        )

        defaults.forEach { (name, url) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.argb(15, 255, 255, 255)); cornerRadius = dp(8).toFloat()
                }
            }
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = name; setTextColor(TEXT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(context).apply {
                    text = url; setTextColor(MUTED_DIM); textSize = 10f
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(TextView(context).apply {
                text = "ADD"; setTextColor(ACCENT); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.argb(30, 207, 174, 126)); cornerRadius = dp(6).toFloat()
                    setStroke(dp(1), Color.argb(40, 207, 174, 126))
                }
                setOnClickListener {
                    animTap(this)
                    sourcesManager.addSource(url)
                }
                makeLiquid(this)
            })

            panel.addView(row)
            panel.addView(spacer(dp(6)))
        }

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
            text = "Clear All Sources"
            setTextColor(Color.rgb(200, 140, 140)); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(TextView(context).apply {
            text = "Remove all configured sources. This cannot be undone."
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

    private fun infoPanel(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassBg(dp(14))
        }

        panel.addView(TextView(context).apply {
            text = """
The Provider System lets you browse and download games from configured sources.

\u2022 API Server — A backend that aggregates game catalogs from multiple sources
\u2022 Sources — URLs pointing to individual game catalogs
\u2022 The app fetches available games from each source via the API

To set up your own provider:
1. Deploy a compatible API server
2. Enter the API URL above
3. Add source URLs that the API can query

Status indicators:
\u25CF Green = source active and reachable
\u25CF Yellow = source pending (not yet checked)
\u25CF Red = source unreachable or failed
            """.trimIndent()
            setTextColor(MUTED); textSize = 11f
            setPadding(0, dp(2), 0, dp(2))
            setLineSpacing(2f, 1f)
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

    private fun makeLiquid(view: View) {
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

    private fun animTap(v: View) {
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
        val MUTED_DIM = Color.rgb(100, 95, 85)
        val ACCENT = Color.rgb(207, 174, 126)
    }
}
