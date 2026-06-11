/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.ui

import android.animation.ObjectAnimator
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
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.R
import com.runestone.app.data.EngineType
import com.runestone.app.data.UIMode
import com.runestone.app.ui.carousel.AmbientGlowView
import com.runestone.app.ui.carousel.Carousel3DScrollEffects
import com.runestone.app.ui.carousel.DetailPanel
import com.runestone.app.ui.carousel.GameCarouselAdapter
import com.runestone.app.ui.carousel.InspectOverlay
import com.runestone.app.ui.carousel.VignetteOverlay
import com.runestone.app.ui.carousel.GrainOverlay
import com.runestone.app.ui.carousel.PageIndicator
import com.runestone.app.ui.carousel.DepthOfFieldController
import com.runestone.app.ui.carousel.BloomOverlay
import com.runestone.app.ui.carousel.GameColorExtractor
import com.runestone.app.ui.carousel.ItemTouchHelperCallback
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

data class GameCardInfo(
    val storageName: String,
    val displayName: String,
    val engineType: EngineType,
    val fileCount: Int,
    val fileSize: Long = 0L,
    val totalPlayTime: Long = 0L,      // total seconds played
    val lastPlayedTimestamp: Long = 0L, // epoch millis
    val isReady: Boolean,
    val isPaused: Boolean = false,
    val coverUrl: String? = null,
    val metadataDeveloper: String = "",
    val metadataGenres: String = "",
    val metadataYear: String = "",
)

enum class HomeCardLayout(val label: String) {
    GRID_2("2"),
    GRID_3("3"),
    WIDE("=");

