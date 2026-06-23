package com.runestone.app.input

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout

class RunestoneOverlayV2View(context: Context) : FrameLayout(context) {

    @JvmField
    val controllerView: TouchOverlayView

    @JvmField
    var keyboardView: VirtualKeyboardOverlay? = null

    var controllerVisible: Boolean = true

    @JvmField
    var keyboardVisible: Boolean = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        controllerView = TouchOverlayView(context)
        controllerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        controllerView.toolbarVisible = true
        addView(controllerView)
    }

    fun setOpacity(a: Float) { controllerView.opacity = a }
    fun setScale(s: Float) { controllerView.scale = s }
    fun setPreset(preset: TouchOverlayView.ControllerPreset) { controllerView.controllerPreset = preset }
    fun toggleMenuOverlay() { controllerView.toggleMenuOverlay() }

    fun toggleKeyboard(ctx: Context, landscapeKeys: Boolean, keyDown: (Int) -> Unit, keyUp: (Int) -> Unit) {
        if (keyboardView != null) {
            removeView(keyboardView)
            keyboardView = null
            keyboardVisible = false
            return
        }
        val kb = VirtualKeyboardOverlay(ctx)
        kb.landscapeKeys = landscapeKeys
        kb.dockMode = VirtualKeyboardOverlay.DockMode.BOTTOM
        kb.onKeyDown = { keyDown(it) }
        kb.onKeyUp = { keyUp(it) }
        kb.onDockModeChanged = { mode ->
            toggleKeyboard(ctx, landscapeKeys, keyDown, keyUp)
            toggleKeyboard(ctx, landscapeKeys, keyDown, keyUp)
        }
        addView(kb, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        keyboardView = kb
        keyboardVisible = true
    }

    fun closeKeyboard() {
        keyboardView?.let { removeView(it); keyboardView = null }
        keyboardVisible = false
    }

    fun toggleControllerVisibility() {
        controllerVisible = !controllerVisible
        controllerView.visibility = if (controllerVisible) VISIBLE else INVISIBLE
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
    }
}
