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

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.sign

/**
 * Applies the 3D carousel presentation to children laid out by a standard
 * LinearLayoutManager. Snapping is intentionally owned by PagerSnapHelper.
 */
class Carousel3DScrollEffects(
    private val context: Context,
) : RecyclerView.OnScrollListener() {

    interface FocusListener {
        fun onFocusChanged(adapterPosition: Int)
    }

    private var lastFocusedPosition = RecyclerView.NO_POSITION
    var focusListener: FocusListener? = null

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        applyTransforms(recyclerView)
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            applyTransforms(recyclerView)
        }
    }

    fun applyTransforms(recyclerView: RecyclerView) {
        val containerCenter = recyclerView.width / 2f
        var closestPosition = RecyclerView.NO_POSITION
        var closestDistance = Float.MAX_VALUE

        for (index in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(index)
            val childCenter = (child.left + child.right) / 2f
            val distance = abs(childCenter - containerCenter)
            if (distance < closestDistance) {
                closestDistance = distance
                closestPosition = recyclerView.getChildAdapterPosition(child)
            }

            val position = (childCenter - containerCenter) / child.width.coerceAtLeast(1).toFloat()
            val absPosition = abs(position)
            val transformProgress = (absPosition / 2f).coerceIn(0f, 1f)

            child.scaleX = lerp(1.25f, 0.55f, transformProgress)
            child.scaleY = child.scaleX
            child.rotationY = sign(position) * lerp(0f, 55f, transformProgress)
            child.alpha = lerp(1f, 0.35f, transformProgress)
            child.translationY = lerp(-dp(28).toFloat(), dp(18).toFloat(), absPosition.coerceIn(0f, 1f))
            child.elevation = lerp(dp(36).toFloat(), dp(2).toFloat(), transformProgress)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurRadius = when {
                    absPosition < 0.4f -> 0f
                    absPosition < 1.2f -> 2f
                    else -> 4f
                }
                child.setRenderEffect(
                    if (blurRadius > 0f) {
                        RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    } else {
                        null
                    },
                )
            }
        }

        if (closestPosition != lastFocusedPosition && closestPosition != RecyclerView.NO_POSITION) {
            lastFocusedPosition = closestPosition
            focusListener?.onFocusChanged(closestPosition)
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun lerp(start: Float, end: Float, t: Float): Float =
        start + (end - start) * t.coerceIn(0f, 1f)
}
