/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Per-game settings screen with glassmorphism UI.
 */

package com.runestone.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.runestone.app.data.*

class PerGameSettingsScreen(private val context: Context) {

    fun create(
        gameTitle: String,
        config: PerGameConfig,
        onConfigChanged: (PerGameConfig) -> Unit,
        onBack: () -> Unit,
    ): LinearLayout {
        var current = config

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(makeTopBar(gameTitle, onBack))

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

        // ── Input Section ──
        content.addView(sectionTitle("Input", "Touch controls and haptics"))
        
        content.addView(switchPanel("Haptic Feedback", "Vibrate when controls are pressed", 
            current.input.hapticsEnabled) { checked ->
            current = current.copy(input = current.input.copy(hapticsEnabled = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Haptic Intensity", 
            "${(current.input.hapticIntensity * 100).toInt()}%") { label ->
            slider(100, (current.input.hapticIntensity * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(input = current.input.copy(hapticIntensity = progress / 100f))
                label.text = "${(current.input.hapticIntensity * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Button Opacity", 
            "${(current.input.buttonOpacity * 100).toInt()}%") { label ->
            slider(100, (current.input.buttonOpacity * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(input = current.input.copy(buttonOpacity = progress / 100f))
                label.text = "${(current.input.buttonOpacity * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Button Scale", 
            "${(current.input.buttonScale * 100).toInt()}%") { label ->
            slider(100, ((current.input.buttonScale - 0.5f) * 200).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(input = current.input.copy(buttonScale = 0.5f + progress / 200f))
                label.text = "${(current.input.buttonScale * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Video Section ──
        content.addView(sectionTitle("Video", "Display and rendering"))

        content.addView(switchPanel("Show FPS", "Display frame rate counter", 
            current.video.showFps) { checked ->
            current = current.copy(video = current.video.copy(showFps = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("VSync", "Synchronize frame rate with display", 
            current.video.vsync) { checked ->
            current = current.copy(video = current.video.copy(vsync = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Integer Scaling", "Pixel-perfect scaling (may add black bars)", 
            current.video.integerScaling) { checked ->
            current = current.copy(video = current.video.copy(integerScaling = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Smooth Scaling", "Bilinear filtering for smoother image", 
            current.video.smoothScaling) { checked ->
            current = current.copy(video = current.video.copy(smoothScaling = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Brightness", 
            "${(current.video.brightness * 100).toInt()}%") { label ->
            slider(200, (current.video.brightness * 100).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(video = current.video.copy(brightness = progress / 100f))
                label.text = "${(current.video.brightness * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Contrast", 
            "${(current.video.contrast * 100).toInt()}%") { label ->
            slider(200, (current.video.contrast * 100).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(video = current.video.copy(contrast = progress / 100f))
                label.text = "${(current.video.contrast * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Audio Section ──
        content.addView(sectionTitle("Audio", "Sound and music"))

        content.addView(switchPanel("Mute Music", "Disable background music", 
            current.audio.muteMusic) { checked ->
            current = current.copy(audio = current.audio.copy(muteMusic = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Mute Sound Effects", "Disable sound effects", 
            current.audio.muteSfx) { checked ->
            current = current.copy(audio = current.audio.copy(muteSfx = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Mute Video Audio", "Disable video sound", 
            current.audio.muteVideo) { checked ->
            current = current.copy(audio = current.audio.copy(muteVideo = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Master Volume", 
            "${(current.audio.volume * 100).toInt()}%") { label ->
            slider(100, (current.audio.volume * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(audio = current.audio.copy(volume = progress / 100f))
                label.text = "${(current.audio.volume * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Music Volume", 
            "${(current.audio.volumeMusic * 100).toInt()}%") { label ->
            slider(100, (current.audio.volumeMusic * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(audio = current.audio.copy(volumeMusic = progress / 100f))
                label.text = "${(current.audio.volumeMusic * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("SFX Volume", 
            "${(current.audio.volumeSfx * 100).toInt()}%") { label ->
            slider(100, (current.audio.volumeSfx * 100).toInt().coerceIn(0, 100)) { progress ->
                current = current.copy(audio = current.audio.copy(volumeSfx = progress / 100f))
                label.text = "${(current.audio.volumeSfx * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Performance Section ──
        content.addView(sectionTitle("Performance", "Optimization settings"))

        content.addView(switchPanel("Threaded Rendering", "Use multiple threads for rendering", 
            current.performance.threadedRendering) { checked ->
            current = current.copy(performance = current.performance.copy(threadedRendering = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Background Loading", "Load assets in background", 
            current.performance.backgroundLoading) { checked ->
            current = current.copy(performance = current.performance.copy(backgroundLoading = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Reduce Shadows", "Lower shadow quality for better performance", 
            current.performance.reduceShadows) { checked ->
            current = current.copy(performance = current.performance.copy(reduceShadows = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Reduce Particles", "Lower particle effects for better performance", 
            current.performance.reduceParticles) { checked ->
            current = current.copy(performance = current.performance.copy(reduceParticles = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Frame Skip", 
            "${current.performance.frameSkip}") { label ->
            slider(5, current.performance.frameSkip.coerceIn(0, 5)) { progress ->
                current = current.copy(performance = current.performance.copy(frameSkip = progress))
                label.text = "$progress"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Texture Cache Size", 
            "${current.performance.textureCacheSize} MB") { label ->
            slider(256, (current.performance.textureCacheSize / 4).coerceIn(8, 64)) { progress ->
                val size = progress * 4
                current = current.copy(performance = current.performance.copy(textureCacheSize = size))
                label.text = "$size MB"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(14))

        // ── Fonts Section ──
        content.addView(sectionTitle("Fonts", "Text rendering"))

        content.addView(switchPanel("Use Game Fonts", "Prefer fonts bundled with the game", 
            current.fonts.useGameFonts) { checked ->
            current = current.copy(fonts = current.fonts.copy(useGameFonts = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Bold Text", "Make all text bold", 
            current.fonts.boldText) { checked ->
            current = current.copy(fonts = current.fonts.copy(boldText = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(switchPanel("Italic Text", "Make all text italic", 
            current.fonts.italicText) { checked ->
            current = current.copy(fonts = current.fonts.copy(italicText = checked))
            onConfigChanged(current)
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Font Scale", 
            "${(current.fonts.fontScale * 100).toInt()}%") { label ->
            slider(200, ((current.fonts.fontScale - 0.5f) * 200).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(fonts = current.fonts.copy(fontScale = 0.5f + progress / 200f))
                label.text = "${(current.fonts.fontScale * 100).toInt()}%"
                onConfigChanged(current)
            }
        })
        content.addView(spacer(10))

        content.addView(sliderPanel("Line Spacing", 
            "${(current.fonts.lineSpacing * 100).toInt()}%") { label ->
            slider(200, ((current.fonts.lineSpacing - 0.5f) * 200).toInt().coerceIn(0, 200)) { progress ->
                current = current.copy(fonts = current.fonts.copy(lineSpacing = 0.5f + progress / 200f))
                label.text = "${(current.fonts.lineSpacing * 100).toInt()}%"
                onConfigChanged(current)
            }
        })

        content.animate().alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.1f)).start()
        return root
    }

    private fun makeTopBar(gameTitle: String, onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.argb(180, 3, 3, 4))

            addView(
                TextView(context).apply {
                    text = "Back"
                    setTextColor(ACCENT)
                    textSize = 13f
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
                LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            addView(
                TextView(context).apply {
                    text = gameTitle
                    setTextColor(TEXT)
                    textSize = 16f
                    letterSpacing = 0.2f
                    gravity = Gravity.CENTER
                    typeface = Typeface.create("serif", Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(View(context), LinearLayout.LayoutParams(dp(80), 1))
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

    private fun spacer(h: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private fun makeLiquid(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.scaleX = 1.08f
                    v.scaleY = 1.08f
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f
                    val cy = v.height / 2f
                    val dx = (event.x - cx) * 0.06f
                    val dy = (event.y - cy) * 0.06f
                    v.translationX = dx
                    v.translationY = dy
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .translationX(0f).translationY(0f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator(1.6f))
                        .withEndAction {
                            v.scaleX = 1f; v.scaleY = 1f
                            v.translationX = 0f; v.translationY = 0f
                        }
                        .start()
                }
            }
            false
        }
    }

    private fun glassBg(radius: Int, alpha: Int = 200): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha, 18, 18, 24))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(40, 207, 174, 126))
        }

    private inner class GlassSlider(
        context: Context,
        private val maxValue: Int,
        initialValue: Int,
        private val onChange: (Int) -> Unit,
    ) : View(context) {

        private var value = initialValue.coerceIn(0, maxValue)
        private val trackPaint = android.graphics.Paint().apply {
            color = Color.argb(60, 255, 255, 255)
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        private val progressPaint = android.graphics.Paint().apply {
            color = ACCENT
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        private val thumbPaint = android.graphics.Paint().apply {
            color = Color.WHITE
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }

        init {
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val newValue = ((event.x / width) * maxValue).toInt().coerceIn(0, maxValue)
                        if (newValue != value) {
                            value = newValue
                            onChange(value)
                            invalidate()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val trackHeight = dp(6)
            val thumbRadius = dp(10)
            val centerY = height / 2f
            val trackLeft = thumbRadius.toFloat()
            val trackRight = width - thumbRadius.toFloat()

            // Track
            canvas.drawRoundRect(
                trackLeft, centerY - trackHeight / 2f,
                trackRight, centerY + trackHeight / 2f,
                trackHeight / 2f, trackHeight / 2f,
                trackPaint
            )

            // Progress
            val progressWidth = trackLeft + (trackRight - trackLeft) * (value.toFloat() / maxValue)
            canvas.drawRoundRect(
                trackLeft, centerY - trackHeight / 2f,
                progressWidth, centerY + trackHeight / 2f,
                trackHeight / 2f, trackHeight / 2f,
                progressPaint
            )

            // Thumb
            canvas.drawCircle(progressWidth, centerY, thumbRadius.toFloat(), thumbPaint)
        }
    }

    companion object {
        private val TEXT = Color.rgb(232, 229, 220)
        private val MUTED = Color.rgb(140, 130, 112)
        private val ACCENT = Color.rgb(207, 174, 126)
    }
}
