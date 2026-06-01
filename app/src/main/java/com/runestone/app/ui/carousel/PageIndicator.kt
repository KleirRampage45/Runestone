package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class PageIndicator(context: Context, count: Int, initialPosition: Int = 0) : LinearLayout(context) {
    private val dots = mutableListOf<TextView>()
    private val activeColor = Color.rgb(244, 213, 164)
    private val inactiveColor = Color.argb(72, 200, 180, 150)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        for (i in 0 until count.coerceIn(1, 30)) {
            val dot = TextView(context).apply {
                text = "\u25CF" // filled circle
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
            }
            dots.add(dot)
            addView(dot)
        }
        setActive(initialPosition)
    }

    fun setActive(position: Int) {
        dots.forEachIndexed { i, dot ->
            dot.setTextColor(if (i == position) activeColor else inactiveColor)
            dot.textSize = if (i == position) 11f else 7f
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
