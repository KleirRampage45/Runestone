package com.runestone.app.input

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

object OverlayStyle {
    const val FILL_ALPHA_IDLE = 0.14f
    const val FILL_ALPHA_PRESSED = 0.28f
    const val BORDER_ALPHA_IDLE = 0.35f
    const val BORDER_ALPHA_PRESSED = 0.58f
    const val HIGHLIGHT_ALPHA = 0.18f
    const val SHADOW_ALPHA = 0.30f
    const val LABEL_ALPHA = 0.90f
    const val MUTED_LABEL_ALPHA = 0.60f
    const val CORNER_KEY = 10f
    const val CORNER_BUTTON = 16f
    const val CORNER_PANEL = 24f
    const val CORNER_TOOLBAR = 14f
    const val MIN_TOUCH = 48f

    fun fillPaint(alpha: Float = FILL_ALPHA_IDLE): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
    }

    fun borderPaint(alpha: Float = BORDER_ALPHA_IDLE, width: Float = 1.8f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
    }

    fun highlightPaint(alpha: Float = HIGHLIGHT_ALPHA): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
    }

    fun shadowPaint(alpha: Float = SHADOW_ALPHA): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), 0, 0, 0)
    }

    fun labelPaint(size: Float = 26f, alpha: Float = LABEL_ALPHA): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = size
        isFakeBoldText = true
        color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
    }

    fun smallLabelPaint(size: Float = 16f, alpha: Float = MUTED_LABEL_ALPHA): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = size
        color = Color.argb((alpha * 255).toInt().coerceIn(0, 255), 232, 229, 220)
    }

    fun accentLabelPaint(size: Float = 24f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = size
        isFakeBoldText = true
        color = Color.rgb(238, 207, 158)
    }

    fun createArrowPath(direction: String, size: Float): Path {
        val path = Path()
        when (direction) {
            "up" -> { path.moveTo(0f, -size); path.lineTo(-size, size * 0.7f); path.lineTo(size, size * 0.7f); path.close() }
            "down" -> { path.moveTo(0f, size); path.lineTo(-size, -size * 0.7f); path.lineTo(size, -size * 0.7f); path.close() }
            "left" -> { path.moveTo(-size, 0f); path.lineTo(size * 0.7f, -size); path.lineTo(size * 0.7f, size); path.close() }
            "right" -> { path.moveTo(size, 0f); path.lineTo(-size * 0.7f, -size); path.lineTo(-size * 0.7f, size); path.close() }
        }
        return path
    }

    fun drawGlassCircle(canvas: android.graphics.Canvas, cx: Float, cy: Float, r: Float, pressed: Boolean, a: Float, label: String = "", labelSize: Float = 26f, accentColor: Int = Color.rgb(238, 207, 158), showLabel: Boolean = true) {
        val fillAlpha = if (pressed) FILL_ALPHA_PRESSED else FILL_ALPHA_IDLE
        val borderAlpha = if (pressed) BORDER_ALPHA_PRESSED else BORDER_ALPHA_IDLE
        canvas.drawCircle(cx + 2f, cy + 3f, r, shadowPaint(SHADOW_ALPHA * a))
        fillPaint(fillAlpha * a).let { p -> canvas.drawCircle(cx, cy, r, p) }
        borderPaint(borderAlpha * a).let { p -> canvas.drawCircle(cx, cy, r, p) }
        if (!pressed) {
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, true, highlightPaint(HIGHLIGHT_ALPHA * a))
        }
        if (label.isNotEmpty() && showLabel) {
            val lp = labelPaint(labelSize, LABEL_ALPHA * a)
            lp.color = if (pressed) accentColor else Color.argb((LABEL_ALPHA * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawText(label, cx, cy + labelSize * 0.3f, lp)
        }
    }

    fun drawGlassRoundRect(canvas: android.graphics.Canvas, rect: RectF, r: Float, pressed: Boolean, a: Float, label: String = "", labelSize: Float = 18f) {
        val fillAlpha = if (pressed) FILL_ALPHA_PRESSED else FILL_ALPHA_IDLE
        val borderAlpha = if (pressed) BORDER_ALPHA_PRESSED else BORDER_ALPHA_IDLE
        canvas.drawRoundRect(rect.left + 1f, rect.top + 3f, rect.right + 1f, rect.bottom + 3f, r, r, shadowPaint(SHADOW_ALPHA * a))
        fillPaint(fillAlpha * a).let { p -> canvas.drawRoundRect(rect, r, r, p) }
        borderPaint(borderAlpha * a).let { p -> canvas.drawRoundRect(rect, r, r, p) }
        if (!pressed) {
            val hl = RectF(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f)
            canvas.drawRoundRect(hl, r, r, highlightPaint(HIGHLIGHT_ALPHA * a))
        }
        if (label.isNotEmpty()) {
            val lp = labelPaint(labelSize, LABEL_ALPHA * a)
            lp.color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((LABEL_ALPHA * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawText(label, rect.centerX(), rect.centerY() + labelSize * 0.35f, lp)
        }
    }

    fun arrowPaint(pressed: Boolean, a: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (pressed) Color.rgb(238, 207, 158) else Color.argb((LABEL_ALPHA * 255 * a).toInt().coerceIn(0, 255), 255, 255, 255)
    }

    fun glassBgDrawable(context: Context, radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha, if (accent) 48 else 14, if (accent) 38 else 14, if (accent) 28 else 18))
            cornerRadius = radius.toFloat()
            setStroke(dp(context, 1), Color.argb(if (accent) 80 else 45, if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
        }

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun dpf(context: Context, value: Int): Float = value * context.resources.displayMetrics.density

    fun drawVectorIcon(canvas: android.graphics.Canvas, cx: Float, cy: Float, size: Float, a: Float, paint: Paint, draw: (Canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, Paint: Paint) -> Unit) {
        paint.alpha = (LABEL_ALPHA * 255 * a).toInt().coerceIn(0, 255)
        draw(canvas, cx, cy, size, paint)
    }

    object Icons {
        fun gamepad(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawRoundRect(RectF(cx - s * 0.8f, cy - s * 0.55f, cx + s * 0.8f, cy + s * 0.55f), s * 0.2f, s * 0.2f, p)
            canvas.drawCircle(cx - s * 0.45f, cy + s * 0.5f, s * 0.18f, p)
            canvas.drawCircle(cx + s * 0.45f, cy + s * 0.5f, s * 0.18f, p)
            canvas.drawCircle(cx - s * 0.35f, cy, s * 0.08f, p)
            canvas.drawCircle(cx, cy, s * 0.08f, p)
            canvas.drawCircle(cx + s * 0.35f, cy, s * 0.08f, p)
        }

        fun sliders(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            val bar = s * 0.6f
            val gap = s * 0.35f
            for (i in 0..2) {
                val y = cy - gap + i * gap
                canvas.drawLine(cx - bar, y, cx + bar, y, p)
                val knob = if (i == 0) cx - bar * 0.4f else if (i == 1) cx + bar * 0.3f else cx
                canvas.drawCircle(knob, y, s * 0.12f, p)
            }
        }

        fun keyboard(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawRoundRect(RectF(cx - s * 0.75f, cy - s * 0.5f, cx + s * 0.75f, cy + s * 0.5f), s * 0.15f, s * 0.15f, p)
            for (row in 0..2) {
                val cols = if (row == 2) 4 else 5
                val keyW = s * 0.2f
                val keyH = s * 0.18f
                val startX = cx - ((cols - 1) * (keyW + 2f)) / 2f
                for (col in 0 until cols) {
                    val kx = startX + col * (keyW + 2f)
                    val ky = cy - s * 0.35f + row * (keyH + 2f)
                    canvas.drawRoundRect(RectF(kx - keyW / 2, ky - keyH / 2, kx + keyW / 2, ky + keyH / 2), 2f, 2f, p)
                }
            }
        }

        fun touchPointer(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            p.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy, s * 0.5f, p)
            p.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, s * 0.15f, p)
            canvas.drawLine(cx + s * 0.5f, cy, cx + s * 0.8f, cy, p)
            canvas.drawLine(cx, cy - s * 0.5f, cx, cy - s * 0.8f, p)
            p.style = Paint.Style.STROKE
        }

        fun close(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawLine(cx - s * 0.5f, cy - s * 0.5f, cx + s * 0.5f, cy + s * 0.5f, p)
            canvas.drawLine(cx + s * 0.5f, cy - s * 0.5f, cx - s * 0.5f, cy + s * 0.5f, p)
        }

        fun check(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            val path = Path()
            path.moveTo(cx - s * 0.5f, cy)
            path.lineTo(cx - s * 0.15f, cy + s * 0.4f)
            path.lineTo(cx + s * 0.5f, cy - s * 0.4f)
            canvas.drawPath(path, p)
        }

        fun undo(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            val path = Path()
            path.arcTo(RectF(cx - s * 0.3f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 0.4f), 0f, -270f, true)
            canvas.drawPath(path, p)
            canvas.drawLine(cx + s * 0.4f, cy - s * 0.4f, cx + s * 0.5f, cy - s * 0.1f, p)
        }

        fun rotate(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawArc(RectF(cx - s * 0.5f, cy - s * 0.5f, cx + s * 0.5f, cy + s * 0.5f), 0f, 300f, false, p)
            val path = Path()
            path.moveTo(cx + s * 0.45f, cy - s * 0.6f)
            path.lineTo(cx + s * 0.7f, cy - s * 0.3f)
            path.lineTo(cx + s * 0.25f, cy - s * 0.3f)
            canvas.drawPath(path, p)
        }

        fun info(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            p.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy, s * 0.5f, p)
            p.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy - s * 0.15f, s * 0.08f, p)
            canvas.drawLine(cx, cy, cx, cy + s * 0.35f, p)
            p.style = Paint.Style.STROKE
        }

        fun speakerMute(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            val path = Path()
            path.moveTo(cx + s * 0.2f, cy - s * 0.3f)
            path.lineTo(cx - s * 0.1f, cy - s * 0.3f)
            path.lineTo(cx - s * 0.3f, cy - s * 0.15f)
            path.lineTo(cx - s * 0.3f, cy + s * 0.15f)
            path.lineTo(cx - s * 0.1f, cy + s * 0.3f)
            path.lineTo(cx + s * 0.2f, cy + s * 0.3f)
            path.close()
            canvas.drawPath(path, p)
            p.style = Paint.Style.STROKE
            canvas.drawLine(cx + s * 0.3f, cy - s * 0.45f, cx + s * 0.3f, cy + s * 0.45f, p)
            canvas.drawLine(cx + s * 0.45f, cy - s * 0.3f, cx + s * 0.45f, cy + s * 0.3f, p)
            p.style = Paint.Style.FILL
        }

        fun exitDoor(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawRoundRect(RectF(cx - s * 0.4f, cy - s * 0.6f, cx + s * 0.4f, cy + s * 0.6f), s * 0.1f, s * 0.1f, p)
            canvas.drawCircle(cx + s * 0.15f, cy - s * 0.1f, s * 0.08f, p)
            canvas.drawLine(cx - s * 0.3f, cy, cx - s * 0.6f, cy, p)
            val arrow = Path()
            arrow.moveTo(cx - s * 0.6f, cy)
            arrow.lineTo(cx - s * 0.45f, cy - s * 0.2f)
            arrow.lineTo(cx - s * 0.45f, cy + s * 0.2f)
            arrow.close()
            canvas.drawPath(arrow, p)
        }

        fun wand(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawLine(cx - s * 0.5f, cy + s * 0.5f, cx + s * 0.3f, cy - s * 0.3f, p)
            val star = Path()
            val r = s * 0.2f
            for (i in 0..4) {
                val a = i * 144f - 90f
                val x = cx + s * 0.5f + Math.cos(Math.toRadians(a.toDouble())).toFloat() * r
                val y = cy - s * 0.5f + Math.sin(Math.toRadians(a.toDouble())).toFloat() * r
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            canvas.drawPath(star, p)
        }

        fun grid(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            val lines = 3
            val step = s * 0.45f / lines
            for (i in 1 until lines) {
                val off = -s * 0.45f + i * step
                canvas.drawLine(cx - s * 0.45f, cy + off, cx + s * 0.45f, cy + off, p)
                canvas.drawLine(cx + off, cy - s * 0.45f, cx + off, cy + s * 0.45f, p)
            }
            canvas.drawRoundRect(RectF(cx - s * 0.45f, cy - s * 0.45f, cx + s * 0.45f, cy + s * 0.45f), 3f, 3f, p)
        }

        fun phoneRotate(canvas: android.graphics.Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
            canvas.drawRoundRect(RectF(cx - s * 0.3f, cy - s * 0.55f, cx + s * 0.3f, cy + s * 0.55f), s * 0.08f, s * 0.08f, p)
            val path = Path()
            path.moveTo(cx + s * 0.45f, cy - s * 0.1f)
            path.lineTo(cx + s * 0.65f, cy + s * 0.1f)
            path.lineTo(cx + s * 0.45f, cy + s * 0.3f)
            canvas.drawPath(path, p)
        }
    }
}
