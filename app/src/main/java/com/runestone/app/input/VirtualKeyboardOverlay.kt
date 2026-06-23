package com.runestone.app.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import kotlin.math.abs
import kotlin.math.min

class VirtualKeyboardOverlay(context: Context) : View(context) {

    enum class DockMode { BOTTOM, TOP, SPLIT }

    var dockMode: DockMode = DockMode.BOTTOM
    var kbOpacity: Float = 0.75f
    var kbScale: Float = 1.0f
    var landscapeKeys: Boolean = false
    var onKeyDown: ((Int) -> Unit)? = null
    var onKeyUp: ((Int) -> Unit)? = null
    var onDockModeChanged: ((DockMode) -> Unit)? = null
    var onHide: (() -> Unit)? = null

    private var safeTop = 0f
    private var safeBottom = 0f
    private var safeLeft = 0f
    private var safeRight = 0f

    private val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val keyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
    private val keyPressedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD
    }
    private val keySmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    data class KeyDef(
        val label: String,
        val code: Int,
        val wide: Float = 1f,
        val small: Boolean = false,
    )

    data class KeyRect(val def: KeyDef, val rect: RectF)

    private val allKeys = mutableListOf<KeyRect>()
    private val activeKeys = mutableSetOf<Int>()
    private val kbRect = RectF()
    private val handleRect = RectF()
    private val dockHandleRect = RectF()
    private var leftPanelRect = RectF()
    private var rightPanelRect = RectF()

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatCode = -1
    private var repeatStartMs = 0L

    fun setSafeInsets(top: Float, bottom: Float, left: Float, right: Float) {
        safeTop = top; safeBottom = bottom; safeLeft = left; safeRight = right
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) rebuildKeys()
    }

    private fun rebuildKeys() {
        allKeys.clear()
        kbRect.setEmpty()
        leftPanelRect.setEmpty()
        rightPanelRect.setEmpty()

        val w = width.toFloat()
        val h = height.toFloat()
        val s = kbScale
        val gap = (4f * s).coerceAtLeast(2f)
        val bottomGap = (8f + safeBottom).coerceAtLeast(8f)
        val topGap = (8f + safeTop).coerceAtLeast(8f)
        val sidePad = (6f * s).coerceAtLeast(4f)

        if (landscapeKeys) {
            if (dockMode == DockMode.SPLIT) {
                layoutSplitLandscape(w, h, s, gap, bottomGap, topGap, sidePad)
            } else {
                layoutFullLandscape(w, h, s, gap, bottomGap, topGap, sidePad)
            }
        } else {
            if (dockMode == DockMode.SPLIT) {
                layoutSplitPortrait(w, h, s, gap, bottomGap, topGap, sidePad)
            } else {
                layoutFullPortrait(w, h, s, gap, bottomGap, topGap, sidePad)
            }
        }
        invalidate()
    }

    private fun layoutFullLandscape(w: Float, h: Float, s: Float, gap: Float, bottomGap: Float, topGap: Float, sidePad: Float) {
        val docked = if (dockMode == DockMode.BOTTOM) h - bottomGap else topGap
        val totalRows = 8
        val availH = if (dockMode == DockMode.BOTTOM) docked - topGap else h - docked - bottomGap
        val keyH = ((availH - gap * (totalRows + 1)) / totalRows).coerceIn(18f, 36f)
        val rowH = keyH + gap
        val kbH = totalRows * rowH + gap
        val kbTop = if (dockMode == DockMode.BOTTOM) docked - kbH else docked
        val kbW = (w - sidePad * 2f - safeLeft - safeRight).coerceAtMost(w * 0.92f)
        val kbLeft = (w - kbW) / 2f
        val kbRight = kbLeft + kbW
        val fontScale = (keyH / 28f).coerceIn(0.55f, 1.2f)
        currentLabelSize = (12f * fontScale).coerceAtLeast(7f)
        currentSmallLabelSize = (10f * fontScale).coerceAtLeast(6f)

        kbRect.set(kbLeft, kbTop, kbRight, kbTop + kbH)

        val rowY = { row: Int -> kbTop + gap + row * rowH + 2f }
        val keyW = { cols: Int -> (kbW - gap * (cols + 1)) / cols }

        fun addRow(row: Int, labels: List<String>, codes: List<Int>, wide: List<Float> = emptyList(), small: List<Boolean> = emptyList()) {
            val cols = labels.size
            val totalW = keyW(cols) * cols + gap * (cols - 1)
            val startX = kbLeft + (kbW - totalW) / 2f
            var x = startX
            val y = rowY(row)
            labels.forEachIndexed { i, label ->
                val wf = wide.getOrElse(i) { 1f }
                val cw = keyW(cols) * wf + gap * (wf - 1f) * 0.5f
                addKey(KeyDef(label, codes.getOrElse(i) { 0 }, wf, small.getOrElse(i) { false }), x, y, cw, keyH, gap)
                x += cw + gap
            }
        }

        addKey(KeyDef("Esc", KeyEvent.KEYCODE_ESCAPE, 1.2f, true), kbLeft + gap, rowY(0), keyW(14) * 1.2f, keyH, gap)
        addRow(0, listOf("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10"),
            listOf(KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5,
                KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10), small = listOf(true, true, true, true, true, true, true, true, true, true))

        val numRow = listOf("`~", "1!", "2@", "3#", "4$", "5%", "6^", "7&", "8*", "9(", "0)", "-_", "=+", "Bksp")
        val numCodes = listOf(KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_DEL)
        addRow(1, numRow, numCodes, wide = listOf(0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.5f), small = listOf(false, false, false, false, false, false, false, false, false, false, false, false, false, true))

        addRow(2, listOf("Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[{", "]}", "\\|"),
            listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH),
            wide = listOf(1.3f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.2f))

        addRow(3, listOf("Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";:", "'\"", "Enter"),
            listOf(KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F,
                KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
                KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_ENTER),
            wide = listOf(1.5f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.7f))

        addRow(4, listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", ",<", ".>", "/?", "Shift"),
            listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V,
                KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD,
                KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_SHIFT_RIGHT),
            wide = listOf(1.8f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.8f))

        // Row 5: Ctrl Meta Alt Space AltGr Fn Ctrl + nav cluster
        val modKeys = listOf(
            KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, 1.2f, small = true),
            KeyDef("Meta", KeyEvent.KEYCODE_META_LEFT, 1.2f, small = true),
            KeyDef("Alt", KeyEvent.KEYCODE_ALT_LEFT, 1.0f, small = true),
            KeyDef("Space", KeyEvent.KEYCODE_SPACE, 3.5f),
            KeyDef("AltGr", KeyEvent.KEYCODE_ALT_RIGHT, 1.0f, small = true),
            KeyDef("Fn", -1, 1.0f, small = true),
            KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_RIGHT, 1.2f, small = true),
            KeyDef("Ins", KeyEvent.KEYCODE_INSERT, 0.8f, small = true),
            KeyDef("Del", KeyEvent.KEYCODE_FORWARD_DEL, 0.8f, small = true),
        )
        val modTotalW = modKeys.sumOf { (it.wide * keyW(14)).toDouble() } + gap * (modKeys.size - 1)
        var modX = kbLeft + (kbW - modTotalW.toFloat()) / 2f
        modKeys.forEach { kd ->
            val cw = kd.wide * keyW(14)
            addKey(kd, modX, rowY(5), cw, keyH, gap)
            modX += cw + gap
        }

        // Row 6: Home End PgUp PgDn + arrows
        val navKeys = listOf(
            KeyDef("Home", KeyEvent.KEYCODE_MOVE_HOME, 1.0f, small = true),
            KeyDef("End", KeyEvent.KEYCODE_MOVE_END, 1.0f, small = true),
            KeyDef("PgUp", KeyEvent.KEYCODE_PAGE_UP, 1.0f, small = true),
            KeyDef("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, 1.0f, small = true),
        )
        var navX = kbLeft + gap
        navKeys.forEach { kd ->
            val cw = kd.wide * keyW(12)
            addKey(kd, navX, rowY(6), cw, keyH, gap)
            navX += cw + gap
        }

        // Arrow cluster on the right of row 6
        val arrowW = keyW(12) * 0.9f
        val arGap = gap * 0.5f
        val ax = kbRight - arrowW * 3f - arGap * 2f - gap
        addKey(KeyDef("\u25B2", KeyEvent.KEYCODE_DPAD_UP, 0.85f, small = true), ax + arrowW + arGap, rowY(6), arrowW, keyH, gap)
        addKey(KeyDef("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, 0.85f, small = true), ax, rowY(7), arrowW, keyH, gap)
        addKey(KeyDef("\u25BC", KeyEvent.KEYCODE_DPAD_DOWN, 0.85f, small = true), ax + arrowW + arGap, rowY(7), arrowW, keyH, gap)
        addKey(KeyDef("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, 0.85f, small = true), ax + (arrowW + arGap) * 2f, rowY(7), arrowW, keyH, gap)

        setupHandle(kbLeft, kbTop, kbRight, kbH)
    }

    private var currentLabelSize = 12f
    private var currentSmallLabelSize = 10f

    private fun layoutFullPortrait(w: Float, h: Float, s: Float, gap: Float, bottomGap: Float, topGap: Float, sidePad: Float) {
        val docked = if (dockMode == DockMode.BOTTOM) h - bottomGap else topGap
        val totalRows = 8
        val availH = if (dockMode == DockMode.BOTTOM) docked - topGap else h - docked - bottomGap
        val keyH = ((availH - gap * (totalRows + 1)) / totalRows).coerceIn(16f, 32f)
        val rowH = keyH + gap
        val kbH = totalRows * rowH + gap
        val kbTop = if (dockMode == DockMode.BOTTOM) docked - kbH else docked
        val kbW = (w - sidePad * 2f).coerceAtMost(w * 0.96f)
        val kbLeft = (w - kbW) / 2f
        val fontScale = (keyH / 26f).coerceIn(0.5f, 1.1f)
        currentLabelSize = (11f * fontScale).coerceAtLeast(6f)
        currentSmallLabelSize = (9f * fontScale).coerceAtLeast(5f)
        kbRect.set(kbLeft, kbTop, kbLeft + kbW, kbTop + kbH)

        val rowY = { row: Int -> kbTop + gap + row * rowH + 2f }
        val keyW = { cols: Int -> (kbW - gap * (cols + 1)) / cols }

        fun addRow(row: Int, labels: List<String>, codes: List<Int>, wide: List<Float> = emptyList(), small: List<Boolean> = emptyList()) {
            val cols = labels.size
            val totalW = keyW(cols) * cols + gap * (cols - 1)
            val startX = kbLeft + (kbW - totalW) / 2f
            var x = startX
            val y = rowY(row)
            labels.forEachIndexed { i, label ->
                val wf = wide.getOrElse(i) { 1f }
                val cw = keyW(cols) * wf + gap * (wf - 1f) * 0.5f
                addKey(KeyDef(label, codes.getOrElse(i) { 0 }, wf, small.getOrElse(i) { false }), x, y, cw, keyH, gap)
                x += cw + gap
            }
        }

        addRow(0, listOf("Esc", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10"),
            listOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
                KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10),
            small = listOf(true, true, true, true, true, true, true, true, true, true, true))

        addRow(1, listOf("`~", "1!", "2@", "3#", "4$", "5%", "6^", "7&", "8*", "9(", "0)", "-_", "=+", "Bksp"),
            listOf(KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
                KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0,
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_DEL),
            wide = listOf(0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.6f))

        addRow(2, listOf("Tab", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[{", "]}", "\\|"),
            listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH),
            wide = listOf(1.3f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.2f))

        addRow(3, listOf("Caps", "A", "S", "D", "F", "G", "H", "J", "K", "L", ";:", "'\"", "Enter"),
            listOf(KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F,
                KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
                KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_ENTER),
            wide = listOf(1.5f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.7f))

        addRow(4, listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", ",<", ".>", "/?", "Shift"),
            listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V,
                KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD,
                KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_SHIFT_RIGHT),
            wide = listOf(1.8f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 1.8f))

        // Row 5: modifiers
        val modKeys = listOf(
            KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, 1.2f, small = true),
            KeyDef("Meta", KeyEvent.KEYCODE_META_LEFT, 1.2f, small = true),
            KeyDef("Alt", KeyEvent.KEYCODE_ALT_LEFT, 1f, small = true),
            KeyDef("Space", KeyEvent.KEYCODE_SPACE, 3f),
            KeyDef("AltGr", KeyEvent.KEYCODE_ALT_RIGHT, 1f, small = true),
            KeyDef("Fn", -1, 1f, small = true),
            KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_RIGHT, 1.2f, small = true),
        )
        val modTotalW = modKeys.sumOf { (it.wide * keyW(13)).toDouble() } + gap * (modKeys.size - 1)
        var modX = kbLeft + (kbW - modTotalW.toFloat()) / 2f
        modKeys.forEach { kd ->
            val cw = kd.wide * keyW(13)
            addKey(kd, modX, rowY(5), cw, keyH, gap)
            modX += cw + gap
        }

        // Row 6: nav + arrows
        val navKeys = listOf(KeyDef("Ins", KeyEvent.KEYCODE_INSERT, 0.9f, small = true),
            KeyDef("Del", KeyEvent.KEYCODE_FORWARD_DEL, 0.9f, small = true),
            KeyDef("Home", KeyEvent.KEYCODE_MOVE_HOME, 0.9f, small = true),
            KeyDef("End", KeyEvent.KEYCODE_MOVE_END, 0.9f, small = true),
            KeyDef("PgUp", KeyEvent.KEYCODE_PAGE_UP, 0.9f, small = true),
            KeyDef("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, 0.9f, small = true))
        var navX = kbLeft + gap
        navKeys.forEach { kd ->
            val cw = kd.wide * keyW(12)
            addKey(kd, navX, rowY(6), cw, keyH, gap)
            navX += cw + gap
        }

        val arrowW = keyW(12) * 0.9f
        val arGap = gap * 0.5f
        val ax = kbLeft + kbW - arrowW * 3f - arGap * 2f - gap
        addKey(KeyDef("\u25B2", KeyEvent.KEYCODE_DPAD_UP, 0.85f, small = true), ax + arrowW + arGap, rowY(6), arrowW, keyH, gap)
        addKey(KeyDef("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, 0.85f, small = true), ax, rowY(7), arrowW, keyH, gap)
        addKey(KeyDef("\u25BC", KeyEvent.KEYCODE_DPAD_DOWN, 0.85f, small = true), ax + arrowW + arGap, rowY(7), arrowW, keyH, gap)
        addKey(KeyDef("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, 0.85f, small = true), ax + (arrowW + arGap) * 2f, rowY(7), arrowW, keyH, gap)

        setupHandle(kbLeft, kbTop, kbLeft + kbW, kbH)
    }

    private fun layoutSplitLandscape(w: Float, h: Float, s: Float, gap: Float, bottomGap: Float, topGap: Float, sidePad: Float) {
        val keyH = (32f * s).coerceIn(24f, 40f)
        val rowH = keyH + gap
        val totalRows = 5
        val panelH = totalRows * rowH + gap * 4f + bottomGap
        val panelTop = if (dockMode == DockMode.BOTTOM) h - bottomGap - panelH else topGap

        val leftW = (w * 0.40f).coerceIn(w * 0.30f, w * 0.48f)
        val rightW = (w * 0.40f).coerceIn(w * 0.30f, w * 0.48f)
        val centerGap = w - leftW - rightW - sidePad * 2f
        val leftX = sidePad
        val rightX = w - sidePad - rightW

        leftPanelRect.set(leftX, panelTop, leftX + leftW, panelTop + panelH)
        rightPanelRect.set(rightX, panelTop, rightX + rightW, panelTop + panelH)

        val keyW = { panelW: Float, cols: Int -> (panelW - gap * (cols + 1)) / cols }
        val rowY = { row: Int -> panelTop + gap + row * rowH + 2f }

        // Left half
        fun addLeftRow(row: Int, labels: List<String>, codes: List<Int>, wide: List<Float> = emptyList()) {
            val cols = labels.size
            val pw = leftW
            val kw = keyW(pw, cols)
            val totalW = kw * cols + gap * (cols - 1)
            var x = leftX + (pw - totalW) / 2f
            val y = rowY(row)
            labels.forEachIndexed { i, label ->
                val wf = wide.getOrElse(i) { 1f }
                val cw = kw * wf + gap * (wf - 1f) * 0.5f
                addKey(KeyDef(label, codes.getOrElse(i) { 0 }, wf), x, y, cw, keyH, gap)
                x += cw + gap
            }
        }

        val leftPw = leftW
        val leftKw = keyW(leftPw, 6)
        addLeftRow(0, listOf("Esc", "F1", "F2", "F3", "F4", "F5"),
            listOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5))
        addLeftRow(1, listOf("`~", "1!", "2@", "3#", "4$", "5%", "6^"),
            listOf(KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6))
        addLeftRow(2, listOf("Tab", "Q", "W", "E", "R", "T"),
            listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T),
            wide = listOf(1.3f, 1f, 1f, 1f, 1f, 1f))
        addLeftRow(3, listOf("Caps", "A", "S", "D", "F", "G"),
            listOf(KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G),
            wide = listOf(1.3f, 1f, 1f, 1f, 1f, 1f))
        addLeftRow(4, listOf("Shift", "Z", "X", "C", "V", "B"),
            listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B),
            wide = listOf(1.5f, 1f, 1f, 1f, 1f, 1f))

        // Left modifiers
        val leftMods = listOf(KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, 1.2f, small = true),
            KeyDef("Meta", KeyEvent.KEYCODE_META_LEFT, 1.2f, small = true),
            KeyDef("Alt", KeyEvent.KEYCODE_ALT_LEFT, 1f, small = true),
            KeyDef("Space", KeyEvent.KEYCODE_SPACE, 2f))
        var lmx = leftX + gap
        leftMods.forEach { kd ->
            val cw = kd.wide * leftKw
            addKey(kd, lmx, rowY(5), cw, keyH, gap)
            lmx += cw + gap
        }

        // Right half
        fun addRightRow(row: Int, labels: List<String>, codes: List<Int>, wide: List<Float> = emptyList()) {
            val cols = labels.size
            val pw = rightW
            val kw = keyW(pw, cols)
            val totalW = kw * cols + gap * (cols - 1)
            var x = rightX + (pw - totalW) / 2f
            val y = rowY(row)
            labels.forEachIndexed { i, label ->
                val wf = wide.getOrElse(i) { 1f }
                val cw = kw * wf + gap * (wf - 1f) * 0.5f
                addKey(KeyDef(label, codes.getOrElse(i) { 0 }, wf), x, y, cw, keyH, gap)
                x += cw + gap
            }
        }

        val rightPw = rightW
        val rightKw = keyW(rightPw, 6)
        addRightRow(0, listOf("F6", "F7", "F8", "F9", "F10", "Ins", "Home", "PgUp"),
            listOf(KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10,
                KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_PAGE_UP),
            wide = listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f))
        addRightRow(1, listOf("7&", "8*", "9(", "0)", "-_", "=+", "Bksp", "Del"),
            listOf(KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_MINUS,
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL),
            wide = listOf(1f, 1f, 1f, 1f, 1f, 1f, 1.3f, 1f))
        addRightRow(2, listOf("Y", "U", "I", "O", "P", "[{", "]}", "\\|"),
            listOf(KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH))
        addRightRow(3, listOf("H", "J", "K", "L", ";:", "'\"", "Enter", "End", "PgDn"),
            listOf(KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_SEMICOLON,
                KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_DOWN),
            wide = listOf(1f, 1f, 1f, 1f, 1f, 1f, 1.3f, 1f, 1f))
        addRightRow(4, listOf("N", "M", ",<", ".>", "/?", "Shift"),
            listOf(KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_SHIFT_RIGHT),
            wide = listOf(1f, 1f, 1f, 1f, 1f, 1.5f))

        val rightMods = listOf(KeyDef("Space", KeyEvent.KEYCODE_SPACE, 2f),
            KeyDef("AltGr", KeyEvent.KEYCODE_ALT_RIGHT, 1f, small = true),
            KeyDef("Fn", -1, 1f, small = true),
            KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_RIGHT, 1.2f, small = true))
        var rmx = rightX + gap
        rightMods.forEach { kd ->
            val cw = kd.wide * rightKw
            addKey(kd, rmx, rowY(5), cw, keyH, gap)
            rmx += cw + gap
        }

        // Arrows on right bottom
        val arrowRowY = rowY(5) + rowH
        val arrowW = rightKw * 0.85f
        val ax = rightX + rightW * 0.15f
        addKey(KeyDef("\u25B2", KeyEvent.KEYCODE_DPAD_UP, 0.85f, small = true), ax + arrowW + gap, arrowRowY, arrowW, keyH, gap)
        addKey(KeyDef("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, 0.85f, small = true), ax, arrowRowY + rowH, arrowW, keyH, gap)
        addKey(KeyDef("\u25BC", KeyEvent.KEYCODE_DPAD_DOWN, 0.85f, small = true), ax + arrowW + gap, arrowRowY + rowH, arrowW, keyH, gap)
        addKey(KeyDef("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, 0.85f, small = true), ax + (arrowW + gap) * 2f, arrowRowY + rowH, arrowW, keyH, gap)

        val ctrX = leftX + leftW + (rightX - leftX - leftW) / 2f
        dockHandleRect.set(ctrX - dp(22), panelTop + dp(4), ctrX + dp(22), panelTop + dp(32))
    }

    private fun layoutSplitPortrait(w: Float, h: Float, s: Float, gap: Float, bottomGap: Float, topGap: Float, sidePad: Float) {
        val keyH = (26f * s).coerceIn(20f, 34f)
        val rowH = keyH + gap
        val totalRows = 5
        val panelH = totalRows * rowH + gap * 4f + bottomGap
        val panelTop = if (dockMode == DockMode.BOTTOM) h - bottomGap - panelH else topGap

        val leftW = (w * 0.44f).coerceAtMost(w * 0.48f)
        val rightW = (w * 0.44f).coerceAtMost(w * 0.48f)
        val leftX = sidePad
        val rightX = w - sidePad - rightW

        leftPanelRect.set(leftX, panelTop, leftX + leftW, panelTop + panelH)
        rightPanelRect.set(rightX, panelTop, rightX + rightW, panelTop + panelH)

        val rowY = { row: Int -> panelTop + gap + row * rowH + 2f }
        val kw = { pw: Float, cols: Int -> (pw - gap * (cols + 1)) / cols }

        fun addTo(panelX: Float, panelW: Float, row: Int, labels: List<String>, codes: List<Int>, wide: List<Float> = emptyList()) {
            val cols = labels.size
            val kk = kw(panelW, cols)
            val totalW = kk * cols + gap * (cols - 1)
            var x = panelX + (panelW - totalW) / 2f
            val y = rowY(row)
            labels.forEachIndexed { i, label ->
                val wf = wide.getOrElse(i) { 1f }
                val cw = kk * wf + gap * (wf - 1f) * 0.5f
                addKey(KeyDef(label, codes.getOrElse(i) { 0 }, wf), x, y, cw, keyH, gap)
                x += cw + gap
            }
        }

        addTo(leftX, leftW, 0, listOf("Esc", "F1", "F2", "F3", "F4", "F5"),
            listOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5))
        addTo(leftX, leftW, 1, listOf("`~", "1!", "2@", "3#", "4$", "5%", "6^"),
            listOf(KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6))
        addTo(leftX, leftW, 2, listOf("Tab", "Q", "W", "E", "R", "T"),
            listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T),
            wide = listOf(1.2f, 1f, 1f, 1f, 1f, 1f))
        addTo(leftX, leftW, 3, listOf("Caps", "A", "S", "D", "F", "G"),
            listOf(KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G),
            wide = listOf(1.2f, 1f, 1f, 1f, 1f, 1f))
        addTo(leftX, leftW, 4, listOf("Shift", "Z", "X", "C", "V", "B"),
            listOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B),
            wide = listOf(1.3f, 1f, 1f, 1f, 1f, 1f))

        val leftMods = listOf(KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, 1.2f, small = true),
            KeyDef("Meta", KeyEvent.KEYCODE_META_LEFT, 1.2f, small = true),
            KeyDef("Alt", KeyEvent.KEYCODE_ALT_LEFT, 1f, small = true),
            KeyDef("Space", KeyEvent.KEYCODE_SPACE, 1.8f))
        var lmx = leftX + gap
        val lk = kw(leftW, 6)
        leftMods.forEach { kd ->
            val cw = kd.wide * lk
            addKey(kd, lmx, rowY(5), cw, keyH, gap)
            lmx += cw + gap
        }

        addTo(rightX, rightW, 0, listOf("F6", "F7", "F8", "F9", "F10", "Ins", "Home", "PgUp"),
            listOf(KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10,
                KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_PAGE_UP))
        addTo(rightX, rightW, 1, listOf("7&", "8*", "9(", "0)", "-_", "=+", "Bksp", "Del"),
            listOf(KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_MINUS,
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL),
            wide = listOf(1f, 1f, 1f, 1f, 1f, 1f, 1.2f, 1f))
        addTo(rightX, rightW, 2, listOf("Y", "U", "I", "O", "P", "[{", "]}", "\\|"),
            listOf(KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH))
        addTo(rightX, rightW, 3, listOf("H", "J", "K", "L", ";:", "'\"", "Enter", "End", "PgDn"),
            listOf(KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_SEMICOLON,
                KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_DOWN),
            wide = listOf(1f, 1f, 1f, 1f, 1f, 1f, 1.2f, 1f, 1f))
        addTo(rightX, rightW, 4, listOf("N", "M", ",<", ".>", "/?", "Shift"),
            listOf(KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_SHIFT_RIGHT),
            wide = listOf(1f, 1f, 1f, 1f, 1f, 1.3f))

        val rightMods = listOf(KeyDef("Space", KeyEvent.KEYCODE_SPACE, 1.4f),
            KeyDef("AltGr", KeyEvent.KEYCODE_ALT_RIGHT, 1f, small = true),
            KeyDef("Fn", -1, 1f, small = true),
            KeyDef("Ctrl", KeyEvent.KEYCODE_CTRL_RIGHT, 1.2f, small = true))
        var rmx = rightX + gap
        val rk = kw(rightW, 6)
        rightMods.forEach { kd ->
            val cw = kd.wide * rk
            addKey(kd, rmx, rowY(5), cw, keyH, gap)
            rmx += cw + gap
        }

        val arrowRowY = rowY(5) + rowH
        val arrowW = rk * 0.85f
        val ax = rightX + rightW * 0.12f
        addKey(KeyDef("\u25B2", KeyEvent.KEYCODE_DPAD_UP, 0.85f, small = true), ax + arrowW + gap, arrowRowY, arrowW, keyH, gap)
        addKey(KeyDef("\u25C0", KeyEvent.KEYCODE_DPAD_LEFT, 0.85f, small = true), ax, arrowRowY + rowH, arrowW, keyH, gap)
        addKey(KeyDef("\u25BC", KeyEvent.KEYCODE_DPAD_DOWN, 0.85f, small = true), ax + arrowW + gap, arrowRowY + rowH, arrowW, keyH, gap)
        addKey(KeyDef("\u25B6", KeyEvent.KEYCODE_DPAD_RIGHT, 0.85f, small = true), ax + (arrowW + gap) * 2f, arrowRowY + rowH, arrowW, keyH, gap)

        val ctrX = leftX + leftW + (rightX - leftX - leftW) / 2f
        dockHandleRect.set(ctrX - dp(22), panelTop + dp(4), ctrX + dp(22), panelTop + dp(32))
    }

    private fun addKey(def: KeyDef, x: Float, y: Float, w: Float, h: Float, gap: Float) {
        val r = RectF(x, y, x + w, y + h)
        if (w > 0 && h > 0) {
            allKeys.add(KeyRect(def, r))
        }
    }

    private fun rowEnd(rowY: Float, usedW: Float, kbLeft: Float, kbW: Float, gap: Float): Float {
        return kbLeft + kbW - gap - usedW
    }

    private fun setupHandle(kbLeft: Float, kbTop: Float, kbRight: Float, kbH: Float) {
        val handleW = dp(48)
        val handleH = dp(6)
        val handleY = if (dockMode == DockMode.BOTTOM) kbTop - handleH - dp(2) else kbTop + kbH + dp(2)
        handleRect.set(kbRight - kbRight / 2f - handleW / 2f, handleY, kbRight - kbRight / 2f + handleW / 2f, handleY + handleH)
        dockHandleRect.set(kbRight - kbRight / 2f - dp(30), handleY - dp(6), kbRight - kbRight / 2f + dp(30), handleY + handleH + dp(6))
    }

    override fun onDraw(canvas: Canvas) {
        val a = kbOpacity
        if (!landscapeKeys && dockMode != DockMode.SPLIT && !kbRect.isEmpty()) {
            drawPanel(canvas, kbRect, a)
        }
        if (dockMode == DockMode.SPLIT) {
            if (!leftPanelRect.isEmpty()) drawPanel(canvas, leftPanelRect, a)
            if (!rightPanelRect.isEmpty()) drawPanel(canvas, rightPanelRect, a)
        }

        allKeys.forEach { kr ->
            val pressed = kr.def.code in activeKeys
            drawKey(canvas, kr, pressed, a)
        }

        drawDockHandle(canvas, a)
    }

    private fun drawPanel(canvas: Canvas, rect: RectF, a: Float) {
        val r = dp(12)
        shadowPaint.alpha = (0.25f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect.left + 2f, rect.top + 4f, rect.right + 2f, rect.bottom + 4f, r.toFloat(), r.toFloat(), shadowPaint)
        glassFill.color = Color.argb((0.10f * 255 * a).toInt().coerceIn(0, 255), 8, 8, 10)
        canvas.drawRoundRect(rect, r.toFloat(), r.toFloat(), glassFill)
        glassBorder.color = Color.argb((0.25f * 255 * a).toInt().coerceIn(0, 255), 200, 180, 140)
        canvas.drawRoundRect(rect, r.toFloat(), r.toFloat(), glassBorder)
    }

    private fun drawKey(canvas: Canvas, kr: KeyRect, pressed: Boolean, a: Float) {
        val r = dp(if (kr.def.small) 6 else 8).toFloat()
        val krR = kr.rect
        val fillA = if (pressed) 0.25f else 0.08f
        val borderA = if (pressed) 0.45f else 0.22f

        shadowPaint.alpha = (0.20f * 255 * a).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(krR.left + 1f, krR.top + 2f, krR.right + 1f, krR.bottom + 2f, r, r, shadowPaint)

        keyFill.color = Color.argb((fillA * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawRoundRect(krR, r, r, keyFill)

        keyBorder.color = Color.argb((borderA * 255 * a).toInt().coerceIn(0, 255), 200, 180, 140)
        canvas.drawRoundRect(krR, r, r, keyBorder)

        if (!pressed) {
            keyFill.color = Color.argb((0.10f * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            val hl = RectF(krR.left, krR.top, krR.right, krR.top + krR.height() * 0.4f)
            canvas.drawRoundRect(hl, r, r, keyFill)
        }

        val label = kr.def.label
        if (label.isNotEmpty()) {
            val p = if (kr.def.small) keySmallPaint else keyLabelPaint
            p.textSize = if (kr.def.small) currentSmallLabelSize else currentLabelSize
            p.color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((0.88f * 255 * a).toInt().coerceIn(0, 255), 232, 229, 220)
            val textY = krR.centerY() + p.textSize * 0.35f
            canvas.drawText(label, krR.centerX(), textY, p)
        }
    }

    private fun drawDockHandle(canvas: Canvas, a: Float) {
        if (dockMode == DockMode.SPLIT) {
            if (dockHandleRect.width() > 0) {
                val r = dp(16).toFloat()
                shadowPaint.alpha = (0.20f * 255 * a).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(dockHandleRect, r, r, shadowPaint)
                glassFill.color = Color.argb((0.12f * 255 * a).toInt().coerceIn(0, 255), 8, 8, 10)
                canvas.drawRoundRect(dockHandleRect, r, r, glassFill)
                glassBorder.color = Color.argb((0.20f * 255 * a).toInt().coerceIn(0, 255), 200, 180, 140)
                canvas.drawRoundRect(dockHandleRect, r, r, glassBorder)
                val chevPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
                    color = Color.argb((0.60f * 255 * a).toInt().coerceIn(0, 255), 200, 180, 140)
                }
                val cy = dockHandleRect.centerY()
                val cx = dockHandleRect.centerX()
                canvas.drawLine(cx - 6f, cy - 3f, cx, cy + 3f, chevPaint)
                canvas.drawLine(cx + 6f, cy - 3f, cx, cy + 3f, chevPaint)
            }
            return
        }
        if (handleRect.width() > 0) {
            glassFill.color = Color.argb((0.16f * 255 * a).toInt().coerceIn(0, 255), 200, 180, 140)
            canvas.drawRoundRect(handleRect, handleRect.height() / 2f, handleRect.height() / 2f, glassFill)
        }
        if (dockHandleRect.width() > 0 && !landscapeKeys) {
            glassFill.color = Color.argb((0.10f * 255 * a).toInt().coerceIn(0, 255), 8, 8, 10)
            val r = dockHandleRect.height() * 0.5f
            canvas.drawRoundRect(dockHandleRect, r, r, glassFill)
            glassBorder.color = Color.argb((0.18f * 255 * a).toInt().coerceIn(0, 255), 200, 180, 140)
            canvas.drawRoundRect(dockHandleRect, r, r, glassBorder)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (dockHandleRect.contains(x, y)) {
                    toggleDock()
                    return true
                }
                val hit = hitTest(x, y)
                if (hit != null) {
                    activeKeys.add(hit.def.code)
                    onKeyDown?.invoke(hit.def.code)
                    invalidate()
                    startRepeat(hit.def.code)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointIdx = event.actionIndex
                val px = event.getX(pointIdx)
                val py = event.getY(pointIdx)
                val wasActive = activeKeys.toSet()
                val newlyActive = mutableSetOf<Int>()
                for (i in 0 until event.pointerCount) {
                    val hit = hitTest(event.getX(i), event.getY(i))
                    if (hit != null) newlyActive.add(hit.def.code)
                }
                val released = wasActive - newlyActive
                val pressed = newlyActive - wasActive
                released.forEach {
                    activeKeys.remove(it)
                    onKeyUp?.invoke(it)
                    stopRepeat(it)
                }
                pressed.forEach {
                    activeKeys.add(it)
                    onKeyDown?.invoke(it)
                    startRepeat(it)
                }
                if (released.isNotEmpty() || pressed.isNotEmpty()) invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val upIdx = event.actionIndex
                val wasActive = activeKeys.toSet()
                val newlyActive = mutableSetOf<Int>()
                for (i in 0 until event.pointerCount) {
                    if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && i == upIdx) continue
                    val hit = hitTest(event.getX(i), event.getY(i))
                    if (hit != null) newlyActive.add(hit.def.code)
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    newlyActive.clear()
                }
                val released = wasActive - newlyActive
                released.forEach {
                    activeKeys.remove(it)
                    onKeyUp?.invoke(it)
                    stopRepeat(it)
                }
                if (released.isNotEmpty()) invalidate()
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float): KeyRect? {
        return allKeys.lastOrNull { it.rect.contains(x, y) }
    }

    private fun toggleDock() {
        dockMode = when (dockMode) {
            DockMode.BOTTOM -> DockMode.TOP
            DockMode.TOP -> DockMode.BOTTOM
            DockMode.SPLIT -> DockMode.BOTTOM
        }
        onDockModeChanged?.invoke(dockMode)
        requestLayout()
        invalidate()
    }

    private fun startRepeat(code: Int) {
        stopRepeat(-1)
        repeatCode = code
        repeatStartMs = SystemClock.uptimeMillis()
        val repeatableCodes = setOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_FORWARD_DEL)
        if (code !in repeatableCodes) return
        repeatHandler.postDelayed(object : Runnable {
            override fun run() {
                if (repeatCode != code) return
                val elapsed = SystemClock.uptimeMillis() - repeatStartMs
                val delay = if (elapsed < 500) 120L else 50L
                if (code in activeKeys) {
                    onKeyDown?.invoke(code)
                }
                repeatHandler.postDelayed(this, delay)
            }
        }, 400)
    }

    private fun stopRepeat(code: Int) {
        if (code == -1 || code == repeatCode) {
            repeatHandler.removeCallbacksAndMessages(null)
            repeatCode = -1
        }
    }

    private fun dp(value: Int): Float = (value * resources.displayMetrics.density)
}
