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
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class Carousel3DLayoutManager(
    private val context: Context,
) : RecyclerView.LayoutManager() {

    interface FocusListener {
        fun onFocusChanged(adapterPosition: Int)
    }

    private var scrollOffset = 0
    private val cardWidthPx: Int
    private val cardHeightPx: Int
    private val cardSpacingPx: Int
    private val visibleCardCount = 5 // show up to 5 cards at once
    private val centerPercent = 0.35f // card center at 35% from left
    private var lastFocusedPosition: Int = RecyclerView.NO_POSITION
    var focusListener: FocusListener? = null

    init {
        cardWidthPx = dp(260)
        cardHeightPx = dp(360)
        cardSpacingPx = dp(8)
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }

        val parentWidth = width
        val parentHeight = height
        if (parentWidth <= 0 || parentHeight <= 0) return

        detachAndScrapAttachedViews(recycler)

        val centerX = (parentWidth * centerPercent).toInt()
        val centerY = parentHeight / 2
        var currentX = centerX - scrollOffset

        for (i in 0 until itemCount) {
            val child = recycler.getViewForPosition(i)
            addView(child)
            measureChildWithMargins(child, cardWidthPx, cardHeightPx)

            val left = currentX - cardWidthPx / 2
            val top = centerY - cardHeightPx / 2
            val right = left + cardWidthPx
            val bottom = top + cardHeightPx

            child.layout(left, top, right, bottom)
            currentX += cardWidthPx + cardSpacingPx
        }
        updateTransforms()
    }

    override fun canScrollHorizontally() = true

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        val consumed = if (scrollOffset + dx < 0) {
            -scrollOffset
        } else {
            val maxScroll = (itemCount - 1) * (cardWidthPx + cardSpacingPx)
            if (scrollOffset + dx > maxScroll) maxScroll - scrollOffset
            else dx
        }
        scrollOffset += consumed
        offsetChildrenHorizontal(-consumed)
        updateTransforms()
        return consumed
    }

    override fun scrollToPosition(position: Int) {
        if (position < 0 || position >= itemCount) return
        scrollOffset = position * (cardWidthPx + cardSpacingPx)
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int,
    ) {
        val scroller = CenterSnapScroller(recyclerView.context)
        scroller.targetPosition = position
        startSmoothScroll(scroller)
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        view?.cameraDistance = 8000f * context.resources.displayMetrics.density
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun lerp(start: Float, end: Float, t: Float): Float =
        start + (end - start) * t.coerceIn(0f, 1f)

    private fun updateTransforms() {
        val containerCenter = width / 2f
        var closestPosition = RecyclerView.NO_POSITION
        var closestDistance = Float.MAX_VALUE
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val childCenter = (child.left + child.right) / 2f
            // Track closest to center for focus
            val distance = abs(childCenter - containerCenter)
            if (distance < closestDistance) {
                closestDistance = distance
                closestPosition = getPosition(child)
            }
            val position = (childCenter - containerCenter) / (cardWidthPx + cardSpacingPx).toFloat()
            val absPos = abs(position)

            // 3D transforms
            val scale = lerp(1.0f, 0.65f, (absPos / 2f).coerceIn(0f, 1f))
            val rotation = sign(position) * lerp(0f, 45f, (absPos / 2f).coerceIn(0f, 1f))
            val alpha = lerp(1.0f, 0.45f, (absPos / 2f).coerceIn(0f, 1f))

            child.scaleX = scale
            child.scaleY = scale
            child.rotationY = rotation
            child.alpha = alpha

            // Elevation
            child.elevation = lerp(dp(14).toFloat(), dp(2).toFloat(), (absPos / 2f).coerceIn(0f, 1f))

            // Blur for edge cards (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurRadius = when {
                    absPos < 0.5f -> 0f
                    absPos < 1.5f -> 2f
                    else -> 4f
                }
                if (blurRadius > 0f) {
                    child.setRenderEffect(RenderEffect.createBlurEffect(
                        blurRadius, blurRadius, Shader.TileMode.CLAMP
                    ))
                } else {
                    child.setRenderEffect(null)
                }
            }
        }
        // Notify focus change
        if (closestPosition != lastFocusedPosition && closestPosition != RecyclerView.NO_POSITION) {
            lastFocusedPosition = closestPosition
            focusListener?.onFocusChanged(closestPosition)
        }
    }

    private class CenterSnapScroller(context: Context) : LinearSmoothScroller(context) {
        override fun calculateDtToFit(
            viewStart: Int, viewEnd: Int,
            boxStart: Int, boxEnd: Int,
            viewVelocity: Int
        ): Int {
            val viewCenter = (viewStart + viewEnd) / 2
            val containerCenter = (boxStart + boxEnd) / 2
            return (containerCenter - viewCenter) / 2
        }

        override fun getVerticalSnapPreference() = SNAP_TO_START
    }
}
