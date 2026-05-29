package com.runestone.app.ui.carousel

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.recyclerview.widget.RecyclerView

class DepthOfFieldController(
    private val glowView: AmbientGlowView,
    private val recyclerView: RecyclerView,
) {
    private var isScrolling = false
    private var scrollStopTime = 0L
    private val blurDecayRunnable = Runnable { updateBlur() }

    fun attach() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> {
                        isScrolling = true
                        applyBlur(16f)
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        isScrolling = false
                        scrollStopTime = System.currentTimeMillis()
                        recyclerView.postDelayed(blurDecayRunnable, 50)
                    }
                }
            }
        })
    }

    private fun updateBlur() {
        if (isScrolling) return
        val elapsed = System.currentTimeMillis() - scrollStopTime
        val blur = when {
            elapsed < 150 -> 12f
            elapsed < 350 -> 6f
            elapsed < 600 -> 2f
            else -> 0f
        }
        applyBlur(blur)
        if (blur > 0f) {
            recyclerView.postDelayed(blurDecayRunnable, 50)
        }
    }

    private fun applyBlur(radius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            glowView.setRenderEffect(
                if (radius > 0f) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                else null
            )
        }
    }
}
