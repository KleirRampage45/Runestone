package com.runestone.app.input

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import com.runestone.app.R

class TouchOverlayView(context: Context) : View(context) {

    /** Which "zone" the user is touching right now. Each zone maps to a key. */
    enum class Zone {
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        BTN_Y, BTN_X, BTN_B, BTN_A,
        SELECT, START, MENU, SETTINGS, HOME,
        L1, R1,
    }

    var opacity: Float = 0.72f
    var scale: Float = 1.0f
    var hapticsEnabled: Boolean = true
    var hapticIntensity: Float = 0.55f
    var controlsOnly: Boolean = false
    var showExtraButtons: Boolean = false
    var diagonalMovement: Boolean = false
    var onInput: ((Zone, pressed: Boolean) -> Unit)? = null
    var onToggleControls: (() -> Unit)? = null
    var onRotateLayout: (() -> Unit)? = null
    var onProfileLayoutChanged: ((List<ControlButtonProfile>) -> Unit)? = null

    // Active presses for visual feedback
    private val activeZones = mutableSetOf<Zone>()
    private var editing = false
    private var selectedControl: Control? = null
    private var draggingControl: Control? = null
    private var initialPinchDistance = 0f
    private var initialPinchSize = 0f
    private var savedLayoutBeforeEdit: Map<Control, ControlPlacement> = emptyMap()

    // Measured positions (set on layout)
    private val dpadCenter = PointF(0f, 0f)
    private var dpadRadius = 72f
    private var dpadInnerRadius = 40f

    private val btnY = PointF(0f, 0f)
    private val btnA = PointF(0f, 0f)
    private val btnX = PointF(0f, 0f)
    private val btnB = PointF(0f, 0f)
    private var actionRadius = 38f

    private val selectRect = RectF()
    private val startRect = RectF()
    private val menuRect = RectF()
    private val imageRect = RectF()
    private val editButtonRect = RectF()
    private val quickSettingsRect = RectF()
    private val doneRect = RectF()
    private val revertRect = RectF()
    private val presetRect = RectF()
    private val controlRects = mutableMapOf<Control, RectF>()
    private val layout = mutableMapOf<Control, ControlPlacement>()
    private var defaultLayout = emptyMap<Control, ControlPlacement>()
    private var loadedLayout = false
    private var quickSettingsOpen = false

    // L1/R1 shoulder button positions (fixed, not part of layout editor)
    private val l1Rect = RectF()
    private val r1Rect = RectF()
    private val shoulderRadius = 20f

