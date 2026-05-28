/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Developer mode utilities — debug console, game info inspector.
 */

package com.runestone.app.media

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * In-game debug console for WebView games.
 * Shows game state and allows JS evaluation.
 */
class DevConsole(context: Context, private val webView: WebView?) : LinearLayout(context) {

    private val output: TextView
    private val input: EditText

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(200, 8, 6, 12))
        setPadding(dp(10), dp(10), dp(10), dp(10))

        addView(TextView(context).apply {
            text = "DEV CONSOLE"; setTextColor(Color.rgb(100, 255, 100))
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        })

        output = TextView(context).apply {
            setTextColor(Color.rgb(180, 220, 180)); textSize = 10f
            typeface = Typeface.MONOSPACE
            text = "// Type JS and press RUN to evaluate\n"
        }
        val scroll = ScrollView(context).apply {
            addView(output, LayoutParams(MATCH, WRAP))
        }
        addView(scroll, LayoutParams(MATCH, 0, 1f))

        val inputRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        input = EditText(context).apply {
            hint = "${'$'}gameParty._gold"; setHintTextColor(Color.argb(80, 100, 255, 100))
            setTextColor(Color.rgb(200, 255, 200)); textSize = 11f
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 0, 255, 0)); cornerRadius = dp(4).toFloat()
            }
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))

        val runBtn = TextView(context).apply {
            text = "RUN"; setTextColor(Color.rgb(100, 255, 100)); textSize = 11f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 0, 200, 0)); cornerRadius = dp(4).toFloat()
            }
            setOnClickListener { evalJs() }
        }
        inputRow.addView(runBtn)
        addView(inputRow, LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
    }

    private fun evalJs() {
        val code = input.text.toString().trim()
        if (code.isEmpty() || webView == null) return
        val wrapped = """
            (function(){
                try {
                    var __result = eval($code);
                    return JSON.stringify(__result !== undefined ? __result : 'undefined');
                } catch(e) {
                    return 'ERROR: ' + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(wrapped) { result ->
            val display = if (result == null) "null"
            else result.trim('"').replace("\\\"", "\"").take(200)
            output.text = "${output.text}\n> $code\n  $display"
            input.text.clear()
        }
    }

    /** Show game state summary. */
    fun showState() {
        if (webView == null) return
        webView.evaluateJavascript("""
            (function(){
                var s = [];
                if (window.${'$'}gameParty) {
                    s.push('Party: ' + ${'$'}gameParty._actors.length + ' members');
                    s.push('Gold: ' + ${'$'}gameParty._gold);
                    s.push('Steps: ' + ${'$'}gameParty._steps);
                    s.push('Items: ' + (${'$'}gameParty._items ? Object.keys(${'$'}gameParty._items).length : 0));
                }
                if (window.${'$'}gameSystem) {
                    s.push('Save#: ' + ${'$'}gameSystem._saveCount);
                    s.push('Encounters: ' + (${'$'}gameSystem._encounterEnabled ? 'ON' : 'OFF'));
                }
                if (window.${'$'}gamePlayer) {
                    s.push('Pos: (' + ${'$'}gamePlayer._x + ',' + ${'$'}gamePlayer._y + ') Map:' + ${'$'}gamePlayer._mapId);
                }
                if (window.${'$'}gameSwitches) s.push('Switches: ' + ${'$'}gameSwitches._data.length);
                if (window.${'$'}gameVariables) s.push('Variables: ' + ${'$'}gameVariables._data.length);
                return s.join('\\n');
            })();
        """.trimIndent()) { result ->
            val state = result?.trim('"')?.replace("\\n", "\n") ?: "No game state"
            output.text = "${output.text}\n=== GAME STATE ===\n$state\n"
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
    companion object { val MATCH = ViewGroup.LayoutParams.MATCH_PARENT; val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT }
}

/**
 * Simple developer mode toggle. When enabled, shows debug info.
 */
object DevMode {
    var enabled: Boolean = false
    var showFpsOverlay: Boolean = true
    var logGameState: Boolean = false
}
