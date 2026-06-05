package com.runestone.app.input

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.runestone.app.ui.Theme

class RunestoneKeyboardView(context: Context) : LinearLayout(context) {
    var onText: ((String) -> Unit)? = null
    var onKeyCode: ((Int) -> Unit)? = null
    var onHide: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply {
            setColor(Color.argb(238, 8, 8, 10))
            setStroke(dp(1), Theme.active.panelStroke)
            cornerRadius = dp(12).toFloat()
        }
        elevation = dp(10).toFloat()

        addRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
        addRow(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"))
        addRow(listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"))
        addRow(listOf("Z", "X", "C", "V", "B", "N", "M"))
        addCommandRow()
    }

    private fun addRow(keys: List<String>) {
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            keys.forEach { label ->
                addView(key(label) { onText?.invoke(label.lowercase()) }, keyParams())
            }
        }, rowParams())
    }

    private fun addCommandRow() {
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            addView(key("Space") { onText?.invoke(" ") }, wideParams(2f))
            addView(key("Back") { onKeyCode?.invoke(KeyEvent.KEYCODE_DEL) }, wideParams(1.25f))
            addView(key("Enter") { onKeyCode?.invoke(KeyEvent.KEYCODE_ENTER) }, wideParams(1.25f))
            addView(key("Hide") { onHide?.invoke() }, wideParams(1f))
        }, rowParams())
    }

    private fun key(label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Theme.TEXT)
            textSize = if (label.length > 1) 12f else 15f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(42)
            background = GradientDrawable().apply {
                setColor(Color.argb(58, Color.red(Theme.active.accent), Color.green(Theme.active.accent), Color.blue(Theme.active.accent)))
                setStroke(dp(1), Theme.active.accentStroke)
                cornerRadius = dp(8).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun rowParams(): LayoutParams =
        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        }

    private fun keyParams(): LayoutParams =
        LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }

    private fun wideParams(weight: Float): LayoutParams =
        LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        @JvmStatic
        fun attachTo(parent: ViewGroup): RunestoneKeyboardView {
            val existing = parent.findViewWithTag<RunestoneKeyboardView>("runestone-keyboard")
            if (existing != null) return existing
            val view = RunestoneKeyboardView(parent.context).apply {
                tag = "runestone-keyboard"
            }
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                leftMargin = view.dp(8)
                rightMargin = view.dp(8)
                bottomMargin = view.dp(8)
            }
            parent.addView(view, params)
            return view
        }
    }
}
