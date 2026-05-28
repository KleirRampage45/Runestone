/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Floating cheat menu overlay for RPG Maker games.
 */

package com.runestone.app.cheats

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Callback for cheat execution.
 * @param cheat The cheat to apply
 * @param customScript Optional custom script text
 */
fun interface CheatMenuCallback {
    fun onExecute(cheat: Cheat, customScript: String?)
}

class CheatMenuView(
    context: Context,
    private val callback: CheatMenuCallback,
) : FrameLayout(context) {

    private var isVisible = false
    private var activeTab = 0  // 0=RPG, 1=Pokemon, 2=Custom
    private val tabButtons = mutableListOf<TextView>()
    private val tabContents = mutableListOf<LinearLayout>()

    init {
        visibility = View.GONE
        setBackgroundColor(Color.argb(140, 0, 0, 0))
        setOnClickListener { hide() }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 12, 18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(14, 12, 18))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(80, 160, 140, 110))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val scroll = ScrollView(context).apply { overScrollMode = ScrollView.OVER_SCROLL_NEVER }
        scroll.addView(panel)

        val panelW = (context.resources.displayMetrics.widthPixels * 0.82f).toInt()
        val panelH = (context.resources.displayMetrics.heightPixels * 0.60f).toInt()
        addView(scroll, LayoutParams(panelW, panelH, Gravity.CENTER))

        // Header
        panel.addView(header("CHEAT MENU"))
        panel.addView(space(dp(8)))

        // Tabs
        val tabBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        for ((i, name) in listOf("RPG Maker", "Pokemon", "Custom").withIndex()) {
            val tab = tabChip(name, i == 0)
            tab.setOnClickListener { switchTab(i); refreshTabs() }
            tabButtons.add(tab)
            tabBar.addView(tab, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        panel.addView(tabBar)
        panel.addView(space(dp(10)))

        // Tab contents container
        for (i in 0..2) {
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (i == 0) View.VISIBLE else View.GONE
            }
            tabContents.add(content)
            panel.addView(content)
        }

        // ── TAB 0: RPG Maker ──
        tabContents[0].apply {
            addView(cheatBtn("Add 9999 Gold") { callback.onExecute(Cheat.SetGold(9999), null) })
            addView(cheatBtn("Level Up All (+10)") { callback.onExecute(Cheat.LevelUp(10), null) })
            addView(cheatBtn("Max Level (99)") { callback.onExecute(Cheat.LevelUp(-1), null) })
            addView(cheatBtn("Heal Party (Full HP/MP)") { callback.onExecute(Cheat.HealParty, null) })
            addView(cheatBtn("Max Stats + Heal") { callback.onExecute(Cheat.MaxStats, null) })
            addView(cheatBtn("Toggle No Encounters") { callback.onExecute(Cheat.ToggleEncounter, null) })
            addView(cheatBtn("Walk Through Walls") { callback.onExecute(Cheat.WalkThroughWalls, null) })
            addView(cheatBtn("All Items x99") { callback.onExecute(Cheat.AllItems, null) })
            addView(cheatBtn("One-Hit Kill") { callback.onExecute(Cheat.OneHitKill, null) })
        }

        // ── TAB 1: Pokemon Essentials ──
        tabContents[1].apply {
            addView(cheatBtn("Get All Items") { callback.onExecute(Cheat.AllItems, null) })
            addView(cheatBtn("All Pokemon (PC)") { callback.onExecute(
                Cheat.CustomScript("""
                    for (var i = 1; i < ${'$'}gameParty.maxItems(); i++) {
                        ${'$'}gameParty.gainItem(${'$'}dataItems[i], 99);
                    }
                """.trimIndent(), ScriptLang.JAVASCRIPT
                ), null)
            })
            addView(cheatBtn("Shiny Encounter: ON") { callback.onExecute(
                Cheat.CustomScript("""
                    ${'$'}gameTemp._shinyEncounter = !(${'$'}gameTemp._shinyEncounter);
                """.trimIndent(), ScriptLang.JAVASCRIPT
                ), null)
            })
            addView(cheatBtn("Catch Rate 100%") { callback.onExecute(
                Cheat.CustomScript("""
                    if (!${'$'}gameTemp.__origCatch) {
                        ${'$'}gameTemp.__origCatch = BattleManager.catchMethod;
                    }
                    BattleManager.catchMethod = function(target) { return true; };
                """.trimIndent(), ScriptLang.JAVASCRIPT
                ), null)
            })
            addView(cheatBtn("Heal Party") { callback.onExecute(Cheat.HealParty, null) })
            addView(cheatBtn("Walk Through Walls") { callback.onExecute(Cheat.WalkThroughWalls, null) })
            addView(cheatBtn("Max Stats") { callback.onExecute(Cheat.MaxStats, null) })
        }

        // ── TAB 2: Custom Script ──
        val customInput = EditText(context).apply {
            hint = "// Paste JS script here..."
            setHintTextColor(Color.argb(100, 180, 160, 130))
            setTextColor(Color.rgb(220, 215, 200))
            textSize = 12f
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            background = GradientDrawable().apply {
                setColor(Color.argb(25, 255, 255, 255))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        tabContents[2].addView(customInput)
        tabContents[2].addView(space(dp(8)))
        tabContents[2].addView(cheatBtn("RUN SCRIPT") {
            val text = customInput.text.toString().trim()
            if (text.isNotEmpty()) {
                callback.onExecute(Cheat.CustomScript(text, ScriptLang.JAVASCRIPT), text)
                Toast.makeText(context, "Script injected", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ── API ──────────────────────────────────────────────────────

    fun show() {
        isVisible = true
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
    }

    fun hide() {
        isVisible = false
        animate().alpha(0f).setDuration(150)
            .withEndAction { visibility = View.GONE }.start()
    }

    fun toggle() { if (isVisible) hide() else show() }

    fun isShowing(): Boolean = isVisible

    // ── internal ─────────────────────────────────────────────────

    private fun switchTab(index: Int) {
        activeTab = index
        for ((i, content) in tabContents.withIndex()) {
            content.visibility = if (i == index) View.VISIBLE else View.GONE
        }
    }

    private fun refreshTabs() {
        for ((i, tab) in tabButtons.withIndex()) {
            val active = i == activeTab
            tab.setTextColor(if (active) Color.rgb(207, 174, 126) else Color.rgb(140, 130, 112))
            tab.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            (tab.background as? GradientDrawable)?.apply {
                if (active) {
                    setColor(Color.argb(80, 200, 170, 130))
                    setStroke(0, Color.TRANSPARENT)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(0, Color.TRANSPARENT)
                }
            }
        }
    }

    private fun header(text: String) = TextView(context).apply {
        this.text = text; setTextColor(Color.rgb(207, 174, 126)); textSize = 15f
        typeface = Typeface.create("serif", Typeface.BOLD)
    }

    private fun tabChip(text: String, active: Boolean) = TextView(context).apply {
        this.text = text; textSize = 11f; gravity = Gravity.CENTER
        setTextColor(if (active) Color.rgb(207, 174, 126) else Color.rgb(140, 130, 112))
        typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(dp(6), dp(7), dp(6), dp(7))
        background = if (active) GradientDrawable().apply {
            setColor(Color.argb(80, 200, 170, 130)); cornerRadius = dp(7).toFloat()
        } else null
    }

    private fun cheatBtn(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.rgb(220, 215, 200)); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255))
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), Color.argb(40, 180, 160, 130))
            }
            setOnClickListener { onClick() }
            (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = dp(6)
        }
    }

    private fun space(h: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, h)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
