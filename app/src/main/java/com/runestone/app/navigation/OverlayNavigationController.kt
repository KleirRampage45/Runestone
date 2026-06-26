/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.navigation

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.runestone.app.data.EngineType
import com.runestone.app.ui.AvailableGamesScreen
import com.runestone.app.ui.GameDetailOverlay
import com.runestone.app.ui.GameCardInfo
import com.runestone.app.ui.HomeCardLayout
import com.runestone.app.ui.HomeScreen
import com.runestone.app.ui.ImportProgressScreen
import com.runestone.app.ui.ManageFilesScreen
import com.runestone.app.ui.PerGameSettingsScreen
import com.runestone.app.ui.ProviderSettingsScreen
import com.runestone.app.ui.SettingsScreen
import com.runestone.app.ui.SettingsStore
import com.runestone.app.ui.Theme
import com.runestone.app.ui.SourcesScreen
import com.runestone.app.ui.SortMode
import com.runestone.app.data.db.GameSizeCacheEntity
import com.runestone.app.data.db.RunestoneDatabase
import com.runestone.app.session.GameSessionManager
import com.runestone.app.services.GameMetadataService
import com.runestone.app.provider.AvailableGame
import com.runestone.app.provider.DownloadManager
import com.runestone.app.workspace.WorkspaceManager
import com.runestone.app.workspace.WorkspaceStorage
import com.runestone.app.store.StoreCoordinator
import com.runestone.app.importer.ImportManager
import com.runestone.app.util.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OverlayNavigationController(
    private val activity: Activity,
    private val callbacks: Callbacks,
    private val settingsStore: SettingsStore,
    private val workspaceManager: WorkspaceManager,
    private val metadataService: GameMetadataService,
    private val downloadManager: DownloadManager,
    private val sourcesManager: com.runestone.app.provider.SourcesManager,
    private val saveManager: com.runestone.app.workspace.SaveManager,
    private val storageReporter: com.runestone.app.workspace.WorkspaceStorageReporter,
    private val installStateStore: com.runestone.app.workspace.InstallStateStore,
    private val storeCoordinator: StoreCoordinator,
    private val importManager: ImportManager,
    private val sessionManager: GameSessionManager,
) {
    private val gameSizeCacheDao = RunestoneDatabase.getInstance(activity).gameSizeCacheDao()
    private val gameSizeCacheMem = mutableMapOf<String, Long>()
    private val gameSizeInFlight = mutableSetOf<String>()
    interface Callbacks {
        fun playGame(storageName: String)
        fun performDeleteGame(storageName: String, gameTitle: String, keepSaves: Boolean)
        fun refreshGames()
        fun applyImmersiveMode(force: Boolean = false)
        fun onSettingsChanged(newSettings: com.runestone.app.data.RunnerSettings)
    }

    lateinit var rootContainer: FrameLayout
    var activeOverlay: View? = null
    var detailOverlay: GameDetailOverlay? = null
    var homeContentView: View? = null
    var persistentDock: View? = null
    var manageFilesVisible = false
    var storageCache: Map<String, WorkspaceStorage> = emptyMap()
    var games: List<WorkspaceManager.GameInfo> = emptyList()
    var gameMetadataCache: MutableMap<String, GameMetadataService.GameMetadata> = mutableMapOf()
    var activeEngineFilter: EngineType? = null
    var currentSort: SortMode = SortMode.DATE_ADDED
    var searchQuery: String = ""
    var homeCardLayout: HomeCardLayout = HomeCardLayout.GRID_2
    var splashView: FrameLayout? = null
    var controllerNavigationEnabled = false
    var settings: com.runestone.app.data.RunnerSettings = com.runestone.app.data.RunnerSettings()

    var metadataWarmupInFlight = mutableSetOf<String>()

    var rtpOverlayStatusText: TextView? = null
    var rtpOverlayProgressBar: ProgressBar? = null
    var activeRtpDialog: AlertDialog? = null

    companion object {
        private const val TAG = "Runestone"
    }

    fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    fun showOverlay(panel: View, dismissOnBgClick: Boolean = true) {
        activeOverlay?.let { rootContainer.removeView(it); activeOverlay = null }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            homeContentView?.setRenderEffect(null)
        }

        val wrapper = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(218, 0, 0, 0))
            alpha = 0f
            translationY = activity.resources.displayMetrics.heightPixels * 0.08f
            animate().alpha(1f).translationY(0f).setDuration(250).start()

            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            lp.setMargins(dp(8), dp(20), dp(8), dp(74))
            addView(panel, lp)

            panel.isClickable = true

            if (dismissOnBgClick) {
                setOnClickListener { dismissOverlay() }
            }
        }
        rootContainer.addView(wrapper,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        persistentDock?.bringToFront()
        activeOverlay = wrapper
        if (controllerNavigationEnabled) {
            rootContainer.post { enableControllerNavigation(wrapper) }
        }
    }

    fun dismissOverlay(onDismissed: () -> Unit = {}) {
        activeOverlay?.let { overlay ->
            overlay.animate().alpha(0f).translationY(activity.resources.displayMetrics.heightPixels * 0.08f).setDuration(200).withEndAction {
                rootContainer.removeView(overlay)
                activeOverlay = null
                onDismissed()
            }.start()
        }
    }

    fun showSplash() {
        val splash = FrameLayout(activity).apply {
            setBackgroundColor(Color.rgb(3, 3, 4))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            val titleText = TextView(activity).apply {
                text = "RUNESTONE"
                setTextColor(Theme.active.accent)
                textSize = 32f
                typeface = Typeface.create("serif", Typeface.BOLD)
                letterSpacing = 0.3f
                gravity = Gravity.CENTER
            }
            addView(titleText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

            val sub = TextView(activity).apply {
                text = "Multi-Engine Game Launcher"
                setTextColor(Color.argb(140, 180, 160, 130))
                textSize = 13f
                letterSpacing = 0.2f
                gravity = Gravity.CENTER
            }
            val subLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            subLp.topMargin = dp(60)
            addView(sub, subLp)

            alpha = 0f
        }
        rootContainer.addView(splash, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        splashView = splash

        splash.animate().alpha(1f).setDuration(300).start()
    }

    fun dismissSplash() {
        val splash = splashView ?: return
        splashView = null
        splash.post {
            splash.animate().alpha(0f).setDuration(300).withEndAction {
                rootContainer.removeView(splash)
                showHome()
            }.start()
        }
    }

    fun showHome(
        uiMode: com.runestone.app.data.UIMode = com.runestone.app.data.UIMode.GRID,
        showGameName: Boolean = true,
    ) {
        Log.i(TAG, "showHome")
        manageFilesVisible = false
        importManager.activeImportProgressView = null

        activeOverlay?.let {
            rootContainer.removeView(it)
            activeOverlay = null
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            homeContentView?.setRenderEffect(null)
        }

        homeContentView?.let { rootContainer.removeView(it) }

        var filtered = if (activeEngineFilter != null) {
            games.filter { it.engineType == activeEngineFilter }
        } else games
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
        filtered = when (currentSort) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            SortMode.RECENT -> filtered
            SortMode.DATE_ADDED -> filtered.sortedByDescending { File(it.originalPath).parentFile?.lastModified() ?: 0L }
        }
        val cards = filtered.map { toCardInfo(it) }.map { card ->
            if (card.coverUrl != null) return@map card
            val availableCoverUrl = storeCoordinator.availableGames.firstOrNull {
                it.title.equals(card.displayName, ignoreCase = true) ||
                it.title.contains(card.displayName, ignoreCase = true) ||
                card.displayName.contains(it.title, ignoreCase = true)
            }?.coverUrl

            var coverUrl = availableCoverUrl ?: gameMetadataCache[card.displayName]?.coverUrl

            if (coverUrl == null) {
                val game = games.find { it.storageName == card.storageName }
                if (game != null) {
                    val fallback = com.runestone.app.services.CoverExtractor.extractFallbackCover(activity, game.storageName, File(game.originalPath))
                    if (fallback != null) coverUrl = "local:$fallback"
                }
            }

            card.copy(coverUrl = coverUrl)
        }

        val pausedGame = cards.find { it.isPaused }

        val homeView = HomeScreen(activity).create(
            games = cards,
            onPlay = { callbacks.playGame(it) },
            onManage = { showPerGameSettings(it) },
            onAddGame = { importManager.startFolderImport() },
            onBrowse = { storeCoordinator.showAvailableGames() },
            onManageAll = { showManageFiles() },
            onSettings = { showSettings(settings) },
            onApplyFilters = { engine, search, sort ->
                activeEngineFilter = engine
                searchQuery = search
                currentSort = sort
                showHome(uiMode, showGameName)
            },
            activeFilter = activeEngineFilter,
            activeSearch = searchQuery,
            currentSort = currentSort,
            pausedGame = pausedGame,
            uiMode = uiMode,
            cardLayout = homeCardLayout,
            showGameName = showGameName,
            onLongPress = { game ->
                HomeScreen(activity).showInspectOverlay(game, { callbacks.playGame(it) }, { showPerGameSettings(it) })
            },
            onCardLayoutChanged = { layout ->
                homeCardLayout = layout
                activity.getSharedPreferences("runestone-settings-v1", Activity.MODE_PRIVATE)
                    .edit()
                    .putString("homeCardLayout", layout.name)
                    .apply()
                showHome(uiMode, showGameName)
            },
            onResume = if (pausedGame != null) {{ callbacks.playGame(pausedGame.storageName) }} else null,
            onStop = if (pausedGame != null) {{ storageName ->
                    val game = games.find { it.storageName == storageName }
                    if (game != null) {
                        Log.i(TAG, "STOP game: $storageName path=${game.originalPath}")
                        sessionManager.recordStop(storageName)
                        activity.getSharedPreferences("runestone", Activity.MODE_PRIVATE).edit()
                            .remove("paused_game")
                            .remove("active_game_storage")
                            .remove("active_game_path")
                            .remove("game_minimized")
                            .apply()
                        callbacks.refreshGames()
                        rootContainer.postDelayed({
                            showHome(uiMode, showGameName)
                        }, 100)
                    }
                }} else null,
        )
        rootContainer.addView(homeView, 0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        homeContentView = homeView
        if (controllerNavigationEnabled) {
            rootContainer.post { enableControllerNavigation(rootContainer) }
        }
        scheduleMetadataWarmup(cards)
    }

    fun showSettings(settings: com.runestone.app.data.RunnerSettings = com.runestone.app.data.RunnerSettings()) {
        manageFilesVisible = false
        showOverlay(
            SettingsScreen(activity).create(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    callbacks.onSettingsChanged(newSettings)
                },
                onBack = { dismissOverlay() },
                onResetDefaults = {
                    showSettings(settings)
                },
                onClearRuntimeCache = {
                    clearRuntimeCache()
                },
            ),
        )
    }

    fun showManageFiles(storageName: String? = null) {
        Log.i(TAG, "showManageFiles: focused=$storageName")
        manageFilesVisible = true
        val allGames = games.map { ManageFilesScreen.GameInfo(it.storageName, it.displayName, it.engineType, it.fileCount) }
        val mgGames = if (storageName != null) {
            allGames.filter { it.storageName == storageName }
        } else allGames
        showOverlay(
            ManageFilesScreen(activity).create(
                games = mgGames,
                storageByGame = storageCache,
                isStorageRefreshing = false,
                importMessage = importManager.importMessage,
                onImport = { sName -> importManager.startFolderImport(sName) },
                onDelete = { sName -> confirmRemoveGameData(sName) },
                onViewSaves = { sName -> viewSaves(sName) },
                onChangeEngine = { sName -> showEnginePicker(sName) },
                onPerGameSettings = { sName -> showPerGameSettings(sName) },
                onBack = { dismissOverlay() },
            ),
        )
        if (storageName == null) refreshStorageReport()
    }

    fun showPerGameSettings(storageName: String) {
        manageFilesVisible = false
        val game = games.find { it.storageName == storageName } ?: return
        val configService = com.runestone.app.data.GameConfigService(activity, workspaceManager)
        val config = configService.loadPerGame(storageName)

        showOverlay(
            PerGameSettingsScreen(activity).create(
                gameTitle = game.displayName,
                config = config,
                storageName = storageName,
                onConfigChanged = { newConfig ->
                    configService.savePerGame(storageName, newConfig)
                },
                onBack = { dismissOverlay() },
                onPickCover = { },
                onFetchMetadata = { },
                onInstallPatch = { },
                onDeleteGame = {
                    showDeleteGameConfirmDialog(storageName, game.displayName)
                },
            ),
        )
    }

    fun showDeleteGameConfirmDialog(storageName: String, gameTitle: String) {
        val wrapper = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(218, 0, 0, 0))
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(248, 18, 17, 22))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(80, 220, 80, 80))
            }
            elevation = dp(8).toFloat()
        }
        val cardLp = FrameLayout.LayoutParams(
            (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        cardLp.gravity = Gravity.CENTER

        card.addView(TextView(activity).apply {
            text = "Delete $gameTitle?"
            setTextColor(Color.rgb(232, 229, 220))
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        })

        card.addView(TextView(activity).apply {
            text = "This removes all installed game files. What about your save games?"
            setTextColor(Color.rgb(170, 160, 145))
            textSize = 13f
            setPadding(0, 0, 0, dp(20))
        })

        fun makeButton(label: String, bg: Int, stroke: Int, fg: Int, onClick: () -> Unit) {
            val btn = TextView(activity).apply {
                text = label
                setTextColor(fg)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(11), dp(16), dp(11))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), stroke)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    animate().scaleX(0.97f).scaleY(0.97f).setDuration(60).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        dismissOverlay { onClick() }
                    }.start()
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(8)
            card.addView(btn, lp)
        }

        val redSoft = Color.argb(45, 220, 80, 80)
        val redStroke = Color.argb(90, 220, 80, 80)
        val greenSoft = Color.argb(40, 110, 180, 120)
        val greenStroke = Color.argb(85, 110, 180, 120)
        val neutralSoft = Color.argb(35, 140, 130, 112)
        val neutralStroke = Color.argb(70, 140, 130, 112)

        makeButton("KEEP SAVES", greenSoft, greenStroke, Color.rgb(180, 230, 190)) {
            callbacks.performDeleteGame(storageName, gameTitle, keepSaves = true)
        }
        makeButton("DELETE FULLY", redSoft, redStroke, Color.rgb(255, 200, 200)) {
            callbacks.performDeleteGame(storageName, gameTitle, keepSaves = false)
        }
        makeButton("Cancel", neutralSoft, neutralStroke, Color.rgb(200, 195, 180)) {
        }

        wrapper.addView(card, cardLp)

        card.alpha = 0f
        card.translationY = dp(20).toFloat()
        wrapper.alpha = 0f
        card.animate().alpha(1f).translationY(0f).setDuration(200).start()
        wrapper.animate().alpha(1f).setDuration(180).start()

        activeOverlay?.let { rootContainer.removeView(it); activeOverlay = null }
        rootContainer.addView(
            wrapper,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        persistentDock?.bringToFront()
        activeOverlay = wrapper
    }

    fun renderAvailableGamesScreen() {
        val titles = installedStoreKeys()
        showOverlay(
            AvailableGamesScreen(activity).create(
                games = storeCoordinator.availableGames,
                isLoading = storeCoordinator.isLoadingGames,
                isMetadataLoading = storeCoordinator.storeMetadataLoading || storeCoordinator.storeMetadataInFlight.isNotEmpty(),
                errorMessage = storeCoordinator.gamesErrorMessage,
                downloadStates = storeCoordinator.downloadProgressMap,
                installStates = storeCoordinator.installProgressMap,
                installedGameTitles = titles,
                gridColumns = storeCoordinator.storeGridColumns,
                initialScrollY = storeCoordinator.availableGamesScrollY,
                onScrollYChanged = { storeCoordinator.availableGamesScrollY = it },
                onGridColumnsChanged = { columns ->
                    storeCoordinator.storeGridColumns = columns.coerceIn(1, 4)
                    renderAvailableGamesScreen()
                },
                onRefresh = { storeCoordinator.showAvailableGames() },
                onManageSources = { showSources() },
                onProviderSettings = { showProviderSettings() },
                onDownload = { storeCoordinator.handleDownload(it) },
                onPauseDownload = { storeCoordinator.handlePauseDownload(it) },
                onBack = { dismissOverlay() },
                onOpenDetail = { game -> showGameDetail(game) },
            ),
        )
    }

    fun showGameDetail(game: AvailableGame) {
        val titles = installedStoreKeys()
        detailOverlay = GameDetailOverlay.show(
            context = activity,
            game = game,
            progress = storeCoordinator.downloadProgressMap[game.id],
            installProgress = storeCoordinator.installProgressMap[game.id],
            installedGameTitles = titles,
            onDownload = { storeCoordinator.handleDownload(it) },
            onPauseDownload = { storeCoordinator.handlePauseDownload(it) },
            onClose = { _ ->
                detailOverlay = null
                callbacks.refreshGames()
            },
        )
    }

    fun pushDetailOverlayUpdate(gameId: String) {
        val overlay = detailOverlay ?: return
        val game = storeCoordinator.availableGames.firstOrNull { it.id == gameId } ?: return
        overlay.update(
            game = game,
            progress = storeCoordinator.downloadProgressMap[gameId],
            installProgress = storeCoordinator.installProgressMap[gameId],
            installedGameTitles = installedStoreKeys(),
        )
    }

    fun showSources() {
        manageFilesVisible = false
        showOverlay(
            SourcesScreen(activity).create(
                sources = sourcesManager.getSources(),
                onAddSource = { url ->
                    runCatching { sourcesManager.addSource(url) }
                        .onFailure { Toast.makeText(activity, it.message ?: "Invalid source URL", Toast.LENGTH_SHORT).show() }
                    showSources()
                },
                onRemoveSource = { id ->
                    sourcesManager.removeSource(id)
                    showSources()
                },
                onBack = { dismissOverlay() },
            ),
        )
    }

    fun showProviderSettings() {
        manageFilesVisible = false
        showOverlay(
            ProviderSettingsScreen(activity).create(
                sources = sourcesManager.getSources(),
                onBack = { dismissOverlay() },
                onUsePublicCatalogue = {
                    runCatching { sourcesManager.addPublicCatalogue() }
                        .onFailure {
                            Toast.makeText(
                                activity,
                                it.message ?: "Invalid catalogue URL",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    showProviderSettings()
                },
                onManageSources = { showSources() },
                onClearAll = {
                    sourcesManager.clearSources()
                    showProviderSettings()
                },
            ),
        )
    }

    fun showImportProgress(message: String) {
        Log.i(TAG, "showImportProgress: $message")
        importManager.importMessage = message
        val progressView = ImportProgressScreen(activity).create(title = message)
        importManager.activeImportProgressView = progressView
        showOverlay(progressView.root, dismissOnBgClick = false)
    }

    fun showEnginePicker(storageName: String) {
        val game = games.find { it.storageName == storageName }
        val currentEngine = game?.engineType
        val engines = EngineType.values().filter { it != EngineType.UNKNOWN }
        val items = engines.map { "${it.label} (${it.name})" }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Change Engine - ${game?.displayName ?: storageName}")
            .setSingleChoiceItems(items, engines.indexOf(currentEngine)) { dialog, which ->
                val selected = engines[which]
                Log.i(TAG, "Engine override: $storageName -> $selected")
                val state = installStateStore.load(storageName) ?: com.runestone.app.workspace.GameInstallState(
                    storageName = storageName,
                    engineType = selected,
                    fileCount = game?.fileCount ?: 0,
                    importedAtMillis = System.currentTimeMillis(),
                )
                installStateStore.save(state.copy(engineOverride = selected))
                importManager.importMessage = "Engine set to ${selected.label}. Relaunch to apply."
                callbacks.refreshGames()
                showManageFiles()
                dialog.dismiss()
            }
            .setNegativeButton("Reset to Auto", null)
            .show()
    }

    fun viewSaves(storageName: String) {
        val saves = saveManager.listSaves(storageName)
        val gameTitle = games.find { it.storageName == storageName }?.displayName ?: storageName
        val message = if (saves.isEmpty()) {
            "No save files were detected yet."
        } else {
            saves.joinToString("\n") { "${it.name} (${formatBytes(it.length())})" }
        }
        AlertDialog.Builder(activity)
            .setTitle("Save Files - $gameTitle")
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setPositiveButton("Actions") { _, _ ->
                showSaveActions(storageName, gameTitle)
            }
            .show()
    }

    fun showSaveActions(storageName: String, gameTitle: String) {
        val actions = arrayOf(
            "Sync protected copy",
            "Backup now",
            "Restore protected saves",
            "Export ZIP",
            "Import ZIP",
            "View backups",
        )
        AlertDialog.Builder(activity)
            .setTitle("Save Actions - $gameTitle")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        val count = saveManager.syncFromActive(storageName)
                        Toast.makeText(activity, "Synced $count save files into protected storage", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val result = saveManager.backupSaves(storageName, "manual")
                        val message = if (result.count > 0) {
                            "Backed up ${result.count} save files"
                        } else {
                            "No save files detected"
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                    2 -> confirmRestoreSaves(storageName, gameTitle)
                    3 -> showSaveExportPicker(storageName)
                    4 -> showSaveImportPicker(storageName)
                    5 -> showSaveBackups(storageName, gameTitle)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun confirmRestoreSaves(storageName: String, gameTitle: String) {
        AlertDialog.Builder(activity)
            .setTitle("Restore saves to $gameTitle?")
            .setMessage("Protected saves will be copied back into the installed game folder and may overwrite matching live save files.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                val count = saveManager.restoreToActive(storageName)
                Toast.makeText(activity, "Restored $count save files", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    fun showSaveExportPicker(storageName: String) {
        importManager.pendingSaveExportStorage = storageName
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        val filename = "${storageName}-saves-$stamp.zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        activity.startActivityForResult(intent, ImportManager.REQUEST_SAVE_EXPORT_ZIP)
    }

    fun showSaveImportPicker(storageName: String) {
        importManager.pendingSaveImportStorage = storageName
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        activity.startActivityForResult(intent, ImportManager.REQUEST_SAVE_IMPORT_ZIP)
    }

    fun showSaveBackups(storageName: String, gameTitle: String) {
        val backups = saveManager.listSaveBackups(storageName)
        val message = if (backups.isEmpty()) {
            "No save backups have been created yet."
        } else {
            backups.joinToString("\n") {
                "${it.name}: ${it.fileCount} files (${formatBytes(it.bytes)})"
            }
        }
        AlertDialog.Builder(activity)
            .setTitle("Save Backups - $gameTitle")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    fun confirmRemoveGameData(storageName: String) {
        val game = games.find { it.storageName == storageName }
        val name = game?.displayName ?: storageName
        AlertDialog.Builder(activity)
            .setTitle("Remove $name data?")
            .setMessage("This deletes the game files. Saves are kept in protected storage.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove data") { _, _ ->
                saveManager.syncFromActive(storageName)
                workspaceManager.removeGame(storageName, keepSaves = true)
                importManager.importMessage = "Removed $name game data. Saves were kept."
                callbacks.refreshGames()
                showManageFiles()
            }
            .show()
    }

    // ═══════════════════════════════════════════════════════
    //  RTP download overlay
    // ═══════════════════════════════════════════════════════

    fun showRtpDownloadDialog(storageName: String, missing: List<com.runestone.app.rtp.RtpPack>) {
        if (missing.isEmpty()) return

        val pack = missing.first()
        val totalBytes = pack.approxBytes
        val sizeMb = totalBytes / 1024 / 1024

        val eulaMessage = buildString {
            append("This game uses the ").append(pack.displayName).append(",\n")
            append("which isn't installed on your device.\n\n")
            append("Size: ~").append(sizeMb).append(" MB (downloaded once, shared with all games)\n\n")
            append("By tapping DOWNLOAD, you confirm that you have read and agree to the ")
                .append("Enterbrain/Kadokawa End User License Agreement for the ")
                .append("RPG Maker Runtime Packages.\n\n")
            append("Source: ").append(pack.sourceAttribution).append("\n")
            append("URL: ").append(pack.sourceUrl)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Runtime Package Required")
            .setMessage(eulaMessage)
            .setPositiveButton("DOWNLOAD") { _, _ ->
                startRtpDownload(storageName, pack)
            }
            .setNegativeButton("LATER") { d, _ ->
                d.dismiss()
                showHome()
            }
            .setCancelable(true)
            .create()
        dialog.show()
    }

    fun startRtpDownload(storageName: String, pack: com.runestone.app.rtp.RtpPack) {
        Log.i(TAG, "Starting RTP download: ${pack.id} for game=$storageName")
        val installer = com.runestone.app.rtp.RtpInstaller(activity)
        showRtpDownloadProgressOverlay(pack)
        installer.install(pack, object : com.runestone.app.rtp.RtpInstaller.Listener {
            override fun onStatus(status: com.runestone.app.rtp.RtpInstaller.Status) {
                activity.runOnUiThread { handleRtpStatus(pack, status) }
            }
        })
    }

    fun handleRtpStatus(pack: com.runestone.app.rtp.RtpPack, status: com.runestone.app.rtp.RtpInstaller.Status) {
        when (status) {
            is com.runestone.app.rtp.RtpInstaller.Status.Downloading -> {
                val pct = if (status.total > 0) (status.bytes.toFloat() / status.total * 100).toInt() else 0
                rtpOverlayStatusText?.text = "Downloading ${pack.displayName}\n$pct%  (${status.bytes / 1024 / 1024} MB / ${status.total / 1024 / 1024} MB)"
                rtpOverlayProgressBar?.progress = pct
            }
            is com.runestone.app.rtp.RtpInstaller.Status.Extracting -> {
                rtpOverlayStatusText?.text = "Extracting ${pack.displayName}..."
                rtpOverlayProgressBar?.progress = 100
            }
            is com.runestone.app.rtp.RtpInstaller.Status.Installed -> {
                rtpOverlayStatusText?.text = "${pack.displayName} ready."
                rtpOverlayProgressBar?.progress = 100
                Toast.makeText(
                    activity,
                    "RTP installed. You can now launch the game.",
                    Toast.LENGTH_LONG,
                ).show()
                dismissRtpDownloadOverlay()
            }
            is com.runestone.app.rtp.RtpInstaller.Status.Error -> {
                rtpOverlayStatusText?.text = "RTP download failed:\n${status.message}"
                rtpOverlayProgressBar?.progress = 0
                Toast.makeText(
                    activity,
                    "RTP download failed: ${status.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
            else -> Unit
        }
    }

    fun showRtpDownloadProgressOverlay(pack: com.runestone.app.rtp.RtpPack) {
        dismissRtpDownloadOverlay()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val title = TextView(activity).apply {
            text = "Runtime Package"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }
        val status = TextView(activity).apply {
            text = "Downloading ${pack.displayName}..."
            setPadding(0, 16, 0, 16)
        }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        container.addView(title)
        container.addView(status)
        container.addView(progress)
        rtpOverlayStatusText = status
        rtpOverlayProgressBar = progress

        val dialog = AlertDialog.Builder(activity)
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("HIDE") { d, _ -> d.dismiss() }
            .create()
        dialog.show()
        activeRtpDialog = dialog
    }

    fun dismissRtpDownloadOverlay() {
        activeRtpDialog?.dismiss()
        activeRtpDialog = null
        rtpOverlayStatusText = null
        rtpOverlayProgressBar = null
    }

    // ═══════════════════════════════════════════════════════
    //  Utility methods
    // ═══════════════════════════════════════════════════════

    fun clearRuntimeCache() {
        val runtimeDir = File(activity.filesDir, "runtime")
        if (runtimeDir.exists()) {
            runtimeDir.deleteRecursively()
        }
        val cacheDir = File(activity.filesDir, "cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    fun cycleSortMode() {
        currentSort = when (currentSort) {
            SortMode.DATE_ADDED -> SortMode.NAME_ASC
            SortMode.NAME_ASC -> SortMode.NAME_DESC
            SortMode.NAME_DESC -> SortMode.RECENT
            SortMode.RECENT -> SortMode.DATE_ADDED
        }
        Toast.makeText(activity, "Sort: ${sortLabel(currentSort)}", Toast.LENGTH_SHORT).show()
        showHome()
    }

    fun cycleEngineFilter() {
        val installedEngines = games.map { it.engineType }
            .filter { it != EngineType.UNKNOWN }
            .distinct()
            .sortedBy { it.label }
        activeEngineFilter = if (installedEngines.isEmpty()) {
            null
        } else {
            val currentIndex = installedEngines.indexOf(activeEngineFilter)
            if (currentIndex < 0) installedEngines.first()
            else installedEngines.getOrNull(currentIndex + 1)
        }
        Toast.makeText(activity, "Filter: ${activeEngineFilter?.label ?: "All games"}", Toast.LENGTH_SHORT).show()
        showHome()
    }

    fun cycleCardLayout() {
        homeCardLayout = homeCardLayout.next()
        activity.getSharedPreferences("runestone-settings-v1", Activity.MODE_PRIVATE)
            .edit()
            .putString("homeCardLayout", homeCardLayout.name)
            .apply()
        Toast.makeText(activity, "Layout: ${homeCardLayout.name.lowercase().replace('_', ' ')}", Toast.LENGTH_SHORT).show()
        showHome()
    }

    fun sortLabel(sort: SortMode): String = when (sort) {
        SortMode.NAME_ASC -> "Name A-Z"
        SortMode.NAME_DESC -> "Name Z-A"
        SortMode.RECENT -> "Recently played"
        SortMode.DATE_ADDED -> "Date added"
    }

    fun installedStoreKeys(): Set<String> {
        return games.flatMap { game ->
            listOf(game.displayName, game.storageName)
        }.toSet()
    }

    fun formatBytes(bytes: Long): String {
        val gb = 1024.0 * 1024.0 * 1024.0
        val mb = 1024.0 * 1024.0
        val kb = 1024.0
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Internal helpers
    // ═══════════════════════════════════════════════════════

    private fun toCardInfo(g: WorkspaceManager.GameInfo): GameCardInfo {
        val perGame = runCatching {
            com.runestone.app.data.GameConfigService(activity, workspaceManager).loadPerGame(g.storageName)
        }.getOrNull()
        val metadata = perGame?.metadata?.takeIf {
            it.gameTitle.isBlank() || metadataTitleMatches(g.displayName, it.gameTitle)
        }

        val customCoverPath = perGame?.game?.customCoverPath?.let { path ->
            if (File(path).exists()) return@let "local:$path"
            null
        }
        val metadataCoverPath = metadata?.localCoverPath?.takeIf { it.isNotEmpty() }?.let { path ->
            if (File(path).exists()) return@let "local:$path"
            null
        }
        val coverUrl = customCoverPath ?: metadataCoverPath

        val pausedStorage = activity.getSharedPreferences("runestone", Activity.MODE_PRIVATE)
            .getString("paused_game", null)
        val isPaused = pausedStorage != null && g.originalPath == pausedStorage

        return GameCardInfo(
            storageName = g.storageName,
            displayName = metadata?.gameTitle?.takeIf { it.isNotEmpty() } ?: g.displayName,
            engineType = g.engineType,
            fileCount = g.fileCount,
            fileSize = cachedGameSize(g),
            totalPlayTime = sessionManager.getPlayTime(g.storageName),
            lastPlayedTimestamp = sessionManager.getLastPlayed(g.storageName),
            isReady = true,
            isPaused = isPaused,
            coverUrl = coverUrl,
            metadataDeveloper = metadata?.developer ?: "",
            metadataGenres = metadata?.genres ?: "",
            metadataYear = metadata?.releaseYear ?: "",
        )
    }

    private fun metadataTitleMatches(installedTitle: String, metadataTitle: String): Boolean {
        val installed = normalizedTitle(installedTitle)
        val metadata = normalizedTitle(metadataTitle)
        if (installed.isBlank() || metadata.isBlank()) return false
        if (installed == metadata) return true
        if (installed.length >= 6 && (installed.contains(metadata) || metadata.contains(installed))) return true
        val installedTokens = installed.split(" ").filter { it.length > 1 }.toSet()
        val metadataTokens = metadata.split(" ").filter { it.length > 1 }.toSet()
        if (installedTokens.isEmpty()) return false
        return installedTokens.intersect(metadataTokens).size >= minOf(2, installedTokens.size)
    }

    private fun cachedGameSize(g: WorkspaceManager.GameInfo): Long =
        gameSizeCacheMem[g.storageName] ?: run { warmGameSize(g); 0L }

    private fun warmGameSize(g: WorkspaceManager.GameInfo) {
        if (!gameSizeInFlight.add(g.storageName)) return
        com.runestone.app.util.AppScope.io.launch {
            val size = runCatching {
                File(g.originalPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
            gameSizeCacheMem[g.storageName] = size
            gameSizeInFlight.remove(g.storageName)
            gameSizeCacheDao.upsert(GameSizeCacheEntity(g.storageName, size))
        }
    }

    private fun normalizedTitle(value: String): String =
        value.lowercase()
            .replace("&", " and ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun scheduleMetadataWarmup(cards: List<GameCardInfo>) {
        val targets = cards
            .asSequence()
            .filter { it.coverUrl == null }
            .filter { !gameMetadataCache.containsKey(it.displayName) }
            .filter { it.storageName !in metadataWarmupInFlight }
            .take(3)
            .toList()
        if (targets.isEmpty()) return
        targets.forEach { metadataWarmupInFlight.add(it.storageName) }
        rootContainer.postDelayed({
            val configService = com.runestone.app.data.GameConfigService(activity, workspaceManager)
            targets.forEach { card ->
                metadataService.fetchAndApplyMetadata(
                    gameTitle = card.displayName,
                    storageName = card.storageName,
                    configService = configService,
                ) { section ->
                    activity.runOnUiThread {
                        metadataWarmupInFlight.remove(card.storageName)
                        section?.let {
                            gameMetadataCache[card.displayName] = GameMetadataService.GameMetadata(
                                title = it.gameTitle,
                                description = it.description,
                                coverUrl = it.coverUrl,
                                localCoverPath = it.localCoverPath,
                                screenshots = emptyList(),
                                releaseDate = it.releaseYear,
                                developer = it.developer,
                                publisher = it.publisher,
                                genres = it.genres.split(",").map { genre -> genre.trim() }.filter { genre -> genre.isNotEmpty() },
                                rating = null,
                                source = it.metadataSource,
                            )
                        }
                    }
                }
            }
        }, 750L)
    }

    private fun refreshStorageReport() {
        val snapshot = games.map { it.storageName }
        AppScope.io.launch {
            val report = snapshot.associateWith { storageReporter.collect(it) }
            withContext(Dispatchers.Main) {
                storageCache = report
                if (manageFilesVisible) {
                    val mgGames = games.map { ManageFilesScreen.GameInfo(it.storageName, it.displayName, it.engineType, it.fileCount) }
                    showOverlay(
                        ManageFilesScreen(activity).create(
                            games = mgGames,
                            storageByGame = storageCache,
                            isStorageRefreshing = false,
                            importMessage = importManager.importMessage,
                            onImport = { sName -> importManager.startFolderImport(sName) },
                            onDelete = { sName -> confirmRemoveGameData(sName) },
                            onViewSaves = { sName -> viewSaves(sName) },
                            onChangeEngine = { sName -> showEnginePicker(sName) },
                            onPerGameSettings = { sName -> showPerGameSettings(sName) },
                            onBack = { dismissOverlay() },
                        ),
                    )
                }
            }
        }
    }

    fun enableControllerNavigation(root: View) {
        val clickables = mutableListOf<View>()
        fun visit(view: View) {
            if (view.isClickable && view.visibility == View.VISIBLE) {
                view.isFocusable = true
                view.isFocusableInTouchMode = false
                clickables += view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
    }
}

