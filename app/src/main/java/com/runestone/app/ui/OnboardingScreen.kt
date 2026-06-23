package com.runestone.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.runestone.app.R

class OnboardingScreen(private val context: Context) {

    data class OnboardingResult(
        val locale: String,
        val selectedEngines: Set<String>,
        val rawgApiKey: String,
        val installRtp: Boolean,
    )

    fun create(onComplete: (OnboardingResult) -> Unit): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(3, 3, 4))
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(40))
        }
        scroll.addView(content)

        var locale = "en"
        var rawgApiKey = ""
        var installRtp = false
        val selectedEngines = mutableSetOf(
            "mkxp-z", "easyrpg", "webview-mv", "webview-mz",
            "tyrano", "html", "ruffle", "onscripter",
        )

        // ── Step 1: Welcome + Language ──
        content.addView(stepTitle("Welcome to Runestone"))
        content.addView(bodyText("Multi-engine game launcher for Android. Free and open source."))
        content.addView(spacer(20))

        content.addView(sectionLabel("Language / Idioma / Idioma"))
        val langRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for ((code, label) in listOf("en" to "English", "es" to "Español", "pt" to "Português")) {
            val btn = TextView(context).apply {
                text = label; textSize = 14f; gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setTextColor(Theme.active.accent)
                background = GradientDrawable().apply {
                    setColor(Color.argb(30, Color.red(Theme.active.accent), Color.green(Theme.active.accent), Color.blue(Theme.active.accent)))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.argb(60, Color.red(Theme.active.accent), Color.green(Theme.active.accent), Color.blue(Theme.active.accent)))
                }
                setOnClickListener {
                    locale = code
                    val children = langRow.getChildren()
                    children.forEach { it.alpha = 0.5f }
                    alpha = 1f
                    makeLiquid(this)
                }
            }
            langRow.addView(btn, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            })
        }
        content.addView(langRow)
        content.addView(spacer(30))

        // ── Step 2: Engine Selection ──
        content.addView(stepTitle("Select Engines"))
        content.addView(bodyText("Choose which game engines to enable. Deselect ones you don't need."))
        content.addView(spacer(12))

        val engines = listOf(
            "mkxp-z" to "RPG Maker XP/VX/VX Ace (~8 MB)",
            "easyrpg" to "RPG Maker 2000/2003 (~6 MB)",
            "onscripter" to "NScripter Visual Novels (~2 MB)",
            "renpy" to "Ren'Py Visual Novels (~55 MB)",
            "webview-mv" to "RPG Maker MV (~0 MB, WebView)",
            "webview-mz" to "RPG Maker MZ (~0 MB, WebView)",
            "tyrano" to "TyranoBuilder (~0 MB, WebView)",
            "html" to "Generic HTML5 Games (~0 MB)",
            "ruffle" to "Flash/SWF (~0 MB, CDN)",
            "godot" to "Godot Engine (~142 MB, optional)",
        )
        for ((id, label) in engines) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            val toggle = Switch(context).apply {
                isChecked = id in selectedEngines
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedEngines.add(id) else selectedEngines.remove(id)
                }
            }
            row.addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) })
            row.addView(TextView(context).apply {
                text = label; setTextColor(Theme.TEXT); textSize = 13f
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            content.addView(row)
        }

        content.addView(spacer(10))
        val rtpRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val rtpToggle = Switch(context).apply {
            isChecked = false
            setOnCheckedChangeListener { _, checked -> installRtp = checked }
        }
        rtpRow.addView(rtpToggle, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) })
        rtpRow.addView(TextView(context).apply {
            text = "Install VX Ace RTP (~100 MB, needed by many games)"
            setTextColor(Theme.TEXT); textSize = 13f
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        content.addView(rtpRow)
        content.addView(spacer(30))

        // ── Step 3: RAWG API Key ──
        content.addView(stepTitle("Game Art Scraping"))
        content.addView(bodyText("Runestone can fetch covers and metadata from RAWG. Get a free API key at:"))
        content.addView(spacer(8))

        val rawgLink = TextView(context).apply {
            text = "https://rawg.io/register"
            setTextColor(Theme.active.accentBright); textSize = 13f
            paint.isUnderlineText = true
            setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rawg.io/register")))
            }
        }
        content.addView(rawgLink)
        content.addView(spacer(8))

        content.addView(TextView(context).apply {
            text = "Paste your API key below (optional, skip to use fallback covers):"
            setTextColor(Theme.MUTED); textSize = 12f
        })
        content.addView(spacer(6))

        val apiInput = EditText(context).apply {
            hint = "RAWG API Key"
            setHintTextColor(Theme.MUTED_DIM)
            setTextColor(Theme.TEXT)
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 255, 255, 255))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Theme.MUTED_DIM)
            }
        }
        content.addView(apiInput)
        content.addView(spacer(30))

        // ── Step 4: Import Game ──
        content.addView(stepTitle("Ready to Play"))
        content.addView(bodyText("You can import games anytime from the + button on the home screen."))
        content.addView(spacer(30))

        // ── Finish Button ──
        val finishBtn = TextView(context).apply {
            text = "START PLAYING"
            setTextColor(Color.rgb(3, 3, 4)); textSize = 16f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(24), dp(14), dp(24), dp(14))
            background = GradientDrawable().apply {
                setColor(Theme.active.accent); cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                rawgApiKey = apiInput.text.toString().trim()
                makeLiquid(this)
                onComplete(OnboardingResult(
                    locale = locale,
                    selectedEngines = selectedEngines.toSet(),
                    rawgApiKey = rawgApiKey,
                    installRtp = installRtp,
                ))
            }
        }
        val btnLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER)
        btnLp.setMargins(0, dp(8), 0, dp(8))
        content.addView(finishBtn, btnLp)

        content.addView(spacer(20))

        return root
    }

    private fun stepTitle(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Theme.active.accent)
        textSize = 20f
        typeface = Typeface.create("serif", Typeface.BOLD)
    }

    private fun bodyText(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Theme.MUTED)
        textSize = 13f
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Theme.TEXT)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(v: Int): Int = com.runestone.app.ui.UiKit.dp(context, v)
    private fun spacer(h: Int) = com.runestone.app.ui.UiKit.spacer(context, h)

    private fun ViewGroup.getChildren(): List<View> = (0 until childCount).map { getChildAt(it) }
    private fun makeLiquid(v: View) { com.runestone.app.ui.UiKit.makeLiquid(v) }

    private companion object {
        private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private val MUTED = Color.rgb(140, 130, 112)
        private val MUTED_DIM = Color.rgb(120, 112, 104)
    }
}
