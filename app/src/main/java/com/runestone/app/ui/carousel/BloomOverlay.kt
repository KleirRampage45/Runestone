package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.*
import android.view.View

class BloomOverlay(context: Context) : View(context) {
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; setLayerType(LAYER_TYPE_HARDWARE, null) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val blurMask = BlurMaskFilter(dp(40).toFloat(), BlurMaskFilter.Blur.NORMAL)
    private var accentColor: Int = Color.argb(40, 207, 174, 126)

    fun setAccentColor(color: Int) {
        accentColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = accentColor
        paint.maskFilter = blurMask
        val cx = width / 2f
        val cy = height * 0.35f
        canvas.drawCircle(cx, cy, dp(120).toFloat(), paint)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
