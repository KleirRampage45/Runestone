/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * In-game slide-out overlay panel. Accessed via MENU button or left-edge swipe.
 */

package com.runestone.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Callbacks for menu actions.
 */
interface InGameMenuActions {
    fun onCloseGame()
    fun onRotateScreen()
    fun onToggleKeyboard()
    fun onSetSpeed(multiplier: Float)
    fun onScreenshot()
    fun onOpenCheats()
}

class InGameMenu(
    context: Context,
    private val actions: InGameMenuActions,
) : FrameLayout(context) {

    private val panel: LinearLayout
    private val panelWidth: Int
    private var isOpen = false
    private var speedMultiplier = 1.0f
    private val speedLabel: TextView
    private var touchStartX = 0f

    init {
        val dm = context.resources.displayMetrics
        panelWidth = (dm.widthPixels * 0.68f).toInt()

        // Semi-transparent background (click to close)
        setBackgroundColor(Color.argb(0, 0, 0, 0))
        visibility = View.GONE
        setOnClickListener { close() }

        // The slide-out panel
        panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 12, 16))
            background = GradientDrawable().apply {
                setColor(Color.rgb(14, 12, 16))
                cornerRadii = floatArrayOf(0f, 0f, dp(16).toFloat(), dp(16).toFloat(), 0f, 0f, 0f, 0f)
                setStroke(dp(1), Color.argb(60, 140, 110, 90))
            }
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        val scroll = ScrollView(context).apply {
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        scroll.addView(panel)

        addView(scroll, LayoutParams(panelWidth, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.START
        })

        // Header
        panel.addView(header("RUNESTONE"))
        panel.addView(space(dp(12)))

        // Speed control
        panel.addView(sectionLabel("SPEED"))
        val speedRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        speedLabel = speedChip("1x", true)
        speedRow.addView(speedLabel)
        speedRow.addView(space(dp(6)))
        for (s in listOf(2f, 3f, 4f)) {
            val chip = speedChip("${s.toInt()}x", false)
            chip.setOnClickListener {
                speedMultiplier = s
                refreshSpeedChips(speedRow, s)
                actions.onSetSpeed(s)
            }
            speedRow.addView(chip)
            speedRow.addView(space(dp(6)))
        }
        panel.addView(speedRow)
        panel.addView(space(dp(16)))

        // Actions
        panel.addView(sectionLabel("ACTIONS"))
        panel.addView(menuItem("Close Game", ::closeGame))
        panel.addView(menuItem("Rotate Screen", actions::onRotateScreen))
        panel.addView(menuItem("Keyboard", actions::onToggleKeyboard))
        panel.addView(menuItem("Screenshot", actions::onScreenshot))
        panel.addView(menuItem("Cheat Menu", actions::onOpenCheats))
        panel.addView(space(dp(16)))

        // Quick Sliders
        panel.addView(sectionLabel("QUICK SETTINGS"))
        panel.addView(sliderRow("Opacity", 0.5f, 1.0f, 0.72f) { /* TODO */ })
        panel.addView(sliderRow("Scale", 0.5f, 1.5f, 1.0f) { /* TODO */ })
        panel.addView(sliderRow("Haptics", 0.0f, 1.0f, 0.55f) { /* TODO */ })
    }

    // ── API ──────────────────────────────────────────────────────

    fun open() {
        if (isOpen) return
        isOpen = true
        visibility = View.VISIBLE
        // Dim background in
        val bgAnim = ValueAnimator.ofInt(0, 180)
        bgAnim.addUpdateListener { setBackgroundColor(Color.argb(it.animatedValue as Int, 0, 0, 0)) }
        bgAnim.duration = 200; bgAnim.start()

        // Slide panel in from left
        panel.translationX = -panelWidth.toFloat()
        panel.animate().translationX(0f).setDuration(250)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        val bgAnim = ValueAnimator.ofInt(180, 0)
        bgAnim.addUpdateListener { setBackgroundColor(Color.argb(it.animatedValue as Int, 0, 0, 0)) }
        bgAnim.duration = 150
        bgAnim.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            override fun onAnimationUpdate(a: ValueAnimator) {
                setBackgroundColor(Color.argb(a.animatedValue as Int, 0, 0, 0))
            }
        })
        bgAnim.start()

        panel.animate().translationX(-panelWidth.toFloat()).setDuration(200)
            .withEndAction { visibility = View.GONE }.start()
    }

    fun toggle() { if (isOpen) close() else open() }

    fun isOpen(): Boolean = isOpen

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isOpen) {
            // Edge swipe detection: from left 30dp
            if (ev.action == MotionEvent.ACTION_DOWN && ev.x < dp(30).toFloat()) {
                touchStartX = ev.x
                return false
            }
            if (ev.action == MotionEvent.ACTION_MOVE && touchStartX > 0f && ev.x - touchStartX > dp(40)) {
                open()
                touchStartX = 0f
                return true
            }
            if (ev.action == MotionEvent.ACTION_UP) touchStartX = 0f
        }
        return super.onInterceptTouchEvent(ev)
    }

    // ── UI builders ──────────────────────────────────────────────

    private fun header(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.rgb(207, 174, 126))
        textSize = 18f
        typeface = Typeface.create("serif", Typeface.BOLD)
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.rgb(140, 130, 112))
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(8))
    }

    private fun menuItem(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.rgb(220, 215, 200))
            textSize = 13f
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255))
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener { onClick(); close() }
            (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = dp(6)
        }
    }

    private fun speedChip(text: String, active: Boolean) = TextView(context).apply {
        this.text = text
        setTextColor(if (active) Color.rgb(207, 174, 126) else Color.rgb(140, 130, 112))
        textSize = 12f
        typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply {
            if (active) setColor(Color.argb(60, 200, 170, 130))
            else setStroke(dp(1), Color.argb(40, 180, 160, 130))
            cornerRadius = dp(6).toFloat()
        }
    }

    private fun refreshSpeedChips(row: LinearLayout, selected: Float) {
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i) as? TextView ?: continue
            val text = child.text.toString()
            val isActive = when (text) {
                "1x" -> selected == 1f
                "2x" -> selected == 2f
                "3x" -> selected == 3f
                "4x" -> selected == 4f
                else -> false
            }
            child.setTextColor(if (isActive) Color.rgb(207, 174, 126) else Color.rgb(140, 130, 112))
            child.typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            (child.background as? GradientDrawable)?.apply {
                if (isActive) {
                    setColor(Color.argb(60, 200, 170, 130))
                    setStroke(0, Color.TRANSPARENT)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.argb(40, 180, 160, 130))
                }
            }
        }
    }

    private fun sliderRow(
        label: String, min: Float, max: Float, value: Float, onChange: (Float) -> Unit,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        row.addView(TextView(context).apply {
            text = label; setTextColor(Color.rgb(180, 175, 160))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(dp(60), WRAP)
        })
        // Simple bar representation (real SeekBar would be better but adds complexity)
        val bar = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f).apply { setMargins(dp(8), 0, dp(8), 0) }
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 255, 255, 255))
                cornerRadius = dp(2).toFloat()
            }
        }
        row.addView(bar)
        row.addView(TextView(context).apply {
            text = "${(value * 100).toInt()}%"
            setTextColor(Color.rgb(140, 130, 112))
            textSize = 10f
        })
        return row
    }

    private fun closeGame() {
        actions.onCloseGame()
        close()
    }

    // ── Utils ────────────────────────────────────────────────────

    private fun space(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, h)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
