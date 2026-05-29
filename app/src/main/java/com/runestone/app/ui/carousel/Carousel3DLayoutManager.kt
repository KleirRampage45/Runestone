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
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class Carousel3DLayoutManager(
    private val context: Context,
) : RecyclerView.LayoutManager() {

    private var scrollOffset = 0
    private val cardWidthPx: Int
    private val cardHeightPx: Int
    private val cardSpacingPx: Int
    private val visibleCardCount = 5 // show up to 5 cards at once
    private val centerPercent = 0.35f // card center at 35% from left

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
        val targetX = position * (cardWidthPx + cardSpacingPx)
        recyclerView.smoothScrollBy(targetX - scrollOffset, 0)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
