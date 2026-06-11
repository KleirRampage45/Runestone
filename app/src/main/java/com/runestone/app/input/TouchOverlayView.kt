package com.runestone.app.input

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.LinearGradient
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import com.runestone.app.R

class TouchOverlayView(context: Context) : View(context) {

    enum class ControllerPreset {
        SIMPLIFIED, FULL, CUSTOM
    }

    enum class Zone {
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        BTN_A, BTN_B, BTN_X, BTN_Y,
        BTN_CONFIRM, BTN_BACK, BTN_DASH,
        BTN_CTRL, BTN_ALT, BTN_SHIFT,
        BTN_EXTRA_A, BTN_EXTRA_S, BTN_EXTRA_D,
        BTN_EXTRA_Z, BTN_EXTRA_X, BTN_EXTRA_C,
        SELECT, START, MENU, SETTINGS, HOME,
        L1, R1,
        OVERLAY_MENU,
    }

    var opacity: Float = 0.72f
    var scale: Float = 1.0f
    var hapticsEnabled: Boolean = true
    var hapticIntensity: Float = 0.55f
    var controlsOnly: Boolean = false
    var diagonalMovement: Boolean = false
    var showLabels: Boolean = true
    var showIcons: Boolean = true
    var controllerPreset: ControllerPreset = ControllerPreset.SIMPLIFIED
    var leftHanded: Boolean = false
    var showSafeArea: Boolean = false
    var showControlBounds: Boolean = false
    var showTouchZones: Boolean = false
    var onInput: ((Zone, pressed: Boolean) -> Unit)? = null
    var onToggleControls: (() -> Unit)? = null
    var onRotateLayout: (() -> Unit)? = null
    var onProfileLayoutChanged: ((List<ControlButtonProfile>) -> Unit)? = null
    var onOverlayMenu: (() -> Unit)? = null

    // Game viewport safe area (set by GameActivity)
    var gameViewportLeft: Float = 0f
    var gameViewportTop: Float = 0f
    var gameViewportRight: Float = 0f
    var gameViewportBottom: Float = 0f

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

    private val btnConfirm = PointF(0f, 0f)
    private val btnBack = PointF(0f, 0f)
    private val btnDash = PointF(0f, 0f)
    private val btnExtraA = PointF(0f, 0f)
    private val btnExtraS = PointF(0f, 0f)
    private val btnExtraD = PointF(0f, 0f)
    private val btnExtraZ = PointF(0f, 0f)
    private val btnExtraX = PointF(0f, 0f)
    private val btnExtraC = PointF(0f, 0f)
    private val btnCtrl = PointF(0f, 0f)
    private val btnAlt = PointF(0f, 0f)
    private val btnShift = PointF(0f, 0f)
    private var actionRadius = 38f

    private val selectRect = RectF()
    private val startRect = RectF()
    private val menuRect = RectF()
    private val imageRect = RectF()
    private val editButtonRect = RectF()
    private val quickSettingsRect = RectF()
    private val overlayMenuRect = RectF()
    private val doneRect = RectF()
    private val revertRect = RectF()
    private val presetRect = RectF()
    private val controlRects = mutableMapOf<Control, RectF>()
    private val layout = mutableMapOf<Control, ControlPlacement>()
    private var defaultLayout = emptyMap<Control, ControlPlacement>()
    private var loadedLayout = false
    private var quickSettingsOpen = false

    // L1/R1 shoulder button positions
    private val l1Rect = RectF()
    private val r1Rect = RectF()
    private val shoulderRadius = 20f

    // Bitmaps
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