    private val buttonBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_button_circle) }
    private val buttonHighlightBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_button_circle_highlight) }
    private val wideButtonBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_button_wide) }
    private val wideButtonHighlightBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_button_wide_highlight) }
    private val dpadBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_dpad) }
    private val dpadHighlightBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_dpad_highlight) }
    private val menuIconBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_icon_menu) }
    private val startIconBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_icon_play) }
    private val selectIconBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_icon_pause) }
    private val wrenchIconBitmap: Bitmap? by lazy { bitmapOrNull(R.drawable.controller_icon_wrench) }

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0) }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val btnPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 200, 170, 130)
        style = Paint.Style.FILL
    }
    private val btnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isFakeBoldText = true
    }
    private val smallLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 170, 130)
        textAlign = Paint.Align.CENTER
        textSize = 18f
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val editorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(210, 180, 134)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val vibrator: Vibrator? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = width.toFloat()
        val h = height.toFloat()
        val s = scale
        val shortSide = minOf(w, h)
        val isLandscape = w > h && !controlsOnly

        if (isLandscape) {
            // Landscape: controls at edges, game in center
            val pad = 32f * s
            val dpadActualRadius = (shortSide * 0.12f).coerceIn(80f, 140f)
            dpadRadius = dpadActualRadius / s
            dpadInnerRadius = dpadRadius * 0.42f
            val actionActualRadius = (shortSide * 0.07f).coerceIn(56f, 90f)
            actionRadius = actionActualRadius / s

            dpadCenter.x = pad + dpadActualRadius
            dpadCenter.y = h * 0.5f

            val actionCX = w - pad - actionActualRadius
            val actionCY = h * 0.5f
            val spread = 52f * s
            btnY.x = actionCX; btnY.y = actionCY - spread
            btnB.x = actionCX + spread; btnB.y = actionCY
            btnA.x = actionCX; btnA.y = actionCY + spread
            btnX.x = actionCX - spread; btnX.y = actionCY

            val barH = 48f * s; val barW = 120f * s; val barY = h - pad - barH
            selectRect.set(pad, barY - barH / 2, pad + barW, barY + barH / 2)
            startRect.set(w * 0.5f - barW / 2, barY - barH / 2, w * 0.5f + barW / 2, barY + barH / 2)
            menuRect.set(w - pad - barW, barY - barH / 2, w - pad, barY + barH / 2)

            // L1/R1 shoulder buttons — floating pill buttons above bar
            val shoulderH = 26f * s
            val shoulderW = 42f * s
            l1Rect.set(pad, barY - barH / 2 - shoulderH - 6f * s, pad + shoulderW, barY - barH / 2 - 6f * s)
            r1Rect.set(w - pad - shoulderW, barY - barH / 2 - shoulderH - 6f * s, w - pad, barY - barH / 2 - 6f * s)
        } else {
            // Portrait (or controlsOnly): standard bottom panel positioning
            val panelTop = if (controlsOnly) 0f else h * 0.55f
            val controlsHeight = h - panelTop

            if (controlsOnly) {
                val dpadActualRadius = (shortSide * 0.17f).coerceIn(120f, 190f)
                dpadRadius = dpadActualRadius / s
                dpadInnerRadius = dpadRadius * 0.42f
                val actionActualRadius = (shortSide * 0.09f).coerceIn(76f, 112f)
                actionRadius = actionActualRadius / s
            }
            dpadCenter.x = if (controlsOnly) w * 0.25f else w * 0.22f
            dpadCenter.y = panelTop + controlsHeight * if (controlsOnly) 0.40f else 0.50f

            val actionCenterX = if (controlsOnly) w * 0.76f else w * 0.78f
            val actionCenterY = panelTop + controlsHeight * if (controlsOnly) 0.39f else 0.50f
            val spread = if (controlsOnly) (actionRadius * s * 1.75f).coerceAtLeast(150f) else 52f * s
            btnY.x = actionCenterX; btnY.y = actionCenterY - spread
            btnB.x = actionCenterX + spread; btnB.y = actionCenterY
            btnA.x = actionCenterX; btnA.y = actionCenterY + spread
            btnX.x = actionCenterX - spread; btnX.y = actionCenterY

            val barY = if (controlsOnly) panelTop + controlsHeight * 0.82f else h - 60f * s
            val barH = 52f * s; val barW = 140f * s
            selectRect.set(w * 0.20f - barW / 2, barY - barH / 2, w * 0.20f + barW / 2, barY + barH / 2)
            startRect.set(w * 0.50f - barW / 2, barY - barH / 2, w * 0.50f + barW / 2, barY + barH / 2)
            menuRect.set(w * 0.80f - barW / 2, barY - barH / 2, w * 0.80f + barW / 2, barY + barH / 2)

            // L1/R1 shoulder buttons — small pills at top of control panel
            val shoulderH = 26f * s
            val shoulderW = 42f * s
            val shoulderY = panelTop + 10f * s
            l1Rect.set(10f, shoulderY, 10f + shoulderW, shoulderY + shoulderH)
            r1Rect.set(w - 10f - shoulderW, shoulderY, w - 10f, shoulderY + shoulderH)
        }

        editButtonRect.set(w - 76f * s, h - 76f * s, w - 8f * s, h - 8f * s)
        val quickW = minOf(w - 32f * s, 420f * s)
        quickSettingsRect.set(w * 0.5f - quickW / 2f, 14f * s, w * 0.5f + quickW / 2f, 62f * s)
        doneRect.set(12f * s, 12f * s, 128f * s, 62f * s)
        revertRect.set(140f * s, 12f * s, 268f * s, 62f * s)
        presetRect.set(280f * s, 12f * s, 398f * s, 62f * s)

        defaultLayout = captureCurrentLayout()
        if (!loadedLayout) {
            loadLayout()
            loadedLayout = true
        }
        applySavedLayout()
    }

    override fun onDraw(canvas: Canvas) {
        val a = opacity
        val isLandscape = width > height

        // ── Control panel background (skip in landscape — controls float) ──
        if (!isLandscape) {
            val panelTop = if (controlsOnly) 0f else height * 0.55f
            bgPaint.alpha = (140 * a).toInt()
            canvas.drawRect(0f, panelTop, width.toFloat(), height.toFloat(), bgPaint)

            val linePaint = Paint().apply {
                color = Color.argb((80 * a).toInt(), 200, 170, 130); strokeWidth = 2f
            }
            canvas.drawLine(0f, panelTop, width.toFloat(), panelTop, linePaint)
        }

        // ── D-Pad ──
        drawDPad(canvas, a)

        // ── Action Buttons ──
        if (showExtraButtons) {
            drawButton(canvas, btnY.x, btnY.y, radiusFor(Control.Y), "Y", a, Zone.BTN_Y)
            drawButton(canvas, btnX.x, btnX.y, radiusFor(Control.X), "X", a, Zone.BTN_X)
        }
        drawButton(canvas, btnB.x, btnB.y, radiusFor(Control.B), "B", a, Zone.BTN_B)
        drawButton(canvas, btnA.x, btnA.y, radiusFor(Control.A), "A", a, Zone.BTN_A)

        // ── Bottom Bar ──
        drawBarButton(canvas, selectRect, "SELECT", a, Zone.SELECT)
        drawBarButton(canvas, startRect, "START", a, Zone.START)
        drawBarButton(canvas, menuRect, "HOME", a, Zone.HOME)

        // ── L1/R1 Shoulder Buttons ──
        drawShoulderButton(canvas, l1Rect, "L1", a, Zone.L1)
        drawShoulderButton(canvas, r1Rect, "R1", a, Zone.R1)

        drawEditButton(canvas, a)
        if (quickSettingsOpen) drawQuickSettings(canvas, a)
        if (editing) drawEditorChrome(canvas)
    }

    fun toggleQuickSettings() {
        quickSettingsOpen = !quickSettingsOpen
        invalidate()
    }

    fun openLayoutEditor() {
        startEditing()
    }

    private fun drawQuickSettings(canvas: Canvas, a: Float) {
        editorPaint.alpha = (185 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(quickSettingsRect, 14f * scale, 14f * scale, editorPaint)
        btnStroke.alpha = (105 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(quickSettingsRect, 14f * scale, 14f * scale, btnStroke)

        val cellCount = 9
        val cellW = quickSettingsRect.width() / cellCount.toFloat()
        for (i in 0 until cellCount) {
            val cx = quickSettingsRect.left + cellW * i + cellW / 2f
            val cy = quickSettingsRect.centerY()
            drawQuickIcon(canvas, i, cx, cy, minOf(cellW, quickSettingsRect.height()) * 0.28f, a)
        }
    }

    private fun drawQuickIcon(canvas: Canvas, index: Int, cx: Float, cy: Float, s: Float, a: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.4f * scale
            color = Color.argb((220 * a).toInt().coerceIn(0, 255), 235, 232, 220)
            textAlign = Paint.Align.CENTER
            textSize = 12f * scale
            isFakeBoldText = true
        }
        val path = Path()
        when (index) {
            0 -> {
                path.moveTo(cx - s, cy); path.lineTo(cx, cy - s); path.lineTo(cx + s, cy)
                canvas.drawPath(path, paint)
                canvas.drawLine(cx - s * 0.6f, cy, cx - s * 0.6f, cy + s, paint)
                canvas.drawLine(cx + s * 0.6f, cy, cx + s * 0.6f, cy + s, paint)
                canvas.drawLine(cx - s * 0.6f, cy + s, cx + s * 0.6f, cy + s, paint)
            }
            1, 2 -> {
                canvas.drawCircle(cx, cy, s, paint)
                canvas.drawLine(cx - s * 0.45f, cy, cx + s * 0.45f, cy, paint)
                if (index == 2) canvas.drawLine(cx, cy - s * 0.45f, cx, cy + s * 0.45f, paint)
            }
            3, 4 -> {
                canvas.drawRoundRect(RectF(cx - s, cy - s * 0.65f, cx + s, cy + s * 0.65f), s * 0.25f, s * 0.25f, paint)
                canvas.drawText(if (index == 3) "O" else "S", cx, cy + s * 0.35f, paint)
            }
            5 -> {
                canvas.drawLine(cx - s, cy, cx + s, cy, paint)
                canvas.drawLine(cx, cy - s, cx, cy + s, paint)
                canvas.drawCircle(cx, cy, s * 0.25f, paint)
            }
            6 -> {
                canvas.drawCircle(cx, cy, s, paint)
                canvas.drawLine(cx, cy, cx + s * 0.75f, cy - s * 0.75f, paint)
            }
            7 -> {
                canvas.drawRoundRect(RectF(cx - s, cy - s * 0.75f, cx + s, cy + s * 0.75f), s * 0.2f, s * 0.2f, paint)
                canvas.drawLine(cx - s * 0.45f, cy, cx + s * 0.45f, cy, paint)
            }
            8 -> {
                canvas.drawArc(RectF(cx - s, cy - s, cx + s, cy + s), 40f, 260f, false, paint)
                path.moveTo(cx + s * 0.65f, cy - s * 0.55f)
                path.lineTo(cx + s * 0.95f, cy - s * 0.2f)
                path.lineTo(cx + s * 0.45f, cy - s * 0.18f)
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun drawDPad(canvas: Canvas, a: Float) {
        val cx = dpadCenter.x
        val cy = dpadCenter.y
        val outer = dpadRadius * scale
        val inner = dpadInnerRadius * scale

        val paint = if (activeZones.any { it.name.startsWith("DPAD") }) btnPressedPaint else btnPaint
        paint.alpha = (100 * a).toInt()
        btnStroke.alpha = (120 * a).toInt()
        val pressed = activeZones.any { it.name.startsWith("DPAD") }
        val bitmap = if (pressed) dpadHighlightBitmap ?: dpadBitmap else dpadBitmap
        if (bitmap != null) {
            imagePaint.alpha = (235 * a).toInt().coerceIn(0, 255)
            imageRect.set(cx - outer, cy - outer, cx + outer, cy + outer)
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint)
        } else {
            canvas.drawCircle(cx, cy, outer, btnStroke)
            canvas.drawCircle(cx, cy, outer, paint)
        }

        drawDpadChevron(canvas, cx, cy - inner * 0.72f, outer * 0.18f, Direction.UP, Zone.DPAD_UP, a)
        drawDpadChevron(canvas, cx, cy + inner * 0.72f, outer * 0.18f, Direction.DOWN, Zone.DPAD_DOWN, a)
        drawDpadChevron(canvas, cx - inner * 0.72f, cy, outer * 0.18f, Direction.LEFT, Zone.DPAD_LEFT, a)
        drawDpadChevron(canvas, cx + inner * 0.72f, cy, outer * 0.18f, Direction.RIGHT, Zone.DPAD_RIGHT, a)
        drawSelection(canvas, Control.DPAD)
    }

    private enum class Direction { UP, DOWN, LEFT, RIGHT }

    private fun drawDpadChevron(canvas: Canvas, x: Float, y: Float, size: Float, direction: Direction, zone: Zone, a: Float) {
        val pressed = zone in activeZones
        val path = Path()
        when (direction) {
            Direction.UP -> {
                path.moveTo(x, y - size)
                path.lineTo(x - size, y + size * 0.7f)
                path.lineTo(x + size, y + size * 0.7f)
            }
            Direction.DOWN -> {
                path.moveTo(x, y + size)
                path.lineTo(x - size, y - size * 0.7f)
                path.lineTo(x + size, y - size * 0.7f)
            }
            Direction.LEFT -> {
                path.moveTo(x - size, y)
                path.lineTo(x + size * 0.7f, y - size)
                path.lineTo(x + size * 0.7f, y + size)
            }
            Direction.RIGHT -> {
                path.moveTo(x + size, y)
                path.lineTo(x - size * 0.7f, y - size)
                path.lineTo(x - size * 0.7f, y + size)
            }
        }
        path.close()

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (pressed) Color.rgb(200, 170, 130) else Color.argb((210 * a).toInt(), 255, 255, 255)
        }
        canvas.drawPath(path, arrowPaint)
    }

    private fun drawButton(canvas: Canvas, x: Float, y: Float, r: Float, label: String, a: Float, zone: Zone) {
        val zonePressed = zone in activeZones
        val paint = if (zonePressed) btnPressedPaint else btnPaint
        paint.alpha = (100 * a).toInt()
        btnStroke.alpha = (120 * a).toInt()
        val bitmap = if (zonePressed) buttonHighlightBitmap ?: buttonBitmap else buttonBitmap
        if (bitmap != null) {
            imagePaint.alpha = (235 * a).toInt().coerceIn(0, 255)
            imageRect.set(x - r, y - r, x + r, y + r)
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint)
        } else {
            canvas.drawCircle(x, y, r, btnStroke)
            canvas.drawCircle(x, y, r, paint)
        }

        labelPaint.alpha = (if (zonePressed) 255 else 200 * a).toInt()
        labelPaint.color = if (zonePressed) Color.rgb(200, 170, 130) else Color.argb((200 * a).toInt(), 255, 255, 255)
        canvas.drawText(label, x, y + 10f, labelPaint)
        drawSelection(canvas, controlForZone(zone))
    }

    private fun drawBarButton(canvas: Canvas, rect: RectF, label: String, a: Float, zone: Zone) {
        val pressed = zone in activeZones
        val paint = if (pressed) btnPressedPaint else btnPaint
        paint.alpha = (80 * a).toInt()
        btnStroke.alpha = (100 * a).toInt()

        val bitmap = if (pressed) wideButtonHighlightBitmap ?: wideButtonBitmap else wideButtonBitmap
        if (bitmap != null) {
            imagePaint.alpha = (220 * a).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bitmap, null, rect, imagePaint)
        } else {
            canvas.drawRoundRect(rect, 8f, 8f, btnStroke)
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }

        iconForZone(zone)?.let { icon ->
            val iconSize = rect.height() * 0.48f
            imageRect.set(
                rect.left + rect.width() * 0.12f,
                rect.centerY() - iconSize / 2f,
                rect.left + rect.width() * 0.12f + iconSize,
                rect.centerY() + iconSize / 2f,
            )
            imagePaint.alpha = (190 * a).toInt().coerceIn(0, 255)
            canvas.drawBitmap(icon, null, imageRect, imagePaint)
        }

        smallLabelPaint.alpha = (if (pressed) 255 else 180 * a).toInt()
        smallLabelPaint.color = if (pressed) Color.rgb(200, 170, 130) else Color.argb((180 * a).toInt(), 200, 170, 130)
        canvas.drawText(label, rect.centerX() + rect.width() * 0.08f, rect.centerY() + 5f, smallLabelPaint)
        drawSelection(canvas, controlForZone(zone))
    }

    private fun drawShoulderButton(canvas: Canvas, rect: RectF, label: String, a: Float, zone: Zone) {
        val pressed = zone in activeZones
        val paint = if (pressed) btnPressedPaint else btnPaint
        paint.alpha = (110 * a).toInt()
        btnStroke.alpha = (100 * a).toInt()
        val r = rect.height() * 0.5f
        canvas.drawRoundRect(rect, r, r, paint)
        canvas.drawRoundRect(rect, r, r, btnStroke)
        labelPaint.alpha = (if (pressed) 255 else 200 * a).toInt()
        labelPaint.color = if (pressed) Color.rgb(200, 170, 130) else Color.argb((200 * a).toInt(), 200, 170, 130)
        labelPaint.textSize = 14f
        canvas.drawText(label, rect.centerX(), rect.centerY() + 6f, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (handleQuickSettingsTouch(event)) return true
        if (handleEditorTouch(event)) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val zones = hitTestMulti(event.getX(idx), event.getY(idx))
                if (zones.isNotEmpty()) {
                    val newZones = zones - activeZones
                    activeZones.addAll(zones)
                    newZones.forEach { onInput?.invoke(it, true) }
                    if (newZones.isNotEmpty()) vibrate()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Track which fingers are where
                val newActive = mutableSetOf<Zone>()
                for (i in 0 until event.pointerCount) {
                    newActive.addAll(hitTestMulti(event.getX(i), event.getY(i)))
                }
                val released = activeZones - newActive
                val pressed = newActive - activeZones
                released.forEach { onInput?.invoke(it, false) }
                pressed.forEach {
                    onInput?.invoke(it, true)
                    vibrate()
                }
                activeZones.clear()
                activeZones.addAll(newActive)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val newActive = mutableSetOf<Zone>()
                for (i in 0 until event.pointerCount) {
                    if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && i == event.actionIndex) continue
                    newActive.addAll(hitTestMulti(event.getX(i), event.getY(i)))
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    newActive.clear()
                }
                val released = activeZones - newActive
                released.forEach { onInput?.invoke(it, false) }
                activeZones.clear()
                activeZones.addAll(newActive)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun handleQuickSettingsTouch(event: MotionEvent): Boolean {
        if (!quickSettingsOpen) return false
        val action = event.actionMasked
        val x = event.getX(event.actionIndex)
        val y = event.getY(event.actionIndex)
        if (!quickSettingsRect.contains(x, y)) {
            if (action == MotionEvent.ACTION_DOWN && y < quickSettingsRect.bottom + 24f * scale) {
                quickSettingsOpen = false
                invalidate()
                return true
            }
            return false
        }
        if (action != MotionEvent.ACTION_UP) return true
        val index = ((x - quickSettingsRect.left) / (quickSettingsRect.width() / 9f)).toInt().coerceIn(0, 8)
        when (index) {
            0 -> onInput?.invoke(Zone.HOME, true)
            1 -> opacity = (opacity - 0.12f).coerceAtLeast(0.18f)
            2 -> opacity = (opacity + 0.12f).coerceAtMost(1f)
            3 -> scale = (scale - 0.10f).coerceAtLeast(0.55f)
            4 -> scale = (scale + 0.10f).coerceAtMost(1.75f)
            5 -> startEditing()
            6 -> showExtraButtons = !showExtraButtons
            7 -> onToggleControls?.invoke()
            8 -> onRotateLayout?.invoke()
        }
        requestLayout()
        invalidate()
        return true
    }

    private fun hitTestMulti(x: Float, y: Float): Set<Zone> {
        return if (diagonalMovement) hitTestZones(x, y) else {
            val single = hitTest(x, y)
            if (single != null) setOf(single) else emptySet()
        }
    }

    private fun hitTest(x: Float, y: Float): Zone? {
        val s = scale
        val panelTop = if (controlsOnly) 0f else height * 0.55f
        if (y < panelTop) return null // Above control panel

        // L1/R1 shoulder buttons (check first — they're at the top)
        if (l1Rect.contains(x, y)) return Zone.L1
        if (r1Rect.contains(x, y)) return Zone.R1

        // Bottom bar buttons
        if (selectRect.contains(x, y)) return Zone.SELECT
        if (startRect.contains(x, y)) return Zone.START
        if (menuRect.contains(x, y)) return Zone.HOME

        // Action buttons
        if (showExtraButtons && dist(x, y, btnY.x, btnY.y) < radiusFor(Control.Y)) return Zone.BTN_Y
        if (showExtraButtons && dist(x, y, btnX.x, btnX.y) < radiusFor(Control.X)) return Zone.BTN_X
        if (dist(x, y, btnB.x, btnB.y) < radiusFor(Control.B)) return Zone.BTN_B
        if (dist(x, y, btnA.x, btnA.y) < radiusFor(Control.A)) return Zone.BTN_A

        // D-pad
        val outer = dpadRadius * s
        val inner = dpadInnerRadius * s
        val d = dist(x, y, dpadCenter.x, dpadCenter.y)
        if (d < outer) {
            if (d < inner) return null // dead zone in center
            val angle = Math.atan2((y - dpadCenter.y).toDouble(), (x - dpadCenter.x).toDouble())
            return when {
                angle in -Math.PI / 4..Math.PI / 4 -> Zone.DPAD_RIGHT
                angle in Math.PI / 4..3 * Math.PI / 4 -> Zone.DPAD_DOWN
                angle in -3 * Math.PI / 4..-Math.PI / 4 -> Zone.DPAD_UP
                else -> Zone.DPAD_LEFT
            }
        }

        return null
    }

    private fun hitTestZones(x: Float, y: Float): Set<Zone> {
        val s = scale
        val panelTop = if (controlsOnly) 0f else height * 0.55f
        if (y < panelTop) return emptySet()

        // L1/R1 shoulder buttons (check first — they're at the top)
        if (l1Rect.contains(x, y)) return setOf(Zone.L1)
        if (r1Rect.contains(x, y)) return setOf(Zone.R1)

        // Bottom bar buttons
        if (selectRect.contains(x, y)) return setOf(Zone.SELECT)
        if (startRect.contains(x, y)) return setOf(Zone.START)
        if (menuRect.contains(x, y)) return setOf(Zone.HOME)

        // Action buttons
        val result = mutableSetOf<Zone>()
        if (showExtraButtons && dist(x, y, btnY.x, btnY.y) < radiusFor(Control.Y)) result.add(Zone.BTN_Y)
        if (showExtraButtons && dist(x, y, btnX.x, btnX.y) < radiusFor(Control.X)) result.add(Zone.BTN_X)
        if (dist(x, y, btnB.x, btnB.y) < radiusFor(Control.B)) result.add(Zone.BTN_B)
        if (dist(x, y, btnA.x, btnA.y) < radiusFor(Control.A)) result.add(Zone.BTN_A)
        if (result.isNotEmpty()) return result

        // D-pad with diagonal support: overlapping angle ranges
        val outer = dpadRadius * s
        val inner = dpadInnerRadius * s
        val d = dist(x, y, dpadCenter.x, dpadCenter.y)
        if (d < outer && d >= inner) {
            val angle = Math.atan2((y - dpadCenter.y).toDouble(), (x - dpadCenter.x).toDouble())
            val result2 = mutableSetOf<Zone>()
            if (angle in -Math.PI / 4.0 * 1.3..Math.PI / 4.0 * 1.3) result2.add(Zone.DPAD_RIGHT)
            if (angle in Math.PI / 4.0 * 0.7..3.0 * Math.PI / 4.0 * 0.85) result2.add(Zone.DPAD_DOWN)
            if (angle in -3.0 * Math.PI / 4.0 * 0.85..-Math.PI / 4.0 * 0.7) result2.add(Zone.DPAD_UP)
            if (angle <= -Math.PI * 0.75 || angle >= Math.PI * 0.75) result2.add(Zone.DPAD_LEFT)
            return result2
        }
        return emptySet()
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        Math.sqrt(((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)).toDouble()).toFloat()

    private fun vibrate() {
        runCatching {
            val intensity = hapticIntensity.coerceIn(0f, 1f)
            if (!hapticsEnabled || intensity <= 0f) return@runCatching
            val duration = (10 + 35 * intensity).toLong()
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val amplitude = (45 + 210 * intensity).toInt().coerceIn(1, 255)
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        }
    }

    private fun bitmapOrNull(resourceId: Int): Bitmap? =
        runCatching { BitmapFactory.decodeResource(resources, resourceId) }.getOrNull()

    private fun iconForZone(zone: Zone): Bitmap? = when (zone) {
        Zone.SELECT -> selectIconBitmap
        Zone.START -> startIconBitmap
        Zone.MENU, Zone.SETTINGS, Zone.HOME -> menuIconBitmap
        else -> null
    }

    private fun controlForZone(zone: Zone): Control = when (zone) {
        Zone.DPAD_UP, Zone.DPAD_DOWN, Zone.DPAD_LEFT, Zone.DPAD_RIGHT -> Control.DPAD
        Zone.BTN_A -> Control.A
        Zone.BTN_B -> Control.B
        Zone.BTN_X -> Control.X
        Zone.BTN_Y -> Control.Y
        Zone.SELECT -> Control.SELECT
        Zone.START -> Control.START
        Zone.MENU, Zone.SETTINGS -> Control.MENU
        Zone.HOME -> Control.MENU // Use MENU icon for HOME too
        Zone.L1, Zone.R1 -> Control.DPAD // Not in editor, fallback
    }

    private fun handleEditorTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val x = event.getX(event.actionIndex)
        val y = event.getY(event.actionIndex)

        if (!editing && action == MotionEvent.ACTION_DOWN && editButtonRect.contains(x, y)) {
            startEditing()
            return true
        }
        if (!editing) return false

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    doneRect.contains(x, y) -> {
                        saveLayout()
                        editing = false
                        selectedControl = null
                        invalidate()
                    }
                    revertRect.contains(x, y) -> {
                        layout.clear()
                        layout.putAll(savedLayoutBeforeEdit.mapValues { it.value.copy() })
                        applySavedLayout()
                        editing = false
                        selectedControl = null
                        invalidate()
                    }
                    presetRect.contains(x, y) -> {
                        resetToPreset()
                    }
                    else -> {
                        draggingControl = hitTestControl(x, y)
                        selectedControl = draggingControl
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                selectedControl?.let {
                    initialPinchDistance = pointerDistance(event)
                    initialPinchSize = layout[it]?.size ?: 0f
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val selected = selectedControl
                if (event.pointerCount >= 2 && selected != null && initialPinchDistance > 1f) {
                    val nextSize = initialPinchSize * (pointerDistance(event) / initialPinchDistance)
                    layout[selected]?.size = nextSize.coerceIn(0.045f, if (selected == Control.DPAD) 0.32f else 0.18f)
                    applySavedLayout()
                    invalidate()
                    return true
                }
                draggingControl?.let { control ->
                    layout[control]?.let { placement ->
                        placement.x = (event.x / width.coerceAtLeast(1)).coerceIn(0.04f, 0.96f)
                        placement.y = (event.y / height.coerceAtLeast(1)).coerceIn(0.04f, 0.96f)
                        applySavedLayout()
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                draggingControl = null
                initialPinchDistance = 0f
                return true
            }
        }
        return true
    }

    private fun startEditing() {
        savedLayoutBeforeEdit = layout.mapValues { it.value.copy() }
        editing = true
        selectedControl = null
        activeZones.clear()
        invalidate()
    }

    private fun drawEditButton(canvas: Canvas, a: Float) {
        val pressedAlpha = if (editing) 210 else 150
        editorPaint.alpha = (pressedAlpha * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(editButtonRect, 10f, 10f, editorPaint)
        btnStroke.alpha = (120 * a).toInt()
        canvas.drawRoundRect(editButtonRect, 10f, 10f, btnStroke)
        wrenchIconBitmap?.let {
            val pad = editButtonRect.width() * 0.22f
            imageRect.set(editButtonRect.left + pad, editButtonRect.top + pad, editButtonRect.right - pad, editButtonRect.bottom - pad)
            imagePaint.alpha = (210 * a).toInt().coerceIn(0, 255)
            canvas.drawBitmap(it, null, imageRect, imagePaint)
        } ?: run {
            smallLabelPaint.alpha = (210 * a).toInt().coerceIn(0, 255)
            canvas.drawText("EDIT", editButtonRect.centerX(), editButtonRect.centerY() + 5f, smallLabelPaint)
        }
    }

    private fun drawEditorChrome(canvas: Canvas) {
        editorPaint.alpha = 150
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), editorPaint)
        drawEditorButton(canvas, doneRect, "DONE")
        drawEditorButton(canvas, revertRect, "REVERT")
        drawEditorButton(canvas, presetRect, "PRESET")
        smallLabelPaint.alpha = 230
        smallLabelPaint.color = Color.rgb(232, 229, 220)
        canvas.drawText("Drag controls. Pinch selected control to resize.", width / 2f, height - 20f, smallLabelPaint)
    }

    private fun drawEditorButton(canvas: Canvas, rect: RectF, label: String) {
        editorPaint.alpha = 210
        canvas.drawRoundRect(rect, 8f, 8f, editorPaint)
        selectedStrokePaint.alpha = 180
        canvas.drawRoundRect(rect, 8f, 8f, selectedStrokePaint)
        smallLabelPaint.alpha = 235
        smallLabelPaint.color = Color.rgb(232, 229, 220)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 5f, smallLabelPaint)
    }

    private fun drawSelection(canvas: Canvas, control: Control) {
        if (!editing || selectedControl != control) return
        controlRects[control]?.let {
            selectedStrokePaint.alpha = 245
            canvas.drawRoundRect(it, 12f, 12f, selectedStrokePaint)
        }
    }

    private fun hitTestControl(x: Float, y: Float): Control? =
        Control.entries.lastOrNull {
            (showExtraButtons || (it != Control.X && it != Control.Y)) &&
                controlRects[it]?.contains(x, y) == true
        }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return dist(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
    }

    private fun captureCurrentLayout(): Map<Control, ControlPlacement> {
        val shortSide = minOf(width, height).coerceAtLeast(1).toFloat()
        return mapOf(
            Control.DPAD to ControlPlacement(dpadCenter.x / width, dpadCenter.y / height, (dpadRadius * scale) / shortSide),
            Control.Y to ControlPlacement(btnY.x / width, btnY.y / height, (actionRadius * scale) / shortSide),
            Control.X to ControlPlacement(btnX.x / width, btnX.y / height, (actionRadius * scale) / shortSide),
            Control.B to ControlPlacement(btnB.x / width, btnB.y / height, (actionRadius * scale) / shortSide),
            Control.A to ControlPlacement(btnA.x / width, btnA.y / height, (actionRadius * scale) / shortSide),
            Control.SELECT to ControlPlacement(selectRect.centerX() / width, selectRect.centerY() / height, selectRect.height() / shortSide),
            Control.START to ControlPlacement(startRect.centerX() / width, startRect.centerY() / height, startRect.height() / shortSide),
            Control.MENU to ControlPlacement(menuRect.centerX() / width, menuRect.centerY() / height, menuRect.height() / shortSide),
        )
    }

    private fun loadLayout() {
        val prefs = context.getSharedPreferences("controller-layout-v1", Context.MODE_PRIVATE)
        layout.clear()
        Control.entries.forEach { control ->
            val default = defaultLayout[control] ?: return@forEach
            val prefix = "${if (controlsOnly) "portrait" else "landscape"}_${control.name}"
            layout[control] = ControlPlacement(
                x = prefs.getFloat("${prefix}_x", default.x),
                y = prefs.getFloat("${prefix}_y", default.y),
                size = prefs.getFloat("${prefix}_size", default.size),
            )
        }
    }

    private fun saveLayout() {
        val prefs = context.getSharedPreferences("controller-layout-v1", Context.MODE_PRIVATE).edit()
        layout.forEach { (control, placement) ->
            val prefix = "${if (controlsOnly) "portrait" else "landscape"}_${control.name}"
            prefs.putFloat("${prefix}_x", placement.x)
            prefs.putFloat("${prefix}_y", placement.y)
            prefs.putFloat("${prefix}_size", placement.size)
        }
        prefs.apply()
        onProfileLayoutChanged?.invoke(exportProfileButtons())
    }

    private fun resetToPreset() {
        layout.clear()
        layout.putAll(defaultLayout.mapValues { it.value.copy() })
        saveLayout()
        applySavedLayout()
        selectedControl = null
        invalidate()
    }

    private fun exportProfileButtons(): List<ControlButtonProfile> {
        val layoutName = if (controlsOnly) "portrait" else "landscape"
        fun keyFor(control: Control): String = when (control) {
            Control.DPAD -> "DPAD"
            Control.A -> "A"
            Control.B -> "B"
            Control.X -> "X"
            Control.Y -> "Y"
            Control.SELECT -> "ESCAPE"
            Control.START -> "ENTER"
            Control.MENU -> "MENU"
        }
        return layout.mapNotNull { (control, placement) ->
            val label = when (control) {
                Control.DPAD -> "D-Pad"
                else -> control.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            ControlButtonProfile(
                id = control.name.lowercase(),
                label = label,
                key = keyFor(control),
                layout = layoutName,
                x = placement.x,
                y = placement.y,
                size = placement.size,
                opacity = opacity,
                hapticIntensity = hapticIntensity,
            )
        }
    }

    private fun applySavedLayout() {
        if (layout.isEmpty() || width <= 0 || height <= 0) return
        val shortSide = minOf(width, height).toFloat()
        fun point(control: Control): PointF {
            val placement = layout.getValue(control)
            return PointF(placement.x * width, placement.y * height)
        }
        fun radius(control: Control): Float =
            layout.getValue(control).size * shortSide

        val dpad = point(Control.DPAD)
        dpadCenter.x = dpad.x
        dpadCenter.y = dpad.y
        dpadRadius = radius(Control.DPAD) / scale
        dpadInnerRadius = dpadRadius * 0.42f

        point(Control.Y).also { btnY.x = it.x; btnY.y = it.y }
        point(Control.X).also { btnX.x = it.x; btnX.y = it.y }
        point(Control.B).also { btnB.x = it.x; btnB.y = it.y }
        point(Control.A).also { btnA.x = it.x; btnA.y = it.y }
        actionRadius = radius(Control.A) / scale

        setBarRect(Control.SELECT, selectRect)
        setBarRect(Control.START, startRect)
        setBarRect(Control.MENU, menuRect)
        updateControlRects()
    }

    private fun setBarRect(control: Control, rect: RectF) {
        val placement = layout.getValue(control)
        val h = placement.size * minOf(width, height)
        val w = h * 2.25f
        val cx = placement.x * width
        val cy = placement.y * height
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    private fun updateControlRects() {
        controlRects.clear()
        val dpadOuter = dpadRadius * scale
        controlRects[Control.DPAD] = RectF(
            dpadCenter.x - dpadOuter,
            dpadCenter.y - dpadOuter,
            dpadCenter.x + dpadOuter,
            dpadCenter.y + dpadOuter,
        )
        radiusFor(Control.Y).also { controlRects[Control.Y] = RectF(btnY.x - it, btnY.y - it, btnY.x + it, btnY.y + it) }
        radiusFor(Control.X).also { controlRects[Control.X] = RectF(btnX.x - it, btnX.y - it, btnX.x + it, btnX.y + it) }
        radiusFor(Control.B).also { controlRects[Control.B] = RectF(btnB.x - it, btnB.y - it, btnB.x + it, btnB.y + it) }
        radiusFor(Control.A).also { controlRects[Control.A] = RectF(btnA.x - it, btnA.y - it, btnA.x + it, btnA.y + it) }
        controlRects[Control.SELECT] = RectF(selectRect)
        controlRects[Control.START] = RectF(startRect)
        controlRects[Control.MENU] = RectF(menuRect)
    }

    private fun radiusFor(control: Control): Float =
        (layout[control]?.size ?: (actionRadius * scale / minOf(width, height).coerceAtLeast(1))) *
            minOf(width, height).coerceAtLeast(1)

    private enum class Control {
        DPAD, Y, X, B, A, SELECT, START, MENU,
    }

    private data class ControlPlacement(
        var x: Float,
        var y: Float,
        var size: Float,
    )

    data class PointF(var x: Float, var y: Float)
}
