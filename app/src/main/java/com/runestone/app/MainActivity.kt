/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.runestone.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.runestone.app.data.GameEntry
import com.runestone.app.data.RunnerSettings
import com.runestone.app.data.InstallStatus
import com.runestone.app.importer.SafGameImporter
import com.runestone.app.importer.SafImportResult
import com.runestone.app.ui.HomeScreen
import com.runestone.app.workspace.WorkspaceManager

class MainActivity : Activity() {

    private lateinit var workspaceManager: WorkspaceManager
    private var settings = RunnerSettings()
    private var games = listOf<GameEntry>()

    companion object {
        private const val REQUEST_IMPORT_FOLDER = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspaceManager = WorkspaceManager(this)
        settings = loadSettings()
        games = workspaceManager.scanInstalledGames()
        showHome()
    }

    private fun loadSettings(): RunnerSettings {
        val prefs = getSharedPreferences("runestone", MODE_PRIVATE)
        return RunnerSettings(
            layoutMode = prefs.getString("layout_mode", RunnerSettings.LAYOUT_PORTRAIT_CONSOLE)
                ?: RunnerSettings.LAYOUT_PORTRAIT_CONSOLE,
        )
    }

    private fun showHome() {
        setContentView(
            HomeScreen(this).create(
                games = games,
                settings = settings,
                onPlay = { gamePath ->
                    GameActivity.start(this, gamePath)
                },
                onImport = {
                    startFolderImport()
                },
                onSettings = {
                    showSettings()
                },
            ),
        )
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
        // TODO: Settings screen
        Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_FOLDER) return
        if (resultCode != Activity.RESULT_OK) return

        val treeUri = data?.data ?: return
        val persistFlags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, persistFlags)
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
                            "Imported: ${result.gameName} (${result.engineType})",
                            Toast.LENGTH_LONG,
                        ).show()
                        games = workspaceManager.scanInstalledGames()
                        showHome()
                    }
                    is SafImportResult.Failure -> {
                        Toast.makeText(
                            this,
                            "Import failed: ${result.reason}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }.start()
    }
}