    // Paints — glassmorphism style
    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glassBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val glassHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pressedGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
        isFakeBoldText = true
    }
    private val smallLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val editorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val safeAreaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val touchZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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

    private fun updateGlassPaints(a: Float) {
        val idleAlpha = (0.12f * 255 * a).toInt().coerceIn(0, 255)
        val borderAlpha = (0.32f * 255 * a).toInt().coerceIn(0, 255)
        val highlightAlpha = (0.18f * 255 * a).toInt().coerceIn(0, 255)
        val pressedAlpha = (0.22f * 255 * a).toInt().coerceIn(0, 255)
        val shadowAlpha = (0.35f * 255 * a).toInt().coerceIn(0, 255)
        val labelAlpha = (0.85f * 255 * a).toInt().coerceIn(0, 255)
        val safeAlpha = (0.50f * 255 * a).toInt().coerceIn(0, 255)
        val touchZoneAlpha = (0.15f * 255 * a).toInt().coerceIn(0, 255)

        glassFillPaint.color = Color.argb(idleAlpha, 255, 255, 255)
        glassBorderPaint.color = Color.argb(borderAlpha, 255, 255, 255)
        glassHighlightPaint.color = Color.argb(highlightAlpha, 255, 255, 255)
        pressedGlassPaint.color = Color.argb(pressedAlpha, 255, 255, 255)
        shadowPaint.color = Color.argb(shadowAlpha, 0, 0, 0)
        labelPaint.color = Color.argb(labelAlpha, 255, 255, 255)
        smallLabelPaint.color = Color.argb((0.70f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        safeAreaPaint.color = Color.argb(safeAlpha, 0, 255, 200)
        touchZonePaint.color = Color.argb(touchZoneAlpha, 255, 100, 100)
        selectedStrokePaint.color = Color.rgb(210, 180, 134)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = width.toFloat()
        val h = height.toFloat()
        val s = scale
        val shortSide = minOf(w, h)
        val isLandscape = w > h && !controlsOnly

        if (isLandscape) {
            layoutLandscape(w, h, s, shortSide)
        } else {
            layoutPortrait(w, h, s, shortSide)
        }

        defaultLayout = captureCurrentLayout()
        if (!loadedLayout) {
            loadLayout()
            loadedLayout = true
        }
        applySavedLayout()
    }

    private fun layoutLandscape(w: Float, h: Float, s: Float, shortSide: Float) {
        // Calculate 4:3 game viewport (set by GameActivity, but recalculate fallback)
        val targetGameRatio = 4f / 3f
        val gameW = if (gameViewportRight > gameViewportLeft) {
            gameViewportRight - gameViewportLeft
        } else {
            minOf(w, h * targetGameRatio)
        }
        val gameH = if (gameViewportBottom > gameViewportTop) {
            gameViewportBottom - gameViewportTop
        } else {
            h
        }
        val leftGutterW = if (gameViewportLeft > 0f) gameViewportLeft else (w - gameW) / 2f
        val rightGutterW = leftGutterW
        val gameLeft = leftGutterW
        val gameRight = w - rightGutterW

        // D-pad in left gutter, lower-left
        val dpadActualRadius = (shortSide * 0.10f).coerceIn(60f, 110f)
        dpadRadius = dpadActualRadius / s
        dpadInnerRadius = dpadRadius * 0.42f
        dpadCenter.x = gameLeft * 0.5f
        dpadCenter.y = h * 0.65f

        // Action buttons in right gutter
        val actionActualRadius = (shortSide * 0.06f).coerceIn(44f, 72f)
        actionRadius = actionActualRadius / s

        val rightGutterCenter = gameRight + rightGutterW * 0.5f
        val actionBaseY = h * 0.58f
        val actionSpread = 56f * s

        if (controllerPreset == ControllerPreset.SIMPLIFIED) {
            btnConfirm.x = rightGutterCenter
            btnConfirm.y = actionBaseY
            btnBack.x = rightGutterCenter
            btnBack.y = actionBaseY + actionSpread * 1.3f
            btnDash.x = rightGutterCenter
            btnDash.y = actionBaseY + actionSpread * 2.6f
        } else {
            // Full mode: compact cluster
            btnExtraA.x = rightGutterCenter - actionSpread * 0.8f
            btnExtraA.y = actionBaseY
            btnExtraS.x = rightGutterCenter
            btnExtraS.y = actionBaseY
            btnExtraD.x = rightGutterCenter + actionSpread * 0.8f
            btnExtraD.y = actionBaseY
            btnExtraZ.x = rightGutterCenter - actionSpread * 0.8f
            btnExtraZ.y = actionBaseY + actionSpread * 1.2f
            btnExtraX.x = rightGutterCenter
            btnExtraX.y = actionBaseY + actionSpread * 1.2f
            btnExtraC.x = rightGutterCenter + actionSpread * 0.8f
            btnExtraC.y = actionBaseY + actionSpread * 1.2f

            btnConfirm.x = rightGutterCenter
            btnConfirm.y = actionBaseY + actionSpread * 2.6f
            btnBack.x = rightGutterCenter
            btnBack.y = actionBaseY + actionSpread * 3.9f
        }

        // L/R shoulder buttons at top of gutters
        val shoulderH = 28f * s
        val shoulderW = 48f * s
        l1Rect.set(leftGutterW * 0.5f - shoulderW / 2f, 24f * s, leftGutterW * 0.5f + shoulderW / 2f, 24f * s + shoulderH)
        r1Rect.set(gameRight + rightGutterW * 0.5f - shoulderW / 2f, 24f * s, gameRight + rightGutterW * 0.5f + shoulderW / 2f, 24f * s + shoulderH)

        // Modifier buttons in left gutter (Full mode only)
        if (controllerPreset == ControllerPreset.FULL) {
            val modX = gameLeft * 0.5f
            val modY = h * 0.28f
            val modSpread = 36f * s
            btnCtrl.x = modX; btnCtrl.y = modY
            btnAlt.x = modX; btnAlt.y = modY + modSpread
            btnShift.x = modX; btnShift.y = modY + modSpread * 2f
        }

        // Bottom bar (small, in gutters or below)
        val barH = 44f * s; val barW = 100f * s
        val barY = h - 40f * s
        selectRect.set(leftGutterW * 0.5f - barW / 2f, barY - barH / 2, leftGutterW * 0.5f + barW / 2f, barY + barH / 2)
        startRect.set(w * 0.5f - barW / 2f, barY - barH / 2, w * 0.5f + barW / 2f, barY + barH / 2)
        menuRect.set(gameRight + rightGutterW * 0.5f - barW / 2f, barY - barH / 2, gameRight + rightGutterW * 0.5f + barW / 2f, barY + barH / 2)

        // Overlay menu button — top corner, outside game area
        overlayMenuRect.set(8f * s, 8f * s, 56f * s, 56f * s)
        editButtonRect.set(w - 76f * s, h - 76f * s, w - 8f * s, h - 8f * s)
    }

    private fun layoutPortrait(w: Float, h: Float, s: Float, shortSide: Float) {
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

        if (controllerPreset == ControllerPreset.SIMPLIFIED) {
            btnConfirm.x = actionCenterX; btnConfirm.y = actionCenterY - spread
            btnBack.x = actionCenterX; btnBack.y = actionCenterY
            btnDash.x = actionCenterX; btnDash.y = actionCenterY + spread
        } else {
            btnExtraA.x = actionCenterX - spread * 0.8f; btnExtraA.y = actionCenterY - spread
            btnExtraS.x = actionCenterX; btnExtraS.y = actionCenterY - spread
            btnExtraD.x = actionCenterX + spread * 0.8f; btnExtraD.y = actionCenterY - spread
            btnExtraZ.x = actionCenterX - spread * 0.8f; btnExtraZ.y = actionCenterY
            btnExtraX.x = actionCenterX; btnExtraX.y = actionCenterY
            btnExtraC.x = actionCenterX + spread * 0.8f; btnExtraC.y = actionCenterY
            btnConfirm.x = actionCenterX; btnConfirm.y = actionCenterY + spread * 1.2f
            btnBack.x = actionCenterX; btnBack.y = actionCenterY + spread * 2.2f
        }

        val barY = if (controlsOnly) panelTop + controlsHeight * 0.82f else h - 60f * s
        val barH = 52f * s; val barW = 140f * s
        selectRect.set(w * 0.20f - barW / 2, barY - barH / 2, w * 0.20f + barW / 2, barY + barH / 2)
        startRect.set(w * 0.50f - barW / 2, barY - barH / 2, w * 0.50f + barW / 2, barY + barH / 2)
        menuRect.set(w * 0.80f - barW / 2, barY - barH / 2, w * 0.80f + barW / 2, barY + barH / 2)

        val shoulderH = 26f * s
        val shoulderW = 42f * s
        val shoulderY = panelTop + 10f * s
        l1Rect.set(10f, shoulderY, 10f + shoulderW, shoulderY + shoulderH)
        r1Rect.set(w - 10f - shoulderW, shoulderY, w - 10f, shoulderY + shoulderH)

        if (controllerPreset == ControllerPreset.FULL) {
            val modX = w * 0.12f
            val modY = panelTop + controlsHeight * 0.15f
            val modSpread = 34f * s
            btnCtrl.x = modX; btnCtrl.y = modY
            btnAlt.x = modX; btnAlt.y = modY + modSpread
            btnShift.x = modX; btnShift.y = modY + modSpread * 2f
        }

        overlayMenuRect.set(8f * s, panelTop + 8f * s, 56f * s, panelTop + 56f * s)
        editButtonRect.set(w - 76f * s, h - 76f * s, w - 8f * s, h - 8f * s)
    }

    override fun onDraw(canvas: Canvas) {
        val a = opacity
        updateGlassPaints(a)
        val isLandscape = width > height && !controlsOnly

        // Debug safe area
        if (showSafeArea && gameViewportRight > gameViewportLeft) {
            canvas.drawRect(gameViewportLeft, gameViewportTop, gameViewportRight, gameViewportBottom, safeAreaPaint)
        }

        // Portrait panel background
        if (!isLandscape) {
            val panelTop = if (controlsOnly) 0f else height * 0.55f
            glassFillPaint.color = Color.argb((0.08f * 255 * a).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawRect(0f, panelTop, width.toFloat(), height.toFloat(), glassFillPaint)
        }

        // D-Pad
        drawDPad(canvas, a)

        // Action buttons based on preset
        if (controllerPreset == ControllerPreset.SIMPLIFIED) {
            drawGlassButton(canvas, btnConfirm.x, btnConfirm.y, radiusFor(Control.CONFIRM), getConfirmLabel(), a, Zone.BTN_CONFIRM, Control.CONFIRM)
            drawGlassButton(canvas, btnBack.x, btnBack.y, radiusFor(Control.BACK), getBackLabel(), a, Zone.BTN_BACK, Control.BACK)
            drawGlassButton(canvas, btnDash.x, btnDash.y, radiusFor(Control.DASH), "Dash", a, Zone.BTN_DASH, Control.DASH)
        } else {
            drawGlassButton(canvas, btnExtraA.x, btnExtraA.y, radiusFor(Control.EXTRA_A), "A", a, Zone.BTN_EXTRA_A, Control.EXTRA_A)
            drawGlassButton(canvas, btnExtraS.x, btnExtraS.y, radiusFor(Control.EXTRA_S), "S", a, Zone.BTN_EXTRA_S, Control.EXTRA_S)
            drawGlassButton(canvas, btnExtraD.x, btnExtraD.y, radiusFor(Control.EXTRA_D), "D", a, Zone.BTN_EXTRA_D, Control.EXTRA_D)
            drawGlassButton(canvas, btnExtraZ.x, btnExtraZ.y, radiusFor(Control.EXTRA_Z), "Z", a, Zone.BTN_EXTRA_Z, Control.EXTRA_Z)
            drawGlassButton(canvas, btnExtraX.x, btnExtraX.y, radiusFor(Control.EXTRA_X), "X", a, Zone.BTN_EXTRA_X, Control.EXTRA_X)
            drawGlassButton(canvas, btnExtraC.x, btnExtraC.y, radiusFor(Control.EXTRA_C), "C", a, Zone.BTN_EXTRA_C, Control.EXTRA_C)
            drawGlassButton(canvas, btnConfirm.x, btnConfirm.y, radiusFor(Control.CONFIRM), getConfirmLabel(), a, Zone.BTN_CONFIRM, Control.CONFIRM)
            drawGlassButton(canvas, btnBack.x, btnBack.y, radiusFor(Control.BACK), getBackLabel(), a, Zone.BTN_BACK, Control.BACK)
        }

        // Modifiers (Full only)
        if (controllerPreset == ControllerPreset.FULL) {
            drawGlassButton(canvas, btnCtrl.x, btnCtrl.y, radiusFor(Control.CTRL) * 0.85f, "Ctrl", a, Zone.BTN_CTRL, Control.CTRL)
            drawGlassButton(canvas, btnAlt.x, btnAlt.y, radiusFor(Control.ALT) * 0.85f, "Alt", a, Zone.BTN_ALT, Control.ALT)
            drawGlassButton(canvas, btnShift.x, btnShift.y, radiusFor(Control.SHIFT) * 0.85f, "Shift", a, Zone.BTN_SHIFT, Control.SHIFT)
        }

        // Bottom bar
        drawGlassBarButton(canvas, selectRect, "SELECT", a, Zone.SELECT)
        drawGlassBarButton(canvas, startRect, "START", a, Zone.START)
        drawGlassBarButton(canvas, menuRect, "HOME", a, Zone.HOME)

        // L1/R1
        drawShoulderButton(canvas, l1Rect, "L", a, Zone.L1)
        drawShoulderButton(canvas, r1Rect, "R", a, Zone.R1)

        // Overlay menu
        drawOverlayMenuButton(canvas, a)
        if (quickSettingsOpen) drawQuickSettings(canvas, a)
        if (editing) drawEditorChrome(canvas)

        // Debug touch zones
        if (showTouchZones) {
            drawTouchZones(canvas)
        }
    }

    private fun getConfirmLabel(): String = when {
        showIcons && !showLabels -> ""
        else -> "Confirm"
    }

    private fun getBackLabel(): String = when {
        showIcons && !showLabels -> ""
        else -> "Back"
    }

    private fun drawGlassButton(canvas: Canvas, x: Float, y: Float, r: Float, label: String, a: Float, zone: Zone, control: Control) {
        val zonePressed = zone in activeZones
        val baseRadius = r

        // Shadow
        shadowPaint.alpha = (0.35f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(x + 2f, y + 4f, baseRadius, shadowPaint)

        // Fill
        val fillPaint = if (zonePressed) pressedGlassPaint else glassFillPaint
        fillPaint.alpha = if (zonePressed) {
            (0.22f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.12f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(x, y, baseRadius, fillPaint)

        // Border
        glassBorderPaint.alpha = if (zonePressed) {
            (0.50f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.32f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(x, y, baseRadius, glassBorderPaint)

        // Top highlight
        if (!zonePressed) {
            glassHighlightPaint.alpha = (0.18f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawArc(RectF(x - baseRadius, y - baseRadius, x + baseRadius, y + baseRadius), 225f, 90f, true, glassHighlightPaint)
        }

        // Pressed scale effect
        val scaleDown = if (zonePressed) 0.96f else 1.0f
        if (zonePressed) {
            canvas.save()
            canvas.scale(scaleDown, scaleDown, x, y)
        }

        // Label
        if (label.isNotEmpty()) {
            labelPaint.alpha = if (zonePressed) 255 else (0.85f * 255 * a).toInt().coerceIn(0, 255)
            labelPaint.color = if (zonePressed) Color.rgb(200, 170, 130) else Color.argb((0.85f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            labelPaint.textSize = 24f * scale
            canvas.drawText(label, x, y + 8f * scale, labelPaint)
        }

        if (zonePressed) canvas.restore()

        // Debug control bounds
        if (showControlBounds) {
            glassBorderPaint.alpha = (0.60f * 255 * a).toInt().coerceIn(0, 255)
            glassBorderPaint.color = Color.argb(glassBorderPaint.alpha, 0, 255, 200)
            canvas.drawCircle(x, y, baseRadius, glassBorderPaint)
            glassBorderPaint.color = Color.argb((0.32f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }

        drawSelection(canvas, control)
        controlRects[control] = RectF(x - baseRadius, y - baseRadius, x + baseRadius, y + baseRadius)
    }

    private fun drawGlassBarButton(canvas: Canvas, rect: RectF, label: String, a: Float, zone: Zone) {
        val pressed = zone in activeZones
        val r = rect.height() * 0.35f

        shadowPaint.alpha = (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect.left + 2f, rect.top + 4f, rect.right + 2f, rect.bottom + 4f, r, r, shadowPaint)

        val fillPaint = if (pressed) pressedGlassPaint else glassFillPaint
        fillPaint.alpha = if (pressed) {
            (0.20f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.10f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(rect, r, r, fillPaint)

        glassBorderPaint.alpha = if (pressed) {
            (0.45f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.28f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(rect, r, r, glassBorderPaint)

        if (!pressed) {
            glassHighlightPaint.alpha = (0.15f * 255 * a).toInt().coerceIn(0, 255)
            val hlRect = RectF(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f)
            canvas.drawRoundRect(hlRect, r, r, glassHighlightPaint)
        }

        smallLabelPaint.alpha = if (pressed) 255 else (0.70f * 255 * a).toInt().coerceIn(0, 255)
        smallLabelPaint.color = if (pressed) Color.rgb(200, 170, 130) else Color.argb((0.70f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawText(label, rect.centerX(), rect.centerY() + 5f, smallLabelPaint)

        drawSelection(canvas, controlForZone(zone))
    }

    private fun drawShoulderButton(canvas: Canvas, rect: RectF, label: String, a: Float, zone: Zone) {
        val pressed = zone in activeZones
        val r = rect.height() * 0.5f

        shadowPaint.alpha = (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect.left + 1f, rect.top + 3f, rect.right + 1f, rect.bottom + 3f, r, r, shadowPaint)

        val fillPaint = if (pressed) pressedGlassPaint else glassFillPaint
        fillPaint.alpha = if (pressed) {
            (0.20f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.14f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(rect, r, r, fillPaint)

        glassBorderPaint.alpha = if (pressed) {
            (0.45f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.35f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(rect, r, r, glassBorderPaint)

        if (!pressed) {
            glassHighlightPaint.alpha = (0.16f * 255 * a).toInt().coerceIn(0, 255)
            val hlRect = RectF(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f)
            canvas.drawRoundRect(hlRect, r, r, glassHighlightPaint)
        }

        labelPaint.alpha = if (pressed) 255 else (0.80f * 255 * a).toInt().coerceIn(0, 255)
        labelPaint.color = if (pressed) Color.rgb(200, 170, 130) else Color.argb((0.80f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        labelPaint.textSize = 14f * scale
        canvas.drawText(label, rect.centerX(), rect.centerY() + 5f, labelPaint)
    }

    private fun drawOverlayMenuButton(canvas: Canvas, a: Float) {
        val pressedAlpha = if (quickSettingsOpen) 210 else 150
        editorPaint.alpha = (pressedAlpha * a).toInt().coerceIn(0, 255)
        val r = overlayMenuRect.height() * 0.5f
        canvas.drawRoundRect(overlayMenuRect, r, r, editorPaint)
        glassBorderPaint.alpha = (0.35f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(overlayMenuRect, r, r, glassBorderPaint)

        // Draw three horizontal lines (hamburger)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 3f * scale
            color = Color.argb((0.80f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        val cx = overlayMenuRect.centerX()
        val cy = overlayMenuRect.centerY()
        val lineW = overlayMenuRect.width() * 0.35f
        val gap = 6f * scale
        canvas.drawLine(cx - lineW, cy - gap, cx + lineW, cy - gap, linePaint)
        canvas.drawLine(cx - lineW, cy, cx + lineW, cy, linePaint)
        canvas.drawLine(cx - lineW, cy + gap, cx + lineW, cy + gap, linePaint)
    }

    private fun drawDPad(canvas: Canvas, a: Float) {
        val cx = dpadCenter.x
        val cy = dpadCenter.y
        val outer = dpadRadius * scale
        val inner = dpadInnerRadius * scale
        val pressed = activeZones.any { it.name.startsWith("DPAD") }

        // Shadow
        shadowPaint.alpha = (0.35f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx + 2f, cy + 4f, outer, shadowPaint)

        // Base fill
        val fillPaint = if (pressed) pressedGlassPaint else glassFillPaint
        fillPaint.alpha = if (pressed) {
            (0.22f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.12f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(cx, cy, outer, fillPaint)

        // Border
        glassBorderPaint.alpha = if (pressed) {
            (0.50f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.32f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(cx, cy, outer, glassBorderPaint)

        // Top highlight
        if (!pressed) {
            glassHighlightPaint.alpha = (0.18f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawArc(RectF(cx - outer, cy - outer, cx + outer, cy + outer), 225f, 90f, true, glassHighlightPaint)
        }

        // Center dot
        glassFillPaint.alpha = (0.08f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, inner * 0.35f, glassFillPaint)
        glassBorderPaint.alpha = (0.20f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, inner * 0.35f, glassBorderPaint)

        // Directional arrows
        drawDpadArrow(canvas, cx, cy - inner * 0.72f, outer * 0.18f, Direction.UP, Zone.DPAD_UP, a)
        drawDpadArrow(canvas, cx, cy + inner * 0.72f, outer * 0.18f, Direction.DOWN, Zone.DPAD_DOWN, a)
        drawDpadArrow(canvas, cx - inner * 0.72f, cy, outer * 0.18f, Direction.LEFT, Zone.DPAD_LEFT, a)
        drawDpadArrow(canvas, cx + inner * 0.72f, cy, outer * 0.18f, Direction.RIGHT, Zone.DPAD_RIGHT, a)

        drawSelection(canvas, Control.DPAD)
        controlRects[Control.DPAD] = RectF(cx - outer, cy - outer, cx + outer, cy + outer)

        // Debug touch zone
        if (showTouchZones) {
            touchZonePaint.alpha = (0.15f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, outer * 1.15f, touchZonePaint)
        }
    }

    private enum class Direction { UP, DOWN, LEFT, RIGHT }

    private fun drawDpadArrow(canvas: Canvas, x: Float, y: Float, size: Float, direction: Direction, zone: Zone, a: Float) {
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
            color = if (pressed) Color.rgb(200, 170, 130) else Color.argb((0.85f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        canvas.drawPath(path, arrowPaint)
    }

    private fun drawTouchZones(canvas: Canvas) {
        touchZonePaint.alpha = (0.15f * 255 * opacity).toInt().coerceIn(0, 255)
        controlRects.values.forEach { rect ->
            canvas.drawRect(rect, touchZonePaint)
        }
    }

    fun toggleQuickSettings() {
        quickSettingsOpen = !quickSettingsOpen
        invalidate()
    }

    fun openLayoutEditor() {
        startEditing()
    }

    fun setPreset(preset: ControllerPreset) {
        controllerPreset = preset
        loadLayout()
        applySavedLayout()
        requestLayout()
        invalidate()
    }

    private fun drawQuickSettings(canvas: Canvas, a: Float) {
        editorPaint.alpha = (185 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(quickSettingsRect, 14f * scale, 14f * scale, editorPaint)
        glassBorderPaint.alpha = (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(quickSettingsRect, 14f * scale, 14f * scale, glassBorderPaint)

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
            color = Color.argb((0.85f * 255 * a).toInt().coerceIn(0, 255), 235, 232, 220)
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
            6 -> showExtraButtonsLegacyToggle()
            7 -> onToggleControls?.invoke()
            8 -> onRotateLayout?.invoke()
        }
        requestLayout()
        invalidate()
        return true
    }

    private fun showExtraButtonsLegacyToggle() {
        // Toggle between SIMPLIFIED and FULL
        controllerPreset = if (controllerPreset == ControllerPreset.SIMPLIFIED) ControllerPreset.FULL else ControllerPreset.SIMPLIFIED
        loadLayout()
        applySavedLayout()
        requestLayout()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (handleQuickSettingsTouch(event)) return true
        if (handleEditorTouch(event)) return true
        if (handleOverlayMenuTouch(event)) return true

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

    private fun handleOverlayMenuTouch(event: MotionEvent): Boolean {
        if (editing || quickSettingsOpen) return false
        val action = event.actionMasked
        val x = event.getX(event.actionIndex)
        val y = event.getY(event.actionIndex)
        if (action == MotionEvent.ACTION_DOWN && overlayMenuRect.contains(x, y)) {
            toggleQuickSettings()
            return true
        }
        return false
    }

    private fun hitTestMulti(x: Float, y: Float): Set<Zone> {
        return if (diagonalMovement) hitTestZones(x, y) else {
            val single = hitTest(x, y)
            if (single != null) setOf(single) else emptySet()
        }
    }

    private fun hitTest(x: Float, y: Float): Zone? {
        val s = scale
        val isLandscape = width > height && !controlsOnly

        // In landscape, ignore touches inside the game viewport unless in edit mode
        if (isLandscape && !editing && gameViewportRight > gameViewportLeft) {
            if (x > gameViewportLeft && x < gameViewportRight && y > gameViewportTop && y < gameViewportBottom) {
                return null
            }
        }

        // In portrait, ignore touches above control panel unless in edit mode
        if (!isLandscape && !editing && !controlsOnly) {
            val panelTop = height * 0.55f
            if (y < panelTop) return null
        }

        // Overlay menu
        if (overlayMenuRect.contains(x, y)) return Zone.OVERLAY_MENU

        // L1/R1
        if (l1Rect.contains(x, y)) return Zone.L1
        if (r1Rect.contains(x, y)) return Zone.R1

        // Bottom bar
        if (selectRect.contains(x, y)) return Zone.SELECT
        if (startRect.contains(x, y)) return Zone.START
        if (menuRect.contains(x, y)) return Zone.HOME

        // Action buttons based on preset
        if (controllerPreset == ControllerPreset.SIMPLIFIED) {
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) return Zone.BTN_CONFIRM
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) return Zone.BTN_BACK
            if (dist(x, y, btnDash.x, btnDash.y) < radiusFor(Control.DASH)) return Zone.BTN_DASH
        } else {
            if (dist(x, y, btnExtraA.x, btnExtraA.y) < radiusFor(Control.EXTRA_A)) return Zone.BTN_EXTRA_A
            if (dist(x, y, btnExtraS.x, btnExtraS.y) < radiusFor(Control.EXTRA_S)) return Zone.BTN_EXTRA_S
            if (dist(x, y, btnExtraD.x, btnExtraD.y) < radiusFor(Control.EXTRA_D)) return Zone.BTN_EXTRA_D
            if (dist(x, y, btnExtraZ.x, btnExtraZ.y) < radiusFor(Control.EXTRA_Z)) return Zone.BTN_EXTRA_Z
            if (dist(x, y, btnExtraX.x, btnExtraX.y) < radiusFor(Control.EXTRA_X)) return Zone.BTN_EXTRA_X
            if (dist(x, y, btnExtraC.x, btnExtraC.y) < radiusFor(Control.EXTRA_C)) return Zone.BTN_EXTRA_C
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) return Zone.BTN_CONFIRM
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) return Zone.BTN_BACK
            if (dist(x, y, btnCtrl.x, btnCtrl.y) < radiusFor(Control.CTRL) * 0.85f) return Zone.BTN_CTRL
            if (dist(x, y, btnAlt.x, btnAlt.y) < radiusFor(Control.ALT) * 0.85f) return Zone.BTN_ALT
            if (dist(x, y, btnShift.x, btnShift.y) < radiusFor(Control.SHIFT) * 0.85f) return Zone.BTN_SHIFT
        }

        // D-pad
        val outer = dpadRadius * s
        val inner = dpadInnerRadius * s
        val d = dist(x, y, dpadCenter.x, dpadCenter.y)
        if (d < outer) {
            if (d < inner) return null
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
        val isLandscape = width > height && !controlsOnly

        if (isLandscape && !editing && gameViewportRight > gameViewportLeft) {
            if (x > gameViewportLeft && x < gameViewportRight && y > gameViewportTop && y < gameViewportBottom) {
                return emptySet()
            }
        }

        if (!isLandscape && !editing && !controlsOnly) {
            val panelTop = height * 0.55f
            if (y < panelTop) return emptySet()
        }

        val result = mutableSetOf<Zone>()

        if (overlayMenuRect.contains(x, y)) return setOf(Zone.OVERLAY_MENU)
        if (l1Rect.contains(x, y)) return setOf(Zone.L1)
        if (r1Rect.contains(x, y)) return setOf(Zone.R1)
        if (selectRect.contains(x, y)) return setOf(Zone.SELECT)
        if (startRect.contains(x, y)) return setOf(Zone.START)
        if (menuRect.contains(x, y)) return setOf(Zone.HOME)

        if (controllerPreset == ControllerPreset.SIMPLIFIED) {
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) result.add(Zone.BTN_CONFIRM)
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) result.add(Zone.BTN_BACK)
            if (dist(x, y, btnDash.x, btnDash.y) < radiusFor(Control.DASH)) result.add(Zone.BTN_DASH)
        } else {
            if (dist(x, y, btnExtraA.x, btnExtraA.y) < radiusFor(Control.EXTRA_A)) result.add(Zone.BTN_EXTRA_A)
            if (dist(x, y, btnExtraS.x, btnExtraS.y) < radiusFor(Control.EXTRA_S)) result.add(Zone.BTN_EXTRA_S)
            if (dist(x, y, btnExtraD.x, btnExtraD.y) < radiusFor(Control.EXTRA_D)) result.add(Zone.BTN_EXTRA_D)
            if (dist(x, y, btnExtraZ.x, btnExtraZ.y) < radiusFor(Control.EXTRA_Z)) result.add(Zone.BTN_EXTRA_Z)
            if (dist(x, y, btnExtraX.x, btnExtraX.y) < radiusFor(Control.EXTRA_X)) result.add(Zone.BTN_EXTRA_X)
            if (dist(x, y, btnExtraC.x, btnExtraC.y) < radiusFor(Control.EXTRA_C)) result.add(Zone.BTN_EXTRA_C)
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) result.add(Zone.BTN_CONFIRM)
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) result.add(Zone.BTN_BACK)
            if (dist(x, y, btnCtrl.x, btnCtrl.y) < radiusFor(Control.CTRL) * 0.85f) result.add(Zone.BTN_CTRL)
            if (dist(x, y, btnAlt.x, btnAlt.y) < radiusFor(Control.ALT) * 0.85f) result.add(Zone.BTN_ALT)
            if (dist(x, y, btnShift.x, btnShift.y) < radiusFor(Control.SHIFT) * 0.85f) result.add(Zone.BTN_SHIFT)
        }
        if (result.isNotEmpty()) return result

        // D-pad with diagonal support
        val outer = dpadRadius * s
        val inner = dpadInnerRadius * s
        val d = dist(x, y, dpadCenter.x, dpadCenter.y)
        if (d < outer && d >= inner) {
            val angle = Math.atan2((y - dpadCenter.y).toDouble(), (x - dpadCenter.x).toDouble())
            if (angle in -Math.PI / 4.0 * 1.3..Math.PI / 4.0 * 1.3) result.add(Zone.DPAD_RIGHT)
            if (angle in Math.PI / 4.0 * 0.7..3.0 * Math.PI / 4.0 * 0.85) result.add(Zone.DPAD_DOWN)
            if (angle in -3.0 * Math.PI / 4.0 * 0.85..-Math.PI / 4.0 * 0.7) result.add(Zone.DPAD_UP)
            if (angle <= -Math.PI * 0.75 || angle >= Math.PI * 0.75) result.add(Zone.DPAD_LEFT)
            return result
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
        Zone.BTN_CONFIRM, Zone.BTN_A -> Control.CONFIRM
        Zone.BTN_BACK, Zone.BTN_B -> Control.BACK
        Zone.BTN_DASH -> Control.DASH
        Zone.BTN_EXTRA_A -> Control.EXTRA_A
        Zone.BTN_EXTRA_S -> Control.EXTRA_S
        Zone.BTN_EXTRA_D -> Control.EXTRA_D
        Zone.BTN_EXTRA_Z -> Control.EXTRA_Z
        Zone.BTN_EXTRA_X -> Control.EXTRA_X
        Zone.BTN_EXTRA_C -> Control.EXTRA_C
        Zone.BTN_CTRL -> Control.CTRL
        Zone.BTN_ALT -> Control.ALT
        Zone.BTN_SHIFT -> Control.SHIFT
        Zone.SELECT -> Control.SELECT
        Zone.START -> Control.START
        Zone.MENU, Zone.SETTINGS -> Control.MENU
        Zone.HOME -> Control.MENU
        Zone.L1 -> Control.L1
        Zone.R1 -> Control.R1
        Zone.OVERLAY_MENU -> Control.MENU
        Zone.BTN_X, Zone.BTN_Y -> Control.DASH
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
        glassBorderPaint.alpha = (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(editButtonRect, 10f, 10f, glassBorderPaint)
        wrenchIconBitmap?.let {
            val pad = editButtonRect.width() * 0.22f
            imageRect.set(editButtonRect.left + pad, editButtonRect.top + pad, editButtonRect.right - pad, editButtonRect.bottom - pad)
            imagePaint.alpha = (0.82f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawBitmap(it, null, imageRect, imagePaint)
        } ?: run {
            smallLabelPaint.alpha = (0.82f * 255 * a).toInt().coerceIn(0, 255)
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
        val radius = rect.height() * 0.42f
        canvas.drawRoundRect(rect, radius, radius, editorPaint)
        selectedStrokePaint.alpha = 180
        canvas.drawRoundRect(rect, radius, radius, selectedStrokePaint)
        smallLabelPaint.alpha = 235
        smallLabelPaint.color = Color.rgb(232, 229, 220)
        smallLabelPaint.textSize = 13f * scale
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
            isControlVisible(it) && controlRects[it]?.contains(x, y) == true
        }

    private fun isControlVisible(control: Control): Boolean {
        return when (control) {
            Control.DPAD, Control.CONFIRM, Control.BACK, Control.DASH, Control.L1, Control.R1 -> true
            Control.CTRL, Control.ALT, Control.SHIFT,
            Control.EXTRA_A, Control.EXTRA_S, Control.EXTRA_D,
            Control.EXTRA_Z, Control.EXTRA_X, Control.EXTRA_C -> controllerPreset == ControllerPreset.FULL
            else -> true
        }
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return dist(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
    }

    private fun captureCurrentLayout(): Map<Control, ControlPlacement> {
        val shortSide = minOf(width, height).coerceAtLeast(1).toFloat()
        return mapOf(
            Control.DPAD to ControlPlacement(dpadCenter.x / width, dpadCenter.y / height, (dpadRadius * scale) / shortSide),
            Control.L1 to ControlPlacement(l1Rect.centerX() / width, l1Rect.centerY() / height, (l1Rect.height() * scale) / shortSide),
            Control.R1 to ControlPlacement(r1Rect.centerX() / width, r1Rect.centerY() / height, (r1Rect.height() * scale) / shortSide),
            Control.CONFIRM to ControlPlacement(btnConfirm.x / width, btnConfirm.y / height, (actionRadius * scale) / shortSide),
            Control.BACK to ControlPlacement(btnBack.x / width, btnBack.y / height, (actionRadius * scale) / shortSide),
            Control.DASH to ControlPlacement(btnDash.x / width, btnDash.y / height, (actionRadius * scale) / shortSide),
            Control.EXTRA_A to ControlPlacement(btnExtraA.x / width, btnExtraA.y / height, (actionRadius * scale) / shortSide),
            Control.EXTRA_S to ControlPlacement(btnExtraS.x / width, btnExtraS.y / height, (actionRadius * scale) / shortSide),
            Control.EXTRA_D to ControlPlacement(btnExtraD.x / width, btnExtraD.y / height, (actionRadius * scale) / shortSide),
            Control.EXTRA_Z to ControlPlacement(btnExtraZ.x / width, btnExtraZ.y / height, (actionRadius * scale) / shortSide),
            Control.EXTRA_X to ControlPlacement(btnExtraX.x / width, btnExtraX.y / height, (actionRadius * scale) / shortSide),
            Control.EXTRA_C to ControlPlacement(btnExtraC.x / width, btnExtraC.y / height, (actionRadius * scale) / shortSide),
            Control.CTRL to ControlPlacement(btnCtrl.x / width, btnCtrl.y / height, (actionRadius * scale * 0.85f) / shortSide),
            Control.ALT to ControlPlacement(btnAlt.x / width, btnAlt.y / height, (actionRadius * scale * 0.85f) / shortSide),
            Control.SHIFT to ControlPlacement(btnShift.x / width, btnShift.y / height, (actionRadius * scale * 0.85f) / shortSide),
            Control.SELECT to ControlPlacement(selectRect.centerX() / width, selectRect.centerY() / height, selectRect.height() / shortSide),
            Control.START to ControlPlacement(startRect.centerX() / width, startRect.centerY() / height, startRect.height() / shortSide),
            Control.MENU to ControlPlacement(menuRect.centerX() / width, menuRect.centerY() / height, menuRect.height() / shortSide),
        )
    }

    private fun loadLayout() {
        val prefs = context.getSharedPreferences("controller-layout-v2", Context.MODE_PRIVATE)
        val savedPreset = runCatching {
            ControllerPreset.valueOf(prefs.getString("preset", ControllerPreset.SIMPLIFIED.name).orEmpty())
        }.getOrDefault(ControllerPreset.SIMPLIFIED)
        controllerPreset = savedPreset

        layout.clear()
        Control.entries.forEach { control ->
            val default = defaultLayout[control] ?: return@forEach
            val prefix = "${if (controlsOnly) "portrait" else "landscape"}_${controllerPreset.name}_${control.name}"
            layout[control] = ControlPlacement(
                x = prefs.getFloat("${prefix}_x", default.x),
                y = prefs.getFloat("${prefix}_y", default.y),
                size = prefs.getFloat("${prefix}_size", default.size),
            )
        }
    }

    private fun saveLayout() {
        val prefs = context.getSharedPreferences("controller-layout-v2", Context.MODE_PRIVATE).edit()
        prefs.putString("preset", controllerPreset.name)
        layout.forEach { (control, placement) ->
            val prefix = "${if (controlsOnly) "portrait" else "landscape"}_${controllerPreset.name}_${control.name}"
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
        return layout.mapNotNull { (control, placement) ->
            if (!isControlVisible(control)) return@mapNotNull null
            val label = when (control) {
                Control.DPAD -> "D-Pad"
                Control.CONFIRM -> "Confirm"
                Control.BACK -> "Back"
                Control.DASH -> "Dash"
                else -> control.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            val key = when (control) {
                Control.DPAD -> "DPAD"
                Control.CONFIRM -> "ENTER"
                Control.BACK -> "ESCAPE"
                Control.DASH -> "SHIFT"
                else -> control.name
            }
            ControlButtonProfile(
                id = control.name.lowercase(),
                label = label,
                key = key,
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

        point(Control.L1).also { l1Rect.set(it.x - l1Rect.width() / 2f, it.y - l1Rect.height() / 2f, it.x + l1Rect.width() / 2f, it.y + l1Rect.height() / 2f) }
        point(Control.R1).also { r1Rect.set(it.x - r1Rect.width() / 2f, it.y - r1Rect.height() / 2f, it.x + r1Rect.width() / 2f, it.y + r1Rect.height() / 2f) }

        point(Control.CONFIRM).also { btnConfirm.x = it.x; btnConfirm.y = it.y }
        point(Control.BACK).also { btnBack.x = it.x; btnBack.y = it.y }
        point(Control.DASH).also { btnDash.x = it.x; btnDash.y = it.y }
        point(Control.EXTRA_A).also { btnExtraA.x = it.x; btnExtraA.y = it.y }
        point(Control.EXTRA_S).also { btnExtraS.x = it.x; btnExtraS.y = it.y }
        point(Control.EXTRA_D).also { btnExtraD.x = it.x; btnExtraD.y = it.y }
        point(Control.EXTRA_Z).also { btnExtraZ.x = it.x; btnExtraZ.y = it.y }
        point(Control.EXTRA_X).also { btnExtraX.x = it.x; btnExtraX.y = it.y }
        point(Control.EXTRA_C).also { btnExtraC.x = it.x; btnExtraC.y = it.y }
        point(Control.CTRL).also { btnCtrl.x = it.x; btnCtrl.y = it.y }
        point(Control.ALT).also { btnAlt.x = it.x; btnAlt.y = it.y }
        point(Control.SHIFT).also { btnShift.x = it.x; btnShift.y = it.y }
        actionRadius = radius(Control.CONFIRM) / scale

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
        radiusFor(Control.CONFIRM).also { controlRects[Control.CONFIRM] = RectF(btnConfirm.x - it, btnConfirm.y - it, btnConfirm.x + it, btnConfirm.y + it) }
        radiusFor(Control.BACK).also { controlRects[Control.BACK] = RectF(btnBack.x - it, btnBack.y - it, btnBack.x + it, btnBack.y + it) }
        radiusFor(Control.DASH).also { controlRects[Control.DASH] = RectF(btnDash.x - it, btnDash.y - it, btnDash.x + it, btnDash.y + it) }
        radiusFor(Control.EXTRA_A).also { controlRects[Control.EXTRA_A] = RectF(btnExtraA.x - it, btnExtraA.y - it, btnExtraA.x + it, btnExtraA.y + it) }
        radiusFor(Control.EXTRA_S).also { controlRects[Control.EXTRA_S] = RectF(btnExtraS.x - it, btnExtraS.y - it, btnExtraS.x + it, btnExtraS.y + it) }
        radiusFor(Control.EXTRA_D).also { controlRects[Control.EXTRA_D] = RectF(btnExtraD.x - it, btnExtraD.y - it, btnExtraD.x + it, btnExtraD.y + it) }
        radiusFor(Control.EXTRA_Z).also { controlRects[Control.EXTRA_Z] = RectF(btnExtraZ.x - it, btnExtraZ.y - it, btnExtraZ.x + it, btnExtraZ.y + it) }
        radiusFor(Control.EXTRA_X).also { controlRects[Control.EXTRA_X] = RectF(btnExtraX.x - it, btnExtraX.y - it, btnExtraX.x + it, btnExtraX.y + it) }
        radiusFor(Control.EXTRA_C).also { controlRects[Control.EXTRA_C] = RectF(btnExtraC.x - it, btnExtraC.y - it, btnExtraC.x + it, btnExtraC.y + it) }
        radiusFor(Control.CTRL).also { controlRects[Control.CTRL] = RectF(btnCtrl.x - it * 0.85f, btnCtrl.y - it * 0.85f, btnCtrl.x + it * 0.85f, btnCtrl.y + it * 0.85f) }
        radiusFor(Control.ALT).also { controlRects[Control.ALT] = RectF(btnAlt.x - it * 0.85f, btnAlt.y - it * 0.85f, btnAlt.x + it * 0.85f, btnAlt.y + it * 0.85f) }
        radiusFor(Control.SHIFT).also { controlRects[Control.SHIFT] = RectF(btnShift.x - it * 0.85f, btnShift.y - it * 0.85f, btnShift.x + it * 0.85f, btnShift.y + it * 0.85f) }
        controlRects[Control.SELECT] = RectF(selectRect)
        controlRects[Control.START] = RectF(startRect)
        controlRects[Control.MENU] = RectF(menuRect)
        controlRects[Control.L1] = RectF(l1Rect)
        controlRects[Control.R1] = RectF(r1Rect)
    }

    private fun radiusFor(control: Control): Float =
        (layout[control]?.size ?: (actionRadius * scale / minOf(width, height).coerceAtLeast(1))) *
            minOf(width, height).coerceAtLeast(1)

    private enum class Control {
        DPAD, CONFIRM, BACK, DASH,
        L1, R1,
        EXTRA_A, EXTRA_S, EXTRA_D,
        EXTRA_Z, EXTRA_X, EXTRA_C,
        CTRL, ALT, SHIFT,
        SELECT, START, MENU,
    }

    private data class ControlPlacement(
        var x: Float,
        var y: Float,
        var size: Float,
    )

    data class PointF(var x: Float, var y: Float)
}
