/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.runestone.app.data.EngineType
import com.runestone.app.engine.EngineDetector
import com.runestone.app.importer.SafGameImporter
import com.runestone.app.importer.SafImportResult
import com.runestone.app.workspace.WorkspaceManager

class MainActivity : Activity() {

    private lateinit var workspaceManager: WorkspaceManager
    private var games: List<WorkspaceManager.GameInfo> = emptyList()

    companion object {
        private const val REQUEST_IMPORT_FOLDER = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspaceManager = WorkspaceManager(this)
        refreshGames()
        showHome()
    }

    private fun refreshGames() {
        games = workspaceManager.scanInstalledGames()
    }

    private fun showHome() {
        val scrollView = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        // Title
        root.addView(TextView(this).apply {
            text = "⚡ Runestone"
            textSize = 32f
            setPadding(0, 0, 0, 24)
        })

        // Game list
        if (games.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No games yet.\nTap 'Import Game' to add one."
                textSize = 16f
                setPadding(0, 0, 0, 16)
                setTextColor(android.graphics.Color.parseColor("#888888"))
            })
        } else {
            root.addView(TextView(this).apply {
                text = "My Games"
                textSize = 18f
                setPadding(0, 0, 0, 12)
            })

            games.forEachIndexed { index, game ->
                val gameRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                    setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
                    setOnClickListener {
                        GameActivity.start(this@MainActivity, game.gamePath, game.engineType.name)
                    }
                }

                gameRow.addView(TextView(this).apply {
                    text = game.displayName
                    textSize = 20f
                })

                gameRow.addView(TextView(this).apply {
                    text = "${game.engineType.label} · ${game.fileCount} files"
                    textSize = 13f
                    setTextColor(android.graphics.Color.parseColor("#888888"))
                })

                root.addView(gameRow)

                if (index < games.size - 1) {
                    val spacer = TextView(this)
                    spacer.height = 8
                    root.addView(spacer)
                }
            }
        }

        // Spacer
        root.addView(TextView(this).apply { height = 32 })

        // Import button
        root.addView(createButton("📥  Import Game") {
            startFolderImport()
        })

        // Spacer
        root.addView(TextView(this).apply { height = 16 })

        // Settings button
        root.addView(createButton("⚙️  Settings") {
            showSettings()
        })

        scrollView.addView(root)
        setContentView(scrollView)
    }

    private fun createButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(24, 16, 24, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#2A2A4E"))
            gravity = android.view.Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun startFolderImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_FOLDER)
    }

    private fun showSettings() {
        Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_FOLDER) return
        if (resultCode != Activity.RESULT_OK) return

        val treeUri = data?.data ?: return
        val flags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, flags)
        }

        Toast.makeText(this, "Importing game...", Toast.LENGTH_SHORT).show()

        Thread {
            val result = SafGameImporter(
                contentResolver = contentResolver,
                workspaceManager = workspaceManager,
            ).importTree(treeUri)

            runOnUiThread {
                when (result) {
                    is SafImportResult.Success -> {
                        Toast.makeText(
                            this,
                            "✓ ${result.gameName} (${result.engineType.label})",
                            Toast.LENGTH_LONG,
                        ).show()
                        refreshGames()
                        showHome()
                    }
                    is SafImportResult.Failure -> {
                        Toast.makeText(
                            this,
                            "✗ ${result.reason}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        refreshGames()
        showHome()
    }
}
