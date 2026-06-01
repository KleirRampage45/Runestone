/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Controller mapping screen — rebind physical controller buttons
 * to game keyboard keys. Press a button to remap it.
 */

package com.runestone.app.input

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class ControllerMappingScreen(context: Context) : LinearLayout(context) {

    private var currentPreset: ControllerMapper.ControllerPreset
    private var customMap: MutableMap<Int, ControllerMapper.GameButton> = mutableMapOf()
    private var isListening = false
    private var listeningFor: ControllerMapper.GameButton? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(3, 3, 4))
        setPadding(dp(14), dp(16), dp(14), dp(16))
        currentPreset = ControllerMapper.getPreset("generic")
        customMap.putAll(currentPreset.buttonMap)

        val scroll = ScrollView(context).apply { overScrollMode = ScrollView.OVER_SCROLL_NEVER }
        addView(scroll, LayoutParams(MATCH, MATCH))

        val content = LinearLayout(context).apply { orientation = VERTICAL }
        scroll.addView(content)

        // Header
        content.addView(text("Controller Mapping", 18f, Color.rgb(207, 174, 126), Typeface.create("serif", Typeface.BOLD)))
        content.addView(text("Press a slot to remap, then press the physical button.", 10f, Color.rgb(140, 130, 112)))
        content.addView(space(dp(10)))

        // Preset selector
        content.addView(text("PRESET", 10f, Color.rgb(207, 174, 126)))
        val spinner = Spinner(context).apply {
            val names = ControllerMapper.presets.values.map { it.name }
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val presetId = ControllerMapper.presets.keys.elementAt(pos)
                    currentPreset = ControllerMapper.getPreset(presetId)
                    customMap.clear(); customMap.putAll(currentPreset.buttonMap)
                    refreshBindings(content)
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
        content.addView(spinner)
        content.addView(space(dp(12)))

        // Binding rows (placeholder — filled by refreshBindings)
        content.addView(text("BUTTON BINDINGS", 10f, Color.rgb(207, 174, 126)))
        content.addView(space(dp(4)))

        // Build initial bindings
        refreshBindings(content)
    }

    private fun refreshBindings(content: LinearLayout) {
        // Remove old binding rows (everything after the header + preset section)
        // We'll rebuild from the "BUTTON BINDINGS" label onwards
        val startIdx = 7 // header(1) + desc(1) + space(1) + preset(1) + spinner(1) + space(1) + label(1)
        while (content.childCount > startIdx) {
            content.removeViewAt(startIdx)
        }

        val buttons = listOf(
            "DPAD Up" to ControllerMapper.GameButton.DPAD_UP,
            "DPAD Down" to ControllerMapper.GameButton.DPAD_DOWN,
            "DPAD Left" to ControllerMapper.GameButton.DPAD_LEFT,
            "DPAD Right" to ControllerMapper.GameButton.DPAD_RIGHT,
            "A Button" to ControllerMapper.GameButton.BTN_A,
            "B Button" to ControllerMapper.GameButton.BTN_B,
            "X Button" to ControllerMapper.GameButton.BTN_X,
            "Y Button" to ControllerMapper.GameButton.BTN_Y,
            "L1 Shoulder" to ControllerMapper.GameButton.BTN_L1,
            "R1 Shoulder" to ControllerMapper.GameButton.BTN_R1,
            "L2 Trigger" to ControllerMapper.GameButton.BTN_L2,
            "R2 Trigger" to ControllerMapper.GameButton.BTN_R2,
            "Select" to ControllerMapper.GameButton.SELECT,
            "Start" to ControllerMapper.GameButton.START,
            "Menu" to ControllerMapper.GameButton.MENU,
        )

        for ((label, button) in buttons) {
            content.addView(bindingRow(label, button))
        }

        // Reset button
        content.addView(space(dp(12)))
        val resetBtn = TextView(context).apply {
            text = "RESET TO PRESET DEFAULTS"
            setTextColor(Color.rgb(200, 180, 150)); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 200, 170, 130)); cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.argb(80, 200, 170, 130))
            }
            setOnClickListener {
                customMap.clear(); customMap.putAll(currentPreset.buttonMap)
                refreshBindings(content)
            }
        }
        content.addView(resetBtn)
    }

    private fun bindingRow(label: String, button: ControllerMapper.GameButton): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(20, 255, 255, 255)); cornerRadius = dp(6).toFloat()
            }
            (layoutParams as? MarginLayoutParams)?.bottomMargin = dp(4)
        }

        row.addView(TextView(context).apply {
            text = label; setTextColor(Color.rgb(200, 195, 180)); textSize = 12f
            layoutParams = LayoutParams(0, WRAP, 1f)
        })

        val currentKey = customMap.entries.find { it.value == button }?.key
        val keyLabel = if (currentKey != null) {
            KeyEvent.keyCodeToString(currentKey).removePrefix("KEYCODE_").replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
        } else "—"

        val bindBtn = TextView(context).apply {
            text = if (listeningFor == button) "LISTENING..." else keyLabel
            setTextColor(if (listeningFor == button) Color.rgb(255, 200, 100) else Color.rgb(160, 150, 130))
            textSize = 11f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 255, 255, 255)); cornerRadius = dp(4).toFloat()
                if (listeningFor == button) setStroke(dp(2), Color.rgb(255, 200, 100))
            }
            setOnClickListener {
                isListening = true
                listeningFor = button
                refreshBindings(parent as LinearLayout)
                // Show toast to guide user
                android.widget.Toast.makeText(context, "Press a button on your controller...", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(bindBtn)
        return row
    }

    /** Handle a physical key event from the activity. Returns true if captured. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isListening || listeningFor == null) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val button = listeningFor!!
        // Remove old binding for this keycode
        customMap.entries.removeAll { it.key == event.keyCode }
        // Remove old binding for this GameButton
        customMap.entries.removeAll { it.value == button }
        // Set new binding
        customMap[event.keyCode] = button

        isListening = false
        listeningFor = null
        android.widget.Toast.makeText(context, "Bound to ${KeyEvent.keyCodeToString(event.keyCode)}", android.widget.Toast.LENGTH_SHORT).show()

        // Rebuild UI
        val parent = this.getChildAt(0) as? ScrollView
        val content = parent?.getChildAt(0) as? LinearLayout
        content?.let { refreshBindings(it) }
        return true
    }

    /** Get the current custom mapping. */
    fun getMapping(): Map<Int, ControllerMapper.GameButton> = customMap.toMap()

    /** Set custom mapping from external source (e.g., PerGameConfig). */
    fun setMapping(mapping: Map<Int, ControllerMapper.GameButton>) {
        customMap.clear(); customMap.putAll(mapping)
        val content = (getChildAt(0) as? ScrollView)?.getChildAt(0) as? LinearLayout
        content?.let { refreshBindings(it) }
    }

    private fun text(t: String, size: Float, color: Int, tf: Typeface = Typeface.DEFAULT): TextView =
        TextView(context).apply {
            text = t; setTextColor(color); textSize = size; typeface = tf
            if (tf == Typeface.DEFAULT_BOLD) typeface = Typeface.DEFAULT_BOLD
        }

    private fun space(h: Int) = View(context).apply { layoutParams = LayoutParams(MATCH, h) }
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    companion object {
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
