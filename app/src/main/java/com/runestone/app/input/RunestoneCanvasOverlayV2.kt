package com.runestone.app.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import kotlin.math.sqrt

class RunestoneCanvasOverlayV2(context: Context) : View(context) {

    enum class Preset { SIMPLIFIED, FULL }
    enum class ButtonId {
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        BTN_ENTER, BTN_ESC, BTN_Z, BTN_B,
        BTN_Y, BTN_X, BTN_A,
        BTN_L, BTN_R, BTN_ZL, BTN_ZR,
        BTN_L3, BTN_R3,
        BTN_SELECT, BTN_START, BTN_HOME,
        LEFT_STICK, RIGHT_STICK,
        TOOLBAR_TOGGLE, TOOLBAR_SETTINGS, TOOLBAR_KEYBOARD, TOOLBAR_POINTER,
        RECOVERY,
    }

    var preset: Preset = Preset.SIMPLIFIED
    var controllerOpacity: Float = 0.75f
    var controllerScale: Float = 1.0f
    var controllerVisible: Boolean = true
    var toolbarVisible: Boolean = true

    var onKeyDown: ((Int) -> Unit)? = null
    var onKeyUp: ((Int) -> Unit)? = null
    var onToggleControls: (() -> Unit)? = null
    var onOpenMenu: (() -> Unit)? = null
    var onRotateLayout: (() -> Unit)? = null

    private var keyboardView: VirtualKeyboardOverlay? = null

