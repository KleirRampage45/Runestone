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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings

class SettingsScreen(private val context: Context) {

    fun create(
        settings: RunnerSettings,
        onSettingsChanged: (RunnerSettings) -> Unit,
        onBack: () -> Unit,
    ): LinearLayout {
        var current = settings.copy(textScale = 1.0f)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll.addView(content)

        // Layout Mode
        content.addView(sectionTitle("Play Layout", "Choose how the phone becomes the handheld."))
        content.addView(
            layoutSelector(current.layoutMode) { selected ->
                current = current.copy(layoutMode = selected)
                onSettingsChanged(current)
            },
        )

        content.addView(spacer(14))

        // Touch Controls
        content.addView(sectionTitle("Touch Controls", "Tune the virtual controller."))
        content.addView(sliderPanel("Touch Opacity", "${(current.touchOpacity * 100).toInt()}%") { label ->
            slider(100, (current.touchOpacity * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(touchOpacity = progress / 100f)
                label.text = "${(current.touchOpacity * 100).toInt()}%"
                onSettingsChanged(current)
            }
        })
        content.addView(spacer(10))
        content.addView(sliderPanel("Touch Scale", "${(current.touchScale * 100).toInt()}%") { label ->
            slider(100, ((current.touchScale - 0.5f) * 200).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(touchScale = 0.5f + (progress / 200f))
                label.text = "${(current.touchScale * 100).toInt()}%"
                onSettingsChanged(current)
            }
        })
        content.addView(spacer(10))
        content.addView(switchPanel("Haptic Feedback", "Vibrate when virtual controls are pressed.", current.hapticsEnabled) { checked ->
            current = current.copy(hapticsEnabled = checked)
            onSettingsChanged(current)
        })
        content.addView(spacer(10))
        content.addView(sliderPanel("Haptic Intensity", "${(current.hapticIntensity * 100).toInt()}%") { label ->
            slider(100, (current.hapticIntensity * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(hapticIntensity = progress / 100f)
                label.text = "${(current.hapticIntensity * 100).toInt()}%"
                onSettingsChanged(current)
            }
        })
        content.addView(spacer(10))
        content.addView(switchPanel("Show X/Y Buttons", "Extra RPG Maker keys. Usually unnecessary.", current.showExtraButtons) { checked ->
            current = current.copy(showExtraButtons = checked)
            onSettingsChanged(current)
        })

        content.addView(spacer(14))

        // Audio
        content.addView(sectionTitle("Audio", "WebView game audio settings."))
        content.addView(
            audioSelector(current.forceAudioExt) { ext ->
                current = current.copy(forceAudioExt = ext)
                onSettingsChanged(current)
            },
        )

        content.addView(spacer(16))
        content.addView(sectionTitle("Help & About", "Learn how Runestone works."))
        content.addView(expandableButton("HELP — How to use Runestone", ::makeHelpContent))
        content.addView(spacer(8))
        content.addView(expandableButton("ABOUT — Version & license", ::makeAboutContent))

        return root
    }

    private fun makeTopBar(onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(15, 14, 18))

            addView(
                TextView(context).apply {
                    text = "Back"
                    setTextColor(ACCENT)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(8), dp(12), dp(8))
                    setOnClickListener { onBack() }
                },
                LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            addView(
                TextView(context).apply {
                    text = "Runestone Setup"
                    setTextColor(TEXT)
                    textSize = 21f
                    letterSpacing = 0f
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("serif", Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(View(context), LinearLayout.LayoutParams(dp(84), 1))
        }

    private fun layoutSelector(selected: LayoutMode, onSelect: (LayoutMode) -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                twoColumn(
                    layoutCard(LayoutMode.PORTRAIT_CONSOLE, selected, "Portrait Console", "Game above, controls below", onSelect),
                    layoutCard(LayoutMode.LANDSCAPE, selected, "Landscape", "Game fills wide screen", onSelect),
                ),
            )
            addView(spacer(10))
            addView(layoutCard(LayoutMode.GAMEPAD, selected, "Gamepad", "Fullscreen, use controller", onSelect))
        }

    private fun layoutCard(
        mode: LayoutMode,
        selected: LayoutMode,
        title: String,
        detail: String,
        onSelect: (LayoutMode) -> Unit,
    ): LinearLayout =
        settingsPanel {
            setOnClickListener { onSelect(mode) }
            background = panelBackground(
                if (selected == mode) Color.rgb(33, 28, 27) else PANEL,
                stroke = if (selected == mode) ACCENT else Color.argb(48, 255, 255, 255),
            )
            addView(LayoutPreviewView(context, mode), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(122)))
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(TEXT)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                },
            )
            addView(
                TextView(context).apply {
                    text = detail
                    setTextColor(MUTED)
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(4), dp(2), 0)
                },
            )
        }

    private fun audioSelector(currentExt: String, onSelect: (String) -> Unit): LinearLayout =
        settingsPanel {
            addView(
                twoColumn(
                    audioCard(".ogg", "Opus audio", currentExt == ".ogg") { onSelect(".ogg") },
                    audioCard(".m4a", "AAC audio", currentExt == ".m4a") { onSelect(".m4a") },
                ),
            )
        }

    private fun audioCard(label: String, detail: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = "$label\n$detail"
            setTextColor(TEXT)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(60)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = panelBackground(
                if (selected) Color.rgb(33, 28, 27) else PANEL,
                stroke = if (selected) ACCENT else Color.argb(48, 255, 255, 255),
            )
            setOnClickListener { onClick() }
        }

    private fun sectionTitle(title: String, detail: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(TEXT)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                },
            )
            addView(
                TextView(context).apply {
                    text = detail
                    setTextColor(MUTED)
                    textSize = 12f
                    setPadding(0, dp(3), 0, 0)
                },
            )
        }

    private fun twoColumn(left: View, right: View): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(spacer(width = 10))
            addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun settingsPanel(build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = panelBackground(PANEL, stroke = Color.argb(48, 255, 255, 255))
            build()
        }

    private fun sliderPanel(title: String, value: String, sliderFactory: (TextView) -> SeekBar): LinearLayout =
        settingsPanel {
            val label = TextView(context).apply {
                text = value
                setTextColor(ACCENT)
                textSize = 13f
                gravity = Gravity.END
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(context).apply {
                        text = title
                        setTextColor(TEXT)
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(label)
            }
            addView(row)
            addView(sliderFactory(label))
        }

    private fun switchPanel(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout =
        settingsPanel {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val copy = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(context).apply {
                        text = title
                        setTextColor(TEXT)
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                    },
                )
                addView(
                    TextView(context).apply {
                        text = detail
                        setTextColor(MUTED)
                        textSize = 11f
                        setPadding(0, dp(3), dp(10), 0)
                    },
                )
            }
            row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(
                Switch(context).apply {
                    isChecked = checked
                    setOnCheckedChangeListener { _, value -> onChange(value) }
                },
            )
            addView(row)
        }

    private fun slider(max: Int, progress: Int, onChange: (Int) -> Unit): SeekBar =
        SeekBar(context).apply {
            this.max = max
            this.progress = progress
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                        if (fromUser) onChange(value)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                },
            )
        }

    private fun panelBackground(color: Int, stroke: Int = Color.TRANSPARENT, corner: Int = 8): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(corner).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun spacer(height: Int = 0, width: Int = 0): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private class LayoutPreviewView(context: Context, private val mode: LayoutMode) : View(context) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val phone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(9, 9, 11)
            style = Paint.Style.FILL
        }
        private val game = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 68, 58)
            style = Paint.Style.FILL
        }
        private val controls = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 32, 36)
            style = Paint.Style.FILL
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val landscape = mode != LayoutMode.PORTRAIT_CONSOLE
            val pw = if (landscape) w * 0.88f else w * 0.50f
            val ph = if (landscape) h * 0.58f else h * 0.92f
            val left = (w - pw) / 2f
            val top = (h - ph) / 2f
            rect.set(left, top, left + pw, top + ph)
            // Phone body
            canvas.drawRoundRect(rect, 22f, 22f, phone)
            canvas.drawRoundRect(rect, 22f, 22f, stroke)

            val inset = if (landscape) ph * 0.10f else pw * 0.09f

            if (mode == LayoutMode.PORTRAIT_CONSOLE) {
                // Game area (top ~46%)
                canvas.drawRoundRect(left + inset, top + ph * 0.08f, left + pw - inset, top + ph * 0.46f, 8f, 8f, game)
                // Inner screen (black center)
                val scInset = inset * 1.3f
                canvas.drawRoundRect(left + scInset, top + ph * 0.11f, left + pw - scInset, top + ph * 0.43f, 4f, 4f, phone)
                // Controls area (bottom ~53%)
                canvas.drawRoundRect(left + inset, top + ph * 0.53f, left + pw - inset, top + ph * 0.88f, 8f, 8f, controls)
                // D-pad on left side of controls
                canvas.drawCircle(left + pw * 0.28f, top + ph * 0.70f, ph * 0.06f, game)
                // Buttons on right side
                drawButtonDots(canvas, left + pw * 0.72f, top + ph * 0.70f, pw * 0.05f)
            } else {
                // Landscape/gamepad: full game area
                canvas.drawRoundRect(left + inset, top + inset, left + pw - inset, top + ph - inset, 8f, 8f, game)
                // Inner screen (black center)
                val scInset = inset * 1.4f
                canvas.drawRoundRect(left + scInset, top + scInset, left + pw - scInset, top + ph - scInset, 6f, 6f, phone)
                if (mode == LayoutMode.LANDSCAPE) {
                    // D-pad on left
                    canvas.drawCircle(left + pw * 0.22f, top + ph * 0.58f, ph * 0.07f, controls)
                    drawButtonDots(canvas, left + pw * 0.78f, top + ph * 0.58f, ph * 0.04f)
                }
            }
        }

        private fun drawButtonDots(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            canvas.drawCircle(cx - r * 1.6f, cy, r, controls)
            canvas.drawCircle(cx + r * 1.6f, cy, r, controls)
            canvas.drawCircle(cx, cy - r * 1.6f, r, controls)
            canvas.drawCircle(cx, cy + r * 1.6f, r, controls)
        }
    }

    private fun expandableButton(label: String, contentBuilder: (LinearLayout) -> Unit): LinearLayout {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val contentArea = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        contentBuilder(contentArea)

        val btn = TextView(context).apply {
            text = label; setTextColor(ACCENT); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(180, 22, 20, 26))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(45, 100, 90, 80))
            }
            setOnClickListener {
                if (contentArea.visibility == View.GONE) {
                    contentArea.visibility = View.VISIBLE
                    contentArea.alpha = 0f; contentArea.animate().alpha(1f).setDuration(150).start()
                } else {
                    contentArea.animate().alpha(0f).setDuration(100)
                        .withEndAction { contentArea.visibility = View.GONE }.start()
                }
            }
        }
        container.addView(btn); container.addView(contentArea)
        return container
    }

    private fun makeHelpContent(panel: LinearLayout) {
        panel.addView(TextView(context).apply {
            text = """
1. ADD A GAME — Tap the dock bar + ADD. Select the game's root folder (containing www/, Game.exe, or data/).

2. PLAY — Tap the game card, then tap PLAY. The game launches with auto-detected engine.

3. OPTIONS — Tap a card, then tap OPTIONS (gear). This opens per-game settings: reimport, change engine, view saves, remove.

4. FILTER — Use the filter button (top-right) to filter by engine type and sort (A-Z, recent, date added).

5. SETTINGS — Layout mode (Portrait Console / Landscape / Gamepad), touch opacity/scale, haptics, audio fallback.

6. KEYBOARD — In-game, tap the keyboard button (bottom-right) to summon phone keyboard.

7. SAVES — Game saves are protected in a separate saves/ folder. When reimporting or deleting, saves are preserved and auto-restored.
            """.trimIndent()
            setTextColor(MUTED); textSize = 11f; setPadding(0, dp(2), 0, dp(2))
            setLineSpacing(2f, 1f)
        })
    }

    private fun makeAboutContent(panel: LinearLayout) {
        panel.addView(TextView(context).apply {
            text = """
Runestone v0.3.0 — "Glass"
Released: May 2026

Open-source multi-engine game launcher for Android.
Supports RPG Maker XP/VX/VX Ace (mkxp-z), MV/MZ (WebView), TyranoBuilder, Construct 2/3, and more planned.

License: GPLv2+
GitHub: github.com/KleirRampage45/Runestone

Built with Kotlin (no XML layouts).
Uses SDL2, mkxp-z, Ruby, OpenAL, and system WebView.

No copyrighted game files included.
All games must be legally owned by the user.
            """.trimIndent()
            setTextColor(MUTED); textSize = 11f; setPadding(0, dp(2), 0, dp(2))
            setLineSpacing(2f, 1f)
        })
    }

    private companion object {
        val BG: Int = Color.rgb(3, 3, 4)
        val PANEL: Int = Color.rgb(22, 20, 26)
        val TEXT: Int = Color.rgb(232, 229, 220)
        val MUTED: Int = Color.rgb(140, 130, 112)
        val ACCENT: Int = Color.rgb(207, 174, 126)
    }
}
