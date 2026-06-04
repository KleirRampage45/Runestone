package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.*
import android.view.View

class VignetteOverlay(context: Context) : View(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val radius = maxOf(width, height) * 0.7f
        val gradient = RadialGradient(
            width / 2f, height / 2f, radius,
            Color.TRANSPARENT,
            Color.argb(140, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