    fun toggleKeyboard(ctx: Context) {
        val existing = keyboardView
        if (existing != null) {
            val parent = existing.parent as? ViewGroup
            parent?.removeView(existing)
            keyboardView = null
            return
        }
        val kb = VirtualKeyboardOverlay(ctx)
        kb.landscapeKeys = true
        kb.dockMode = VirtualKeyboardOverlay.DockMode.BOTTOM
        kb.onKeyDown = onKeyDown
        kb.onKeyUp = onKeyUp
        kb.onDockModeChanged = { toggleKeyboard(ctx) }
        val vg = parent as? ViewGroup
        if (vg != null) {
            vg.addView(kb, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        keyboardView = kb
    }

    private val activeButtons = mutableSetOf<ButtonId>()
    private val buttonRects = mutableMapOf<ButtonId, RectF>()
    private val recoveryRect = RectF()
    private val toolbarRects = arrayOfNulls<RectF>(4)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD
    }

    private fun d(v: Int): Float = v * resources.displayMetrics.density

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) layoutControls()
    }

    private fun layoutControls() {
        buttonRects.clear()
        toolbarRects.fill(null)
        val w = width.toFloat()
        val h = height.toFloat()
        val s = controllerScale
        val isLandscape = w > h

        if (isLandscape) {
            layoutLandscape(w, h, s)
        } else {
            layoutPortrait(w, h, s)
        }
    }

    private fun layoutLandscape(w: Float, h: Float, s: Float) {
        val dpadS = (h * 0.13f * s).coerceIn(d(48), d(90))
        val gap = dpadS * 0.08f
        val btnW = dpadS * 0.44f
        val btnH = dpadS * 0.40f
        val dpadCx = w * 0.13f
        val dpadCy = h * 0.60f

        val corner = 8f * s
        buttonRects[ButtonId.DPAD_UP] = RectF(dpadCx - btnW / 2f, dpadCy - btnH - gap / 2f, dpadCx + btnW / 2f, dpadCy - gap / 2f)
        buttonRects[ButtonId.DPAD_DOWN] = RectF(dpadCx - btnW / 2f, dpadCy + gap / 2f, dpadCx + btnW / 2f, dpadCy + btnH + gap / 2f)
        buttonRects[ButtonId.DPAD_LEFT] = RectF(dpadCx - btnW - gap / 2f, dpadCy - btnH / 2f, dpadCx - gap / 2f, dpadCy + btnH / 2f)
        buttonRects[ButtonId.DPAD_RIGHT] = RectF(dpadCx + gap / 2f, dpadCy - btnH / 2f, dpadCx + btnW + gap / 2f, dpadCy + btnH / 2f)

        val actionR = (h * 0.05f * s).coerceIn(d(22), d(40))
        val actionCx = w * 0.87f
        val actionCy = h * 0.56f
        val aSpread = actionR * 2.6f

        if (preset == Preset.SIMPLIFIED) {
            val gridR = actionR * 0.9f
            val gx = actionCx
            val gy = actionCy
            buttonRects[ButtonId.BTN_ENTER] = RectF(gx - gridR, gy - gridR, gx + gridR, gy + gridR)
            buttonRects[ButtonId.BTN_ESC] = RectF(gx + aSpread * 0.5f - gridR, gy - gridR, gx + aSpread * 0.5f + gridR, gy + gridR)
            buttonRects[ButtonId.BTN_Z] = RectF(gx - gridR, gy + aSpread * 0.55f - gridR, gx + gridR, gy + aSpread * 0.55f + gridR)
            buttonRects[ButtonId.BTN_B] = RectF(gx + aSpread * 0.5f - gridR, gy + aSpread * 0.55f - gridR, gx + aSpread * 0.5f + gridR, gy + aSpread * 0.55f + gridR)
        } else {
            buttonRects[ButtonId.BTN_Y] = RectF(actionCx - actionR, actionCy - aSpread * 0.6f - actionR, actionCx + actionR, actionCy - aSpread * 0.6f + actionR)
            buttonRects[ButtonId.BTN_X] = RectF(actionCx - aSpread * 0.5f - actionR, actionCy - actionR, actionCx - aSpread * 0.5f + actionR, actionCy + actionR)
            buttonRects[ButtonId.BTN_B] = RectF(actionCx + aSpread * 0.5f - actionR, actionCy - actionR, actionCx + aSpread * 0.5f + actionR, actionCy + actionR)
            buttonRects[ButtonId.BTN_A] = RectF(actionCx - actionR, actionCy + aSpread * 0.6f - actionR, actionCx + actionR, actionCy + aSpread * 0.6f + actionR)
            buttonRects[ButtonId.BTN_ENTER] = RectF(actionCx - actionR, actionCy + aSpread * 1.5f - actionR, actionCx + actionR, actionCy + aSpread * 1.5f + actionR)
            buttonRects[ButtonId.BTN_ESC] = RectF(actionCx - actionR, actionCy + aSpread * 2.5f - actionR, actionCx + actionR, actionCy + aSpread * 2.5f + actionR)

            val stickS = (h * 0.07f * s).coerceIn(d(36), d(65))
            buttonRects[ButtonId.LEFT_STICK] = RectF(w * 0.13f - stickS, h * 0.28f - stickS, w * 0.13f + stickS, h * 0.28f + stickS)
            buttonRects[ButtonId.RIGHT_STICK] = RectF(w * 0.87f - stickS, h * 0.18f - stickS, w * 0.87f + stickS, h * 0.18f + stickS)
            buttonRects[ButtonId.BTN_L] = RectF(w * 0.05f, h * 0.04f, w * 0.05f + d(44), h * 0.04f + d(28))
            buttonRects[ButtonId.BTN_R] = RectF(w * 0.85f, h * 0.04f, w * 0.85f + d(44), h * 0.04f + d(28))
            buttonRects[ButtonId.BTN_ZL] = RectF(w * 0.05f, h * 0.04f + d(32), w * 0.05f + d(44), h * 0.04f + d(28) + d(32))
            buttonRects[ButtonId.BTN_ZR] = RectF(w * 0.85f, h * 0.04f + d(32), w * 0.85f + d(44), h * 0.04f + d(28) + d(32))
            buttonRects[ButtonId.BTN_L3] = RectF(w * 0.13f - d(16), h * 0.36f, w * 0.13f + d(16), h * 0.36f + d(24))
            buttonRects[ButtonId.BTN_R3] = RectF(w * 0.87f - d(16), h * 0.26f, w * 0.87f + d(16), h * 0.26f + d(24))
            buttonRects[ButtonId.BTN_SELECT] = RectF(w * 0.30f - d(24), h * 0.92f - d(14), w * 0.30f + d(24), h * 0.92f + d(14))
            buttonRects[ButtonId.BTN_START] = RectF(w * 0.50f - d(24), h * 0.92f - d(14), w * 0.50f + d(24), h * 0.92f + d(14))
            buttonRects[ButtonId.BTN_HOME] = RectF(w * 0.70f - d(24), h * 0.92f - d(14), w * 0.70f + d(24), h * 0.92f + d(14))
        }

        layoutToolbar(w, h, s)
        val rSize = d(22)
        recoveryRect.set(w - rSize - d(8), d(8), w - d(8), d(8) + rSize * 2)
    }

    private fun layoutPortrait(w: Float, h: Float, s: Float) {
        val panelTop = h * 0.50f
        val ch = h - panelTop
        val dpadS = (ch * 0.22f * s).coerceIn(d(40), d(76))
        val gap = dpadS * 0.08f
        val btnW = dpadS * 0.44f
        val btnH = dpadS * 0.40f
        val dpadCx = w * 0.22f
        val dpadCy = panelTop + ch * 0.50f

        val corner = 7f * s
        buttonRects[ButtonId.DPAD_UP] = RectF(dpadCx - btnW / 2f, dpadCy - btnH - gap / 2f, dpadCx + btnW / 2f, dpadCy - gap / 2f)
        buttonRects[ButtonId.DPAD_DOWN] = RectF(dpadCx - btnW / 2f, dpadCy + gap / 2f, dpadCx + btnW / 2f, dpadCy + btnH + gap / 2f)
        buttonRects[ButtonId.DPAD_LEFT] = RectF(dpadCx - btnW - gap / 2f, dpadCy - btnH / 2f, dpadCx - gap / 2f, dpadCy + btnH / 2f)
        buttonRects[ButtonId.DPAD_RIGHT] = RectF(dpadCx + gap / 2f, dpadCy - btnH / 2f, dpadCx + btnW + gap / 2f, dpadCy + btnH / 2f)

        val actionR = (ch * 0.09f * s).coerceIn(d(20), d(36))
        val actionCx = w * 0.78f
        val actionCy = panelTop + ch * 0.50f
        val aSpread = actionR * 2.4f

        if (preset == Preset.SIMPLIFIED) {
            val gridR = actionR * 0.9f
            buttonRects[ButtonId.BTN_ENTER] = RectF(actionCx - aSpread * 0.3f - gridR, actionCy - aSpread * 0.3f - gridR, actionCx - aSpread * 0.3f + gridR, actionCy - aSpread * 0.3f + gridR)
            buttonRects[ButtonId.BTN_ESC] = RectF(actionCx + aSpread * 0.3f - gridR, actionCy - aSpread * 0.3f - gridR, actionCx + aSpread * 0.3f + gridR, actionCy - aSpread * 0.3f + gridR)
            buttonRects[ButtonId.BTN_Z] = RectF(actionCx - aSpread * 0.3f - gridR, actionCy + aSpread * 0.3f - gridR, actionCx - aSpread * 0.3f + gridR, actionCy + aSpread * 0.3f + gridR)
            buttonRects[ButtonId.BTN_B] = RectF(actionCx + aSpread * 0.3f - gridR, actionCy + aSpread * 0.3f - gridR, actionCx + aSpread * 0.3f + gridR, actionCy + aSpread * 0.3f + gridR)
        } else {
            buttonRects[ButtonId.BTN_Y] = RectF(actionCx - actionR, actionCy - aSpread * 0.6f - actionR, actionCx + actionR, actionCy - aSpread * 0.6f + actionR)
            buttonRects[ButtonId.BTN_X] = RectF(actionCx - aSpread * 0.5f - actionR, actionCy - actionR, actionCx - aSpread * 0.5f + actionR, actionCy + actionR)
            buttonRects[ButtonId.BTN_B] = RectF(actionCx + aSpread * 0.5f - actionR, actionCy - actionR, actionCx + aSpread * 0.5f + actionR, actionCy + actionR)
            buttonRects[ButtonId.BTN_A] = RectF(actionCx - actionR, actionCy + aSpread * 0.6f - actionR, actionCx + actionR, actionCy + aSpread * 0.6f + actionR)
            buttonRects[ButtonId.BTN_SELECT] = RectF(w * 0.25f - d(22), panelTop + ch * 0.85f - d(12), w * 0.25f + d(22), panelTop + ch * 0.85f + d(12))
            buttonRects[ButtonId.BTN_START] = RectF(w * 0.50f - d(22), panelTop + ch * 0.85f - d(12), w * 0.50f + d(22), panelTop + ch * 0.85f + d(12))
            buttonRects[ButtonId.BTN_HOME] = RectF(w * 0.75f - d(22), panelTop + ch * 0.85f - d(12), w * 0.75f + d(22), panelTop + ch * 0.85f + d(12))
        }
        layoutToolbarPortrait(w, h, s, panelTop)
        val rSize = d(20)
        recoveryRect.set(w - rSize - d(6), panelTop + d(6), w - d(6), panelTop + d(6) + rSize * 2)
    }

    private fun layoutToolbar(w: Float, h: Float, s: Float) {
        val btnS = (d(44) * s).coerceIn(d(40), d(56))
        val gap = d(8) * s
        val totalH = btnS * 4 + gap * 3
        val tbLeft = w - btnS - d(6)
        val tbTop = (h - totalH) / 2f
        repeat(4) { i -> toolbarRects[i] = RectF(tbLeft, tbTop + i * (btnS + gap), tbLeft + btnS, tbTop + i * (btnS + gap) + btnS) }
    }

    private fun layoutToolbarPortrait(w: Float, h: Float, s: Float, panelTop: Float) {
        val btnS = (d(40) * s).coerceIn(d(36), d(50))
        val gap = d(6) * s
        val tbLeft = w - btnS - d(6)
        val tbTop = panelTop + d(8)
        repeat(4) { i -> toolbarRects[i] = RectF(tbLeft, tbTop + i * (btnS + gap), tbLeft + btnS, tbTop + i * (btnS + gap) + btnS) }
    }

    override fun onDraw(canvas: Canvas) {
        val a = controllerOpacity

        if (controllerVisible) {
            drawDPad(canvas, a)
            drawActionButtons(canvas, a)
            if (preset == Preset.FULL) drawFullExtras(canvas, a)
            if (toolbarVisible) drawToolbar(canvas, a)
        }
        drawRecovery(canvas, a)
    }

    private fun drawDPad(canvas: Canvas, a: Float) {
        val dirs = listOf(ButtonId.DPAD_UP to "up", ButtonId.DPAD_DOWN to "down", ButtonId.DPAD_LEFT to "left", ButtonId.DPAD_RIGHT to "right")
        dirs.forEach { (id, dir) ->
            val rect = buttonRects[id] ?: return@forEach
            val pressed = id in activeButtons
            val r = 8f
            CanvasGlassStyle.drawRoundRect(canvas, rect, r, pressed, a, "")
            val cx = rect.centerX(); val cy = rect.centerY(); val sz = rect.width() * 0.3f
            CanvasGlassStyle.drawArrow(canvas, cx, cy, dir, sz, pressed, a)
        }
    }

    private fun drawActionButtons(canvas: Canvas, a: Float) {
        if (preset == Preset.SIMPLIFIED) {
            drawCircleBtn(canvas, ButtonId.BTN_ENTER, "ENTER", a)
            drawCircleBtn(canvas, ButtonId.BTN_ESC, "ESC", a)
            drawCircleBtn(canvas, ButtonId.BTN_Z, "Z", a)
            drawCircleBtn(canvas, ButtonId.BTN_B, "B", a)
        } else {
            drawCircleBtn(canvas, ButtonId.BTN_Y, "Y", a)
            drawCircleBtn(canvas, ButtonId.BTN_X, "X", a)
            drawCircleBtn(canvas, ButtonId.BTN_B, "B", a)
            drawCircleBtn(canvas, ButtonId.BTN_A, "A", a)
            drawCircleBtn(canvas, ButtonId.BTN_ENTER, "ENTER", a)
            drawCircleBtn(canvas, ButtonId.BTN_ESC, "ESC", a)
        }
    }

    private fun drawCircleBtn(canvas: Canvas, id: ButtonId, label: String, a: Float) {
        val rect = buttonRects[id] ?: return
        val r = rect.width() / 2f
        val pressed = id in activeButtons
        val ls = r * 0.7f
        CanvasGlassStyle.drawCircle(canvas, rect.centerX(), rect.centerY(), r, pressed, a, label, if (label.length > 1) ls * 0.7f else ls)
    }

    private fun drawFullExtras(canvas: Canvas, a: Float) {
        fun drawShoulder(id: ButtonId, label: String) {
            val rect = buttonRects[id] ?: return
            CanvasGlassStyle.drawRoundRect(canvas, rect, 6f, id in activeButtons, a, label, rect.height() * 0.5f)
        }
        drawShoulder(ButtonId.BTN_L, "L")
        drawShoulder(ButtonId.BTN_R, "R")
        drawShoulder(ButtonId.BTN_ZL, "ZL")
        drawShoulder(ButtonId.BTN_ZR, "ZR")
        drawShoulder(ButtonId.BTN_L3, "L3")
        drawShoulder(ButtonId.BTN_R3, "R3")

        fun drawStick(id: ButtonId) {
            val rect = buttonRects[id] ?: return
            val cx = rect.centerX(); val cy = rect.centerY(); val r = rect.width() / 2f
            CanvasGlassStyle.drawCircle(canvas, cx, cy, r, id in activeButtons, a, "", 0f)
            CanvasGlassStyle.drawCircle(canvas, cx, cy, r * 0.4f, false, a, "", 0f)
        }
        drawStick(ButtonId.LEFT_STICK)
        drawStick(ButtonId.RIGHT_STICK)

        fun drawPill(id: ButtonId, label: String) {
            val rect = buttonRects[id] ?: return
            CanvasGlassStyle.drawRoundRect(canvas, rect, rect.height() / 2f, id in activeButtons, a, label, rect.height() * 0.5f)
        }
        drawPill(ButtonId.BTN_SELECT, "SELECT")
        drawPill(ButtonId.BTN_START, "START")
        drawPill(ButtonId.BTN_HOME, "HOME")
    }

    private fun drawToolbar(canvas: Canvas, a: Float) {
        data class TbIcon(val id: ButtonId, val draw: (Canvas, Float, Float, Float, Paint) -> Unit)
        val icons = listOf(
            TbIcon(ButtonId.TOOLBAR_TOGGLE, { c, cx, cy, s, p -> CanvasGlassStyle.Icons.gamepad(c, cx, cy, s, p) }),
            TbIcon(ButtonId.TOOLBAR_SETTINGS, { c, cx, cy, s, p -> CanvasGlassStyle.Icons.sliders(c, cx, cy, s, p) }),
            TbIcon(ButtonId.TOOLBAR_KEYBOARD, { c, cx, cy, s, p -> CanvasGlassStyle.Icons.keyboard(c, cx, cy, s, p) }),
            TbIcon(ButtonId.TOOLBAR_POINTER, { c, cx, cy, s, p -> CanvasGlassStyle.Icons.touchPointer(c, cx, cy, s, p) }),
        )
        icons.forEachIndexed { i, icon ->
            val rect = toolbarRects[i] ?: return@forEachIndexed
            val pressed = icon.id in activeButtons
            CanvasGlassStyle.drawRoundRect(canvas, rect, 10f, pressed, a, "", 0f)
            CanvasGlassStyle.drawIcon(canvas, rect.centerX(), rect.centerY(), rect.width() * 0.3f, a, icon.draw)
        }
    }

    private fun drawRecovery(canvas: Canvas, a: Float) {
        val r = recoveryRect.height() / 2f
        val pressed = ButtonId.RECOVERY in activeButtons
        CanvasGlassStyle.drawRoundRect(canvas, recoveryRect, r, pressed, a, "\u2022\u2022\u2022", r * 0.8f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = hitTest(event.x, event.y)
                if (id != null) {
                    activeButtons.add(id)
                    dispatchInput(id, true)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val prev = activeButtons.toSet()
                val curr = mutableSetOf<ButtonId>()
                for (i in 0 until event.pointerCount) {
                    val hit = hitTest(event.getX(i), event.getY(i))
                    if (hit != null) curr.add(hit)
                }
                val released = prev - curr
                val pressed = curr - prev
                released.forEach { dispatchInput(it, false); activeButtons.remove(it) }
                pressed.forEach { dispatchInput(it, true); activeButtons.add(it) }
                if (released.isNotEmpty() || pressed.isNotEmpty()) invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val upIdx = event.actionIndex
                val curr = mutableSetOf<ButtonId>()
                for (i in 0 until event.pointerCount) {
                    if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && i == upIdx) continue
                    val hit = hitTest(event.getX(i), event.getY(i))
                    if (hit != null) curr.add(hit)
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) curr.clear()
                val released = activeButtons - curr
                released.forEach { dispatchInput(it, false); activeButtons.remove(it) }
                if (released.isNotEmpty()) invalidate()
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float): ButtonId? {
        if (recoveryRect.contains(x, y)) return ButtonId.RECOVERY
        if (!controllerVisible) return null
        toolbarRects.forEachIndexed { i, rect ->
            if (rect != null && rect.contains(x, y)) return when (i) { 0 -> ButtonId.TOOLBAR_TOGGLE; 1 -> ButtonId.TOOLBAR_SETTINGS; 2 -> ButtonId.TOOLBAR_KEYBOARD; else -> ButtonId.TOOLBAR_POINTER }
        }
        return buttonRects.entries.firstOrNull { (_, rect) -> rect.contains(x, y) }?.key
    }

    private fun dispatchInput(id: ButtonId, pressed: Boolean) {
        when (id) {
            ButtonId.DPAD_UP -> sendKey(KeyEvent.KEYCODE_DPAD_UP, pressed)
            ButtonId.DPAD_DOWN -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN, pressed)
            ButtonId.DPAD_LEFT -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT, pressed)
            ButtonId.DPAD_RIGHT -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, pressed)
            ButtonId.BTN_ENTER -> sendKey(KeyEvent.KEYCODE_ENTER, pressed)
            ButtonId.BTN_ESC -> sendKey(KeyEvent.KEYCODE_ESCAPE, pressed)
            ButtonId.BTN_Z -> sendKey(KeyEvent.KEYCODE_Z, pressed)
            ButtonId.BTN_B -> sendKey(KeyEvent.KEYCODE_B, pressed)
            ButtonId.BTN_Y -> sendKey(KeyEvent.KEYCODE_W, pressed)
            ButtonId.BTN_X -> sendKey(KeyEvent.KEYCODE_Q, pressed)
            ButtonId.BTN_A -> sendKey(KeyEvent.KEYCODE_ENTER, pressed)
            ButtonId.BTN_L -> sendKey(KeyEvent.KEYCODE_PAGE_UP, pressed)
            ButtonId.BTN_R -> sendKey(KeyEvent.KEYCODE_PAGE_DOWN, pressed)
            ButtonId.BTN_ZL -> sendKey(KeyEvent.KEYCODE_PAGE_UP, pressed)
            ButtonId.BTN_ZR -> sendKey(KeyEvent.KEYCODE_PAGE_DOWN, pressed)
            ButtonId.BTN_L3 -> sendKey(KeyEvent.KEYCODE_Z, pressed)
            ButtonId.BTN_R3 -> sendKey(KeyEvent.KEYCODE_X, pressed)
            ButtonId.BTN_SELECT -> sendKey(KeyEvent.KEYCODE_ESCAPE, pressed)
            ButtonId.BTN_START -> sendKey(KeyEvent.KEYCODE_ENTER, pressed)
            ButtonId.BTN_HOME -> if (pressed) onKeyDown?.invoke(KeyEvent.KEYCODE_HOME)
            ButtonId.LEFT_STICK -> sendKey(KeyEvent.KEYCODE_DPAD_UP, pressed)
            ButtonId.RIGHT_STICK -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN, pressed)
            ButtonId.TOOLBAR_TOGGLE -> if (pressed) onToggleControls?.invoke()
            ButtonId.TOOLBAR_SETTINGS -> if (pressed) onOpenMenu?.invoke()
            ButtonId.TOOLBAR_KEYBOARD -> if (pressed) toggleKeyboard(context)
            ButtonId.TOOLBAR_POINTER -> if (pressed) {}
            ButtonId.RECOVERY -> if (pressed) onOpenMenu?.invoke()
        }
    }

    private fun sendKey(code: Int, pressed: Boolean) {
        if (code == KeyEvent.KEYCODE_UNKNOWN) return
        if (pressed) onKeyDown?.invoke(code) else onKeyUp?.invoke(code)
    }
}
