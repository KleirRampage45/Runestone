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
                text = "Game Sources"
                setTextColor(TEXT); textSize = 21f
                letterSpacing = 0.5f; gravity = Gravity.CENTER
                typeface = Typeface.create("serif", Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(View(context), LinearLayout.LayoutParams(dp(84), 1))
        }

    private fun sourceRow(source: ProviderSource, onRemove: (String) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = glassBg(dp(12))
        }

        // Status indicator
        val statusColor = when (source.status) {
            SourceStatus.ACTIVE -> Color.rgb(100, 200, 100)
            SourceStatus.FAILED -> Color.rgb(200, 100, 100)
            SourceStatus.PENDING -> Color.rgb(200, 180, 100)
        }
        val statusDot = TextView(context).apply {
            text = "\u25CF"; setTextColor(statusColor); textSize = 10f
            setPadding(0, 0, dp(8), 0)
        }
        row.addView(statusDot)

        // Name + URL
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        info.addView(TextView(context).apply {
            text = source.name; setTextColor(TEXT); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; maxLines = 1
        })
        val displayUrl = if (source.url.length > 40) source.url.take(40) + "..." else source.url
        info.addView(TextView(context).apply {
            text = displayUrl; setTextColor(MUTED_DIM); textSize = 11f
            maxLines = 1; setPadding(0, dp(2), 0, 0)
        })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Status badge
        row.addView(TextView(context).apply {
            text = source.status.name
            setTextColor(statusColor); textSize = 9f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor)))
                cornerRadius = dp(4).toFloat()
            }
        })

        // Remove button
        row.addView(spacer(dp(8)))
        row.addView(TextView(context).apply {
            text = "X"; setTextColor(Color.rgb(200, 120, 120)); textSize = 14f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
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
        }

        // Add Source dialog (hidden initially)
        val addDialog = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = glassBg(dp(14), accent = true)
            visibility = View.GONE
        }

        val urlInput = EditText(context).apply {
            hint = "Source URL (e.g. https://example.com/games.json)"
            setHintTextColor(Color.argb(80, 200, 180, 130))
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
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = glassBg(dp(10), alpha = 120, accent = true)
            setOnClickListener {
                animTap(this)
                addDialog.visibility = if (addDialog.visibility == View.GONE) View.VISIBLE else View.GONE
            }
            makeLiquid(this)
        }
        container.addView(toggleBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return container
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
        const val DEFAULT_CATALOGUE_URL = "https://kleirrampage45.github.io/runestone-catalogue/games.json"
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(100, 95, 85)
        val ACCENT: Int get() = Theme.active.accent
    }
}
