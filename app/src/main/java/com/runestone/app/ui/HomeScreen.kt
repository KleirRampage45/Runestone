/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.runestone.app.R
import com.runestone.app.data.GameEntry
import com.runestone.app.data.RunnerSettings

class HomeScreen(private val context: Context) {

    fun create(
        games: List<GameEntry>,
        settings: RunnerSettings,
        onPlay: (String) -> Unit,
        onImport: () -> Unit,
        onSettings: () -> Unit,
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        root.addView(TextView(context).apply {
            text = "Runestone"
            textSize = 28f
        })

        // Game list
        if (games.isEmpty()) {
            root.addView(TextView(context).apply {
                text = "No games imported yet.\nTap Import to add one."
                textSize = 16f
            })
        } else {
            games.forEach { game ->
                root.addView(TextView(context).apply {
                    text = "${game.displayName} (${game.engineType.label})"
                    textSize = 18f
                    setOnClickListener { onPlay(game.gamePath) }
                })
            }
        }

        // Import button
        root.addView(TextView(context).apply {
            text = "Import Game"
            textSize = 18f
            setOnClickListener { onImport() }
        })

        // Settings button
        root.addView(TextView(context).apply {
            text = "Settings"
            textSize = 18f
            setOnClickListener { onSettings() }
        })

        return root
    }
}
