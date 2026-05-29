package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class PageIndicator(context: Context, count: Int, initialPosition: Int = 0) : LinearLayout(context) {
    private val dots = mutableListOf<TextView>()
    private val activeColor = Color.rgb(207, 174, 126)
    private val inactiveColor = Color.argb(80, 200, 180, 150)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        for (i in 0 until count.coerceIn(1, 30)) {
            val dot = TextView(context).apply {
                text = "\u25CF" // filled circle
                textSize = 8f
                gravity = Gravity.CENTER
            }
            dots.add(dot)
            addView(dot)
        }
        setActive(initialPosition)
    }

    fun setActive(position: Int) {
        dots.forEachIndexed { i, dot ->
            dot.setTextColor(if (i == position) activeColor else inactiveColor)
        }
    }
}
