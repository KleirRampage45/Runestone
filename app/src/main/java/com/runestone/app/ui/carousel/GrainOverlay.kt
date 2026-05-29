package com.runestone.app.ui.carousel

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View

class GrainOverlay(context: Context) : View(context) {
    private val frames: List<Bitmap>
    private var currentFrame = 0
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = 8
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            currentFrame = (currentFrame + 1) % frames.size
            invalidate()
            handler.postDelayed(this, 66)
        }
    }

    init {
        val size = 128
        frames = List(4) {
            val pixels = IntArray(size * size) {
                val v = (Math.random() * 256).toInt()
                Color.rgb(v, v, v)
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }
        handler.post(frameRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bm = frames[currentFrame]
        for (x in 0..width step 128) {
            for (y in 0..height step 128) {
                canvas.drawBitmap(bm, x.toFloat(), y.toFloat(), grainPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(frameRunnable)
    }
}
