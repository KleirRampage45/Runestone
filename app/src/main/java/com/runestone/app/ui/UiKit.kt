package com.runestone.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    val homeBg: Int get() = Theme.BACKGROUND
    val homeText: Int get() = Theme.TEXT
    val homeMuted: Int get() = Theme.MUTED
    val homeAccent: Int get() = Theme.active.accent

    fun vertical(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 28, 28, 28)
        setBackgroundColor(Theme.SURFACE)
    }

    fun title(context: Context, value: String): TextView = TextView(context).apply {
        text = value; setTextColor(Theme.TEXT); textSize = 26f
    }

    fun label(context: Context, value: String): TextView = TextView(context).apply {
        text = value; setTextColor(Theme.MUTED); textSize = 15f
    }

    fun button(context: Context, value: String, onClick: () -> Unit): Button = Button(context).apply {
        text = value; setTextColor(Theme.TEXT)
        setBackgroundColor(Theme.active.accent)
        setOnClickListener { onClick() }
    }

    fun spacer(context: Context, height: Int = 18): View = View(context).apply {
        layoutParams = ViewGroup.LayoutParams(MATCH, height)
    }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun makeLiquid(v: View) {
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    fun animTap(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(100).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
}
