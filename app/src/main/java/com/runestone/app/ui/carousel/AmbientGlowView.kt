/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.ui.carousel

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.OvershootInterpolator
import com.runestone.app.data.EngineType

class AmbientGlowView(context: Context) : View(context) {

    private var currentColor: Int = Color.argb(40, 207, 174, 126) // default accent
    private var targetColor: Int = currentColor
    private var animProgress: Float = 1f
    private var animator: ValueAnimator? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun transitionToColor(newColor: Int) {
        targetColor = newColor
        animProgress = 0f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                animProgress = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    fun transitionToEngine(engineType: EngineType) {
        transitionToColor(engineColor(engineType))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val blended = blendColors(currentColor, targetColor, animProgress)
        val cx = width / 2f
        val cy = height * 0.25f
        val radius = maxOf(width, height) * 0.75f

        val gradient = RadialGradient(
            cx, cy, radius,
            blended, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun blendColors(from: Int, to: Int, t: Float): Int {
        val a = ((Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t)).toInt()
        val r = ((Color.red(from) + (Color.red(to) - Color.red(from)) * t)).toInt()
        val g = ((Color.green(from) + (Color.green(to) - Color.green(from)) * t)).toInt()
        val b = ((Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t)).toInt()
        return Color.argb(a.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    companion object {
        fun engineColor(engine: EngineType): Int = when (engine) {
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> Color.argb(50, 180, 120, 60)
            EngineType.MV, EngineType.MZ -> Color.argb(50, 100, 160, 200)
            EngineType.EASYRPG -> Color.argb(50, 120, 170, 120)
            EngineType.RENPY -> Color.argb(50, 180, 130, 160)
            EngineType.GODOT, EngineType.GODOT3, EngineType.GODOT4 -> Color.argb(50, 80, 170, 170)
            EngineType.RUFFLE -> Color.argb(50, 160, 120, 180)
            else -> Color.argb(40, 207, 174, 126)
        }
    }
}
