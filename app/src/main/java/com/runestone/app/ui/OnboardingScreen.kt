package com.runestone.app.ui

import android.content.ClipboardManager
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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class OnboardingScreen(private val context: Context) {

    data class OnboardingResult(
        val locale: String,
        val selectedEngines: Set<String>,
        val rawgApiKey: String,
        val installRtp: Boolean,
    )

    fun create(onComplete: (OnboardingResult) -> Unit): FrameLayout {
        val root = FrameLayout(context).apply {
            setBackgroundColor(BACKGROUND)
        }

        val pageContainer = FrameLayout(context)
        root.addView(pageContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val systemLocale = detectSystemLocale()
        var currentLocale = systemLocale
        var selectedEngines = mutableSetOf("mkxp-z", "onscripter")
        var rawgApiKey = ""
        var installRtp = false

        fun localizedText(key: String): String {
            val config = context.resources.configuration
            val localeObj = Locale(currentLocale)
            val localizedConfig = android.content.res.Configuration(config).apply { setLocale(localeObj) }
            val localizedRes = context.createConfigurationContext(localizedConfig).resources
            val id = localizedRes.getIdentifier(key, "string", context.packageName)
            return if (id != 0) localizedRes.getString(id) else key
        }

        fun showPage(page: View) {
            pageContainer.removeAllViews()
            page.alpha = 0f
            page.translationX = context.resources.displayMetrics.widthPixels.toFloat()
            pageContainer.addView(page, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            page.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }

        fun nextButton(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
            text = label
            setTextColor(Color.rgb(3, 3, 4))
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(UiKit.dp(context, 20), UiKit.dp(context, 12), UiKit.dp(context, 20), UiKit.dp(context, 12))
            background = GradientDrawable().apply {
                setColor(Theme.active.accent)
                cornerRadius = UiKit.dp(context, 10).toFloat()
            }
            setOnClickListener {
                UiKit.animTap(this)
                onClick()
            }
        }

        lateinit var step2: () -> View
        lateinit var step2a: () -> View
        lateinit var step3: () -> View
        lateinit var step4: () -> View

        step2 = {
            val step = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiKit.dp(context, 28), UiKit.dp(context, 60), UiKit.dp(context, 28), UiKit.dp(context, 28))
            }

            step.addView(TextView(context).apply {
                text = localizedText("onboarding_engines_title")
                setTextColor(Theme.active.accent)
                textSize = 24f
                typeface = Typeface.create("serif", Typeface.BOLD)
            })
            step.addView(UiKit.spacer(context, 10))
            step.addView(TextView(context).apply {
                text = localizedText("onboarding_engines_desc")
                setTextColor(Theme.MUTED)
                textSize = 13f
            })
            step.addView(UiKit.spacer(context, 20))

            val engines = listOf(
                "mkxp-z" to localizedText("onboarding_engine_mkxpz"),
                "easyrpg" to localizedText("onboarding_engine_easyrpg"),
                "onscripter" to localizedText("onboarding_engine_onscripter"),
                "renpy" to localizedText("onboarding_engine_renpy"),
                "godot" to localizedText("onboarding_engine_godot"),
            )
            for ((id, label) in engines) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(UiKit.dp(context, 4), UiKit.dp(context, 6), UiKit.dp(context, 4), UiKit.dp(context, 6))
                }
                val toggle = Switch(context).apply {
                    isChecked = id in selectedEngines
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedEngines.add(id) else selectedEngines.remove(id)
                    }
                }
                row.addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = UiKit.dp(context, 10) })
                row.addView(TextView(context).apply {
                    text = label
                    setTextColor(Theme.TEXT)
                    textSize = 13f
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                step.addView(row)
            }

            val spacer = View(context)
            step.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

            val navRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            navRow.addView(nextButton(localizedText("onboarding_next")) {
                showPage(if (selectedEngines.contains("mkxp-z")) step2a() else step3())
            })
            step.addView(navRow)
            step
        }

        step2a = {
            val step = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiKit.dp(context, 28), UiKit.dp(context, 60), UiKit.dp(context, 28), UiKit.dp(context, 28))
            }

            step.addView(TextView(context).apply {
                text = localizedText("onboarding_rtp_title")
                setTextColor(Theme.active.accent)
                textSize = 24f
                typeface = Typeface.create("serif", Typeface.BOLD)
            })
            step.addView(UiKit.spacer(context, 10))
            step.addView(TextView(context).apply {
                text = localizedText("onboarding_rtp_desc")
                setTextColor(Theme.MUTED)
                textSize = 13f
                setLineSpacing(0f, 1.4f)
            })
            step.addView(UiKit.spacer(context, 20))

            val rtpRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(UiKit.dp(context, 4), UiKit.dp(context, 6), UiKit.dp(context, 4), UiKit.dp(context, 6))
            }
            val rtpToggle = Switch(context).apply {
                isChecked = false
                setOnCheckedChangeListener { _, checked -> installRtp = checked }
            }
            rtpRow.addView(rtpToggle, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = UiKit.dp(context, 10) })
            rtpRow.addView(TextView(context).apply {
                text = localizedText("onboarding_rtp_toggle")
                setTextColor(Theme.TEXT)
                textSize = 13f
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            step.addView(rtpRow)

            val spacer = View(context)
            step.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            val navRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            navRow.addView(nextButton(localizedText("onboarding_next")) { showPage(step3()) })
            step.addView(navRow)
            step
        }

        step3 = {
            val step = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(UiKit.dp(context, 28), UiKit.dp(context, 60), UiKit.dp(context, 28), UiKit.dp(context, 28))
            }

            step.addView(TextView(context).apply {
                text = localizedText("onboarding_scraping_title")
                setTextColor(Theme.active.accent)
                textSize = 24f
                typeface = Typeface.create("serif", Typeface.BOLD)
            })
            step.addView(UiKit.spacer(context, 10))
            step.addView(TextView(context).apply {
                text = localizedText("onboarding_scraping_desc")
                setTextColor(Theme.MUTED)
                textSize = 13f
                setLineSpacing(0f, 1.4f)
            })
            step.addView(UiKit.spacer(context, 8))

            val rawgLink = TextView(context).apply {
                text = "https://rawg.io/register"
                setTextColor(Theme.active.accentBright)
                textSize = 13f
                paint.isUnderlineText = true
                setOnClickListener {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rawg.io/register")))
                }
            }
            step.addView(rawgLink)
            step.addView(UiKit.spacer(context, 12))

            step.addView(TextView(context).apply {
                text = localizedText("onboarding_scraping_input_label")
                setTextColor(Theme.MUTED)
                textSize = 12f
            })
            step.addView(UiKit.spacer(context, 6))

            val inputRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val apiInput = EditText(context).apply {
                hint = localizedText("onboarding_scraping_hint")
                setHintTextColor(Theme.MUTED_DIM)
                setTextColor(Theme.TEXT)
                textSize = 14f
                setPadding(UiKit.dp(context, 12), UiKit.dp(context, 10), UiKit.dp(context, 12), UiKit.dp(context, 10))
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 255, 255, 255))
                    cornerRadius = UiKit.dp(context, 8).toFloat()
                    setStroke(UiKit.dp(context, 1), Theme.MUTED_DIM)
                }
            }
            inputRow.addView(apiInput, LinearLayout.LayoutParams(0, WRAP, 1f))

            val pasteBtn = TextView(context).apply {
                text = localizedText("onboarding_paste")
                setTextColor(Color.rgb(3, 3, 4))
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(UiKit.dp(context, 14), UiKit.dp(context, 10), UiKit.dp(context, 14), UiKit.dp(context, 10))
                background = GradientDrawable().apply {
                    setColor(Theme.active.accent)
                    cornerRadius = UiKit.dp(context, 8).toFloat()
                }
                setOnClickListener {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
                    val text = clip?.getItemAt(0)?.text?.toString()
                    if (text != null && text.isNotBlank()) {
                        apiInput.setText(text)
                        apiInput.setSelection(text.length)
                        Toast.makeText(context, localizedText("onboarding_pasted"), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, localizedText("onboarding_clipboard_empty"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            inputRow.addView(pasteBtn, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                leftMargin = UiKit.dp(context, 8)
            })
            step.addView(inputRow)

            val spacer = View(context)
            step.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

            val navRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            navRow.addView(nextButton(localizedText("onboarding_next")) {
                rawgApiKey = apiInput.text.toString().trim()
                showPage(step4())
            })
            step.addView(navRow)
            step
        }

        step4 = {
            val step = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(UiKit.dp(context, 28), UiKit.dp(context, 60), UiKit.dp(context, 28), UiKit.dp(context, 28))
            }

            step.addView(TextView(context).apply {
                text = localizedText("onboarding_ready_title")
                setTextColor(Theme.active.accent)
                textSize = 28f
                typeface = Typeface.create("serif", Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            step.addView(UiKit.spacer(context, 12))
            step.addView(TextView(context).apply {
                text = localizedText("onboarding_ready_desc")
                setTextColor(Theme.MUTED)
                textSize = 14f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.4f)
            })

            val spacer = View(context)
            step.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

            val navRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            navRow.addView(nextButton(localizedText("onboarding_start")) {
                onComplete(OnboardingResult(
                    locale = currentLocale,
                    selectedEngines = selectedEngines.toSet(),
                    rawgApiKey = rawgApiKey,
                    installRtp = installRtp,
                ))
            })
            step.addView(navRow)
            step
        }

        // ── Build Step 1 ──
        val step1 = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(context, 28), UiKit.dp(context, 60), UiKit.dp(context, 28), UiKit.dp(context, 28))
        }

        step1.addView(TextView(context).apply {
            text = localizedText("onboarding_welcome_title")
            setTextColor(Theme.active.accent)
            textSize = 28f
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        step1.addView(UiKit.spacer(context, 12))

        step1.addView(TextView(context).apply {
            text = localizedText("onboarding_welcome_desc")
            setTextColor(Theme.MUTED)
            textSize = 14f
            setLineSpacing(0f, 1.4f)
        })
        step1.addView(UiKit.spacer(context, 36))

        step1.addView(TextView(context).apply {
            text = localizedText("onboarding_language")
            setTextColor(Theme.TEXT)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        step1.addView(UiKit.spacer(context, 10))

        val langRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val langCodes = listOf("en" to "English", "es" to "Español", "pt" to "Português")
        for ((code, label) in langCodes) {
            val btn = TextView(context).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(UiKit.dp(context, 16), UiKit.dp(context, 10), UiKit.dp(context, 16), UiKit.dp(context, 10))
                setTextColor(Theme.active.accent)
                val bgColor = Color.argb(30, Color.red(Theme.active.accent), Color.green(Theme.active.accent), Color.blue(Theme.active.accent))
                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = UiKit.dp(context, 10).toFloat()
                    setStroke(UiKit.dp(context, 1), Color.argb(60, Color.red(Theme.active.accent), Color.green(Theme.active.accent), Color.blue(Theme.active.accent)))
                }
                if (code == currentLocale) alpha = 1f else alpha = 0.5f
                setOnClickListener {
                    currentLocale = code
                    val children = langRow.getChildren()
                    children.forEach { it.alpha = 0.5f }
                    alpha = 1f
                    UiKit.animTap(this)
                }
            }
            langRow.addView(btn, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                leftMargin = UiKit.dp(context, 4)
                rightMargin = UiKit.dp(context, 4)
            })
        }
        step1.addView(langRow)

        val spacer = View(context)
        step1.addView(spacer, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        navRow.addView(nextButton(localizedText("onboarding_next")) { showPage(step2()) })
        step1.addView(navRow)

        showPage(step1)
        return root
    }

    private fun detectSystemLocale(): String {
        val lang = Locale.getDefault().language
        return when {
            lang.startsWith("es") -> "es"
            lang.startsWith("pt") -> "pt"
            else -> "en"
        }
    }

    private fun ViewGroup.getChildren(): List<View> = (0 until childCount).map { getChildAt(it) }

    private companion object {
        val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val BACKGROUND = Color.rgb(3, 3, 4)
        val Theme = com.runestone.app.ui.Theme
    }
}
