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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
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
            // Transparent — overlay dim layer provides the background
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
        content.alpha = 0f

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

        content.animate().alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.1f)).start()
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
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = GradientDrawable().apply {
                        setColor(Color.argb(40, 207, 174, 126))
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.argb(60, 207, 174, 126))
                    }
                    setOnClickListener { onBack() }
                },
                LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            addView(
                TextView(context).apply {
                    text = "Runestone Setup"
                    setTextColor(TEXT)
                    textSize = 21f
                    letterSpacing = 0.5f
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
            setOnClickListener {
                animTap(this)
                onSelect(mode)
            }
            background = panelBackground(
                if (selected == mode) Color.argb(200, 33, 28, 27) else Color.argb(190, 12, 11, 16),
                stroke = if (selected == mode) ACCENT else Color.argb(60, 207, 174, 126),
                corner = 16,
            )
            makeLiquid(this)
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
                if (selected) Color.argb(200, 33, 28, 27) else Color.argb(190, 12, 11, 16),
                stroke = if (selected) ACCENT else Color.argb(60, 207, 174, 126),
                corner = 16,
            )
            makeLiquid(this)
            setOnClickListener {
                animTap(this)
                onClick()
            }
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
            background = glassBg(16, alpha = 200)
            makeLiquid(this)
            build()
        }

    private fun sliderPanel(title: String, value: String, sliderFactory: (TextView) -> GlassSlider): LinearLayout =
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

    private fun slider(max: Int, progress: Int, onChange: (Int) -> Unit): GlassSlider =
        GlassSlider(context, max, progress, onChange)

    private fun panelBackground(color: Int, stroke: Int = Color.TRANSPARENT, corner: Int = 16): GradientDrawable =
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

    private fun makeLiquid(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f
                    val cy = v.height / 2f
                    val dx = (event.x - cx) * 0.06f
                    val dy = (event.y - cy) * 0.06f
                    v.translationX = dx
                    v.translationY = dy
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f)
                        .translationX(0f).translationY(0f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator(1.6f))
                        .start()
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

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha,
                if (accent) 50 else 22, if (accent) 40 else 20, if (accent) 30 else 26))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(if (accent) 80 else 45,
                if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
        }

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
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126)
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
            } else if (mode == LayoutMode.LANDSCAPE) {
                // Landscape: full game area with side controls
                canvas.drawRoundRect(left + inset, top + inset, left + pw - inset, top + ph - inset, 8f, 8f, game)
                val scInset = inset * 1.4f
                canvas.drawRoundRect(left + scInset, top + scInset, left + pw - scInset, top + ph - scInset, 6f, 6f, phone)
                // D-pad on left
                canvas.drawCircle(left + pw * 0.22f, top + ph * 0.58f, ph * 0.07f, controls)
                drawButtonDots(canvas, left + pw * 0.78f, top + ph * 0.58f, ph * 0.04f)
            } else {
                // Gamepad mode: phone with controller icon overlay
                canvas.drawRoundRect(left + inset, top + inset, left + pw - inset, top + ph - inset, 8f, 8f, game)
                val scInset = inset * 1.4f
                canvas.drawRoundRect(left + scInset, top + scInset, left + pw - scInset, top + ph - scInset, 6f, 6f, phone)
                // Controller body
                val cx = left + pw / 2f
                val cy = top + ph / 2f
                val cw = pw * 0.35f
                val ch = ph * 0.20f
                // Controller body (rounded rect)
                val bodyRect = RectF(cx - cw, cy - ch * 0.5f, cx + cw, cy + ch * 0.5f)
                canvas.drawRoundRect(bodyRect, ch * 0.5f, ch * 0.5f, controls)
                canvas.drawRoundRect(bodyRect, ch * 0.5f, ch * 0.5f, Paint().apply {
                    color = Color.argb(60, 207, 174, 126)
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                })
                // Left stick
                canvas.drawCircle(cx - cw * 0.45f, cy, ch * 0.25f, game)
                // Right stick
                canvas.drawCircle(cx + cw * 0.45f, cy, ch * 0.25f, game)
                // D-pad cross
                val dpx = cx - cw * 0.18f
                val dpy = cy
                val ds = ch * 0.08f
                canvas.drawRoundRect(dpx - ds, dpy - ds * 2.2f, dpx + ds, dpy + ds * 2.2f, 2f, 2f, accentPaint)
                canvas.drawRoundRect(dpx - ds * 2.2f, dpy - ds, dpx + ds * 2.2f, dpy + ds, 2f, 2f, accentPaint)
                // Face buttons (ABXY)
                val fcx = cx + cw * 0.18f
                canvas.drawCircle(fcx + ds * 1.6f, cy - ds * 1.6f, ds * 0.6f, accentPaint)
                canvas.drawCircle(fcx - ds * 1.6f, cy - ds * 1.6f, ds * 0.6f, accentPaint)
                canvas.drawCircle(fcx, cy - ds * 3f, ds * 0.6f, accentPaint)
                canvas.drawCircle(fcx, cy + ds * 0.4f, ds * 0.6f, accentPaint)
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
            background = glassBg(14, alpha = 160)
        }
        contentBuilder(contentArea)

        val btn = TextView(context).apply {
            text = label; setTextColor(ACCENT); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = glassBg(14, alpha = 180)
            makeLiquid(this)
            setOnClickListener {
                animTap(this)
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
1. ADD A GAME — Tap the dock bar [+ ADD]. Select the game's root folder (containing www/, Game.exe, or Data/).

2. PLAY — Tap a game card to select it, then tap PLAY. The game launches with auto-detected engine.

3. RESUME — If a game was running, a RESUME bar appears at the bottom of the home screen. Tap RESUME to continue, or STOP to end the session.

4. OPTIONS — Tap a game card to select it, then tap OPTIONS. This opens per-game settings: reimport data, change engine, view saves, or remove the game.

5. FILTER & SORT — Tap the filter button (top-right) to filter games by engine type (MV/MZ, VX/ACE, XP, 2000, RNPY) or search by name. Sort by name, recently played, or date added.

6. SETTINGS — Tap the gear icon on the dock. Configure layout mode (Portrait Console / Landscape / Gamepad), touch opacity, touch scale, haptics, and audio fallback format.

7. KEYBOARD — In-game, tap the keyboard button (bottom-right) to show the phone keyboard for text input.

8. SAVES — Game saves are protected in a separate saves/ folder. When reimporting or deleting game data, saves are preserved and auto-restored.

9. IMPORT — Games are stored as a single copy. Reimporting replaces game data but keeps saves intact.
            """.trimIndent()
            setTextColor(MUTED); textSize = 11f; setPadding(0, dp(2), 0, dp(2))
            setLineSpacing(2f, 1f)
        })
    }

    private fun makeAboutContent(panel: LinearLayout) {
        panel.addView(TextView(context).apply {
            text = """
Runestone v0.6.10 — "Glass UI"
Released: May 2026

Open-source multi-engine game launcher for Android.
Supports RPG Maker XP/VX/VX Ace (mkxp-z), MV/MZ (WebView),
TyranoBuilder, Construct 2/3, and more planned.

License: GPLv2+
GitHub: github.com/KleirRampage45/Runestone

Built with Kotlin — 100% programmatic UI, no XML layouts.
Uses SDL2, mkxp-z, Ruby, OpenAL, and system WebView.

Features:
- Engine auto-detection from game files
- Glassmorphism UI with blur effects
- Portrait Console / Landscape / Gamepad layouts
- Virtual touch controls with adjustable opacity and scale
- Haptic feedback support
- Protected save storage (survives reimports)
- SAF-based folder import
- Single-copy game storage

No copyrighted game files included.
All games must be legally owned by the user.
            """.trimIndent()
            setTextColor(MUTED); textSize = 11f; setPadding(0, dp(2), 0, dp(2))
            setLineSpacing(2f, 1f)
        })
    }

    /**
     * GlassSlider — custom drawn slider with glass aesthetic
     */
    private inner class GlassSlider(
        context: Context,
        private val maxVal: Int,
        private var currentProgress: Int,
        private val onChanged: (Int) -> Unit,
    ) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.FILL
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126)
            style = Paint.Style.FILL
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 229, 220)
            style = Paint.Style.FILL
        }
        private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(207, 174, 126)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private var isTracking = false
        private val trackH = dp(6)
        private val thumbR = dp(12)

        init {
            minimumHeight = dp(40)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cy = h / 2f
            val trackL = thumbR.toFloat()
            val trackR = w - thumbR
            val frac = currentProgress.toFloat() / maxVal.coerceAtLeast(1)
            val thumbX = trackL + (trackR - trackL) * frac

            // Track background
            canvas.drawRoundRect(trackL, cy - trackH / 2f, trackR, cy + trackH / 2f, trackH / 2f, trackH / 2f, bgPaint)
            // Filled track
            canvas.drawRoundRect(trackL, cy - trackH / 2f, thumbX, cy + trackH / 2f, trackH / 2f, trackH / 2f, fillPaint)
            // Thumb
            val scale = if (isTracking) 1.25f else 1f
            canvas.drawCircle(thumbX, cy, thumbR * scale, thumbPaint)
            canvas.drawCircle(thumbX, cy, thumbR * scale, thumbStroke)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTracking = true
                updateProgress(event.x)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgress(event.x)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTracking = false
                invalidate()
                true
            }
            else -> false
        }

        private fun updateProgress(x: Float) {
            val w = width.toFloat()
            val trackL = thumbR.toFloat()
            val trackR = w - thumbR
            val frac = ((x - trackL) / (trackR - trackL)).coerceIn(0f, 1f)
            currentProgress = (frac * maxVal).toInt().coerceIn(0, maxVal)
            onChanged(currentProgress)
            invalidate()
        }
    }

    private companion object {
        val PANEL: Int = Color.argb(190, 12, 11, 16)
        val TEXT: Int = Color.rgb(232, 229, 220)
        val MUTED: Int = Color.rgb(140, 130, 112)
        val ACCENT: Int = Color.rgb(207, 174, 126)
    }
}
