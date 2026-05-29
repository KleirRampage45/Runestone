/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.R
import com.runestone.app.data.EngineType

data class GameCardInfo(
    val storageName: String,
    val displayName: String,
    val engineType: EngineType,
    val fileCount: Int,
    val isReady: Boolean,
    val isPaused: Boolean = false,
)

/** Tracks the currently selected card's views for single-selection UX */
data class SelectedCardRef(
    var dimOverlay: View?,
    var actionPanel: View?,
    var cardFrame: View?,
)

class HomeScreen(private val context: Context) {

    fun create(
        games: List<GameCardInfo>,
        onPlay: (String) -> Unit,
        onManage: (String) -> Unit,
        onAddGame: () -> Unit,
        onManageAll: () -> Unit,
        onSettings: () -> Unit,
        onApplyFilters: ((engine: EngineType?, search: String, sort: SortMode) -> Unit)? = null,
        activeFilter: EngineType? = null,
        activeSearch: String = "",
        currentSort: SortMode = SortMode.NAME_ASC,
        pausedGame: GameCardInfo? = null,
        onResume: (() -> Unit)? = null,
        onStop: ((String) -> Unit)? = null,
    ): FrameLayout {
        // ── Single-selection tracker ──
        val selectedCard = SelectedCardRef(null, null, null)
        fun deselectCurrent() {
            selectedCard.dimOverlay?.visibility = View.GONE
            selectedCard.actionPanel?.visibility = View.GONE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (selectedCard.cardFrame as? FrameLayout)?.setRenderEffect(null)
            }
            selectedCard.dimOverlay = null
            selectedCard.actionPanel = null
            selectedCard.cardFrame = null
        }
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(3, 3, 4))
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true; overScrollMode = ScrollView.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, dp(56))
        }
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(12), dp(10), dp(18))
        }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Header
        content.addView(makeHeaderRow(activeFilter, activeSearch, currentSort, onApplyFilters))
        content.addView(spacer(dp(8)))

        if (games.isEmpty()) {
            content.addView(spacer(dp(48)))
            content.addView(TextView(context).apply {
                text = "No games yet"; setTextColor(MUTED); textSize = 16f; gravity = Gravity.CENTER
            })
            content.addView(TextView(context).apply {
                text = "Tap + ADD to get started"; setTextColor(MUTED_DIM); textSize = 12f; gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        } else {
            games.forEach { game ->
                content.addView(createHeroCard(game, onPlay, onManage, ::deselectCurrent, selectedCard))
                content.addView(spacer(dp(10)))
            }
        }

        // RESUME bar — side by side STOP + RESUME
        if (pausedGame != null && onResume != null) {
            val barRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.argb(200, 15, 15, 18))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.argb(100, 100, 100, 100))
                }
            }

            // STOP button (red)
            val stopBtn = TextView(context).apply {
                text = "STOP — ${pausedGame.displayName}"
                setTextColor(Color.rgb(240, 120, 120)); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    // ── Glass stop confirmation overlay ──
                    val dimOverlay = FrameLayout(context).apply {
                        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                        setBackgroundColor(Color.argb(180, 0, 0, 0))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setRenderEffect(android.graphics.RenderEffect.createBlurEffect(20f, 20f,
                                android.graphics.Shader.TileMode.CLAMP))
                        }
                        alpha = 0f; animate().alpha(1f).setDuration(250).start()
                        setOnClickListener {} // block clicks through
                    }

                    // Glass card
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        setPadding(dp(24), dp(20), dp(24), dp(20))
                        translationY = 200f; alpha = 0f
                        animate().translationY(0f).alpha(1f).setDuration(350)
                            .setInterpolator(OvershootInterpolator(1.15f)).start()
                    }

                    val cardBg = GradientDrawable().apply {
                        setColor(Color.argb(230, 15, 14, 20))
                        cornerRadius = dp(22).toFloat()
                        setStroke(dp(1), Color.argb(80, 160, 140, 110))
                    }
                    card.background = cardBg

                    // Title
                    card.addView(TextView(context).apply {
                        text = "Stop ${pausedGame.displayName}?"
                        setTextColor(Color.rgb(232, 229, 220)); textSize = 17f
                        typeface = Typeface.create("serif", Typeface.BOLD)
                        gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
                    })

                    // Message
                    card.addView(TextView(context).apply {
                        text = "Any unsaved progress will be lost. Save data on disk is NOT affected."
                        setTextColor(Color.rgb(160, 150, 135)); textSize = 12f
                        gravity = Gravity.CENTER; maxLines = 3
                        setPadding(0, 0, 0, dp(20))
                    })

                    // Button row
                    val btnRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    }

                    // Cancel
                    btnRow.addView(TextView(context).apply {
                        text = "CANCEL"; setTextColor(Color.rgb(160, 150, 135)); textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                        setPadding(dp(24), dp(10), dp(24), dp(10))
                        background = glassBg(dp(10), alpha = 60)
                        setOnClickListener {
                            dimOverlay.animate().alpha(0f).scaleY(0.9f).setDuration(180)
                                .withEndAction {
                                    (dimOverlay.parent as? ViewGroup)?.removeView(dimOverlay)
                                }.start()
                        }
                    })
                    btnRow.addView(spacer(dp(12)))

                    // STOP
                    btnRow.addView(TextView(context).apply {
                        text = "STOP GAME"; setTextColor(Color.rgb(240, 120, 120)); textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                        setPadding(dp(24), dp(10), dp(24), dp(10))
                        background = GradientDrawable().apply {
                            setColor(Color.argb(80, 200, 80, 80))
                            cornerRadius = dp(10).toFloat()
                            setStroke(dp(1), Color.argb(100, 200, 100, 100))
                        }
                        setOnClickListener {
                            dimOverlay.animate().alpha(0f).scaleY(0.9f).setDuration(120)
                                .withEndAction {
                                    (dimOverlay.parent as? ViewGroup)?.removeView(dimOverlay)
                                    onStop?.invoke(pausedGame.storageName)
                                }.start()
                        }
                    })
                    card.addView(btnRow)

                    // Mount
                    val lp = FrameLayout.LayoutParams(
                        (context.resources.displayMetrics.widthPixels * 0.8f).toInt(),
                        WRAP, Gravity.CENTER)
                    dimOverlay.addView(card, lp)

                    val root = (context as? android.app.Activity)?.window?.decorView
                        ?.findViewById<ViewGroup>(android.R.id.content)
                    root?.addView(dimOverlay)
                }
            }
            barRow.addView(stopBtn, LinearLayout.LayoutParams(0, WRAP, 1f))

            // Divider
            barRow.addView(TextView(context).apply {
                text = "  |  "; setTextColor(Color.argb(60, 200, 200, 200))
                textSize = 12f; gravity = Gravity.CENTER
            })

            // RESUME button (green)
            val resumeBtn = TextView(context).apply {
                text = "RESUME"
                setTextColor(Color.rgb(140, 240, 140)); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { onResume() }
            }
            barRow.addView(resumeBtn, LinearLayout.LayoutParams(0, WRAP, 1f))

            root.addView(barRow, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, WRAP, Gravity.BOTTOM).apply {
                setMargins(dp(10), 0, dp(10), dp(56))
            })
        }

        // Dock bar
        val dock = makeDockBar(onAddGame, onManageAll, onSettings)
        root.addView(dock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44), Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(8))
        })
        return root
    }

    // ============================================================
    //  Dock
    // ============================================================

    private fun makeDockBar(onAdd: () -> Unit, onManage: () -> Unit, onSettings: () -> Unit): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = glassBg(dp(24))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        // + ADD icon
        val addIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            setOnClickListener { onAdd() }
            makeLiquid(this)
        }
        bar.addView(iconWrap(addIcon), LinearLayout.LayoutParams(0, MATCH, 1f))
        bar.addView(dockSep())

        // FILES folder icon
        val folderIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_folder)
            setOnClickListener { onManage() }
            makeLiquid(this)
        }
        bar.addView(iconWrap(folderIcon), LinearLayout.LayoutParams(0, MATCH, 1f))
        bar.addView(dockSep())

        // SET gear icon
        val gearIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_gear)
            setOnClickListener { spinAnim(this); onSettings() }
            makeLiquid(this)
        }
        bar.addView(iconWrap(gearIcon), LinearLayout.LayoutParams(0, MATCH, 1f))
        return bar
    }

    private fun iconWrap(view: View): FrameLayout = FrameLayout(context).apply {
        addView(view, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
    }

    private fun dockSep(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(1), MATCH)
        setBackgroundColor(Color.argb(25, 255, 255, 255))
    }

    // ============================================================
    //  Header
    // ============================================================

    private fun makeHeaderRow(
        activeFilter: EngineType?, activeSearch: String, currentSort: SortMode,
        onApplyFilters: ((EngineType?, String, SortMode) -> Unit)?,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(context).apply {
            text = "Runestone"; setTextColor(TEXT); textSize = 26f
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        titleCol.addView(TextView(context).apply {
            text = "multi-engine game launcher"; setTextColor(MUTED); textSize = 10f
        })
        row.addView(titleCol, LinearLayout.LayoutParams(0, WRAP, 1f))

        // Filter+Sort button
        if (onApplyFilters != null) {
            val filterLabel = activeFilter?.let { e ->
                when (e) { EngineType.MV, EngineType.MZ -> "MV/MZ"; EngineType.RGSS_VX_ACE -> "VX/ACE"; EngineType.RGSS_XP -> "XP"; EngineType.EASYRPG -> "2000"; EngineType.RENPY -> "RNPY"; else -> e.name.take(4) }
            } ?: "ALL"
            val searchBadge = if (activeSearch.isNotEmpty()) "  \uD83D\uDD0D" else ""
            val filterBtn = TextView(context).apply {
                text = "$filterLabel$searchBadge  |  A-Z"; setTextColor(ACCENT); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = glassBg(dp(12))
                setOnClickListener { showFilterSortDialog(onApplyFilters, activeFilter, activeSearch, currentSort) }
                makeLiquid(this)
            }
            row.addView(filterBtn)
        }
        return row
    }

    // ============================================================
    //  Filter+Sort overlay — glass panel with blur + animations
    // ============================================================

    private fun showFilterSortDialog(
        onApplyFilters: (EngineType?, String, SortMode) -> Unit,
        initialFilter: EngineType?, initialSearch: String, initialSort: SortMode,
    ) {
        var selectedFilter = initialFilter
        var selectedSort = initialSort
        var searchText = initialSearch

        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels

        // ── Helpers (defined first so all code below can reference them) ──
        fun animTap(v: View) {
            v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(OvershootInterpolator(1.5f)).start()
                }.start()
        }
        fun dismissOverlay(v: FrameLayout, root: ViewGroup?) {
            v.animate().alpha(0f).translationY(60f).setDuration(200).withEndAction {
                root?.removeView(v)
            }.start()
        }
        // doApply will reference searchInput and overlay which are defined later,
        // but lambdas are not executed at definition time, so forward refs work
        lateinit var doApply: () -> Unit

        // ── Overlay root ──
        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(18f, 18f,
                    android.graphics.Shader.TileMode.CLAMP))
            }
            alpha = 0f
            animate().alpha(1f).setDuration(280).start()
        }

        // ── Glass panel ──
        val panelW = (screenW * 0.88f).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 12, 11, 16))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(70, 160, 140, 110))
            }
            translationY = 120f
            alpha = 0f
            animate().translationY(0f).alpha(1f).setDuration(350)
                .setInterpolator(OvershootInterpolator(1.1f)).start()
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = false; overScrollMode = ScrollView.OVER_SCROLL_NEVER
        }
        scroll.addView(panel)

        // ── Title + Done button ──
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "FILTER & SORT"; setTextColor(ACCENT); textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(TextView(context).apply {
                text = "DONE"; setTextColor(Color.rgb(220, 200, 160)); textSize = 11f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(16), dp(6), dp(16), dp(6))
                background = glassBg(dp(8), alpha = 120, accent = true)
                setOnClickListener { animTap(this); doApply() }
                makeLiquid(this)
            })
        }
        panel.addView(titleRow)
        panel.addView(spacer(dp(10)))

        // ── Search input with Clear (X) button ──
        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(2), dp(4), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.argb(30, 255, 255, 255)); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(30, 200, 180, 150))
            }
        }
        val searchInput = EditText(context).apply {
            hint = "Search games..."; setHintTextColor(Color.argb(80, 200, 180, 130))
            setTextColor(TEXT); textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_ACTION_SEARCH
            maxLines = 1; background = null
            setPadding(0, dp(6), 0, dp(6))
            if (initialSearch.isNotEmpty()) { setText(initialSearch); searchText = initialSearch }
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, WRAP, 1f))

        val clearSearchBtn = TextView(context).apply {
            text = "X"; setTextColor(MUTED_DIM); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = if (initialSearch.isNotEmpty()) View.VISIBLE else View.INVISIBLE
            setOnClickListener {
                animTap(this)
                searchInput.setText(""); searchText = ""
                visibility = View.INVISIBLE
            }
            makeLiquid(this)
        }
        searchRow.addView(clearSearchBtn)
        panel.addView(searchRow)
        panel.addView(spacer(dp(12)))

        // Live search
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchText = s?.toString()?.trim() ?: ""
                clearSearchBtn.visibility = if (searchText.isNotEmpty()) View.VISIBLE else View.INVISIBLE
                onApplyFilters(selectedFilter, searchText, selectedSort)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, co: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, be: Int, co: Int) {}
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doApply(); true } else false
        }

        // doApply implementation (uses searchInput, overlay, rootView)
        doApply = {
            val finalSearch = searchInput.text.toString().trim()
            onApplyFilters(selectedFilter, finalSearch, selectedSort)
            val rootView = (context as? android.app.Activity)?.window?.decorView
                ?.findViewById<android.view.ViewGroup>(android.R.id.content)
            dismissOverlay(overlay, rootView)
        }

        // ── Engine filter chips ──
        panel.addView(TextView(context).apply {
            text = "ENGINE"; setTextColor(ACCENT); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        })
        val engines = listOf(null to "All", EngineType.MV to "MV/MZ",
            EngineType.RGSS_VX_ACE to "VX/ACE", EngineType.RGSS_XP to "XP",
            EngineType.EASYRPG to "2000", EngineType.RENPY to "RNPY")
        val engineChips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun rebuildChips() {
            engineChips.removeAllViews()
            engines.forEach { (type, label) ->
                val active = type == selectedFilter
                engineChips.addView(TextView(context).apply {
                    text = label; textSize = 11f
                    setTextColor(if (active) Color.rgb(220, 200, 160) else MUTED)
                    typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                    background = if (active) GradientDrawable().apply {
                        setColor(Color.argb(60, 200, 170, 130)); cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.argb(60, 200, 170, 130))
                    } else GradientDrawable().apply {
                        setColor(Color.argb(20, 200, 180, 150)); cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.argb(25, 255, 255, 255))
                    }
                    setOnClickListener {
                        animTap(this)
                        selectedFilter = if (selectedFilter == type) null else type
                        rebuildChips()
                    }
                    makeLiquid(this)
                })
                engineChips.addView(spacer(4))
            }
        }
        rebuildChips()
        panel.addView(engineChips)

        // ── Sort options ──
        panel.addView(spacer(dp(12)))
        panel.addView(TextView(context).apply {
            text = "SORT"; setTextColor(ACCENT); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
        })
        data class SortOption(val mode: SortMode, val label: String, val icon: String)
        val sorts = listOf(
            SortOption(SortMode.NAME_ASC, "Name (A to Z)", "\u2191A"),
            SortOption(SortMode.NAME_DESC, "Name (Z to A)", "Z\u2193"),
            SortOption(SortMode.RECENT, "Recently Played", "\u23F1"),
            SortOption(SortMode.DATE_ADDED, "Date Added", "\uD83D\uDCC5"),
        )
        val sortContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun rebuildSorts() {
            sortContainer.removeAllViews()
            sorts.forEach { (mode, label, icon) ->
                val active = mode == selectedSort
                sortContainer.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = if (active) GradientDrawable().apply {
                        setColor(Color.argb(40, 200, 170, 130)); cornerRadius = dp(8).toFloat()
                    } else GradientDrawable().apply {
                        setColor(Color.argb(10, 255, 255, 255)); cornerRadius = dp(8).toFloat()
                    }
                    setOnClickListener {
                        animTap(this)
                        selectedSort = mode
                        rebuildSorts()
                        // Slide animation when switching sort
                        sortContainer.animate().translationX(30f).alpha(0.6f).setDuration(80)
                            .withEndAction {
                                sortContainer.translationX = -30f
                                sortContainer.animate().translationX(0f).alpha(1f).setDuration(200)
                                    .setInterpolator(OvershootInterpolator(1.2f)).start()
                            }.start()
                    }
                    makeLiquid(this)
                    // Icon
                    addView(TextView(context).apply {
                        text = if (active) "\u25CF" else icon; textSize = 13f
                        setTextColor(if (active) Color.rgb(220, 200, 160) else MUTED_DIM)
                        setPadding(0, 0, dp(10), 0)
                    })
                    // Label
                    addView(TextView(context).apply {
                        text = label; textSize = 13f
                        setTextColor(if (active) Color.rgb(220, 200, 160) else MUTED)
                        typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    }, LinearLayout.LayoutParams(0, WRAP, 1f))
                    // Checkmark
                    if (active) {
                        addView(TextView(context).apply {
                            text = "\u2713"; setTextColor(Color.rgb(140, 220, 140)); textSize = 14f
                            typeface = Typeface.DEFAULT_BOLD
                        })
                    }
                })
                sortContainer.addView(spacer(2))
            }
        }
        rebuildSorts()
        panel.addView(sortContainer)
        panel.addView(spacer(dp(12)))

        // ── REVERT button ──
        panel.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(TextView(context).apply {
                text = "REVERT"; setTextColor(MUTED); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(20), dp(8), dp(20), dp(8))
                background = glassBg(dp(8), alpha = 60)
                setOnClickListener {
                    animTap(this)
                    selectedFilter = initialFilter
                    selectedSort = initialSort
                    searchInput.setText(initialSearch)
                    searchText = initialSearch
                    clearSearchBtn.visibility = if (initialSearch.isNotEmpty()) View.VISIBLE else View.INVISIBLE
                    rebuildChips(); rebuildSorts()
                    onApplyFilters(initialFilter, initialSearch, initialSort)
                }
                makeLiquid(this)
            })
        })

        // ── Mount overlay ──
        val rootView = (context as? android.app.Activity)?.window?.decorView
            ?.findViewById<android.view.ViewGroup>(android.R.id.content)
        overlay.addView(scroll, FrameLayout.LayoutParams(panelW, WRAP, Gravity.CENTER).apply {
            setMargins(0, dp(30), 0, dp(30))
        })
        rootView?.addView(overlay)

        // Backdrop tap → dismiss
        overlay.setOnClickListener {
            dismissOverlay(overlay, rootView)
        }
        // Allow scroll touch without dismissing
        scroll.setOnTouchListener { _, _ -> false }
    }

    // ============================================================
    //  Hero card — name below, tap shows overlay
    // ============================================================

    private fun createHeroCard(
        game: GameCardInfo, onPlay: (String) -> Unit, onManage: (String) -> Unit,
        deselectAll: () -> Unit,
        selected: SelectedCardRef,
    ): LinearLayout {
        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }

        val w = context.resources.displayMetrics.widthPixels
        val cardW = (w * 0.88f).toInt()
        val cardH = (cardW * 0.56f).toInt()

        // Wrapper stacks card + overlay — blur only hits card
        val cardWrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(cardW, cardH)
        }
        cardContainer.addView(cardWrapper)

        // Card frame (gets blurred)
        val cardFrame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            background = GradientDrawable().apply {
                setColor(cardColor(game.engineType))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(50, 100, 90, 80))
            }
        }
        cardWrapper.addView(cardFrame)

        // Engraved engine label (inside cardFrame, gets blurred)
        cardFrame.addView(TextView(context).apply {
            text = game.engineType.label; setTextColor(Color.argb(60, 255, 255, 255))
            textSize = 36f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        })

        // Dim overlay — light tint on top
        val dimOverlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.argb(100, 0, 0, 0))
            visibility = View.GONE
        }
        cardWrapper.addView(dimOverlay)

        // Action buttons
        val actionPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        dimOverlay.addView(actionPanel)

        // PLAY / RESUME — fixed symmetric width
        val btnLabel = if (game.isPaused) "RESUME" else "PLAY"
        val btnW = dp(120)
        val playBtn = TextView(context).apply {
            text = btnLabel; setTextColor(Color.rgb(220, 200, 160)); textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = glassBg(dp(6), alpha = 100, accent = !game.isPaused)
            if (game.isPaused) (background as GradientDrawable).setColor(Color.argb(120, 30, 60, 30))
            setOnClickListener { onPlay(game.storageName) }
            makeLiquid(this)
        }
        actionPanel.addView(playBtn, LinearLayout.LayoutParams(btnW, WRAP))
        actionPanel.addView(spacer(dp(14)))

        // OPTIONS — same width
        val optsBtn = TextView(context).apply {
            text = "OPTIONS"; setTextColor(Color.rgb(200, 180, 150)); textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = glassBg(dp(6), alpha = 80)
            setOnClickListener { onManage(game.storageName) }
            makeLiquid(this)
        }
        actionPanel.addView(optsBtn, LinearLayout.LayoutParams(btnW, WRAP))

        // Tap wrapper → toggle overlay + blur (single-selection)
        cardWrapper.setOnClickListener {
            if (dimOverlay.visibility == View.GONE) {
                // Deselect previously selected card first
                deselectAll()
                // Select this card
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    cardFrame.setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(32f, 32f,
                            android.graphics.Shader.TileMode.CLAMP))
                }
                dimOverlay.visibility = View.VISIBLE
                actionPanel.visibility = View.VISIBLE
                actionPanel.scaleX = 0.9f; actionPanel.scaleY = 0.9f
                actionPanel.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(OvershootInterpolator(1.2f)).start()
                // Track this card as selected
                selected.dimOverlay = dimOverlay
                selected.actionPanel = actionPanel
                selected.cardFrame = cardFrame
            } else {
                // Deselect this card (tap same card again)
                deselectAll()
            }
        }

        // Game name below card
        cardContainer.addView(TextView(context).apply {
            text = game.displayName; setTextColor(TEXT); textSize = 15f
            typeface = Typeface.create("serif", Typeface.BOLD)
            gravity = Gravity.CENTER; maxLines = 1
            setPadding(dp(4), dp(8), dp(4), 0)
        })
        cardContainer.addView(TextView(context).apply {
            text = "${game.fileCount} files  |  ${game.engineType.label}"
            setTextColor(MUTED); textSize = 10f; gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })

        return cardContainer
    }

    private fun cardColor(engine: EngineType): Int = when (engine) {
        EngineType.MV, EngineType.MZ -> Color.rgb(30, 35, 28)
        EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> Color.rgb(35, 28, 32)
        EngineType.EASYRPG -> Color.rgb(28, 32, 35)
        EngineType.RENPY -> Color.rgb(32, 28, 35)
        else -> Color.rgb(28, 28, 28)
    }

    // ============================================================
    //  Glass background
    // ============================================================

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha,
                if (accent) 50 else 22, if (accent) 40 else 20, if (accent) 30 else 26))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(if (accent) 80 else 45,
                if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
        }

    // ============================================================
    //  Liquid Glass touch — zoom + parallax on press-and-move
    // ============================================================

    private fun makeLiquid(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f
                    val cy = v.height / 2f
                    val dx = (event.x - cx) * 0.06f
                    val dy = (event.y - cy) * 0.06f
                    v.translationX = dx
                    v.translationY = dy
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f)
                        .translationX(0f).translationY(0f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator(1.6f))
                        .start()
                }
            }
            false // let click through
        }
    }

    // ============================================================
    //  Spin animation for gear icon
    // ============================================================

    private fun spinAnim(view: View) {
        view.animate().cancel()
        // Scale down + spin
        view.animate().scaleX(0.85f).scaleY(0.85f).rotationBy(180f).setDuration(120)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).rotationBy(180f).setDuration(280)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            }.start()
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private fun spacer(h: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, if (h > 0) h else 1)
    }
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(100, 95, 85)
        val ACCENT = Color.rgb(207, 174, 126)
    }
}

enum class SortMode { NAME_ASC, NAME_DESC, RECENT, DATE_ADDED }
