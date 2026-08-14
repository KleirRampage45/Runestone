package com.runestone.app.input

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.DashPathEffect
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
        L1, R1, ZL, ZR, L3, R3,
        GUIDE, PLUS, MINUS,
        LEFT_STICK, RIGHT_STICK,
        TOOLBAR_TOGGLE, TOOLBAR_SETTINGS, TOOLBAR_KEYBOARD, TOOLBAR_POINTER,
        OVERLAY_MENU, MENU_CHEATS, MENU_MUTE, MENU_ROTATE, MENU_REMAP, MENU_QUIT,
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
    var onToggleKeyboard: (() -> Unit)? = null
    var onTogglePointer: (() -> Unit)? = null
    var toolbarVisible: Boolean = true
    var menuOverlayVisible: Boolean = false
    var pointerMode: Boolean = false

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

    // Full mode additions
    private val leftStickRect = RectF()
    private val rightStickRect = RectF()
    private val leftStickThumb = PointF(0f, 0f)
    private val rightStickThumb = PointF(0f, 0f)
    private val zlRect = RectF()
    private val zrRect = RectF()
    private val l3Rect = RectF()
    private val r3Rect = RectF()
    private val guideRect = RectF()
    private val plusRect = RectF()
    private val minusRect = RectF()
    private var stickRadius = 32f
    private var innerStickRadius = 14f
    private var leftStickActive = false
    private var rightStickActive = false

    // Toolbar
    private val toolbarButtons = arrayOfNulls<RectF>(4)
    private val toolbarRect = RectF()
    private var toolbarButtonSize = 48f

    // Menu overlay
    private val menuOverlayRect = RectF()
    private val menuItems = mutableListOf<Pair<RectF, Zone>>()

    // Editor right toolbar
    private val editorCheckRect = RectF()
    private val editorUndoRect = RectF()
    private val editorRotateRect = RectF()
    private val editorCloseRect = RectF()
    private val editorHeaderRect = RectF()

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
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val touchZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val editorIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.5f
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

    private fun orientationPrefix(): String = when {
        controlsOnly -> "portrait_console"
        width > height -> "landscape"
        else -> "portrait"
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
            // 2x2 grid: Confirm tl, Back tr, Dash bl, ExtraS br
            val gridSize = actionSpread * 0.9f
            btnConfirm.x = rightGutterCenter - gridSize
            btnConfirm.y = actionBaseY - gridSize
            btnBack.x = rightGutterCenter + gridSize
            btnBack.y = actionBaseY - gridSize
            btnDash.x = rightGutterCenter - gridSize
            btnDash.y = actionBaseY + gridSize
            btnExtraS.x = rightGutterCenter + gridSize
            btnExtraS.y = actionBaseY + gridSize
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

        // ZL/ZR below L1/R1
        val zlH = 26f * s
        val zlW = 44f * s
        val zlY = l1Rect.bottom + 8f * s
        zlRect.set(l1Rect.centerX() - zlW / 2f, zlY, l1Rect.centerX() + zlW / 2f, zlY + zlH)
        zrRect.set(r1Rect.centerX() - zlW / 2f, zlY, r1Rect.centerX() + zlW / 2f, zlY + zlH)

        // Modifier buttons in left gutter (Full mode only)
        if (controllerPreset == ControllerPreset.FULL) {
            val modX = gameLeft * 0.5f
            val modY = h * 0.28f
            val modSpread = 36f * s
            btnCtrl.x = modX; btnCtrl.y = modY
            btnAlt.x = modX; btnAlt.y = modY + modSpread
            btnShift.x = modX; btnShift.y = modY + modSpread * 2f

            // Analog sticks (left in left gutter, right in right gutter)
            val stickOuter = 36f * s
            stickRadius = stickOuter / s
            innerStickRadius = stickRadius * 0.44f
            leftStickRect.set(
                leftGutterW * 0.5f - stickOuter, h * 0.46f - stickOuter,
                leftGutterW * 0.5f + stickOuter, h * 0.46f + stickOuter,
            )
            rightStickRect.set(
                rightGutterCenter - stickOuter, h * 0.46f - stickOuter,
                rightGutterCenter + stickOuter, h * 0.46f + stickOuter,
            )
            leftStickThumb.x = leftStickRect.centerX(); leftStickThumb.y = leftStickRect.centerY()
            rightStickThumb.x = rightStickRect.centerX(); rightStickThumb.y = rightStickRect.centerY()

            // L3/R3 below sticks
            val l3H = 24f * s
            val l3W = 40f * s
            l3Rect.set(
                leftStickRect.centerX() - l3W / 2f, leftStickRect.bottom + 6f * s,
                leftStickRect.centerX() + l3W / 2f, leftStickRect.bottom + 6f * s + l3H,
            )
            r3Rect.set(
                rightStickRect.centerX() - l3W / 2f, rightStickRect.bottom + 6f * s,
                rightStickRect.centerX() + l3W / 2f, rightStickRect.bottom + 6f * s + l3H,
            )

            // Guide/Plus/Minus — between SELECT/START/MENU in bottom bar area
            val miniW = 36f * s
            val miniH = 24f * s
            val miniY = h - 48f * s
            guideRect.set(w * 0.5f - miniW / 2f, miniY - miniH / 2f, w * 0.5f + miniW / 2f, miniY + miniH / 2f)
            plusRect.set(w * 0.5f - miniW / 2f - w * 0.08f, miniY - miniH / 2f, w * 0.5f + miniW / 2f - w * 0.08f, miniY + miniH / 2f)
            minusRect.set(w * 0.5f - miniW / 2f + w * 0.08f, miniY - miniH / 2f, w * 0.5f + miniW / 2f + w * 0.08f, miniY + miniH / 2f)
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

        // Toolbar on right edge
        layoutToolbar(w, h, s)
    }

    private fun layoutToolbar(w: Float, h: Float, s: Float) {
        val btnSize = 48f * s
        toolbarButtonSize = btnSize
        val gap = 8f * s
        val panelW = btnSize + gap * 2f
        val panelH = btnSize * 4f + gap * 5f
        val panelL = w - panelW - 4f * s
        val panelT = (h - panelH) / 2f
        toolbarRect.set(panelL, panelT, panelL + panelW, panelT + panelH)

        for (i in 0..3) {
            val b = RectF()
            b.set(
                panelL + gap, panelT + gap + i * (btnSize + gap),
                panelL + gap + btnSize, panelT + gap + i * (btnSize + gap) + btnSize,
            )
            toolbarButtons[i] = b
        }
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
            // 2x2 grid: Confirm tl, Back tr, Dash bl, ExtraS br
            val gridSize = spread * 0.45f
            btnConfirm.x = actionCenterX - gridSize
            btnConfirm.y = actionCenterY - gridSize
            btnBack.x = actionCenterX + gridSize
            btnBack.y = actionCenterY - gridSize
            btnDash.x = actionCenterX - gridSize
            btnDash.y = actionCenterY + gridSize
            btnExtraS.x = actionCenterX + gridSize
            btnExtraS.y = actionCenterY + gridSize
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

        // ZL/ZR beside L1/R1
        val zlH = 24f * s
        val zlW = 38f * s
        zlRect.set(l1Rect.right + 4f * s, shoulderY, l1Rect.right + 4f * s + zlW, shoulderY + zlH)
        zrRect.set(r1Rect.left - 4f * s - zlW, shoulderY, r1Rect.left - 4f * s, shoulderY + zlH)

        if (controllerPreset == ControllerPreset.FULL) {
            val modX = w * 0.12f
            val modY = panelTop + controlsHeight * 0.15f
            val modSpread = 34f * s
            btnCtrl.x = modX; btnCtrl.y = modY
            btnAlt.x = modX; btnAlt.y = modY + modSpread
            btnShift.x = modX; btnShift.y = modY + modSpread * 2f

            // Analog sticks
            val stickOuter = 34f * s
            stickRadius = stickOuter / s
            innerStickRadius = stickRadius * 0.44f
            leftStickRect.set(
                w * 0.35f - stickOuter, panelTop + controlsHeight * 0.20f - stickOuter,
                w * 0.35f + stickOuter, panelTop + controlsHeight * 0.20f + stickOuter,
            )
            rightStickRect.set(
                w * 0.65f - stickOuter, panelTop + controlsHeight * 0.20f - stickOuter,
                w * 0.65f + stickOuter, panelTop + controlsHeight * 0.20f + stickOuter,
            )
            leftStickThumb.x = leftStickRect.centerX(); leftStickThumb.y = leftStickRect.centerY()
            rightStickThumb.x = rightStickRect.centerX(); rightStickThumb.y = rightStickRect.centerY()

            // L3/R3
            val l3H = 22f * s
            val l3W = 36f * s
            l3Rect.set(
                leftStickRect.centerX() - l3W / 2f, leftStickRect.bottom + 4f * s,
                leftStickRect.centerX() + l3W / 2f, leftStickRect.bottom + 4f * s + l3H,
            )
            r3Rect.set(
                rightStickRect.centerX() - l3W / 2f, rightStickRect.bottom + 4f * s,
                rightStickRect.centerX() + l3W / 2f, rightStickRect.bottom + 4f * s + l3H,
            )

            // Guide/Plus/Minus
            val miniW = 32f * s
            val miniH = 20f * s
            val miniY = barY - 4f * s
            val miniGap = 20f * s
            guideRect.set(w * 0.5f - miniW / 2f, miniY - miniH / 2f, w * 0.5f + miniW / 2f, miniY + miniH / 2f)
            plusRect.set(guideRect.centerX() - miniGap - miniW, miniY - miniH / 2f, guideRect.centerX() - miniGap, miniY + miniH / 2f)
            minusRect.set(guideRect.centerX() + miniGap, miniY - miniH / 2f, guideRect.centerX() + miniGap + miniW, miniY + miniH / 2f)
        }

        overlayMenuRect.set(8f * s, panelTop + 8f * s, 56f * s, panelTop + 56f * s)
        editButtonRect.set(w - 76f * s, h - 76f * s, w - 8f * s, h - 8f * s)

        layoutToolbarPortrait(w, h, s, panelTop)
    }

    private fun layoutToolbarPortrait(w: Float, h: Float, s: Float, panelTop: Float) {
        val btnSize = 44f * s
        toolbarButtonSize = btnSize
        val gap = 6f * s
        val panelW = btnSize + gap * 2f
        val panelH = btnSize * 4f + gap * 5f
        val panelL = w - panelW - 4f * s
        val panelT = panelTop + 16f * s
        toolbarRect.set(panelL, panelT, panelL + panelW, panelT + panelH)

        for (i in 0..3) {
            val b = RectF()
            b.set(
                panelL + gap, panelT + gap + i * (btnSize + gap),
                panelL + gap + btnSize, panelT + gap + i * (btnSize + gap) + btnSize,
            )
            toolbarButtons[i] = b
        }
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
            drawGlassButton(canvas, btnDash.x, btnDash.y, radiusFor(Control.DASH), "Z", a, Zone.BTN_DASH, Control.DASH)
            drawGlassButton(canvas, btnExtraS.x, btnExtraS.y, radiusFor(Control.EXTRA_S), "B", a, Zone.BTN_EXTRA_S, Control.EXTRA_S)
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
        drawGlassRoundRect(canvas, selectRect, "SELECT", a, Zone.SELECT, Control.SELECT)
        drawGlassRoundRect(canvas, startRect, "START", a, Zone.START, Control.START)
        drawGlassRoundRect(canvas, menuRect, "HOME", a, Zone.HOME, Control.MENU)

        // L1/R1
        drawShoulderButton(canvas, l1Rect, "L", a, Zone.L1)
        drawShoulderButton(canvas, r1Rect, "R", a, Zone.R1)

        // ZL/ZR in FULL mode
        if (controllerPreset == ControllerPreset.FULL) {
            drawShoulderButton(canvas, zlRect, "ZL", a, Zone.ZL)
            drawShoulderButton(canvas, zrRect, "ZR", a, Zone.ZR)
        }

        // Analog sticks + L3/R3 in FULL mode
        if (controllerPreset == ControllerPreset.FULL) {
            drawAnalogStick(canvas, leftStickRect, leftStickActive, a, true)
            drawAnalogStick(canvas, rightStickRect, rightStickActive, a, false)
            drawShoulderButton(canvas, l3Rect, "L3", a, Zone.L3)
            drawShoulderButton(canvas, r3Rect, "R3", a, Zone.R3)
            drawShoulderButton(canvas, guideRect, "GUIDE", a, Zone.GUIDE)
            drawGlassBarButton(canvas, plusRect, "+", a, Zone.PLUS)
            drawGlassBarButton(canvas, minusRect, "-", a, Zone.MINUS)
        }

        // Toolbar
        if (toolbarVisible) {
            drawToolbar(canvas, a)
        }

        // Menu overlay
        if (menuOverlayVisible) {
            drawMenuOverlay(canvas, a)
        }

        // Overlay menu
        drawOverlayMenuButton(canvas, a)
        if (quickSettingsOpen) drawQuickSettings(canvas, a)
        if (editing) drawEditorChrome(canvas)

        // Debug touch zones
        if (showTouchZones) {
            drawTouchZones(canvas)
        }
    }

    private fun drawAnalogStick(canvas: Canvas, rect: RectF, pressed: Boolean, a: Float, isLeft: Boolean) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        val sr = r * 0.45f
        val isr = r * 0.20f

        // Outer shadow
        shadowPaint.alpha = (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx + 2f, cy + 3f, r, shadowPaint)

        // Outer ring
        val ringPaint = if (pressed) pressedGlassPaint else glassFillPaint
        ringPaint.alpha = if (pressed) (0.22f * 255 * a).toInt().coerceIn(0, 255) else (0.12f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, ringPaint)

        glassBorderPaint.alpha = if (pressed) (0.50f * 255 * a).toInt().coerceIn(0, 255) else (0.32f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, glassBorderPaint)

        // Top highlight on ring
        if (!pressed) {
            glassHighlightPaint.alpha = (0.18f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, true, glassHighlightPaint)
        }

        // Crosshair lines
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 1.5f * scale
            color = Color.argb((0.25f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        val crossExtent = r * 0.55f
        canvas.drawLine(cx - crossExtent, cy, cx + crossExtent, cy, linePaint)
        canvas.drawLine(cx, cy - crossExtent, cx, cy + crossExtent, linePaint)

        // Center dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb((0.20f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, r * 0.08f, dotPaint)

        // Thumb circle
        val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val thumbAlpha = if (pressed) (0.30f * 255 * a).toInt().coerceIn(0, 255) else (0.18f * 255 * a).toInt().coerceIn(0, 255)
        thumbPaint.color = Color.argb(thumbAlpha, 255, 255, 255)
        val thumbX = if (isLeft) leftStickThumb.x else rightStickThumb.x
        val thumbY = if (isLeft) leftStickThumb.y else rightStickThumb.y
        canvas.drawCircle(thumbX, thumbY, sr, thumbPaint)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb((0.40f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        canvas.drawCircle(thumbX, thumbY, sr, tp)

        // Inner thumb dot
        if (!pressed) {
            glassHighlightPaint.alpha = (0.15f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawCircle(thumbX, thumbY, sr * 0.5f, glassHighlightPaint)
        }
    }

    private fun drawToolbar(canvas: Canvas, a: Float) {
        if (!toolbarVisible || toolbarButtons[0] == null) return

        // Panel background
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb((0.10f * 255 * a).toInt().coerceIn(0, 255), 0, 0, 0)
        }
        canvas.drawRoundRect(toolbarRect, 12f * scale, 12f * scale, panelPaint)

        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb((0.20f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        canvas.drawRoundRect(toolbarRect, 12f * scale, 12f * scale, borderP)

        val iconLabelAlpha = (0.85f * 255 * a).toInt().coerceIn(0, 255)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2f * scale
            color = Color.argb(iconLabelAlpha, 232, 229, 220)
        }

        val zones = listOf(
            Zone.TOOLBAR_TOGGLE to "Toggle",
            Zone.TOOLBAR_SETTINGS to "Settings",
            Zone.TOOLBAR_KEYBOARD to "Keys",
            Zone.TOOLBAR_POINTER to "Pointer",
        )

        for (i in 0..3) {
            val rect = toolbarButtons[i] ?: continue
            val zone = zones[i].first
            val label = zones[i].second
            val pressed = zone in activeZones

            val btnFill = if (pressed) pressedGlassPaint else glassFillPaint
            btnFill.alpha = if (pressed) (0.22f * 255 * a).toInt().coerceIn(0, 255) else (0.12f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, 8f * scale, 8f * scale, btnFill)

            if (pressed) {
                val bdr = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                    color = Color.argb((0.50f * 255 * a).toInt().coerceIn(0, 255), 210, 180, 134)
                }
                canvas.drawRoundRect(rect, 8f * scale, 8f * scale, bdr)
            }

            val cx = rect.centerX()
            val cy = rect.centerY()
            val s = rect.width() * 0.30f

            iconPaint.color = if (pressed) Color.rgb(238, 207, 158) else Color.argb(iconLabelAlpha, 232, 229, 220)

            when (i) {
                0 -> OverlayStyle.Icons.gamepad(canvas, cx, cy, s, iconPaint)
                1 -> OverlayStyle.Icons.sliders(canvas, cx, cy, s, iconPaint)
                2 -> OverlayStyle.Icons.keyboard(canvas, cx, cy, s, iconPaint)
                3 -> OverlayStyle.Icons.touchPointer(canvas, cx, cy, s, iconPaint)
            }
        }
    }

    private fun drawMenuOverlay(canvas: Canvas, a: Float) {
        // Dimmed background
        val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb((0.55f * 255).toInt().coerceIn(0, 255), 0, 0, 0)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        // Centered glass panel
        val panelW = (260f * scale).coerceAtLeast(220f)
        val itemH = (52f * scale).coerceAtLeast(44f)
        val gap = (8f * scale).coerceAtLeast(4f)
        val panelItems = 5
        val panelPad = 20f * scale
        val panelH = panelPad * 2f + itemH * panelItems + gap * (panelItems - 1)
        val panelL = (width - panelW) / 2f
        val panelT = (height - panelH) / 2f
        menuOverlayRect.set(panelL, panelT, panelL + panelW, panelT + panelH)

        val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb((0.15f * 255).toInt().coerceIn(0, 255), 14, 14, 18)
        }
        canvas.drawRoundRect(menuOverlayRect, 20f * scale, 20f * scale, panelFill)

        val panelBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb((0.35f * 255).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        canvas.drawRoundRect(menuOverlayRect, 20f * scale, 20f * scale, panelBorder)

        menuItems.clear()
        val entries = listOf(
            Zone.MENU_CHEATS to "Cheats",
            Zone.MENU_MUTE to "Mute",
            Zone.MENU_ROTATE to "Rotate",
            Zone.MENU_REMAP to "Remap",
            Zone.MENU_QUIT to "Quit",
        )

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2f * scale
            color = Color.argb((0.85f * 255).toInt().coerceIn(0, 255), 232, 229, 220)
        }

        for ((index, entry) in entries.withIndex()) {
            val (zone, name) = entry
            val itemRect = RectF(
                menuOverlayRect.left + panelPad,
                menuOverlayRect.top + panelPad + index * (itemH + gap),
                menuOverlayRect.right - panelPad,
                menuOverlayRect.top + panelPad + index * (itemH + gap) + itemH,
            )
            menuItems.add(itemRect to zone)

            val pressed = zone in activeZones

            // Item background
            if (pressed) {
                val itemBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb((0.20f * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                }
                canvas.drawRoundRect(itemRect, 10f * scale, 10f * scale, itemBg)
            }

            // Icon
            val iconCx = itemRect.left + itemH * 0.5f
            val iconCy = itemRect.centerY()
            val iconS = itemH * 0.30f

            when (index) {
                0 -> OverlayStyle.Icons.wand(canvas, iconCx, iconCy, iconS, iconPaint)
                1 -> OverlayStyle.Icons.speakerMute(canvas, iconCx, iconCy, iconS, iconPaint)
                2 -> OverlayStyle.Icons.phoneRotate(canvas, iconCx, iconCy, iconS, iconPaint)
                3 -> OverlayStyle.Icons.grid(canvas, iconCx, iconCy, iconS, iconPaint)
                4 -> OverlayStyle.Icons.exitDoor(canvas, iconCx, iconCy, iconS, iconPaint)
            }

            // Label
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.LEFT
                textSize = 15f * scale
                isFakeBoldText = true
                color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((0.85f * 255).toInt().coerceIn(0, 255), 255, 255, 255)
            }
            canvas.drawText(name, iconCx + iconS + 12f * scale, itemRect.centerY() + 5f * scale, lp)
        }
    }

    private fun getConfirmLabel(): String = "ENTER"
    private fun getBackLabel(): String = if (showIcons && !showLabels) "" else "ESC"

    private fun drawGlassButton(canvas: Canvas, x: Float, y: Float, r: Float, label: String, a: Float, zone: Zone, control: Control) {
        val zonePressed = zone in activeZones
        val baseRadius = r

        shadowPaint.alpha = (0.35f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawCircle(x + 2f, y + 4f, baseRadius, shadowPaint)

        val fillPaint = if (zonePressed) pressedGlassPaint else glassFillPaint
        fillPaint.alpha = if (zonePressed) {
            (0.22f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.12f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(x, y, baseRadius, fillPaint)

        glassBorderPaint.alpha = if (zonePressed) {
            (0.50f * 255 * a).toInt().coerceIn(0, 255)
        } else {
            (0.32f * 255 * a).toInt().coerceIn(0, 255)
        }
        canvas.drawCircle(x, y, baseRadius, glassBorderPaint)

        if (!zonePressed) {
            glassHighlightPaint.alpha = (0.18f * 255 * a).toInt().coerceIn(0, 255)
            canvas.drawArc(RectF(x - baseRadius, y - baseRadius, x + baseRadius, y + baseRadius), 225f, 90f, true, glassHighlightPaint)
        }

        val scaleDown = if (zonePressed) 0.96f else 1.0f
        if (zonePressed) {
            canvas.save()
            canvas.scale(scaleDown, scaleDown, x, y)
        }

        if (label.isNotEmpty()) {
            labelPaint.alpha = if (zonePressed) 255 else (0.85f * 255 * a).toInt().coerceIn(0, 255)
            labelPaint.color = if (zonePressed) Color.rgb(200, 170, 130) else Color.argb((0.85f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            labelPaint.textSize = 24f * scale
            canvas.drawText(label, x, y + 8f * scale, labelPaint)
        }

        if (zonePressed) canvas.restore()

        if (showControlBounds) {
            glassBorderPaint.alpha = (0.60f * 255 * a).toInt().coerceIn(0, 255)
            glassBorderPaint.color = Color.argb(glassBorderPaint.alpha, 0, 255, 200)
            canvas.drawCircle(x, y, baseRadius, glassBorderPaint)
            glassBorderPaint.color = Color.argb((0.32f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }

        drawSelection(canvas, control)
        controlRects[control] = RectF(x - baseRadius, y - baseRadius, x + baseRadius, y + baseRadius)
    }

    private fun drawGlassRoundRect(canvas: Canvas, rect: RectF, label: String, a: Float, zone: Zone, control: Control) {
        val pressed = zone in activeZones
        val r = rect.height() * 0.35f

        shadowPaint.alpha = (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect.left + 1f, rect.top + 3f, rect.right + 1f, rect.bottom + 3f, r, r, shadowPaint)

        val fillPaint = if (pressed) pressedGlassPaint else glassFillPaint
        fillPaint.alpha = if (pressed) (0.22f * 255 * a).toInt().coerceIn(0, 255) else (0.12f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, r, r, fillPaint)

        glassBorderPaint.alpha = if (pressed) (0.45f * 255 * a).toInt().coerceIn(0, 255) else (0.32f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, r, r, glassBorderPaint)

        if (!pressed) {
            glassHighlightPaint.alpha = (0.15f * 255 * a).toInt().coerceIn(0, 255)
            val hlRect = RectF(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f)
            canvas.drawRoundRect(hlRect, r, r, glassHighlightPaint)
        }

        labelPaint.alpha = if (pressed) 255 else (0.85f * 255 * a).toInt().coerceIn(0, 255)
        labelPaint.color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((0.85f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        labelPaint.textSize = 14f * scale
        canvas.drawText(label, rect.centerX(), rect.centerY() + 5f, labelPaint)

        drawSelection(canvas, control)
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
        val totalSize = dpadRadius * scale * 1.6f
        val gap = totalSize * 0.06f
        val btnW = totalSize * 0.34f
        val btnH = totalSize * 0.32f
        val corner = 8f * scale

        drawDpadBtn(canvas, RectF(cx - btnW / 2f, cy - btnH - gap / 2f, cx + btnW / 2f, cy - gap / 2f), corner, a, Zone.DPAD_UP, "\u25B2")
        drawDpadBtn(canvas, RectF(cx - btnW / 2f, cy + gap / 2f, cx + btnW / 2f, cy + btnH + gap / 2f), corner, a, Zone.DPAD_DOWN, "\u25BC")
        drawDpadBtn(canvas, RectF(cx - btnW - gap / 2f, cy - btnH / 2f, cx - gap / 2f, cy + btnH / 2f), corner, a, Zone.DPAD_LEFT, "\u25C0")
        drawDpadBtn(canvas, RectF(cx + gap / 2f, cy - btnH / 2f, cx + btnW + gap / 2f, cy + btnH / 2f), corner, a, Zone.DPAD_RIGHT, "\u25B6")

        val outer = totalSize / 2f + gap
        controlRects[Control.DPAD] = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
    }

    private fun drawDpadBtn(canvas: Canvas, rect: RectF, corner: Float, a: Float, zone: Zone, arrow: String) {
        val pressed = zone in activeZones
        val r = corner
        shadowPaint.alpha = (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect.left + 1f, rect.top + 2f, rect.right + 1f, rect.bottom + 2f, r, r, shadowPaint)
        val fillA = if (pressed) 0.22f else 0.10f
        glassFillPaint.color = Color.argb((fillA * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawRoundRect(rect, r, r, glassFillPaint)
        glassBorderPaint.alpha = if (pressed) (0.45f * 255 * a).toInt().coerceIn(0, 255) else (0.30f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, r, r, glassBorderPaint)
        if (!pressed) {
            glassHighlightPaint.alpha = (0.12f * 255 * a).toInt().coerceIn(0, 255)
            val hl = RectF(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.4f)
            canvas.drawRoundRect(hl, r, r, glassHighlightPaint)
        }
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; isFakeBoldText = true
            textSize = rect.height() * 0.6f
            color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((0.85f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        canvas.drawText(arrow, rect.centerX(), rect.centerY() + ap.textSize * 0.35f, ap)
        drawSelection(canvas, Control.DPAD)
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
                    handleSpecialZones(newZones)
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
                handleSpecialZones(pressed)
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

    private fun handleSpecialZones(zones: Set<Zone>) {
        for (zone in zones) {
            when (zone) {
                Zone.TOOLBAR_TOGGLE -> {
                    onToggleControls?.invoke()
                }
                Zone.TOOLBAR_SETTINGS -> {
                    onOverlayMenu?.invoke()
                }
                Zone.TOOLBAR_KEYBOARD -> {
                    onToggleKeyboard?.invoke()
                }
                Zone.TOOLBAR_POINTER -> {
                    onTogglePointer?.invoke()
                }
                Zone.MENU_CHEATS -> {
                    menuOverlayVisible = false
                    invalidate()
                }
                Zone.MENU_MUTE -> {
                    menuOverlayVisible = false
                    invalidate()
                }
                Zone.MENU_ROTATE -> {
                    menuOverlayVisible = false
                    onRotateLayout?.invoke()
                }
                Zone.MENU_REMAP -> {
                    menuOverlayVisible = false
                    invalidate()
                }
                Zone.MENU_QUIT -> {
                    menuOverlayVisible = false
                    onOverlayMenu?.invoke()
                }
                Zone.LEFT_STICK -> {
                    leftStickActive = true
                }
                Zone.RIGHT_STICK -> {
                    rightStickActive = true
                }
                else -> {}
            }
        }
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

    fun toggleMenuOverlay() {
        menuOverlayVisible = !menuOverlayVisible
        invalidate()
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

        if (isLandscape && !editing && gameViewportRight > gameViewportLeft) {
            if (x > gameViewportLeft && x < gameViewportRight && y > gameViewportTop && y < gameViewportBottom) {
                return null
            }
        }

        if (!isLandscape && !editing && !controlsOnly) {
            val panelTop = height * 0.55f
            if (y < panelTop) return null
        }

        // Menu items (highest priority)
        for ((rect, zone) in menuItems) {
            if (rect.contains(x, y)) return zone
        }

        // Toolbar buttons
        if (toolbarVisible) {
            for (i in 0..3) {
                val rect = toolbarButtons[i] ?: continue
                if (rect.contains(x, y)) {
                    return when (i) {
                        0 -> Zone.TOOLBAR_TOGGLE
                        1 -> Zone.TOOLBAR_SETTINGS
                        2 -> Zone.TOOLBAR_KEYBOARD
                        3 -> Zone.TOOLBAR_POINTER
                        else -> null
                    }
                }
            }
        }

        // Overlay menu
        if (overlayMenuRect.contains(x, y)) return Zone.OVERLAY_MENU

        // ZL/ZR in FULL mode
        if (controllerPreset == ControllerPreset.FULL) {
            if (zlRect.contains(x, y)) return Zone.ZL
            if (zrRect.contains(x, y)) return Zone.ZR
        }

        // L1/R1
        if (l1Rect.contains(x, y)) return Zone.L1
        if (r1Rect.contains(x, y)) return Zone.R1

        // Bottom bar
        if (selectRect.contains(x, y)) return Zone.SELECT
        if (startRect.contains(x, y)) return Zone.START
        if (menuRect.contains(x, y)) return Zone.HOME

        // FULL mode extras
        if (controllerPreset == ControllerPreset.FULL) {
            // Analog sticks
            if (leftStickRect.contains(x, y)) return Zone.LEFT_STICK
            if (rightStickRect.contains(x, y)) return Zone.RIGHT_STICK

            // L3/R3
            if (l3Rect.contains(x, y)) return Zone.L3
            if (r3Rect.contains(x, y)) return Zone.R3

            // Guide/Plus/Minus
            if (guideRect.contains(x, y)) return Zone.GUIDE
            if (plusRect.contains(x, y)) return Zone.PLUS
            if (minusRect.contains(x, y)) return Zone.MINUS

            // Modifiers
            if (dist(x, y, btnCtrl.x, btnCtrl.y) < radiusFor(Control.CTRL) * 0.85f) return Zone.BTN_CTRL
            if (dist(x, y, btnAlt.x, btnAlt.y) < radiusFor(Control.ALT) * 0.85f) return Zone.BTN_ALT
            if (dist(x, y, btnShift.x, btnShift.y) < radiusFor(Control.SHIFT) * 0.85f) return Zone.BTN_SHIFT
        }

        // Action buttons based on preset
        if (controllerPreset == ControllerPreset.SIMPLIFIED) {
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) return Zone.BTN_CONFIRM
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) return Zone.BTN_BACK
            if (dist(x, y, btnDash.x, btnDash.y) < radiusFor(Control.DASH)) return Zone.BTN_DASH
            if (dist(x, y, btnExtraS.x, btnExtraS.y) < radiusFor(Control.EXTRA_S)) return Zone.BTN_EXTRA_S
        } else {
            if (dist(x, y, btnExtraA.x, btnExtraA.y) < radiusFor(Control.EXTRA_A)) return Zone.BTN_EXTRA_A
            if (dist(x, y, btnExtraS.x, btnExtraS.y) < radiusFor(Control.EXTRA_S)) return Zone.BTN_EXTRA_S
            if (dist(x, y, btnExtraD.x, btnExtraD.y) < radiusFor(Control.EXTRA_D)) return Zone.BTN_EXTRA_D
            if (dist(x, y, btnExtraZ.x, btnExtraZ.y) < radiusFor(Control.EXTRA_Z)) return Zone.BTN_EXTRA_Z
            if (dist(x, y, btnExtraX.x, btnExtraX.y) < radiusFor(Control.EXTRA_X)) return Zone.BTN_EXTRA_X
            if (dist(x, y, btnExtraC.x, btnExtraC.y) < radiusFor(Control.EXTRA_C)) return Zone.BTN_EXTRA_C
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) return Zone.BTN_CONFIRM
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) return Zone.BTN_BACK
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

        // Menu items (highest priority)
        for ((rect, zone) in menuItems) {
            if (rect.contains(x, y)) return setOf(zone)
        }

        // Toolbar buttons
        if (toolbarVisible) {
            for (i in 0..3) {
                val rect = toolbarButtons[i] ?: continue
                if (rect.contains(x, y)) {
                    return setOf(
                        when (i) {
                            0 -> Zone.TOOLBAR_TOGGLE
                            1 -> Zone.TOOLBAR_SETTINGS
                            2 -> Zone.TOOLBAR_KEYBOARD
                            3 -> Zone.TOOLBAR_POINTER
                            else -> null
                        }!!
                    )
                }
            }
        }

        if (overlayMenuRect.contains(x, y)) return setOf(Zone.OVERLAY_MENU)

        // FULL mode extras
        if (controllerPreset == ControllerPreset.FULL) {
            if (zlRect.contains(x, y)) return setOf(Zone.ZL)
            if (zrRect.contains(x, y)) return setOf(Zone.ZR)
            if (leftStickRect.contains(x, y)) result.add(Zone.LEFT_STICK)
            if (rightStickRect.contains(x, y)) result.add(Zone.RIGHT_STICK)
            if (l3Rect.contains(x, y)) return setOf(Zone.L3)
            if (r3Rect.contains(x, y)) return setOf(Zone.R3)
            if (guideRect.contains(x, y)) return setOf(Zone.GUIDE)
            if (plusRect.contains(x, y)) return setOf(Zone.PLUS)
            if (minusRect.contains(x, y)) return setOf(Zone.MINUS)
        }

        if (l1Rect.contains(x, y)) return setOf(Zone.L1)
        if (r1Rect.contains(x, y)) return setOf(Zone.R1)
        if (selectRect.contains(x, y)) return setOf(Zone.SELECT)
        if (startRect.contains(x, y)) return setOf(Zone.START)
        if (menuRect.contains(x, y)) return setOf(Zone.HOME)

        if (controllerPreset == ControllerPreset.SIMPLIFIED) {
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) result.add(Zone.BTN_CONFIRM)
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) result.add(Zone.BTN_BACK)
            if (dist(x, y, btnDash.x, btnDash.y) < radiusFor(Control.DASH)) result.add(Zone.BTN_DASH)
            if (dist(x, y, btnExtraS.x, btnExtraS.y) < radiusFor(Control.EXTRA_S)) result.add(Zone.BTN_EXTRA_S)
        } else {
            if (dist(x, y, btnExtraA.x, btnExtraA.y) < radiusFor(Control.EXTRA_A)) result.add(Zone.BTN_EXTRA_A)
            if (dist(x, y, btnExtraS.x, btnExtraS.y) < radiusFor(Control.EXTRA_S)) result.add(Zone.BTN_EXTRA_S)
            if (dist(x, y, btnExtraD.x, btnExtraD.y) < radiusFor(Control.EXTRA_D)) result.add(Zone.BTN_EXTRA_D)
            if (dist(x, y, btnExtraZ.x, btnExtraZ.y) < radiusFor(Control.EXTRA_Z)) result.add(Zone.BTN_EXTRA_Z)
            if (dist(x, y, btnExtraX.x, btnExtraX.y) < radiusFor(Control.EXTRA_X)) result.add(Zone.BTN_EXTRA_X)
            if (dist(x, y, btnExtraC.x, btnExtraC.y) < radiusFor(Control.EXTRA_C)) result.add(Zone.BTN_EXTRA_C)
            if (dist(x, y, btnConfirm.x, btnConfirm.y) < radiusFor(Control.CONFIRM)) result.add(Zone.BTN_CONFIRM)
            if (dist(x, y, btnBack.x, btnBack.y) < radiusFor(Control.BACK)) result.add(Zone.BTN_BACK)
            if (controllerPreset == ControllerPreset.FULL) {
                if (dist(x, y, btnCtrl.x, btnCtrl.y) < radiusFor(Control.CTRL) * 0.85f) result.add(Zone.BTN_CTRL)
                if (dist(x, y, btnAlt.x, btnAlt.y) < radiusFor(Control.ALT) * 0.85f) result.add(Zone.BTN_ALT)
                if (dist(x, y, btnShift.x, btnShift.y) < radiusFor(Control.SHIFT) * 0.85f) result.add(Zone.BTN_SHIFT)
            }
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
        Zone.ZL -> Control.ZL
        Zone.ZR -> Control.ZR
        Zone.L3 -> Control.L3
        Zone.R3 -> Control.R3
        Zone.GUIDE -> Control.GUIDE
        Zone.PLUS -> Control.PLUS
        Zone.MINUS -> Control.MINUS
        Zone.LEFT_STICK -> Control.LEFT_STICK
        Zone.RIGHT_STICK -> Control.RIGHT_STICK
        Zone.TOOLBAR_TOGGLE -> Control.MENU
        Zone.TOOLBAR_SETTINGS -> Control.MENU
        Zone.TOOLBAR_KEYBOARD -> Control.MENU
        Zone.TOOLBAR_POINTER -> Control.MENU
        Zone.OVERLAY_MENU -> Control.MENU
        Zone.MENU_CHEATS -> Control.MENU
        Zone.MENU_MUTE -> Control.MENU
        Zone.MENU_ROTATE -> Control.MENU
        Zone.MENU_REMAP -> Control.MENU
        Zone.MENU_QUIT -> Control.MENU
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
                    editorCheckRect.contains(x, y) -> {
                        saveLayout()
                        editing = false
                        selectedControl = null
                        invalidate()
                    }
                    editorUndoRect.contains(x, y) -> {
                        layout.clear()
                        layout.putAll(savedLayoutBeforeEdit.mapValues { it.value.copy() })
                        applySavedLayout()
                        editing = false
                        selectedControl = null
                        invalidate()
                    }
                    editorRotateRect.contains(x, y) -> {
                        resetToPreset()
                    }
                    editorCloseRect.contains(x, y) -> {
                        layout.clear()
                        layout.putAll(savedLayoutBeforeEdit.mapValues { it.value.copy() })
                        applySavedLayout()
                        editing = false
                        selectedControl = null
                        invalidate()
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

        // Layout Editor header at top
        val headerH = (44f * scale).coerceAtLeast(36f)
        editorHeaderRect.set(0f, 0f, width.toFloat(), headerH)

        // Right toolbar buttons
        val btnSize = (48f * scale).coerceAtLeast(40f)
        val btnGap = (8f * scale).coerceAtLeast(4f)
        val tbX = width - btnSize - 12f * scale
        val tbStartY = headerH + 16f * scale

        editorCheckRect.set(tbX, tbStartY, tbX + btnSize, tbStartY + btnSize)
        editorUndoRect.set(tbX, tbStartY + btnSize + btnGap, tbX + btnSize, tbStartY + (btnSize + btnGap) * 2)
        editorRotateRect.set(tbX, tbStartY + (btnSize + btnGap) * 2, tbX + btnSize, tbStartY + (btnSize + btnGap) * 3)
        editorCloseRect.set(tbX, tbStartY + (btnSize + btnGap) * 3, tbX + btnSize, tbStartY + (btnSize + btnGap) * 4)

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
        // Grid overlay
        val gridPatternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.argb(30, 255, 255, 255)
        }
        val gridSpacing = 48f * scale
        var gy = editorHeaderRect.bottom + gridSpacing
        while (gy < height) {
            canvas.drawLine(0f, gy, width.toFloat(), gy, gridPatternPaint)
            gy += gridSpacing
        }
        var gx = gridSpacing
        while (gx < width) {
            canvas.drawLine(gx, 0f, gx, height.toFloat(), gridPatternPaint)
            gx += gridSpacing
        }

        // Header bar
        editorPaint.alpha = 200
        editorPaint.color = Color.argb(200, 14, 14, 18)
        canvas.drawRect(editorHeaderRect, editorPaint)

        val headerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(60, 255, 255, 255)
        }
        canvas.drawLine(editorHeaderRect.left, editorHeaderRect.bottom, editorHeaderRect.right, editorHeaderRect.bottom, headerLinePaint)

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 16f * scale
            isFakeBoldText = true
            color = Color.argb(220, 232, 229, 220)
        }
        canvas.drawText("Layout Editor", editorHeaderRect.centerX(), editorHeaderRect.centerY() + 6f * scale, headerTextPaint)

        // Right toolbar (check/undo/rotate/close)
        val toolbarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(180, 14, 14, 18)
        }
        val toolBarL = editorCheckRect.left - 8f * scale
        val toolBarR = editorCheckRect.right + 8f * scale
        val toolBarT = editorCheckRect.top - 8f * scale
        val toolBarB = editorCloseRect.bottom + 8f * scale
        canvas.drawRoundRect(RectF(toolBarL, toolBarT, toolBarR, toolBarB), 12f * scale, 12f * scale, toolbarBgPaint)

        val toolBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.argb(50, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(toolBarL, toolBarT, toolBarR, toolBarB), 12f * scale, 12f * scale, toolBorderPaint)

        drawEditorToolButton(canvas, editorCheckRect, OverlayStyle.Icons::check, "Done")
        drawEditorToolButton(canvas, editorUndoRect, OverlayStyle.Icons::undo, "Undo")
        drawEditorToolButton(canvas, editorRotateRect, OverlayStyle.Icons::rotate, "Reset")
        drawEditorToolButton(canvas, editorCloseRect, OverlayStyle.Icons::close, "Cancel")

        // Dashed bounding boxes + circular handles on selected control
        selectedControl?.let { control ->
            controlRects[control]?.let { rect ->
                val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
                    color = Color.rgb(210, 180, 134)
                }
                canvas.drawRoundRect(rect, 12f, 12f, dashPaint)

                // Corner handles (small circles at each corner)
                val handleR = 6f * scale
                val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.rgb(210, 180, 134)
                }
                val corners = listOf(
                    rect.left to rect.top,
                    rect.right to rect.top,
                    rect.left to rect.bottom,
                    rect.right to rect.bottom,
                )
                for ((hx, hy) in corners) {
                    canvas.drawCircle(hx, hy, handleR, handlePaint)
                    handlePaint.color = Color.argb(180, 210, 180, 134)
                    canvas.drawCircle(hx, hy, handleR, handlePaint)
                    handlePaint.color = Color.rgb(210, 180, 134)
                }
            }
        }

        // Bottom hint pill with info icon
        val hintText = "Drag · Resize · Pinch"
        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            textSize = 12f * scale
            color = Color.argb(160, 232, 229, 220)
        }
        val hintW = hintPaint.measureText(hintText) + 40f * scale
        val hintH = 28f * scale
        val hintX = (width - hintW) / 2f
        val hintY = height - hintH - 16f * scale

        val hintBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(140, 0, 0, 0)
        }
        val hintRect = RectF(hintX, hintY, hintX + hintW, hintY + hintH)
        canvas.drawRoundRect(hintRect, hintH / 2f, hintH / 2f, hintBgPaint)

        val hintBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.argb(60, 255, 255, 255)
        }
        canvas.drawRoundRect(hintRect, hintH / 2f, hintH / 2f, hintBorderPaint)

        // Info icon
        val iconPs = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 1.5f
            color = Color.argb(160, 232, 229, 220)
        }
        val infoCx = hintX + 14f * scale
        val infoCy = hintY + hintH / 2f
        iconPs.style = Paint.Style.STROKE
        canvas.drawCircle(infoCx, infoCy, 5f * scale, iconPs)
        iconPs.style = Paint.Style.FILL
        canvas.drawCircle(infoCx, infoCy - 1.5f * scale, 1.5f * scale, iconPs)
        canvas.drawLine(infoCx, infoCy + 1f * scale, infoCx, infoCy + 4f * scale, iconPs)
        iconPs.style = Paint.Style.STROKE

        canvas.drawText(hintText, hintX + 22f * scale, hintY + hintH / 2f + 4f * scale, hintPaint)
    }

    private fun drawEditorToolButton(canvas: Canvas, rect: RectF, icon: (Canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) -> Unit, label: String) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(80, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, 10f * scale, 10f * scale, bgPaint)

        val bdrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.argb(100, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, 10f * scale, 10f * scale, bdrPaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2f * scale
            color = Color.argb(200, 232, 229, 220)
        }
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = rect.width() * 0.32f
        icon(canvas, cx, cy, s, iconPaint)
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
            Control.DPAD, Control.CONFIRM, Control.BACK, Control.DASH, Control.L1, Control.R1,
            Control.ZL, Control.ZR, Control.L3, Control.R3, Control.GUIDE, Control.PLUS, Control.MINUS,
            Control.LEFT_STICK, Control.RIGHT_STICK -> true
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
            Control.ZL to ControlPlacement(zlRect.centerX() / width, zlRect.centerY() / height, (zlRect.height() * scale) / shortSide),
            Control.ZR to ControlPlacement(zrRect.centerX() / width, zrRect.centerY() / height, (zrRect.height() * scale) / shortSide),
            Control.L3 to ControlPlacement(l3Rect.centerX() / width, l3Rect.centerY() / height, (l3Rect.height() * scale) / shortSide),
            Control.R3 to ControlPlacement(r3Rect.centerX() / width, r3Rect.centerY() / height, (r3Rect.height() * scale) / shortSide),
            Control.GUIDE to ControlPlacement(guideRect.centerX() / width, guideRect.centerY() / height, (guideRect.height() * scale) / shortSide),
            Control.PLUS to ControlPlacement(plusRect.centerX() / width, plusRect.centerY() / height, (plusRect.height() * scale) / shortSide),
            Control.MINUS to ControlPlacement(minusRect.centerX() / width, minusRect.centerY() / height, (minusRect.height() * scale) / shortSide),
            Control.LEFT_STICK to ControlPlacement(leftStickRect.centerX() / width, leftStickRect.centerY() / height, (leftStickRect.width() * scale) / shortSide),
            Control.RIGHT_STICK to ControlPlacement(rightStickRect.centerX() / width, rightStickRect.centerY() / height, (rightStickRect.width() * scale) / shortSide),
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
        val prefix = orientationPrefix()
        Control.entries.forEach { control ->
            val default = defaultLayout[control] ?: return@forEach
            val key = "${prefix}_${controllerPreset.name}_${control.name}"
            layout[control] = ControlPlacement(
                x = prefs.getFloat("${key}_x", default.x),
                y = prefs.getFloat("${key}_y", default.y),
                size = prefs.getFloat("${key}_size", default.size),
            )
        }
    }

    private fun saveLayout() {
        val prefs = context.getSharedPreferences("controller-layout-v2", Context.MODE_PRIVATE).edit()
        prefs.putString("preset", controllerPreset.name)
        val prefix = orientationPrefix()
        layout.forEach { (control, placement) ->
            val key = "${prefix}_${controllerPreset.name}_${control.name}"
            prefs.putFloat("${key}_x", placement.x)
            prefs.putFloat("${key}_y", placement.y)
            prefs.putFloat("${key}_size", placement.size)
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
        val layoutName = orientationPrefix()
        return layout.mapNotNull { (control, placement) ->
            if (!isControlVisible(control)) return@mapNotNull null
            val label = when (control) {
                Control.DPAD -> "D-Pad"
                Control.CONFIRM -> "Confirm"
                Control.BACK -> "Back"
                Control.DASH -> "Dash"
                Control.ZL -> "ZL"
                Control.ZR -> "ZR"
                Control.L3 -> "L3"
                Control.R3 -> "R3"
                Control.GUIDE -> "Guide"
                Control.PLUS -> "Plus"
                Control.MINUS -> "Minus"
                Control.LEFT_STICK -> "Left Stick"
                Control.RIGHT_STICK -> "Right Stick"
                else -> control.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            val key = when (control) {
                Control.DPAD -> "DPAD"
                Control.CONFIRM -> "ENTER"
                Control.BACK -> "ESCAPE"
                Control.DASH -> "SHIFT"
                Control.ZL -> "ZL"
                Control.ZR -> "ZR"
                Control.L3 -> "L3"
                Control.R3 -> "R3"
                Control.GUIDE -> "GUIDE"
                Control.PLUS -> "PLUS"
                Control.MINUS -> "MINUS"
                Control.LEFT_STICK -> "LEFT_STICK"
                Control.RIGHT_STICK -> "RIGHT_STICK"
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

        point(Control.L1).also {
            val h = layout.getValue(Control.L1).size * shortSide
            val w = h * 1.6f
            l1Rect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.R1).also {
            val h = layout.getValue(Control.R1).size * shortSide
            val w = h * 1.6f
            r1Rect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.ZL).also {
            val h = layout.getValue(Control.ZL).size * shortSide
            val w = h * 1.5f
            zlRect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.ZR).also {
            val h = layout.getValue(Control.ZR).size * shortSide
            val w = h * 1.5f
            zrRect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.L3).also {
            val h = layout.getValue(Control.L3).size * shortSide
            val w = h * 1.5f
            l3Rect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.R3).also {
            val h = layout.getValue(Control.R3).size * shortSide
            val w = h * 1.5f
            r3Rect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.GUIDE).also {
            val h = layout.getValue(Control.GUIDE).size * shortSide
            val w = h * 1.5f
            guideRect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.PLUS).also {
            val h = layout.getValue(Control.PLUS).size * shortSide
            val w = h * 1.5f
            plusRect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.MINUS).also {
            val h = layout.getValue(Control.MINUS).size * shortSide
            val w = h * 1.5f
            minusRect.set(it.x - w / 2f, it.y - h / 2f, it.x + w / 2f, it.y + h / 2f)
        }
        point(Control.LEFT_STICK).also {
            val size = layout.getValue(Control.LEFT_STICK).size * shortSide
            leftStickRect.set(it.x - size / 2f, it.y - size / 2f, it.x + size / 2f, it.y + size / 2f)
            leftStickThumb.x = it.x; leftStickThumb.y = it.y
        }
        point(Control.RIGHT_STICK).also {
            val size = layout.getValue(Control.RIGHT_STICK).size * shortSide
            rightStickRect.set(it.x - size / 2f, it.y - size / 2f, it.x + size / 2f, it.y + size / 2f)
            rightStickThumb.x = it.x; rightStickThumb.y = it.y
        }

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
        controlRects[Control.ZL] = RectF(zlRect)
        controlRects[Control.ZR] = RectF(zrRect)
        controlRects[Control.L3] = RectF(l3Rect)
        controlRects[Control.R3] = RectF(r3Rect)
        controlRects[Control.GUIDE] = RectF(guideRect)
        controlRects[Control.PLUS] = RectF(plusRect)
        controlRects[Control.MINUS] = RectF(minusRect)
        controlRects[Control.LEFT_STICK] = RectF(leftStickRect)
        controlRects[Control.RIGHT_STICK] = RectF(rightStickRect)
    }

    private fun radiusFor(control: Control): Float =
        (layout[control]?.size ?: (actionRadius * scale / minOf(width, height).coerceAtLeast(1))) *
            minOf(width, height).coerceAtLeast(1)

    private enum class Control {
        DPAD, CONFIRM, BACK, DASH,
        L1, R1, ZL, ZR, L3, R3,
        EXTRA_A, EXTRA_S, EXTRA_D,
        EXTRA_Z, EXTRA_X, EXTRA_C,
        CTRL, ALT, SHIFT,
        SELECT, START, MENU, GUIDE, PLUS, MINUS,
        LEFT_STICK, RIGHT_STICK,
    }

    private data class ControlPlacement(
        var x: Float,
        var y: Float,
        var size: Float,
    )

    data class PointF(var x: Float, var y: Float)
}
