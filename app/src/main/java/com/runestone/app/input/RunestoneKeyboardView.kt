package com.runestone.app.input

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.runestone.app.R
import com.runestone.app.ui.Theme

class RunestoneKeyboardView(context: Context) : LinearLayout(context) {
    var onText: ((String) -> Unit)? = null
    var onKeyCode: ((Int) -> Unit)? = null
    var onHide: (() -> Unit)? = null
    private var caps = false
    private var numericMode = false

    init {
        orientation = VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(4))
        background = GradientDrawable().apply {
            setColor(Color.argb(232, 8, 8, 10))
            setStroke(dp(1), Theme.active.panelStroke)
            cornerRadius = dp(10).toFloat()
        }
        elevation = dp(10).toFloat()
        rebuildKeys()
    }

    private fun rebuildKeys() {
        removeAllViews()
        if (numericMode) {
            addTextRow(listOf("1", "2", "3"))
            addTextRow(listOf("4", "5", "6"))
            addTextRow(listOf("7", "8", "9"))
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(key("ABC") {
                    numericMode = false
                    rebuildKeys()
                }, wideParams(1.05f))
                addView(key("0") { onText?.invoke("0") }, wideParams(1f))
                addView(iconKey(R.drawable.ic_key_backspace, "Backspace") {
                    onKeyCode?.invoke(KeyEvent.KEYCODE_DEL)
                }, wideParams(1f))
                addView(iconKey(R.drawable.ic_key_enter, "Enter") {
                    onKeyCode?.invoke(KeyEvent.KEYCODE_ENTER)
                }, wideParams(1f))
                addView(iconKey(R.drawable.ic_key_keyboard_hide, "Hide keyboard") {
                    onHide?.invoke()
                }, wideParams(1f))
            }, rowParams())
        } else {
            addTextRow(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"))
            addTextRow(listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"))
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(iconKey(R.drawable.ic_key_shift, "Shift", active = caps) {
                    caps = !caps
                    rebuildKeys()
                }, wideParams(1.1f))
                listOf("Z", "X", "C", "V", "B", "N", "M").forEach { label ->
                    addView(letterKey(label), keyParams())
                }
                addView(iconKey(R.drawable.ic_key_backspace, "Backspace") {
                    onKeyCode?.invoke(KeyEvent.KEYCODE_DEL)
                }, wideParams(1.1f))
            }, rowParams())
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(key("123") {
                    numericMode = true
                    rebuildKeys()
                }, wideParams(1f))
                addView(key("Space") { onText?.invoke(" ") }, wideParams(3.8f))
                addView(iconKey(R.drawable.ic_key_enter, "Enter") {
                    onKeyCode?.invoke(KeyEvent.KEYCODE_ENTER)
                }, wideParams(1.05f))
                addView(iconKey(R.drawable.ic_key_keyboard_hide, "Hide keyboard") {
                    onHide?.invoke()
                }, wideParams(1.05f))
            }, rowParams())
        }
    }

    private fun addTextRow(keys: List<String>) {
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            keys.forEach { label ->
                addView(letterKey(label), keyParams())
            }
        }, rowParams())
    }

    private fun letterKey(label: String): TextView =
        key(if (caps) label else label.lowercase()) {
            onText?.invoke(if (caps) label else label.lowercase())
        }

    private fun key(label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Theme.TEXT)
            textSize = if (label.length > 1) 11f else 14f
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(32)
            background = GradientDrawable().apply {
                setColor(Color.argb(58, Color.red(Theme.active.accent), Color.green(Theme.active.accent), Color.blue(Theme.active.accent)))
                setStroke(dp(1), Theme.active.accentStroke)
                cornerRadius = dp(7).toFloat()
            }
            setOnClickListener { action() }
        }

    private fun iconKey(iconRes: Int, description: String, active: Boolean = false, action: () -> Unit): TextView =
        key("", action).apply {
            contentDescription = description
            compoundDrawableTintList = ColorStateList.valueOf(Theme.TEXT)
            setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0)
            if (active) {
                background = GradientDrawable().apply {
                    setColor(Theme.active.accentBg)
                    setStroke(dp(1), Theme.active.accent)
                    cornerRadius = dp(7).toFloat()
                }
            }
        }

    private fun rowParams(): LayoutParams =
        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(4)
        }

    private fun keyParams(): LayoutParams =
        LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
        }

    private fun wideParams(weight: Float): LayoutParams =
        LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            marginStart = dp(1)
            marginEnd = dp(1)
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
            val params = when (parent) {
                is FrameLayout -> FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ).apply {
                    leftMargin = view.dp(6)
                    rightMargin = view.dp(6)
                    bottomMargin = view.dp(6)
                }
                is RelativeLayout -> RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    leftMargin = view.dp(6)
                    rightMargin = view.dp(6)
                    bottomMargin = view.dp(6)
                }
                is LinearLayout -> LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.BOTTOM
                    marginStart = view.dp(6)
                    marginEnd = view.dp(6)
                    bottomMargin = view.dp(6)
                }
                else -> ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = view.dp(6)
                    rightMargin = view.dp(6)
                    bottomMargin = view.dp(6)
                }
            }
            parent.addView(view, params)
            return view
        }
    }
}
