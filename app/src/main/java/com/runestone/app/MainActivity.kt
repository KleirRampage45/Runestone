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
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.runestone.app.data.EngineType
import com.runestone.app.data.RunnerSettings
import com.runestone.app.ui.SortMode
import com.runestone.app.importer.SafGameImporter
import com.runestone.app.importer.SafImportResult
import com.runestone.app.ui.GameCardInfo
import com.runestone.app.ui.HomeScreen
import com.runestone.app.ui.ImportProgressScreen
import com.runestone.app.ui.ImportProgressView
import com.runestone.app.ui.ManageFilesScreen
import com.runestone.app.ui.SettingsScreen
import com.runestone.app.ui.SettingsStore
import com.runestone.app.workspace.GameInstallState
import com.runestone.app.workspace.InstallStateStore
import com.runestone.app.workspace.SaveManager
import com.runestone.app.workspace.WorkspaceManager
import com.runestone.app.workspace.WorkspaceStorage
import com.runestone.app.workspace.WorkspaceStorageReporter
import java.io.File

class MainActivity : Activity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var installStateStore: InstallStateStore
    private lateinit var saveManager: SaveManager
    private lateinit var storageReporter: WorkspaceStorageReporter
    private var settings = RunnerSettings()
    private var games: List<WorkspaceManager.GameInfo> = emptyList()
    private var importMessage: String? = null
    private var activeImportProgressView: ImportProgressView? = null
    private var manageFilesVisible = false
    private var storageCache: Map<String, WorkspaceStorage> = emptyMap()
    private var pendingImportStorage: String? = null

    companion object {
        private const val REQUEST_IMPORT_FOLDER = 9001
        private const val TAG = "Runestone"
    }

    private var pausedGamePath: String? = null
    private var activeEngineFilter: EngineType? = null
    private var currentSort: SortMode = SortMode.NAME_ASC
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        // Check if launched from HOME button with a paused game
        pausedGamePath = intent.getStringExtra("paused_game")
        settingsStore = SettingsStore(this)
        workspaceManager = WorkspaceManager(this)
        installStateStore = InstallStateStore(workspaceManager)
        saveManager = SaveManager(workspaceManager)
        storageReporter = WorkspaceStorageReporter(workspaceManager)
        settings = settingsStore.load()
        refreshGames()
        showHome()
    }

    private fun refreshGames() {
        games = workspaceManager.scanInstalledGames()
        Log.i(TAG, "refreshGames: found ${games.size} games")
    }

    private fun toCardInfo(g: WorkspaceManager.GameInfo) = GameCardInfo(
        storageName = g.storageName,
        displayName = g.displayName,
        engineType = g.engineType,
        fileCount = g.fileCount,
        isReady = true,
        isPaused = pausedGamePath == g.originalPath,
    )

    private fun showHome() {
        Log.i(TAG, "showHome")
        manageFilesVisible = false
        activeImportProgressView = null
        var filtered = if (activeEngineFilter != null) {
            games.filter { it.engineType == activeEngineFilter }
        } else games
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
        filtered = when (currentSort) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            else -> filtered
        }
        val cards = filtered.map { toCardInfo(it) }
        val pausedGame = cards.find { it.isPaused }
        setContentView(
            HomeScreen(this).create(
                games = cards,
                onPlay = { playGame(it) },
                onManage = { showManageFiles(it) },
                onAddGame = { startFolderImport() },
                onManageAll = { showManageFiles() },
                onSettings = { showSettings() },
                onApplyFilters = { engine, search, sort ->
                    activeEngineFilter = engine
                    searchQuery = search
                    currentSort = sort
                    showHome()
                },
                activeFilter = activeEngineFilter,
                activeSearch = searchQuery,
                currentSort = currentSort,
                pausedGame = pausedGame,
                onResume = if (pausedGame != null) {{ playGame(pausedGame.storageName) }} else null,
                onStop = if (pausedGame != null) {{ storageName ->
                    val game = games.find { it.storageName == storageName }
                    if (game != null) {
                        Log.i(TAG, "STOP game: $storageName path=${game.originalPath}")
                        getSharedPreferences("runestone", MODE_PRIVATE).edit()
                            .putString("stop_game", game.originalPath)
                            .apply()
                        pausedGamePath = null
                        finish()
                    }
                }} else null,
            ),
        )
    }

    private fun showManageFiles(storageName: String? = null) {
        Log.i(TAG, "showManageFiles: focused=$storageName")
        manageFilesVisible = true
        val allGames = games.map { ManageFilesScreen.GameInfo(it.storageName, it.displayName, it.engineType, it.fileCount) }
        val mgGames = if (storageName != null) {
            allGames.filter { it.storageName == storageName }
        } else allGames
        setContentView(
            ManageFilesScreen(this).create(
                games = mgGames,
                storageByGame = storageCache,
                isStorageRefreshing = false,
                importMessage = importMessage,
                onImport = { sName -> startFolderImport(sName) },
                onDelete = { sName -> confirmRemoveGameData(sName) },
                onViewSaves = { sName -> viewSaves(sName) },
                onChangeEngine = { sName -> showEnginePicker(sName) },
                onBack = { if (storageName != null) showHome() else showHome() },
            ),
        )
        if (storageName == null) refreshStorageReport()
    }

    private fun refreshStorageReport() {
        val snapshot = games.map { it.storageName }
        Thread {
            val report = snapshot.associateWith { storageReporter.collect(it) }
            runOnUiThread {
                storageCache = report
                if (manageFilesVisible) {
                    val mgGames = games.map { ManageFilesScreen.GameInfo(it.storageName, it.displayName, it.engineType, it.fileCount) }
                    setContentView(
                        ManageFilesScreen(this).create(
                            games = mgGames,
                            storageByGame = storageCache,
                            isStorageRefreshing = false,
                            importMessage = importMessage,
                            onImport = { sName -> startFolderImport(sName) },
                            onDelete = { sName -> confirmRemoveGameData(sName) },
                            onViewSaves = { sName -> viewSaves(sName) },
                            onChangeEngine = { sName -> showEnginePicker(sName) },
                            onBack = { showHome() },
                        ),
                    )
                }
            }
        }.start()
    }

    private fun playGame(storageName: String) {
        val game = games.find { it.storageName == storageName } ?: return

        if (pausedGamePath != null && pausedGamePath == game.originalPath) {
            // Resume: go back to the game underneath
            Log.i(TAG, "RESUME: $storageName")
            pausedGamePath = null
            finish()
            return
        }

        // New launch (or resume cleared above)
        Log.i(TAG, "playGame: $storageName path=${game.originalPath}")

        // If we were paused, clear the stacked game activity by starting
        // a fresh task. Otherwise the old GameActivity remains in the stack.
        val wasPaused = pausedGamePath != null
        pausedGamePath = null

        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("game_path", game.originalPath)
            putExtra("engine_type", game.engineType.name)
            putExtra("layout_mode", settings.layoutMode.name)
            putExtra("touch_opacity", settings.touchOpacity)
            putExtra("touch_scale", settings.touchScale)
            putExtra("haptics", settings.hapticsEnabled)
            putExtra("haptic_intensity", settings.hapticIntensity)
            putExtra("show_extra_btns", settings.showExtraButtons)
            putExtra("audio_ext", settings.forceAudioExt)
            // Start a fresh task so the old paused game isn't in the back stack
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        if (wasPaused) finish()
    }

    private fun startFolderImport(requestedName: String? = null) {
        Log.i(TAG, "startFolderImport: requestedName=$requestedName")
        importMessage = null
        pendingImportStorage = requestedName
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_FOLDER)
    }

    private fun showSettings() {
        manageFilesVisible = false
        setContentView(
            SettingsScreen(this).create(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    settings = newSettings
                    settingsStore.save(newSettings)
                    showSettings()
                },
                onBack = { showHome() },
            ),
        )
    }

    private fun confirmRemoveGameData(storageName: String) {
        val game = games.find { it.storageName == storageName }
        val name = game?.displayName ?: storageName
        AlertDialog.Builder(this)
            .setTitle("Remove $name data?")
            .setMessage("This deletes the game files. Saves are kept in protected storage.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove data") { _, _ ->
                saveManager.syncFromActive(storageName)
                workspaceManager.removeGame(storageName, keepSaves = true)
                importMessage = "Removed $name game data. Saves were kept."
                refreshGames()
                showManageFiles()
            }
            .show()
    }

    private fun viewSaves(storageName: String) {
        val saves = saveManager.listSaves(storageName)
        if (saves.isEmpty()) {
            Toast.makeText(this, "No save files found for $storageName", Toast.LENGTH_SHORT).show()
            return
        }
        val names = saves.joinToString("\n") { "${it.name} (${formatBytes(it.length())})" }
        AlertDialog.Builder(this)
            .setTitle("Save Files — ${games.find { it.storageName == storageName }?.displayName ?: storageName}")
            .setMessage(names)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showEnginePicker(storageName: String) {
        val game = games.find { it.storageName == storageName }
        val currentEngine = game?.engineType
        val engines = EngineType.values().filter { it != EngineType.UNKNOWN }
        val items = engines.map { "${it.label} (${it.name})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Change Engine — ${game?.displayName ?: storageName}")
            .setSingleChoiceItems(items, engines.indexOf(currentEngine)) { dialog, which ->
                val selected = engines[which]
                Log.i(TAG, "Engine override: $storageName -> $selected")
                // Save to install_state.json
                val state = installStateStore.load(storageName) ?: GameInstallState(
                    storageName = storageName,
                    engineType = selected,
                    fileCount = game?.fileCount ?: 0,
                    importedAtMillis = System.currentTimeMillis(),
                )
                installStateStore.save(state.copy(engineOverride = selected))
                importMessage = "Engine set to ${selected.label}. Relaunch to apply."
                refreshGames()
                showManageFiles()
                dialog.dismiss()
            }
            .setNegativeButton("Reset to Auto", null)
            .show()
    }

    private fun formatBytes(bytes: Long): String {
        val gb = 1024.0 * 1024.0 * 1024.0; val mb = 1024.0 * 1024.0
        return if (bytes >= gb) String.format("%.2f GB", bytes / gb) else String.format("%.1f MB", bytes / mb)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "onActivityResult: requestCode=$requestCode resultCode=$resultCode data=$data")
        if (requestCode != REQUEST_IMPORT_FOLDER) return
        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "onActivityResult: result not OK")
            return
        }
        val treeUri = data?.data ?: run {
            Log.w(TAG, "onActivityResult: no data URI"); return
        }
        Log.i(TAG, "onActivityResult: treeUri=$treeUri pending=$pendingImportStorage")

        runCatching { contentResolver.takePersistableUriPermission(
            treeUri, data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        ) }

        // Back up saves BEFORE import wipes original/
        if (pendingImportStorage != null) {
            val backedUp = saveManager.syncFromActive(pendingImportStorage!!)
            Log.i(TAG, "Backed up $backedUp saves for $pendingImportStorage before import")
        }

        showImportProgress("Importing game")
        Log.i(TAG, "onActivityResult: import progress screen shown, starting thread")

        Thread {
            val importer = SafGameImporter(
                contentResolver = contentResolver,
                workspaceManager = workspaceManager,
                onProgress = { msg ->
                    runOnUiThread {
                        Log.d(TAG, "import progress: $msg")
                        val pv = activeImportProgressView
                        if (pv != null) {
                            when {
                                msg.startsWith("Copying game") -> { pv.phaseView.text = msg; pv.fileView.text = ""; pv.countView.text = "" }
                                msg.startsWith("Copying ") -> pv.fileView.text = msg.removePrefix("Copying ")
                                else -> { pv.phaseView.text = msg; pv.fileView.text = "" }
                            }
                        }
                        importMessage = msg
                    }
                },
            )
            val result = importer.importTree(treeUri, pendingImportStorage)
            Log.i(TAG, "import finished: $result")

            runOnUiThread {
                pendingImportStorage = null
                when (result) {
                    is SafImportResult.Success -> {
                        Log.i(TAG, "Import OK: ${result.storageName} (${result.fileCount} files)")
                        importMessage = null
                        saveManager.restoreToActive(result.storageName)
                        activeImportProgressView = null
                        refreshGames()
                        showHome()
                    }
                    is SafImportResult.Failure -> {
                        Log.e(TAG, "Import FAILED: ${result.reason}")
                        val pv = activeImportProgressView
                        if (pv != null) { pv.phaseView.text = "❌ Import failed"; pv.fileView.text = result.reason; pv.countView.text = "" }
                        importMessage = "Import failed: ${result.reason}"
                        android.os.Handler(mainLooper).postDelayed({
                            refreshGames(); activeImportProgressView = null; showManageFiles()
                        }, 3000)
                    }
                }
            }
        }.start()
    }

    private fun showImportProgress(message: String) {
        Log.i(TAG, "showImportProgress: $message")
        importMessage = message
        activeImportProgressView = ImportProgressScreen(this).create(title = message)
        setContentView(activeImportProgressView?.root)
        Log.i(TAG, "showImportProgress: content view set")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume importActive=${activeImportProgressView != null}")
        if (activeImportProgressView == null) {
            refreshGames()
            showHome()
        }
    }

    override fun onBackPressed() {
        if (activeImportProgressView != null) {
            Toast.makeText(this, "Operation still running.", Toast.LENGTH_SHORT).show()
        } else if (manageFilesVisible) {
            showHome()
        } else if (activeEngineFilter != null) {
            activeEngineFilter = null
            showHome()
        } else {
            super.onBackPressed()
        }
    }
}
