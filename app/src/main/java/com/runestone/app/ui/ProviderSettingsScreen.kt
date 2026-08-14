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
import com.runestone.app.provider.ProviderSource
import com.runestone.app.provider.SourcesManager

class ProviderSettingsScreen(private val context: Context) {

    fun create(
        sources: List<ProviderSource>,
        onBack: () -> Unit,
        onUsePublicCatalogue: () -> Unit,
        onManageSources: () -> Unit,
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

        content.addView(sectionTitle("Game Catalogue", "Browse downloadable games from trusted JSON sources."))
        content.addView(publicCataloguePanel(sources, onUsePublicCatalogue, onManageSources))
        content.addView(spacer(dp(16)))

        content.addView(sectionTitle("Current Sources", "Active catalogue URLs used by the Store."))
        content.addView(currentSourcesPanel(sources, onManageSources))
        content.addView(spacer(dp(16)))

        // How to get a catalogue
        content.addView(sectionTitle("Source Format", "Paste a raw JSON URL or REST API endpoint."))
        content.addView(helpPanel())
        content.addView(spacer(dp(16)))

        // Danger zone
        content.addView(sectionTitle("Danger Zone", "Irreversible actions."))
        content.addView(dangerPanel(onClearAll))

        return root
    }

    private fun publicCataloguePanel(
        sources: List<ProviderSource>,
        onUsePublicCatalogue: () -> Unit,
        onManageSources: () -> Unit,
    ): LinearLayout {
        val hasPublicCatalogue = sources.any { it.url == SourcesManager.DEFAULT_PUBLIC_CATALOGUE_URL }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassBg(dp(14), accent = true)

            addView(TextView(context).apply {
                text = "Runestone Public Catalogue"
                setTextColor(TEXT); textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = if (hasPublicCatalogue) {
                    "The public catalogue is already enabled."
                } else {
                    "Add the maintained public JSON catalogue as a source."
                }
                setTextColor(MUTED); textSize = 11f
                setPadding(0, dp(5), 0, dp(10))
            })

            addView(actionButton(
                text = if (hasPublicCatalogue) "PUBLIC CATALOGUE ENABLED" else "USE PUBLIC CATALOGUE",
                accent = true,
                enabled = !hasPublicCatalogue,
                onClick = onUsePublicCatalogue,
            ))
            addView(spacer(dp(8)))
            addView(actionButton("MANAGE SOURCES", accent = false, enabled = true, onClick = onManageSources))
        }
    }

    private fun currentSourcesPanel(
        sources: List<ProviderSource>,
        onManageSources: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glassBg(dp(14))

            if (sources.isEmpty()) {
                addView(TextView(context).apply {
                    text = "No user-added sources."
                    setTextColor(MUTED); textSize = 12f
                })
                addView(TextView(context).apply {
                    text = "The bundled local catalogue still appears in Store when available."
                    setTextColor(MUTED_DIM); textSize = 11f
                    setPadding(0, dp(4), 0, dp(10))
                })
            } else {
                sources.forEachIndexed { index, source ->
                    addView(TextView(context).apply {
                        text = source.name
                        setTextColor(TEXT); textSize = 13f
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                    })
                    addView(TextView(context).apply {
                        text = source.url
                        setTextColor(MUTED_DIM); textSize = 10f
                        maxLines = 2
                        setPadding(0, dp(3), 0, if (index == sources.lastIndex) dp(10) else dp(12))
                    })
                }
            }

            addView(actionButton("EDIT SOURCE URLS", accent = false, enabled = true, onClick = onManageSources))
        }

    private fun actionButton(
        text: String,
        accent: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(if (enabled) if (accent) Color.rgb(220, 200, 160) else ACCENT else MUTED_DIM)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isEnabled = enabled
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = glassBg(dp(8), alpha = if (enabled) 120 else 55, accent = accent)
            setOnClickListener {
                if (!enabled) return@setOnClickListener
                animTap(this)
                onClick()
            }
            makeLiquid(this)
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
        com.runestone.app.ui.theme.ThemeProvider.getInstance(context).glassBg(radius, alpha, accent)

    private fun makeLiquid(view: View) { com.runestone.app.ui.UiKit.makeLiquid(view) }

    private fun animTap(v: View) { com.runestone.app.ui.UiKit.animTap(v) }

    private fun spacer(h: Int): View = com.runestone.app.ui.UiKit.spacer(context, h)

    private fun dp(v: Int): Int = com.runestone.app.ui.UiKit.dp(context, v)

    private companion object {
        val TEXT: Int get() = Theme.TEXT
        val MUTED: Int get() = Theme.MUTED
        val MUTED_DIM: Int get() = Theme.MUTED_DIM
        val ACCENT: Int get() = Theme.active.accent
    }
}
