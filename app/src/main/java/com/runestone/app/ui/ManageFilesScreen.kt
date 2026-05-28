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

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.runestone.app.data.EngineType
import com.runestone.app.workspace.WorkspaceStorage
import java.io.File

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
        onBack: () -> Unit,
    ): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
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
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
                ),
            )
            content.addView(spacer(14))
        }

        content.addView(footerNote())
        return root
    }

    private fun topBar(onBack: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.rgb(15, 14, 18))

            addView(TextView(context).apply {
                text = "Back"
                setTextColor(ACCENT); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), dp(10), dp(8))
                setOnClickListener { onBack() }
            }, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))

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
            addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(View(context), LinearLayout.LayoutParams(dp(72), 1))
        }

    private fun summaryPanel(storages: List<WorkspaceStorage>, isRefreshing: Boolean): LinearLayout {
        val total = storages.sumOf { it.totalBytes }
        val saves = storages.sumOf { it.savesBytes }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = panelBackground(PANEL, stroke = Color.argb(70, 200, 170, 130))
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

    private fun gamePanel(
        game: GameInfo,
        storage: WorkspaceStorage?,
        onImport: (String) -> Unit,
        onDelete: (String) -> Unit,
        onViewSaves: (String) -> Unit,
        onChangeEngine: (String) -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = panelBackground(Color.rgb(18, 17, 21), stroke = Color.argb(54, 255, 255, 255))

        addView(gameHeader(game))
        addView(spacer(12))
        addView(storageBlock(storage))
        addView(spacer(14))
        addView(actionBlock(game, onImport, onDelete, onViewSaves, onChangeEngine))
    }

    private fun gameHeader(game: GameInfo): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = game.displayName
            setTextColor(TEXT); textSize = 19f; typeface = Typeface.create("serif", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = game.engineType.label.uppercase()
            setTextColor(MUTED); textSize = 10f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = panelBackground(Color.argb(45, 255, 255, 255), corner = 20)
        })
    }

    private fun storageBlock(storage: WorkspaceStorage?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = panelBackground(Color.rgb(11, 11, 14), stroke = Color.argb(40, 255, 255, 255))

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
        addView(TextView(context).apply { text = label; setTextColor(MUTED); textSize = 13f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply { text = formatBytes(bytes); setTextColor(TEXT_DIM); textSize = 13f; typeface = Typeface.DEFAULT_BOLD })
    }

    private fun actionBlock(
        game: GameInfo,
        onImport: (String) -> Unit,
        onDelete: (String) -> Unit,
        onViewSaves: (String) -> Unit,
        onChangeEngine: (String) -> Unit,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL

        addView(bigButton("REIMPORT GAME", "Replace game files, keep saves", Color.argb(64, 132, 36, 42)) { onImport(game.storageName) })
        addView(spacer(9))
        addView(twoButtonRow(
            bigButton("VIEW SAVES", "Browse or restore save files", Color.argb(58, 130, 170, 200)) { onViewSaves(game.storageName) },
            bigButton("CHANGE ENGINE", "Force engine type if detection fails", Color.argb(58, 200, 170, 130)) { onChangeEngine(game.storageName) },
        ))
        addView(spacer(9))
        addView(bigButton("REMOVE DATA", "Delete game, keep saves", Color.argb(54, 145, 31, 43)) { onDelete(game.storageName) })
    }

    private fun twoButtonRow(left: TextView, right: TextView): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(spacer(width = 10))
        addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun bigButton(label: String, detail: String, backgroundColor: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = "$label\n$detail"
            setTextColor(TEXT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            includeFontPadding = true; minHeight = dp(58)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = panelBackground(backgroundColor, stroke = Color.argb(74, 255, 255, 255), corner = 8)
            setOnClickListener { onClick() }
        }

    private fun messagePanel(message: String): TextView = TextView(context).apply {
        text = message
        setTextColor(ACCENT); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = panelBackground(Color.rgb(22, 20, 24), stroke = Color.argb(80, 200, 170, 130))
    }

    private fun footerNote(): TextView = TextView(context).apply {
        text = "Game files are stored once. Saves live outside the game for safekeeping."
        setTextColor(Color.argb(130, 170, 164, 154)); textSize = 12f; gravity = Gravity.CENTER
        setPadding(dp(8), dp(2), dp(8), 0)
    }

    private fun panelBackground(color: Int, stroke: Int = Color.TRANSPARENT, corner: Int = 8): GradientDrawable =
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
        val BG = Color.rgb(3, 3, 4); val PANEL = Color.rgb(22, 20, 26)
        val TEXT = Color.rgb(232, 229, 220); val TEXT_DIM = Color.rgb(200, 194, 182)
        val MUTED = Color.rgb(140, 130, 112); val ACCENT = Color.rgb(207, 174, 126)
        val CARD_STROKE = Color.argb(50, 100, 90, 80)
    }
}

