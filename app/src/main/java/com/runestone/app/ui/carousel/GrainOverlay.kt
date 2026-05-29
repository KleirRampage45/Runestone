package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.*
import android.view.View

class GrainOverlay(context: Context) : View(context) {
    private val grainBitmap: Bitmap
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 10 // very subtle
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }

    init {
        val size = 256
        val pixels = IntArray(size * size) {
            val v = (Math.random() * 256).toInt()
            Color.rgb(v, v, v)
        }
        grainBitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (x in 0..width step 256) {
            for (y in 0..height step 256) {
                canvas.drawBitmap(grainBitmap, x.toFloat(), y.toFloat(), grainPaint)
            }
        }
    }
}
