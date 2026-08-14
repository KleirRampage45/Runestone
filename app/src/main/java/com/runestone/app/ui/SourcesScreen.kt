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
import android.net.Uri
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.provider.ProviderSource
import com.runestone.app.provider.SourceStatus
import com.runestone.app.provider.SourcesManager

class SourcesScreen(private val context: Context) {

    fun create(
        sources: List<ProviderSource>,
        onAddSource: (String) -> Unit,
        onRemoveSource: (String) -> Unit,
        onBack: () -> Unit,
    ): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(252, 3, 3, 4))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(makeTopBar(onBack))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(26))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
        }
        root.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(34))
        }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        if (sources.isEmpty()) {
            content.addView(spacer(dp(36)))
            content.addView(TextView(context).apply {
                text = "No sources added yet"
                setTextColor(MUTED); textSize = 16f; gravity = Gravity.CENTER
            })
            content.addView(TextView(context).apply {
                text = "Add game sources to browse available games"
                setTextColor(MUTED_DIM); textSize = 12f; gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
            content.addView(spacer(dp(6)))
            content.addView(TextView(context).apply {
                text = "Sources are HTTPS URLs that point to JSON game catalogs.\nOnly add repositories you trust and are authorized to use."
                setTextColor(MUTED_DIM); textSize = 11f; gravity = Gravity.CENTER
                setPadding(dp(16), dp(4), dp(16), 0)
                setLineSpacing(2f, 1f)
            })
            content.addView(spacer(dp(20)))
            // Use Public Catalogue button
            content.addView(TextView(context).apply {
                text = "USE PUBLIC CATALOGUE"; setTextColor(Color.rgb(140, 220, 160)); textSize = 13f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 80, 160, 80)); cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.argb(60, 100, 200, 120))
                }
                setOnClickListener {
                    animTap(this)
                    onAddSource(DEFAULT_CATALOGUE_URL)
                }
                makeLiquid(this)
            }, LinearLayout.LayoutParams(
                (context.resources.displayMetrics.widthPixels * 0.8f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER })
        } else {
            sources.forEach { source ->
                content.addView(sourceRow(source, onRemoveSource))
                content.addView(spacer(dp(8)))
            }
        }

        content.addView(spacer(dp(16)))
        content.addView(addSourceButton(onAddSource))

        return root
    }

    private fun makeTopBar(onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(Color.TRANSPARENT)

            addView(TextView(context).apply {
                text = "Back"
                setTextColor(ACCENT); textSize = 15f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                background = glassBg(dp(22), alpha = 55)
                setOnClickListener { onBack() }
                makeLiquid(this)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)))

            addView(TextView(context).apply {
                text = "Sources"
                setTextColor(TEXT); textSize = 22f
                letterSpacing = 0f; gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun sourceRow(source: ProviderSource, onRemove: (String) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = glassBg(dp(14), alpha = 205)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // Status indicator
        val statusColor = when (source.status) {
            SourceStatus.ACTIVE -> Color.rgb(100, 200, 100)
            SourceStatus.FAILED -> Color.rgb(200, 100, 100)
            SourceStatus.PENDING -> Color.rgb(200, 180, 100)
        }
        val statusDot = TextView(context).apply {
            text = "\u25CF"; setTextColor(statusColor); textSize = 11f
            setPadding(0, 0, dp(10), 0)
        }
        row.addView(statusDot)

        // Name + URL
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        info.addView(TextView(context).apply {
            text = sourceDisplayName(source)
            setTextColor(TEXT); textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        info.addView(TextView(context).apply {
            text = source.url
            setTextColor(MUTED_DIM); textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Status badge
        row.addView(TextView(context).apply {
            text = source.status.name.lowercase().replaceFirstChar { it.uppercase() }
            setTextColor(statusColor); textSize = 9f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor)))
                cornerRadius = dp(7).toFloat()
            }
        })

        // Remove button
        row.addView(spacer(dp(8)))
        row.addView(TextView(context).apply {
            text = "X"; setTextColor(Color.rgb(200, 120, 120)); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 200, 80, 80)); cornerRadius = dp(6).toFloat()
            }
            setOnClickListener {
                animTap(this)
                onRemove(source.id)
            }
            makeLiquid(this)
        })

        return row
    }

    private fun addSourceButton(onAdd: (String) -> Unit): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // Add Source dialog (hidden initially)
        val addDialog = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = glassBg(dp(14), alpha = 215, accent = true)
            visibility = View.GONE
        }

        val urlInput = EditText(context).apply {
            hint = "Source URL (e.g. https://example.com/games.json)"
            setHintTextColor(Color.argb(130, 200, 180, 130))
            setTextColor(TEXT); textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1; background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        addDialog.addView(urlInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addDialog.addView(spacer(dp(10)))

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        btnRow.addView(TextView(context).apply {
            text = "ADD"; setTextColor(Color.rgb(220, 200, 160)); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = glassBg(dp(8), alpha = 120, accent = true)
            setOnClickListener {
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    onAdd(url)
                    urlInput.setText("")
                    addDialog.visibility = View.GONE
                }
            }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        })
        btnRow.addView(TextView(context).apply {
            text = "CANCEL"; setTextColor(MUTED); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(20), dp(8), dp(20), dp(8))
            background = glassBg(dp(8), alpha = 60)
            setOnClickListener {
                urlInput.setText("")
                addDialog.visibility = View.GONE
            }
            makeLiquid(this)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        })
        addDialog.addView(btnRow)
        container.addView(addDialog)

        // Toggle button
        val toggleBtn = TextView(context).apply {
            text = "+ ADD SOURCE"; setTextColor(Color.rgb(220, 200, 160)); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = glassBg(dp(22), alpha = 55)
            setOnClickListener {
                animTap(this)
                addDialog.visibility = if (addDialog.visibility == View.GONE) View.VISIBLE else View.GONE
            }
            makeLiquid(this)
        }
        container.addView(toggleBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        return container
    }

    private fun sourceDisplayName(source: ProviderSource): String {
        val trimmed = source.name.trim()
        if (trimmed.isNotBlank()) return trimmed
        val host = runCatching { Uri.parse(source.url).host.orEmpty() }.getOrDefault("")
            .removePrefix("www.")
        return when {
            host.isNotBlank() -> host
            source.url.isNotBlank() -> source.url
            else -> "Catalogue source"
        }
    }

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        com.runestone.app.ui.theme.ThemeProvider.getInstance(context).glassBg(radius, alpha, accent)

    private fun makeLiquid(view: View) { com.runestone.app.ui.UiKit.makeLiquid(view) }

    private fun animTap(v: View) { com.runestone.app.ui.UiKit.animTap(v) }

    private fun spacer(h: Int): View = com.runestone.app.ui.UiKit.spacer(context, h)

    private fun dp(v: Int): Int = com.runestone.app.ui.UiKit.dp(context, v)

    private companion object {
        const val DEFAULT_CATALOGUE_URL = SourcesManager.DEFAULT_PUBLIC_CATALOGUE_URL
        val TEXT: Int get() = Theme.TEXT
        val MUTED: Int get() = Theme.MUTED
        val MUTED_DIM: Int get() = Theme.MUTED_DIM
        val ACCENT: Int get() = Theme.active.accent
    }
}
