/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.data.EngineType
import com.runestone.app.workspace.WorkspaceStorage

class ManageFilesScreen(private val context: Context) {

    data class GameInfo(
        val storageName: String,
        val displayName: String,
        val engineType: EngineType,
        val fileCount: Int,
    )

    fun create(
        games: List<GameInfo>,
        storageByGame: Map<String, WorkspaceStorage>,
        isStorageRefreshing: Boolean,
        importMessage: String?,
        onImport: (storageName: String) -> Unit,
        onDelete: (storageName: String) -> Unit,
        onViewSaves: (storageName: String) -> Unit,
        onChangeEngine: (storageName: String) -> Unit,
        onPerGameSettings: (storageName: String) -> Unit,
        onBack: () -> Unit,
    ): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Transparent — overlay dim layer provides the background
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        root.addView(topBar(onBack))

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(scroll)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        scroll.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))

        importMessage?.takeIf { it.isNotBlank() }?.let {
            content.addView(messagePanel(it))
            content.addView(spacer(12))
        }

        content.addView(summaryPanel(storageByGame.values.toList(), isStorageRefreshing))
        content.addView(spacer(14))

        games.forEach { game ->
            content.addView(
                gamePanel(
                    game = game,
                    storage = storageByGame[game.storageName],
                    onImport = onImport,
                    onDelete = onDelete,
                    onViewSaves = onViewSaves,
                    onChangeEngine = onChangeEngine,
                    onPerGameSettings = onPerGameSettings,
                ),
            )
            content.addView(spacer(14))
        }

        content.addView(footerNote())
        return root
    }

    // ============================================================
    //  Top bar
    // ============================================================

    private fun topBar(onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(15, 14, 18))

            addView(TextView(context).apply {
                text = "Back"
                setTextColor(ACCENT); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.argb(40, 207, 174, 126))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.argb(60, 207, 174, 126))
                }
                setOnClickListener { onBack() }
                makeLiquid(this)
            }, LinearLayout.LayoutParams(dp(84), WRAP))

            val titleColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            }
            titleColumn.addView(TextView(context).apply {
                text = "Game Files"
                setTextColor(TEXT); textSize = 21f; typeface = Typeface.create("serif", Typeface.BOLD); gravity = Gravity.CENTER
            })
            titleColumn.addView(TextView(context).apply {
                text = "imports, cache, saves"
                setTextColor(MUTED); textSize = 11f; gravity = Gravity.CENTER
            })
            addView(titleColumn, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(View(context), LinearLayout.LayoutParams(dp(84), 1))
        }

    // ============================================================
    //  Summary panel — glass card
    // ============================================================

    private fun summaryPanel(storages: List<WorkspaceStorage>, isRefreshing: Boolean): LinearLayout {
        val total = storages.sumOf { it.totalBytes }
        val saves = storages.sumOf { it.savesBytes }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 12, 11, 16))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(70, 160, 140, 110))
            }
            alpha = 0f
            animate().alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.05f)).start()
            addView(TextView(context).apply {
                text = "Workspace Storage"
                setTextColor(TEXT); textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = if (isRefreshing && storages.isEmpty()) "Checking storage..." else "${formatBytes(total)} total · ${formatBytes(saves)} saves"
                setTextColor(ACCENT); textSize = 13f; setPadding(0, dp(5), 0, 0)
            })
        }
    }

    // ============================================================
    //  Game panel — glass card for each game
    // ============================================================

    private fun gamePanel(
        game: GameInfo,
        storage: WorkspaceStorage?,
        onImport: (String) -> Unit,
        onDelete: (String) -> Unit,
        onViewSaves: (String) -> Unit,
        onChangeEngine: (String) -> Unit,
        onPerGameSettings: (String) -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.argb(190, 12, 11, 16))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.argb(60, 160, 140, 110))
        }
        alpha = 0f
        animate().alpha(1f).setDuration(300).setInterpolator(OvershootInterpolator(1.05f)).start()

        addView(gameHeader(game))
        addView(spacer(12))
        addView(storageBlock(storage))
        addView(spacer(14))
        addView(actionBlock(game, onImport, onDelete, onViewSaves, onChangeEngine, onPerGameSettings))
    }

    // ============================================================
    //  Game header — name + engine badge
    // ============================================================

    private fun gameHeader(game: GameInfo): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = game.displayName
            setTextColor(TEXT); textSize = 19f; typeface = Typeface.create("serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(TextView(context).apply {
            text = game.engineType.label.uppercase()
            setTextColor(ACCENT); textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = glassBg(dp(10), alpha = 50)
        })
    }

    // ============================================================
    //  Storage block — workspace data breakdown
    // ============================================================

    private fun storageBlock(storage: WorkspaceStorage?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.argb(120, 10, 10, 13))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.argb(40, 160, 140, 110))
        }

        if (storage == null || storage.totalBytes == 0L) {
            addView(TextView(context).apply { text = "No workspace data yet."; setTextColor(MUTED); textSize = 13f })
            return@apply
        }
        addView(TextView(context).apply {
            text = formatBytes(storage.totalBytes)
            setTextColor(TEXT); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
        })
        addView(TextView(context).apply {
            text = if (storage.savesBytes > 0L) "Saves are protected outside the game files." else "No saves detected yet."
            setTextColor(if (storage.savesBytes > 0L) Color.rgb(190, 224, 176) else MUTED)
            textSize = 12f; setPadding(0, dp(2), 0, dp(10))
        })
        addView(storageLine("Game files", storage.originalBytes))
        addView(storageLine("Saves", storage.savesBytes))
        if (storage.otherBytes > 0L) addView(storageLine("Other", storage.otherBytes))
    }

    private fun storageLine(label: String, bytes: Long): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(5), 0, 0)
        addView(TextView(context).apply { text = label; setTextColor(MUTED); textSize = 13f }, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(TextView(context).apply { text = formatBytes(bytes); setTextColor(ACCENT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD })
    }

    // ============================================================
    //  Action block — glass buttons with makeLiquid + animTap
    // ============================================================

    private fun actionBlock(
        game: GameInfo,
        onImport: (String) -> Unit,
        onDelete: (String) -> Unit,
        onViewSaves: (String) -> Unit,
        onChangeEngine: (String) -> Unit,
        onPerGameSettings: (String) -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL

        addView(glassBigButton("PER-GAME SETTINGS", "Customize settings for this game", Color.argb(58, 180, 140, 100)) { onPerGameSettings(game.storageName) })
        addView(spacer(9))
        addView(glassBigButton("REIMPORT GAME", "Replace game files, keep saves", Color.argb(64, 132, 36, 42)) { onImport(game.storageName) })
        addView(spacer(9))
        addView(twoButtonRow(
            glassBigButton("VIEW SAVES", "Browse or restore save files", Color.argb(58, 130, 170, 200)) { onViewSaves(game.storageName) },
            glassBigButton("CHANGE ENGINE", "Force engine type if detection fails", Color.argb(58, 200, 170, 130)) { onChangeEngine(game.storageName) },
        ))
        addView(spacer(9))
        addView(glassBigButton("REMOVE DATA", "Delete game, keep saves", Color.argb(54, 145, 31, 43)) { onDelete(game.storageName) })
    }

    private fun twoButtonRow(left: TextView, right: TextView): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(spacer(width = 10))
        addView(right, LinearLayout.LayoutParams(0, WRAP, 1f))
    }

    private fun glassBigButton(label: String, detail: String, tint: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = "$label\n$detail"
            setTextColor(TEXT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            includeFontPadding = true; minHeight = dp(58)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(tint)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(
                    minOf(Color.alpha(tint) + 24, 255),
                    minOf(Color.red(tint) + 60, 255),
                    minOf(Color.green(tint) + 40, 255),
                    minOf(Color.blue(tint) + 40, 255),
                ))
            }
            setOnClickListener {
                animTap(this)
                onClick()
            }
            makeLiquid(this)
        }

    // ============================================================
    //  Message panel
    // ============================================================

    private fun messagePanel(message: String): TextView = TextView(context).apply {
        text = message
        setTextColor(ACCENT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = panelBackground(Color.rgb(22, 20, 24), stroke = Color.argb(80, 200, 170, 130), corner = 14)
        alpha = 0f
        animate().alpha(1f).setDuration(280).start()
    }

    // ============================================================
    //  Footer note
    // ============================================================

    private fun footerNote(): TextView = TextView(context).apply {
        text = "Game files are stored once. Saves live outside the game for safekeeping."
        setTextColor(Color.argb(130, 170, 164, 154)); textSize = 12f; gravity = Gravity.CENTER
        setPadding(dp(8), dp(2), dp(8), 0)
    }

    // ============================================================
    //  Glass touch helpers — ported from HomeScreen
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
            false
        }
    }

    private fun animTap(v: View) {
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(60)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            }.start()
    }

    private fun glassBg(radius: Int, alpha: Int = 200, accent: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.argb(alpha,
                if (accent) 50 else 22, if (accent) 40 else 20, if (accent) 30 else 26))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(if (accent) 80 else 45,
                if (accent) 180 else 100, if (accent) 140 else 90, if (accent) 100 else 80))
        }

    // ============================================================
    //  Base helpers
    // ============================================================

    private fun panelBackground(color: Int, stroke: Int = Color.TRANSPARENT, corner: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(corner).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun spacer(height: Int = 0, width: Int = 0): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
    }

    private fun formatBytes(bytes: Long): String {
        val gb = 1024.0 * 1024.0 * 1024.0; val mb = 1024.0 * 1024.0
        return if (bytes >= gb) String.format("%.2f GB", bytes / gb) else String.format("%.1f MB", bytes / mb)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val PANEL = Color.argb(190, 12, 11, 16)
        val TEXT = Color.rgb(232, 229, 220)
        val MUTED = Color.rgb(140, 130, 112)
        val MUTED_DIM = Color.rgb(100, 95, 85)
        val ACCENT: Int get() = Theme.active.accent
    }
}
