package com.runestone.app.input

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

object CanvasGlassStyle {

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD }

    fun dp(context: android.content.Context, value: Int): Float = value * context.resources.displayMetrics.density

    fun drawCircle(canvas: Canvas, cx: Float, cy: Float, r: Float, pressed: Boolean, a: Float, label: String, labelSize: Float = 0f) {
        shadowPaint.color = Color.argb((55 * a).toInt().coerceIn(0, 255), 0, 0, 0)
        canvas.drawCircle(cx + 1.5f, cy + 2.5f, r, shadowPaint)
        fillPaint.color = Color.argb((if (pressed) 80 else 38).coerceIn(0, 255), 255, 255, 255)
        canvas.drawCircle(cx, cy, r, fillPaint)
        borderPaint.alpha = (if (pressed) 160 else 90).coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r - 0.9f, borderPaint)
        if (!pressed) {
            highlightPaint.color = Color.argb((40 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, true, highlightPaint)
        }
        if (label.isNotEmpty()) {
            val size = if (labelSize > 0f) labelSize else r * 0.9f
            labelPaint.textSize = size
            labelPaint.color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((230 * a).toInt().coerceIn(0, 255), 232, 229, 220)
            canvas.drawText(label, cx, cy + size * 0.35f, labelPaint)
        }
    }

    fun drawRoundRect(canvas: Canvas, rect: RectF, r: Float, pressed: Boolean, a: Float, label: String, labelSize: Float = 0f) {
        shadowPaint.color = Color.argb((50 * a).toInt().coerceIn(0, 255), 0, 0, 0)
        canvas.drawRoundRect(rect.left + 1f, rect.top + 2.5f, rect.right + 1f, rect.bottom + 2.5f, r, r, shadowPaint)
        fillPaint.color = Color.argb((if (pressed) 75 else 35).coerceIn(0, 255), 255, 255, 255)
        canvas.drawRoundRect(rect, r, r, fillPaint)
        borderPaint.alpha = (if (pressed) 150 else 80).coerceIn(0, 255)
        canvas.drawRoundRect(rect, r, r, borderPaint)
        if (!pressed) {
            highlightPaint.color = Color.argb((35 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            val hl = RectF(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.4f)
            canvas.drawRoundRect(hl, r, r, highlightPaint)
        }
        if (label.isNotEmpty()) {
            val size = if (labelSize > 0f) labelSize else rect.height() * 0.55f
            labelPaint.textSize = size
            labelPaint.color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((225 * a).toInt().coerceIn(0, 255), 232, 229, 220)
            canvas.drawText(label, rect.centerX(), rect.centerY() + size * 0.35f, labelPaint)
        }
    }

    fun drawIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, a: Float, draw: (Canvas, Float, Float, Float, Paint) -> Unit) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2.2f
            color = Color.argb((220 * a).toInt().coerceIn(0, 255), 232, 229, 220)
        }
        draw(canvas, cx, cy, s, p)
    }

    fun drawArrow(canvas: Canvas, cx: Float, cy: Float, dir: String, size: Float, pressed: Boolean, a: Float) {
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; textAlign = Paint.Align.CENTER; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD
            textSize = size * 1.6f
            color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((230 * a).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        val arrow = when (dir) { "up" -> "\u25B2"; "down" -> "\u25BC"; "left" -> "\u25C0"; else -> "\u25B6" }
        canvas.drawText(arrow, cx, cy + ap.textSize * 0.35f, ap)
    }

    fun drawVectorIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, a: Float, paint: Paint) {
        paint.alpha = (220 * a).toInt().coerceIn(0, 255)
    }

    object Icons {
        fun gamepad(canvas: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawRoundRect(RectF(cx - s * 0.8f, cy - s * 0.55f, cx + s * 0.8f, cy + s * 0.55f), s * 0.2f, s * 0.2f, p)
            canvas.drawCircle(cx - s * 0.45f, cy + s * 0.5f, s * 0.18f, p)
            canvas.drawCircle(cx + s * 0.45f, cy + s * 0.5f, s * 0.18f, p)
            canvas.drawCircle(cx - s * 0.35f, cy, s * 0.08f, p)
            canvas.drawCircle(cx, cy, s * 0.08f, p)
            canvas.drawCircle(cx + s * 0.35f, cy, s * 0.08f, p)
        }
        fun sliders(canvas: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            val bar = s * 0.6f; val gap = s * 0.35f
            for (i in 0..2) { val y = cy - gap + i * gap
                canvas.drawLine(cx - bar, y, cx + bar, y, p)
                val knob = if (i == 0) cx - bar * 0.4f else if (i == 1) cx + bar * 0.3f else cx
                canvas.drawCircle(knob, y, s * 0.12f, p)
            }
        }
        fun keyboard(canvas: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawRoundRect(RectF(cx - s * 0.75f, cy - s * 0.5f, cx + s * 0.75f, cy + s * 0.5f), s * 0.15f, s * 0.15f, p)
            for (row in 0..2) { val cols = if (row == 2) 4 else 5; val kw = s * 0.2f; val kh = s * 0.18f
                val sx = cx - ((cols - 1) * (kw + 2f)) / 2f
                for (col in 0 until cols) { val kx = sx + col * (kw + 2f); val ky = cy - s * 0.35f + row * (kh + 2f)
                    canvas.drawRoundRect(RectF(kx - kw / 2, ky - kh / 2, kx + kw / 2, ky + kh / 2), 2f, 2f, p) }
            }
        }
        fun touchPointer(canvas: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            p.style = Paint.Style.STROKE; canvas.drawCircle(cx, cy, s * 0.5f, p)
            p.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, s * 0.15f, p)
            canvas.drawLine(cx + s * 0.5f, cy, cx + s * 0.8f, cy, p)
            canvas.drawLine(cx, cy - s * 0.5f, cx, cy - s * 0.8f, p)
            p.style = Paint.Style.STROKE
        }
    }
}