    fun next(): HomeCardLayout = when (this) {
        GRID_2 -> GRID_3
        GRID_3 -> WIDE
        WIDE -> GRID_2
    }
}

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
        onBrowse: (() -> Unit)? = null,
        onManageAll: () -> Unit,
        onSettings: () -> Unit,
        onApplyFilters: ((engine: EngineType?, search: String, sort: SortMode) -> Unit)? = null,
        activeFilter: EngineType? = null,
        activeSearch: String = "",
        currentSort: SortMode = SortMode.NAME_ASC,
        pausedGame: GameCardInfo? = null,
        onResume: (() -> Unit)? = null,
        onStop: ((String) -> Unit)? = null,
        uiMode: UIMode = UIMode.GRID,
        cardLayout: HomeCardLayout = HomeCardLayout.GRID_2,
        onCardLayoutChanged: ((HomeCardLayout) -> Unit)? = null,
        showGameName: Boolean = true,
        onLongPress: ((GameCardInfo) -> Unit)? = null,
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
        val bottomClearance = dp(76)

        val mainColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(mainColumn, FrameLayout.LayoutParams(MATCH, MATCH))

        val stickyHeader = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.argb(232, 3, 3, 4))
                setStroke(dp(1), Color.argb(24, 207, 174, 126))
            }
        }
        mainColumn.addView(stickyHeader, LinearLayout.LayoutParams(MATCH, WRAP))

        // Pre-build game cards so they can be placed in a RecyclerView.
        // Cards are built once using the existing hero-card rendering code,
        // then hosted in a RecyclerView for proper measured layout pass.
        val columnCount = if (cardLayout == HomeCardLayout.GRID_3) 3 else if (cardLayout == HomeCardLayout.WIDE) 1 else 2
        val screenW = context.resources.displayMetrics.widthPixels
        val gap = dp(8)
        val available = screenW - dp(20) - gap * (columnCount - 1)
        val cardW = available / columnCount

        // Build all cards upfront (same as before, same rendering code)
        val prebuiltCards = mutableListOf<LinearLayout>()
        if (games.isNotEmpty() && uiMode != UIMode.CAROUSEL_3D) {
            if (uiMode == UIMode.TILES) {
                // TILES: build each tile as a separate card — RecyclerView handles 2-col grid
                val tileW = ((screenW - dp(10) * 3) / 2).toInt()
                games.forEach { game ->
                    val tile = createHeroCard(game, onPlay, onManage, ::deselectCurrent, selectedCard,
                        tileW, true, false, null)
                    prebuiltCards.add(tile)
                }
            } else {
                // GRID: build hero cards for each game
                val compact = cardLayout != HomeCardLayout.WIDE
                games.forEach { game ->
                    val card = createHeroCard(game, onPlay, onManage, ::deselectCurrent, selectedCard,
                        if (columnCount > 1) cardW else (screenW * 0.88f).toInt(), compact, showGameName, onLongPress)
                    prebuiltCards.add(card)
                }
            }
        }

        val recyclerView = RecyclerView(context).apply {
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, bottomClearance)
            clipToPadding = false
            isNestedScrollingEnabled = true
            layoutManager = when {
                uiMode == UIMode.TILES -> GridLayoutManager(context, 2)
                uiMode == UIMode.LIST -> LinearLayoutManager(context)
                else -> GridLayoutManager(context, columnCount)
            }
            adapter = PrebuiltCardAdapter(prebuiltCards)
        }
        mainColumn.addView(recyclerView, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Header
        stickyHeader.addView(makeHeaderRow(
            activeFilter, activeSearch, currentSort, onApplyFilters, cardLayout, onCardLayoutChanged,
        ))
        stickyHeader.addView(spacer(dp(8)))

        // Standalone search bar
        if (onApplyFilters != null && games.isNotEmpty()) {
            val searchBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = glassBg(dp(14))
            }

            val searchIcon = TextView(context).apply {
                text = "\u26B2" // magnifying glass
                setTextColor(MUTED)
                textSize = 16f
                setPadding(0, 0, dp(8), 0)
            }
            searchBar.addView(searchIcon)

            val searchInput = EditText(context).apply {
                hint = "Search games..."
                setHintTextColor(MUTED_DIM)
                setTextColor(TEXT)
                textSize = 14f
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                background = null
                setPadding(0, dp(4), 0, dp(4))
                if (activeSearch.isNotEmpty()) {
                    setText(activeSearch)
                    setSelection(activeSearch.length)
                }
            }
            searchBar.addView(searchInput, LinearLayout.LayoutParams(0, WRAP, 1f))

            val clearBtn = TextView(context).apply {
                text = "\u2715" // X
                setTextColor(MUTED)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(4), dp(8), dp(4))
                visibility = if (activeSearch.isNotEmpty()) View.VISIBLE else View.GONE
                setOnClickListener {
                    searchInput.setText("")
                    visibility = View.GONE
                    onApplyFilters(activeFilter, "", currentSort)
                    makeLiquid(this)
                }
            }
            searchBar.addView(clearBtn)

            val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var searchRunnable: Runnable? = null
            
            searchInput.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString()?.trim() ?: ""
                    clearBtn.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    searchRunnable = Runnable {
                        onApplyFilters(activeFilter, query, currentSort)
                    }
                    searchHandler.postDelayed(searchRunnable!!, 300)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            searchInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    val query = searchInput.text.toString().trim()
                    onApplyFilters(activeFilter, query, currentSort)
                    searchInput.clearFocus()
                    true
                } else false
            }

            stickyHeader.addView(searchBar)
            stickyHeader.addView(spacer(dp(4)))
        }

        if (games.isEmpty()) {
            val emptyCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = context.resources.displayMetrics.heightPixels / 2
                addView(spacer(dp(48)))
                addView(TextView(context).apply {
                    text = "No games yet"; setTextColor(MUTED); textSize = 16f; gravity = Gravity.CENTER
                })
                addView(TextView(context).apply {
                    text = "Tap + ADD to get started"; setTextColor(MUTED_DIM); textSize = 12f; gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, 0)
                })
            }
            prebuiltCards.add(emptyCard)
        } else if (uiMode == UIMode.LIST) {
            prebuiltCards.clear()
            prebuiltCards.add(renderListLayout(games, onPlay, onManage) as LinearLayout)
        } else if (uiMode == UIMode.CAROUSEL_3D) {
            prebuiltCards.clear()
            root.addView(renderCarousel3D(
                games = games,
                onPlay = onPlay,
                onManage = onManage,
            ), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
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
                        alpha = 0f; animate().alpha(1f).setDuration(250).start()
                    }

                    // Glass card — COMPACT
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(18), dp(16), dp(18), dp(16))
                        background = GradientDrawable().apply {
                            setColor(Color.argb(230, 15, 14, 20))
                            cornerRadius = dp(22).toFloat()
                            setStroke(dp(1), Color.argb(80, 160, 140, 110))
                        }
                        translationY = 200f; alpha = 0f
                        animate().translationY(0f).alpha(1f).setDuration(350)
                            .setInterpolator(OvershootInterpolator(1.15f)).start()
                    }

                    // Icon row
                    card.addView(TextView(context).apply {
                        text = "STOP GAME"; setTextColor(Color.rgb(240, 120, 120)); textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                        setPadding(0, 0, 0, dp(4))
                    })

                    // Title
                    card.addView(TextView(context).apply {
                        text = pausedGame.displayName
                        setTextColor(Color.rgb(232, 229, 220)); textSize = 16f
                        typeface = Typeface.create("serif", Typeface.BOLD)
                        gravity = Gravity.CENTER
                    })

                    // Message
                    card.addView(TextView(context).apply {
                        text = "Unsaved progress will be lost. Saves are safe."
                        setTextColor(Color.rgb(160, 150, 135)); textSize = 11f
                        gravity = Gravity.CENTER; maxLines = 2
                        setPadding(0, dp(6), 0, dp(16))
                    })

                    // Button row — equal width, side by side
                    val btnRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    }

                    // Cancel
                    btnRow.addView(TextView(context).apply {
                        text = "CANCEL"; setTextColor(MUTED); textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        background = glassBg(dp(10), alpha = 60)
                        setOnClickListener {
                            dimOverlay.animate().alpha(0f).scaleY(0.9f).setDuration(180)
                                .withEndAction {
                                    (dimOverlay.parent as? ViewGroup)?.removeView(dimOverlay)
                                }.start()
                        }
                        makeLiquid(this)
                    }, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        setMargins(0, 0, dp(4), 0)
                    })

                    // STOP
                    btnRow.addView(TextView(context).apply {
                        text = "CLOSE GAME"; setTextColor(Color.rgb(240, 120, 120)); textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        background = GradientDrawable().apply {
                            setColor(Color.argb(60, 200, 80, 80))
                            cornerRadius = dp(10).toFloat()
                            setStroke(dp(1), Color.argb(80, 200, 100, 100))
                        }
                        setOnClickListener {
                            // Set kill flag so GameActivity self-destructs
                            val prefs = context.getSharedPreferences("runestone", Context.MODE_PRIVATE)
                            prefs.edit().putString("kill_game", pausedGame.storageName).apply()
                            dimOverlay.animate().alpha(0f).scaleY(0.9f).setDuration(120)
                                .withEndAction {
                                    (dimOverlay.parent as? ViewGroup)?.removeView(dimOverlay)
                                    onStop?.invoke(pausedGame.storageName)
                                }.start()
                        }
                        makeLiquid(this)
                    }, LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                        setMargins(dp(4), 0, 0, 0)
                    })

                    card.addView(btnRow)

                    // Mount
                    val lp = FrameLayout.LayoutParams(
                        (context.resources.displayMetrics.widthPixels * 0.78f).toInt(),
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
                setMargins(dp(10), 0, dp(10), dp(72))
            })
        }

        return root
    }

    // ============================================================
    //  Dock
    // ============================================================

    fun createDockBar(onHome: () -> Unit, onAdd: () -> Unit, onBrowse: () -> Unit, onManage: () -> Unit, onSettings: () -> Unit): LinearLayout {
        var selectedItem: FrameLayout? = null
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(52, 22, 21, 28))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.argb(60, 200, 190, 170))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            clipChildren = false
            clipToPadding = false
        }
        fun selectDockItem(item: FrameLayout) {
            selectedItem?.apply {
                isSelected = false
                background = null
            }
            item.isSelected = true
            item.background = glassBg(dp(14), alpha = 70, accent = true)
            selectedItem = item
        }

        // HOME icon
        val homeIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_home)
        }
        bar.addView(dockItem(homeIcon, "Home") {
            selectDockItem(it)
            onHome()
        })

        // STORE icon (moved left)
        val storeIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_store)
        }
        bar.addView(dockItem(storeIcon, "Store") {
            selectDockItem(it)
            onBrowse()
        })

        // + ADD icon (center position)
        val addIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
        }
        bar.addView(dockItem(addIcon, "Add game") {
            selectDockItem(it)
            onAdd()
        })

        // FILES folder icon
        val folderIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_folder)
        }
        bar.addView(dockItem(folderIcon, "Manage files") {
            selectDockItem(it)
            onManage()
        })

        // SET gear icon
        val gearIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_gear)
        }
        val gearRotator = ObjectAnimator.ofFloat(gearIcon, View.ROTATION, 0f, 360f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        gearIcon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { if (!Theme.isReducedMotion(context)) gearRotator.start() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    gearRotator.cancel()
                    if (!Theme.isReducedMotion(context)) {
                        gearIcon.animate().cancel()
                        gearIcon.animate()
                            .rotation(0f)
                            .setDuration(250)
                            .setInterpolator(OvershootInterpolator(1.6f))
                            .start()
                    } else {
                        gearIcon.rotation = 0f
                    }
                }
            }
            false
        }
        // Ensure animation stops when view is detached
        gearIcon.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                gearRotator.cancel()
                gearIcon.animate().cancel()
                gearIcon.rotation = 0f
            }
        })
        bar.addView(dockItem(gearIcon, "Settings") {
            gearRotator.cancel()
            gearIcon.animate().cancel()
            gearIcon.rotation = 0f
            selectDockItem(it)
            onSettings()
        })
        return bar
    }

    private fun dockItem(icon: View, label: String, onClick: (FrameLayout) -> Unit): FrameLayout = FrameLayout(context).apply {
        minimumWidth = dp(48); minimumHeight = dp(48)
        layoutParams = LinearLayout.LayoutParams(0, MATCH, 1f)
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(icon, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        contentDescription = label
        isClickable = true
        setOnClickListener { onClick(this) }
        makeLiquid(this)
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
        cardLayout: HomeCardLayout,
        onCardLayoutChanged: ((HomeCardLayout) -> Unit)?,
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
            val searchBadge = if (activeSearch.isNotEmpty()) " [S]" else ""
            val sortLabel = when (currentSort) {
                SortMode.NAME_ASC -> "A-Z"
                SortMode.NAME_DESC -> "Z-A"
                SortMode.RECENT -> "REC"
                SortMode.DATE_ADDED -> "NEW"
            }
            val filterBtn = TextView(context).apply {
                text = " \u25A4 $filterLabel$searchBadge  |  $sortLabel "; setTextColor(ACCENT); textSize = 13f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = glassBg(dp(12))
                setOnClickListener { showFilterSortDialog(onApplyFilters, activeFilter, activeSearch, currentSort) }
                makeLiquid(this)
            }
            row.addView(filterBtn)
        }
        if (onCardLayoutChanged != null) {
            row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
            val gridIcon = createGridIcon(cardLayout).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val iconWrapper = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                background = glassBg(dp(12))
                addView(gridIcon, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
                setOnClickListener {
                    val next = cardLayout.next()
                    onCardLayoutChanged(next)
                    gridIcon.invalidate()
                }
                makeLiquid(this)
            }
            row.addView(iconWrapper)
        }
        return row
    }

    // ============================================================
    //  Grid Layout Icon - small custom view showing grid pattern
    // ============================================================

    private fun createGridIcon(layout: HomeCardLayout): View = object : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            paint.color = ACCENT
            paint.style = android.graphics.Paint.Style.FILL
            strokePaint.color = Color.argb(100, 255, 255, 255)
            strokePaint.style = android.graphics.Paint.Style.STROKE
            strokePaint.strokeWidth = dp(1).toFloat()
            val w = width.toFloat(); val h = height.toFloat()
            val pad = dp(4).toFloat(); val gap = dp(2).toFloat()
            when (layout) {
                HomeCardLayout.WIDE -> {
                    val cw = w - pad * 2; val ch = (h - pad * 2 - gap) / 2
                    val r = dp(3).toFloat()
                    canvas.drawRoundRect(pad, pad, pad + cw, pad + ch, r, r, paint)
                    canvas.drawRoundRect(pad, pad + ch + gap, pad + cw, pad + ch * 2 + gap, r, r, paint)
                }
                HomeCardLayout.GRID_2 -> {
                    val cw = (w - pad * 2 - gap) / 2; val ch = (h - pad * 2 - gap) / 2
                    val r = dp(3).toFloat()
                    // Top row
                    canvas.drawRoundRect(pad, pad, pad + cw, pad + ch, r, r, paint)
                    canvas.drawRoundRect(pad + cw + gap, pad, pad + cw * 2 + gap, pad + ch, r, r, paint)
                    // Bottom row
                    canvas.drawRoundRect(pad, pad + ch + gap, pad + cw, pad + ch * 2 + gap, r, r, paint)
                    canvas.drawRoundRect(pad + cw + gap, pad + ch + gap, pad + cw * 2 + gap, pad + ch * 2 + gap, r, r, paint)
                }
                HomeCardLayout.GRID_3 -> {
                    val cw = (w - pad * 2 - gap * 2) / 3
                    val ch = h - pad * 2
                    val r = dp(3).toFloat()
                    for (i in 0..2) {
                        canvas.drawRoundRect(pad + (cw + gap) * i, pad, pad + (cw + gap) * i + cw, pad + ch, r, r, paint)
                    }
                }
            }
        }
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
        fun animTap(v: View) { if (Theme.isReducedMotion(context)) return
            v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(OvershootInterpolator(1.5f)).start()
                }.start()
        }
        fun dismissOverlay(v: FrameLayout, root: ViewGroup?) {
            v.animate().alpha(0f).translationY(120f).setDuration(200).withEndAction {
                root?.removeView(v)
            }.start()
        }
        // doApply will reference searchInput and overlay which are defined later,
        // but lambdas are not executed at definition time, so forward refs work
        lateinit var doApply: () -> Unit

        // ── Overlay root ──
        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
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

        // ── Title ──
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "FILTER & SORT"; setTextColor(ACCENT); textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
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
            hint = "Search games..."; setHintTextColor(Color.argb(120, 200, 180, 130))
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
            minimumWidth = dp(48); minimumHeight = dp(48)
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

        // Live search — debounced, accumulates locally, applies on DONE
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchText = s?.toString()?.trim() ?: ""
                clearSearchBtn.visibility = if (searchText.isNotEmpty()) View.VISIBLE else View.INVISIBLE
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
            SortOption(SortMode.NAME_ASC, "Name (A to Z)", "A>Z"),
            SortOption(SortMode.NAME_DESC, "Name (Z to A)", "Z>A"),
            SortOption(SortMode.RECENT, "Recently Played", "[>]"),
            SortOption(SortMode.DATE_ADDED, "Date Added", "[+]"),
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

        // ── CLEAR + DONE buttons (swapped) ──
        panel.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            // CLEAR
            addView(TextView(context).apply {
                text = "CLEAR"; setTextColor(MUTED); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(24), dp(8), dp(24), dp(8))
                background = glassBg(dp(8), alpha = 60)
                setOnClickListener {
                    animTap(this)
                    selectedFilter = null
                    selectedSort = SortMode.DATE_ADDED
                    searchInput.setText("")
                    searchText = ""
                    clearSearchBtn.visibility = View.INVISIBLE
                    rebuildChips(); rebuildSorts()
                }
                makeLiquid(this)
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            // DONE
            addView(TextView(context).apply {
                text = "DONE"; setTextColor(Color.rgb(220, 200, 160)); textSize = 12f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(24), dp(8), dp(24), dp(8))
                background = glassBg(dp(8), alpha = 120, accent = true)
                setOnClickListener { animTap(this); doApply() }
                makeLiquid(this)
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
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

    private fun renderHeroGrid(
        games: List<GameCardInfo>,
        layout: HomeCardLayout,
        onPlay: (String) -> Unit,
        onManage: (String) -> Unit,
        deselectAll: () -> Unit,
        selected: SelectedCardRef,
        showGameName: Boolean = true,
        onLongPress: ((GameCardInfo) -> Unit)? = null,
    ): View {
        val screenW = context.resources.displayMetrics.widthPixels
        if (layout == HomeCardLayout.WIDE) {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                games.forEach { game ->
                    addView(createHeroCard(game, onPlay, onManage, deselectAll, selected, (screenW * 0.88f).toInt(), false, showGameName, onLongPress))
                    addView(spacer(dp(10)))
                }
            }
        }

        val columns = if (layout == HomeCardLayout.GRID_3) 3 else 2
        val gap = dp(8)
        val available = screenW - dp(20) - gap * (columns - 1)
        val cardW = available / columns
        return GridLayout(context).apply {
            columnCount = columns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            games.forEachIndexed { index, game ->
                addView(
                    createHeroCard(game, onPlay, onManage, deselectAll, selected, cardW, true, showGameName, onLongPress),
                    GridLayout.LayoutParams().apply {
                        width = cardW
                        height = WRAP
                        rightMargin = if ((index + 1) % columns == 0) 0 else gap
                        bottomMargin = gap
                    },
                )
            }
        }
    }

    private fun createHeroCard(
        game: GameCardInfo, onPlay: (String) -> Unit, onManage: (String) -> Unit,
        deselectAll: () -> Unit,
        selected: SelectedCardRef,
        cardW: Int,
        compact: Boolean,
        showGameName: Boolean = true,
        onLongPress: ((GameCardInfo) -> Unit)? = null,
    ): LinearLayout {
        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(cardW, WRAP)
        }

        val cardH = (cardW * if (compact) 1.16f else 0.62f).toInt()

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
            clipToOutline = true
        }
        cardWrapper.addView(cardFrame)

        // Cover art image (loaded asynchronously if URL available)
        val coverImage = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            contentDescription = game.displayName
        }
        cardFrame.addView(coverImage)

        // Game name placeholder while no cover artwork is available.
        val enginePlaceholder = TextView(context).apply {
            text = game.displayName; setTextColor(Color.argb(60, 255, 255, 255))
            textSize = if (compact) 15f else 28f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        cardFrame.addView(enginePlaceholder)

        // Load cover art in background thread
        if (!game.coverUrl.isNullOrBlank()) {
            Thread {
                try {
                    val bitmap = if (game.coverUrl.startsWith("local:")) {
                        android.graphics.BitmapFactory.decodeFile(game.coverUrl.removePrefix("local:"))
                    } else {
                        android.graphics.BitmapFactory.decodeStream(
                            java.net.URL(game.coverUrl).openStream()
                        )
                    }
                    if (bitmap != null) {
                        coverImage.post {
                            coverImage.setImageBitmap(bitmap)
                            coverImage.visibility = View.VISIBLE
                            enginePlaceholder.visibility = View.GONE
                            coverImage.alpha = 0f
                            coverImage.animate().alpha(1f).setDuration(300).start()
                        }
                    }
                } catch (_: Exception) {
                    // Cover load failed, keep engine-colored background
                }
            }.start()
        }

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

        // SETTINGS — same width
        val optsBtn = TextView(context).apply {
            text = "SETTINGS"; setTextColor(Color.rgb(200, 180, 150)); textSize = if (compact) 11f else 16f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = glassBg(dp(6), alpha = 80)
            setOnClickListener { onManage(game.storageName) }
            makeLiquid(this)
        }
        actionPanel.addView(optsBtn, LinearLayout.LayoutParams(if (compact) (cardW * 0.78f).toInt() else dp(150), WRAP))
        if (!game.isPaused) {
            actionPanel.addView(spacer(dp(14)))
            val btnW = if (compact) (cardW * 0.78f).toInt() else dp(150)
            val playBtn = TextView(context).apply {
                text = "PLAY"; setTextColor(Color.rgb(220, 200, 160)); textSize = if (compact) 12f else 16f
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = glassBg(dp(6), alpha = 100, accent = true)
                setOnClickListener { onPlay(game.storageName) }
                makeLiquid(this)
            }
            actionPanel.addView(playBtn, LinearLayout.LayoutParams(btnW, WRAP))
        }

        // Tap wrapper → toggle overlay + blur (single-selection)
        cardWrapper.setOnClickListener {
            if (dimOverlay.visibility == View.GONE) {
                // Deselect previously selected card first
                deselectAll()
                // Select this card - just show blur and buttons, no animation
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    cardFrame.setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(32f, 32f,
                            android.graphics.Shader.TileMode.CLAMP))
                }
                dimOverlay.visibility = View.VISIBLE
                actionPanel.visibility = View.VISIBLE
                // Track this card as selected
                selected.dimOverlay = dimOverlay
                selected.actionPanel = actionPanel
                selected.cardFrame = cardFrame
            } else {
                // Deselect this card (tap same card again)
                deselectAll()
            }
        }
        cardWrapper.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
                return@setOnKeyListener false
            }
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    onPlay(game.storageName)
                    true
                }
                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_MENU -> {
                    onManage(game.storageName)
                    true
                }
                else -> false
            }
        }

        // Long press → inspect overlay
        cardWrapper.setOnLongClickListener {
            if (onLongPress != null) {
                deselectAll()
                onLongPress(game)
                true
            } else false
        }

        // Shadow box for game name (toggleable)
        if (showGameName) {
            val shadowBox = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(cardW, dp(28))
                background = GradientDrawable().apply {
                    setColor(Color.argb(200, 12, 11, 16))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.argb(30,
                        Color.red(Theme.active.accent),
                        Color.green(Theme.active.accent),
                        Color.blue(Theme.active.accent)))
                }
                addView(TextView(context).apply {
                    text = game.displayName; setTextColor(TEXT); textSize = 12f
                    typeface = Typeface.create("serif", Typeface.BOLD)
                    gravity = Gravity.CENTER; maxLines = 1
                    layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                })
            }
            cardContainer.addView(shadowBox.apply {
                (layoutParams as LinearLayout.LayoutParams).setMargins(dp(6), dp(-12), dp(6), 0)
            })
        }
        if (!compact) {
            cardContainer.addView(TextView(context).apply {
                text = "${game.engineType.label}  \u2022  ${game.fileCount} files"
                setTextColor(MUTED); textSize = 11f; gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
        }
        // Metadata line
        val metaParts = mutableListOf<String>()
        if (game.metadataDeveloper.isNotEmpty()) metaParts.add(game.metadataDeveloper)
        if (game.metadataYear.isNotEmpty()) metaParts.add(game.metadataYear)
        if (game.metadataGenres.isNotEmpty()) metaParts.add(game.metadataGenres)
        if (metaParts.isNotEmpty() && !compact) {
            cardContainer.addView(TextView(context).apply {
                text = metaParts.joinToString(" • "); setTextColor(Color.rgb(160, 140, 110))
                textSize = 10f; gravity = Gravity.CENTER; maxLines = 1
                setPadding(dp(4), dp(2), dp(4), 0)
            })
        }
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
                if (accent) Color.red(Theme.active.accent) / 4 else 22,
                if (accent) Color.green(Theme.active.accent) / 4 else 20,
                if (accent) Color.blue(Theme.active.accent) / 4 else 26))
            cornerRadius = dp(radius).toFloat()
            if (accent) {
                setStroke(dp(1), Color.argb(80,
                    Color.red(Theme.active.accent),
                    Color.green(Theme.active.accent),
                    Color.blue(Theme.active.accent)))
            } else {
                setStroke(dp(1), Color.argb(45, 100, 90, 80))
            }
        }

    // ============================================================
    //  Liquid Glass touch — zoom + parallax on press-and-move
    // ============================================================

    private fun makeLiquid(view: View) { if (Theme.isReducedMotion(context)) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.scaleX = 1.35f
                    v.scaleY = 1.35f
                    v.elevation = dp(12).toFloat()
                }
                MotionEvent.ACTION_MOVE -> {
                    val cx = v.width / 2f
                    val cy = v.height / 2f
                    val dx = (event.x - cx) * 0.25f
                    val dy = (event.y - cy) * 0.25f
                    v.translationX = dx
                    v.translationY = dy
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    ObjectAnimator.ofFloat(v, "elevation", 0f).apply {
                        duration = 250
                        interpolator = OvershootInterpolator(1.6f)
                    }.start()
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .translationX(0f).translationY(0f)
                        .setDuration(250)
                        .setInterpolator(OvershootInterpolator(1.6f))
                        .withEndAction {
                            v.scaleX = 1f; v.scaleY = 1f
                            v.translationX = 0f; v.translationY = 0f
                            v.elevation = 0f
                        }
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

    private fun renderCarousel3D(
        games: List<GameCardInfo>,
        onPlay: (String) -> Unit,
        onManage: ((String) -> Unit)? = null,
    ): FrameLayout {
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.rgb(3, 3, 4))
        }

        // Empty state
        if (games.isEmpty()) {
            val emptyLabel = TextView(context).apply {
                text = "No games yet"
                setTextColor(Color.rgb(140, 130, 112))
                textSize = 16f
                gravity = Gravity.CENTER
            }
            container.addView(emptyLabel, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ))
            return container
        }

        // Ambient glow behind everything
        val glowView = AmbientGlowView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        container.addView(glowView)

        val colorExtractor = GameColorExtractor(context)

        // Carousel
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val cardWidth = dp(260)
        val cardHeight = dp(360)
        val cardTopPadding = ((screenHeight * 0.42f).toInt() - cardHeight / 2).coerceAtLeast(0)
        // Add extra height to accommodate scaled cards (1.15x scale)
        val carouselHeight = (cardTopPadding + (cardHeight * 1.2f).toInt() + dp(20)).coerceAtMost(screenHeight)
        val horizontalPadding = ((screenWidth - cardWidth) / 2).coerceAtLeast(0)
        val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        val scrollEffects = Carousel3DScrollEffects(context)
        val pageIndicator = PageIndicator(context, games.size, 0)
        fun createCarouselAdapter(carouselGames: List<GameCardInfo>) = GameCarouselAdapter(
            games = carouselGames,
            onPlay = { game -> onPlay(game.storageName) },
            onSettings = { game -> onManage?.invoke(game.storageName) },
            onCardLongPressed = { game ->
                val overlay = InspectOverlay(
                    context = context,
                    game = game,
                    onPlay = { name -> onPlay(name) },
                    onSettings = { name -> onManage?.invoke(name) },
                    onDismiss = { /* nothing */ },
                )
                container.addView(overlay)
            },
        )
        val recyclerView = RecyclerView(context).apply {
            this.layoutManager = layoutManager
            adapter = createCarouselAdapter(games)
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            clipToPadding = false
            isNestedScrollingEnabled = false
            setPadding(horizontalPadding, cardTopPadding, horizontalPadding, 0)
            addOnScrollListener(scrollEffects)
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN &&
                    findChildViewUnder(event.x, event.y) == null
                ) {
                    (adapter as? GameCarouselAdapter)?.clearSelection()
                }
                false
            }
        }
        PagerSnapHelper().attachToRecyclerView(recyclerView)
        container.setOnClickListener {
            (recyclerView.adapter as? GameCarouselAdapter)?.clearSelection()
        }
        // Snap to nearest card on fling/release
        container.addView(recyclerView, FrameLayout.LayoutParams(
            MATCH, carouselHeight, Gravity.TOP,
        ))

        // Drag-to-reorder
        val gameOrder = mutableListOf<String>()
        gameOrder.addAll(games.map { it.storageName })

        val touchHelper = ItemTouchHelper(ItemTouchHelperCallback { from, to ->
            val fromGame = gameOrder.removeAt(from)
            gameOrder.add(to, fromGame)
            val reordered = games.sortedBy { gameOrder.indexOf(it.storageName) }
            recyclerView.adapter = createCarouselAdapter(reordered)
            recyclerView.post { scrollEffects.applyTransforms(recyclerView) }
        })
        touchHelper.attachToRecyclerView(recyclerView)

        // Keep carousel metadata above the overlaid dock on every screen size.
        val detailPanel = DetailPanel(context)
        container.addView(detailPanel, FrameLayout.LayoutParams(
            MATCH, WRAP, Gravity.BOTTOM,
        ).apply { bottomMargin = dp(58) + dp(8) + dp(10) })
        
        // Page indicator — small dots just below the carousel cards
        container.addView(pageIndicator, FrameLayout.LayoutParams(
            WRAP,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.TOP,
        ).also { it.topMargin = carouselHeight + dp(8) })

        // Vignette overlay (cinematic corners)
        container.addView(VignetteOverlay(context), FrameLayout.LayoutParams(MATCH, MATCH))

        // Film grain overlay (subtle noise)
        container.addView(GrainOverlay(context), FrameLayout.LayoutParams(MATCH, MATCH))

        // Bloom overlay (accent glow)
        val bloomOverlay = BloomOverlay(context)
        container.addView(bloomOverlay, FrameLayout.LayoutParams(MATCH, MATCH))

        // Depth of Field — blur glow during scroll
        val dofController = DepthOfFieldController(glowView, recyclerView)
        dofController.attach()

        // Wire focus listener — updates detail panel + glow + page indicator
        scrollEffects.focusListener = object : Carousel3DScrollEffects.FocusListener {
            override fun onFocusChanged(adapterPosition: Int) {
                val game = (recyclerView.adapter as? GameCarouselAdapter)?.getGame(adapterPosition)
                if (game != null) {
                    (recyclerView.adapter as? GameCarouselAdapter)?.setFocusedPosition(adapterPosition)
                    detailPanel.bind(game)
                    colorExtractor.getColor(game.displayName, game.coverUrl, game.engineType) { color ->
                        glowView.transitionToColor(color)
                        bloomOverlay.setAccentColor(color)
                    }
                    pageIndicator.setActive(adapterPosition)
                }
            }
        }

        // Initialize with first game
        (recyclerView.adapter as? GameCarouselAdapter)?.setFocusedPosition(0)
        detailPanel.bind(games[0])
        colorExtractor.getColor(games[0].displayName, games[0].coverUrl, games[0].engineType) { color ->
            glowView.transitionToColor(color)
            bloomOverlay.setAccentColor(color)
        }
        recyclerView.post { scrollEffects.applyTransforms(recyclerView) }

        return container
    }

    // ============================================================
    //  List layout — compact horizontal rows
    // ============================================================

    private fun renderListLayout(
        games: List<GameCardInfo>,
        onPlay: (String) -> Unit,
        onManage: (String) -> Unit,
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        games.forEach { game ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(cardColor(game.engineType))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.argb(30, 100, 90, 80))
                }
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                    setMargins(0, 0, 0, dp(6))
                }
            }
            // Engine badge
            row.addView(TextView(context).apply {
                text = game.engineType.label.take(2)
                setTextColor(Color.argb(100, 255, 255, 255))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 255, 255, 255))
                    cornerRadius = dp(8).toFloat()
                }
            })
            row.addView(spacer(dp(10)))
            // Game info
            val infoCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            infoCol.addView(TextView(context).apply {
                text = game.displayName
                setTextColor(TEXT)
                textSize = 14f
                typeface = Typeface.create("serif", Typeface.BOLD)
                maxLines = 1
            })
            infoCol.addView(TextView(context).apply {
                text = "${game.fileCount} files  |  ${game.engineType.label}"
                setTextColor(MUTED)
                textSize = 10f
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(infoCol, LinearLayout.LayoutParams(0, WRAP, 1f))
            // Play arrow
            row.addView(TextView(context).apply {
                text = "\u25B6"
                setTextColor(ACCENT)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { onPlay(game.storageName) }
                makeLiquid(this)
            })
            container.addView(row)
        }
        return container
    }

    // ============================================================
    //  Tiles layout — 2-column grid
    // ============================================================

    private fun renderTileLayout(
        games: List<GameCardInfo>,
        onPlay: (String) -> Unit,
        onManage: (String) -> Unit,
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val w = context.resources.displayMetrics.widthPixels
        val tileW = ((w - dp(10) * 3) / 2).toInt()
        val tileH = (tileW * 0.65f).toInt()
        var i = 0
        while (i < games.size) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            repeat(2) { col ->
                if (i < games.size) {
                    val game = games[i]
                    val tile = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(tileW, tileH).apply {
                            setMargins(dp(4), 0, dp(4), dp(8))
                        }
                        background = GradientDrawable().apply {
                            setColor(cardColor(game.engineType))
                            cornerRadius = dp(12).toFloat()
                            setStroke(dp(1), Color.argb(40, 100, 90, 80))
                        }
                        setOnClickListener { onPlay(game.storageName) }
                        makeLiquid(this)
                    }
                    // Engine watermark
                    tile.addView(TextView(context).apply {
                        text = game.engineType.label
                        setTextColor(Color.argb(40, 255, 255, 255))
                        textSize = 28f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                    })
                    // Name at bottom
                    tile.addView(TextView(context).apply {
                        text = game.displayName
                        setTextColor(TEXT)
                        textSize = 11f
                        typeface = Typeface.create("serif", Typeface.BOLD)
                        gravity = Gravity.CENTER
                        maxLines = 2
                        setPadding(dp(6), 0, dp(6), dp(6))
                        layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM)
                    })
                    row.addView(tile)
                }
                i++
            }
            container.addView(row)
        }
        return container
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private fun spacer(h: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, if (h > 0) h else 1)
    }
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    // ============================================================
    //  Inspect Overlay — long press hero card
    // ============================================================

    fun showInspectOverlay(
        game: GameCardInfo,
        onPlay: (String) -> Unit,
        onManage: (String) -> Unit,
    ) {
        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val rootView = (context as? android.app.Activity)?.window?.decorView
            ?.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Overlay – tap on empty space dismisses
        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            alpha = 0f
            animate().alpha(1f).setDuration(250).start()
            setOnClickListener { dismissOverlay(this, rootView) }
        }

        // Big card – 80% width
        val cardW = (screenW * 0.80f).toInt()
        val cardH = (cardW * 1.2f).toInt()
        val bigCard = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(cardW, cardH)
            background = GradientDrawable().apply {
                setColor(cardColor(game.engineType))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.argb(60,
                    Color.red(Theme.active.accent),
                    Color.green(Theme.active.accent),
                    Color.blue(Theme.active.accent)))
            }
            clipToOutline = true
            setOnClickListener { /* consume tap – doesn't dismiss */ }

            // Cover art image
            val coverImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
                contentDescription = game.displayName
            }
            addView(coverImage)

            // Placeholder game name
            val placeholder = TextView(context).apply {
                text = game.displayName
                setTextColor(Color.argb(80, 255, 255, 255))
                textSize = 24f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            }
            addView(placeholder)

            // Load cover art in background if available
            if (!game.coverUrl.isNullOrBlank()) {
                Thread {
                    try {
                        val bitmap = if (game.coverUrl.startsWith("local:")) {
                            android.graphics.BitmapFactory.decodeFile(game.coverUrl.removePrefix("local:"))
                        } else {
                            android.graphics.BitmapFactory.decodeStream(
                                java.net.URL(game.coverUrl).openStream()
                            )
                        }
                        if (bitmap != null) {
                            coverImage.post {
                                coverImage.setImageBitmap(bitmap)
                                coverImage.visibility = View.VISIBLE
                                placeholder.visibility = View.GONE
                                coverImage.alpha = 0f
                                coverImage.animate().alpha(1f).setDuration(300).start()
                            }
                        }
                    } catch (_: Exception) { }
                }.start()
            }
        }

        // Compact info panel
        val infoPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 12, 11, 16))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(60,
                    Color.red(Theme.active.accent),
                    Color.green(Theme.active.accent),
                    Color.blue(Theme.active.accent)))
            }
            setOnClickListener { /* consume tap */ }
        }

        // Game title
        infoPanel.addView(TextView(context).apply {
            text = game.displayName; setTextColor(TEXT); textSize = 18f
            typeface = Typeface.create("serif", Typeface.BOLD); gravity = Gravity.CENTER
        })
        infoPanel.addView(TextView(context).apply {
            text = "${game.engineType.label}  •  ${game.fileCount} files"
            setTextColor(MUTED); textSize = 11f; gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
        })

        infoPanel.addView(spacer(dp(8)))

        // Metadata
        val metaData = mutableListOf<Pair<String, String>>()
        metaData.add("Engine" to game.engineType.label)
        if (game.metadataDeveloper.isNotEmpty()) metaData.add("Developer" to game.metadataDeveloper)
        if (game.metadataGenres.isNotEmpty()) metaData.add("Genres" to game.metadataGenres)
        if (game.metadataYear.isNotEmpty()) metaData.add("Year" to game.metadataYear)

        val sizeStr = when {
            game.fileSize < 1024 -> "${game.fileSize} B"
            game.fileSize < 1024 * 1024 -> "${game.fileSize / 1024} KB"
            game.fileSize < 1024 * 1024 * 1024 -> "%.1f MB".format(game.fileSize / (1024f * 1024f))
            else -> "%.1f GB".format(game.fileSize / (1024f * 1024f * 1024f))
        }
        metaData.add("Size" to sizeStr)

        if (game.totalPlayTime > 0) {
            val hours = game.totalPlayTime / 3600
            val minutes = (game.totalPlayTime % 3600) / 60
            metaData.add("Played" to if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m")
        }
        if (game.lastPlayedTimestamp > 0) {
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            metaData.add("Last played" to sdf.format(java.util.Date(game.lastPlayedTimestamp)))
        }

        metaData.forEachIndexed { i, (label, value) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(2), dp(2), dp(2))
                if (i > 0) setPadding(dp(2), dp(3), dp(2), dp(2))
                addView(TextView(context).apply {
                    text = label; setTextColor(MUTED_DIM); textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(dp(75), WRAP)
                })
                addView(TextView(context).apply {
                    text = value; setTextColor(TEXT); textSize = 12f; maxLines = 1
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            infoPanel.addView(row)
        }

        infoPanel.addView(spacer(dp(10)))

        // Buttons
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        btnRow.addView(TextView(context).apply {
            text = "PLAY"; setTextColor(Theme.active.accentBright); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Theme.active.accentBg)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(80,
                    Color.red(Theme.active.accent),
                    Color.green(Theme.active.accent),
                    Color.blue(Theme.active.accent)))
            }
            setOnClickListener { v ->
                v.isClickable = true // consume
                dismissOverlay(overlay, rootView); onPlay(game.storageName)
            }
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, dp(6), 0) })
        btnRow.addView(TextView(context).apply {
            text = "SETTINGS"; setTextColor(MUTED); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = glassBg(10, alpha = 80)
            setOnClickListener { v ->
                v.isClickable = true
                dismissOverlay(overlay, rootView); onManage(game.storageName)
            }
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        infoPanel.addView(btnRow)

        // Container – wraps content, centered vertically
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(spacer(dp(60)))
        container.addView(bigCard)
        container.addView(spacer(dp(14)))
        container.addView(infoPanel)

        // Fade in animations
        bigCard.translationY = -80f; bigCard.alpha = 0f
        bigCard.animate().translationY(0f).alpha(1f).setDuration(400)
            .setInterpolator(OvershootInterpolator(1.1f)).start()
        infoPanel.translationY = 80f; infoPanel.alpha = 0f
        infoPanel.animate().translationY(0f).alpha(1f).setDuration(350)
            .setInterpolator(OvershootInterpolator(1.08f)).start()

        overlay.addView(container, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        rootView.addView(overlay)
    }

    private fun dismissOverlay(overlay: FrameLayout, root: ViewGroup) {
        overlay.animate().alpha(0f).translationY(60f).setDuration(200)
            .withEndAction { root.removeView(overlay) }.start()
    }

    private companion object {
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val VERT = LinearLayout.VERTICAL
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(120, 112, 104)
        val ACCENT: Int get() = Theme.active.accent
    }
}

enum class SortMode { NAME_ASC, NAME_DESC, RECENT, DATE_ADDED }

/**
 * RecyclerView adapter that hosts pre-built game card views.
 * Cards are built once by HomeScreen.create() (reusing all existing
 * hero-card rendering code) and placed into RecyclerView ViewHolders.
 */
class PrebuiltCardAdapter(
    private val cards: List<LinearLayout>,
) : RecyclerView.Adapter<PrebuiltCardAdapter.Holder>() {

    class Holder(val container: LinearLayout) : RecyclerView.ViewHolder(container)

    override fun getItemCount() = cards.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val newCard = cards[position]
        holder.container.removeAllViews()
        while (newCard.childCount > 0) {
            val child = newCard.getChildAt(0)
            newCard.removeViewAt(0)
            holder.container.addView(child)
        }
    }
}
