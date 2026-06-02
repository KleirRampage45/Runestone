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
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.runestone.app.data.EngineType
import com.runestone.app.data.RunnerSettings
import com.runestone.app.data.UIMode
import com.runestone.app.engine.EngineRegistry
import com.runestone.app.ui.SortMode
import com.runestone.app.importer.SafGameImporter
import com.runestone.app.importer.SafImportResult
import com.runestone.app.ui.AvailableGamesScreen
import com.runestone.app.ui.GameCardInfo
import com.runestone.app.ui.HomeScreen
import com.runestone.app.ui.ImportProgressScreen
import com.runestone.app.ui.ImportProgressView
import com.runestone.app.ui.ManageFilesScreen
import com.runestone.app.ui.PerGameSettingsScreen
import com.runestone.app.ui.ProviderSettingsScreen
import com.runestone.app.ui.SettingsScreen
import com.runestone.app.ui.SettingsStore
import com.runestone.app.ui.SourcesScreen
import com.runestone.app.services.GameMetadataService
import com.runestone.app.provider.AvailableGame
import com.runestone.app.provider.DownloadManager
import com.runestone.app.provider.ExtractionManager
import com.runestone.app.provider.SourcesManager
import com.runestone.app.workspace.GameInstallState
import com.runestone.app.workspace.InstallStateStore
import com.runestone.app.workspace.SaveManager
import com.runestone.app.workspace.WorkspaceManager
import com.runestone.app.workspace.WorkspaceStorage
import com.runestone.app.workspace.WorkspaceStorageReporter
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class MainActivity : Activity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var installStateStore: InstallStateStore
    private lateinit var saveManager: SaveManager
    private lateinit var storageReporter: WorkspaceStorageReporter
    private lateinit var sourcesManager: SourcesManager
    private lateinit var downloadManager: DownloadManager
    private lateinit var extractionManager: ExtractionManager
    private lateinit var metadataService: GameMetadataService
    private var settings = RunnerSettings()
    private var games: List<WorkspaceManager.GameInfo> = emptyList()
    private var gameMetadataCache: MutableMap<String, GameMetadataService.GameMetadata> = mutableMapOf()
    private var importMessage: String? = null
    private var activeImportProgressView: ImportProgressView? = null
    private var manageFilesVisible = false
    private var storageCache: Map<String, WorkspaceStorage> = emptyMap()
    private var pendingImportStorage: String? = null
    private var downloadProgressMap = mutableMapOf<String, DownloadManager.DownloadProgress>()

    // Overlay navigation - root container set once, overlays added on top
    private lateinit var rootContainer: FrameLayout
    private var activeOverlay: View? = null
    private var homeContentView: View? = null
    private lateinit var persistentDock: View

    companion object {
        private const val REQUEST_IMPORT_FOLDER = 9001
        private const val TAG = "Runestone"
        private const val NOTIFICATION_CHANNEL = "runestone_downloads"
        private const val NOTIFICATION_ID_DOWNLOAD = 2001
    }

    private var pausedGamePath: String? = null
    private var initialLaunch = true
    private var activeEngineFilter: EngineType? = null
    private var currentSort: SortMode = SortMode.DATE_ADDED
    private var searchQuery: String = ""
    private var availableGames: List<AvailableGame> = emptyList()
    private var isLoadingGames = false
    private var gamesErrorMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        // Check for paused game from SharedPreferences — load for display,
        // then clear immediately since the game activity is dead after fresh onCreate
        pausedGamePath = getSharedPreferences("runestone", MODE_PRIVATE)
            .getString("paused_game", null)
        getSharedPreferences("runestone", MODE_PRIVATE).edit()
            .remove("paused_game").apply()
        settingsStore = SettingsStore(this)
        workspaceManager = WorkspaceManager(this)
        installStateStore = InstallStateStore(workspaceManager)
        saveManager = SaveManager(workspaceManager)
        storageReporter = WorkspaceStorageReporter(workspaceManager)
        sourcesManager = SourcesManager(this)
        downloadManager = DownloadManager(this)
        extractionManager = ExtractionManager(this)
        metadataService = GameMetadataService(this)
        settings = settingsStore.load()
        refreshGames()
        createNotificationChannel()
        setupDownloadCallbacks()

        // Create permanent root frame - setContentView ONCE
        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.rgb(3, 3, 4))
        }
        setContentView(rootContainer)
        showSplash()
        persistentDock = HomeScreen(this).createDockBar(
            onHome = { dismissOverlay() },
            onAdd = { startFolderImport() },
            onBrowse = { showAvailableGames() },
            onManage = { showManageFiles() },
            onSettings = { showSettings() },
        )
        rootContainer.addView(persistentDock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(8))
        })
    }

    private fun refreshGames() {
        games = workspaceManager.scanInstalledGames()
        Log.i(TAG, "refreshGames: found ${games.size} games")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Game download progress"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupDownloadCallbacks() {
        downloadManager.setCallback(object : DownloadManager.DownloadCallback {
            override fun onProgress(gameId: String, progress: DownloadManager.DownloadProgress) {
                runOnUiThread {
                    downloadProgressMap[gameId] = progress
                    showDownloadNotification(gameId, progress)
                    if (activeOverlay != null) renderAvailableGamesScreen()
                }
            }

            override fun onComplete(gameId: String, filePath: String) {
                runOnUiThread {
                    downloadProgressMap[gameId] = DownloadManager.DownloadProgress(
                        bytesDownloaded = 0, totalBytes = 0, speed = 0f,
                        state = DownloadManager.DownloadState.COMPLETED
                    )
                    showInstallNotification(gameId)
                    startExtraction(gameId, filePath)
                }
            }

            override fun onError(gameId: String, message: String) {
                runOnUiThread {
                    downloadProgressMap[gameId] = DownloadManager.DownloadProgress(
                        bytesDownloaded = 0, totalBytes = 0, speed = 0f,
                        state = DownloadManager.DownloadState.FAILED, error = message
                    )
                    showErrorNotification(gameId, message)
                    if (activeOverlay != null) renderAvailableGamesScreen()
                }
            }
        })
    }

    private fun showDownloadNotification(gameId: String, progress: DownloadManager.DownloadProgress) {
        val percent = if (progress.totalBytes > 0) {
            (progress.bytesDownloaded * 100 / progress.totalBytes).toInt()
        } else 0

        val game = availableGames.find { it.id == gameId }
        val title = game?.title ?: gameId

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText("$percent%")
            .setOngoing(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_DOWNLOAD + gameId.hashCode() % 100, notification)
    }

    private fun showInstallNotification(gameId: String) {
        val game = availableGames.find { it.id == gameId }
        val title = game?.title ?: gameId

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText("$title — extracting...")
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_DOWNLOAD + gameId.hashCode() % 100, notification)
    }

    private fun showErrorNotification(gameId: String, error: String) {
        val game = availableGames.find { it.id == gameId }
        val title = game?.title ?: gameId

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("$title: $error")
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_DOWNLOAD + gameId.hashCode() % 100, notification)
    }

    private fun startExtraction(gameId: String, zipPath: String) {
        val game = availableGames.find { it.id == gameId } ?: return
        val outputDir = workspaceManager.allocateGameDir(game.title)

        extractionManager.extract(zipPath, outputDir, object : ExtractionManager.ExtractionCallback {
            override fun onProgress(progress: ExtractionManager.ExtractionProgress) {
                Log.d(TAG, "Extracting: ${progress.currentFile} (${progress.filesExtracted}/${progress.totalFiles})")
                val notification = Notification.Builder(this@MainActivity, NOTIFICATION_CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Extracting game files")
                    .setContentText("${progress.currentFile} (${progress.filesExtracted}/${progress.totalFiles})")
                    .setOngoing(true)
                    .build()
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID_DOWNLOAD, notification)
            }

            override fun onComplete(result: ExtractionManager.ExtractionResult) {
                runOnUiThread {
                    try {
                        val gameDir = finalizeDownloadedGame(result)
                        File(zipPath).delete()
                        Log.i(TAG, "Deleted ZIP: $zipPath")

                        downloadManager.cleanup(gameId)
                        downloadProgressMap.remove(gameId)
                        refreshGames()
                        dismissOverlay()
                        Toast.makeText(this@MainActivity, "${gameDir.name} installed!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Installation failed", e)
                        discardFailedInstall(gameId, zipPath, result.outputDir, e.message ?: "Installation failed")
                    }
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Log.e(TAG, "Extraction failed: $message")
                    discardFailedInstall(gameId, zipPath, outputDir, "Extraction failed: $message")
                }
            }
        })
    }

    private fun finalizeDownloadedGame(result: ExtractionManager.ExtractionResult): File {
        val engine = EngineRegistry.detect(result.gameRoot)
        requireNotNull(engine) { "Could not detect a supported game engine" }
        val engineType = EngineType.fromEngineId(engine.id)
        require(engineType != EngineType.UNKNOWN) { "Could not detect a supported game engine" }
        Log.i(TAG, "Detected engine: $engineType for ${result.gameRoot.name}")

        val gameDir = result.outputDir
        val originalDir = File(gameDir, "original")
        require(!originalDir.exists()) { "Install workspace already contains original files" }

        if (result.gameRoot.canonicalFile == gameDir.canonicalFile) {
            val extractedFiles = gameDir.listFiles()?.toList().orEmpty()
            originalDir.mkdirs()
            extractedFiles.forEach { file ->
                require(file.renameTo(File(originalDir, file.name))) {
                    "Could not move ${file.name} into the installed game"
                }
            }
        } else {
            require(result.gameRoot.renameTo(originalDir)) {
                "Could not move extracted game files into the install workspace"
            }
        }

        workspaceManager.ensureWorkspace(gameDir.name)
        workspaceManager.rebuildActiveWorkspace(gameDir.name)

        val fileCount = originalDir.walkTopDown().count { it.isFile }
        File(gameDir, "manifest.json").writeText(JSONObject().apply {
            put("storageName", gameDir.name)
            put("engineType", engineType.name)
            put("engineLabel", engineType.label)
            put("fileCount", fileCount)
            put("importedAt", System.currentTimeMillis())
        }.toString(2))

        return gameDir
    }

    private fun discardFailedInstall(gameId: String, zipPath: String, outputDir: File, message: String) {
        outputDir.deleteRecursively()
        File(zipPath).delete()
        downloadManager.cleanup(gameId)
        downloadProgressMap[gameId] = DownloadManager.DownloadProgress(
            bytesDownloaded = 0, totalBytes = 0, speed = 0f,
            state = DownloadManager.DownloadState.FAILED, error = message,
        )
        if (activeOverlay != null) renderAvailableGamesScreen()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleDownload(game: AvailableGame) {
        val url = game.downloadUrl ?: return
        val fileName = "${sha256(game.id).take(32)}.zip"
        downloadManager.setFileName(game.id, fileName)
        if (downloadManager.getState(game.id) == DownloadManager.DownloadState.PAUSED) {
            downloadManager.resumeDownload(game.id, url, fileName)
        } else {
            downloadManager.startDownload(game.id, url, fileName)
        }
        downloadProgressMap[game.id] = DownloadManager.DownloadProgress(
            bytesDownloaded = 0, totalBytes = 0, speed = 0f,
            state = DownloadManager.DownloadState.DOWNLOADING
        )
        renderAvailableGamesScreen()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun handlePauseDownload(gameId: String) {
        downloadManager.pauseDownload(gameId)
        downloadProgressMap[gameId] = DownloadManager.DownloadProgress(
            bytesDownloaded = downloadManager.getDownloadedBytes(gameId),
            totalBytes = downloadManager.getTotalBytes(gameId),
            speed = 0f,
            state = DownloadManager.DownloadState.PAUSED
        )
        renderAvailableGamesScreen()
    }

    private fun toCardInfo(g: WorkspaceManager.GameInfo) = GameCardInfo(
        storageName = g.storageName,
        displayName = g.displayName,
        engineType = g.engineType,
        fileCount = g.fileCount,
        isReady = true,
        isPaused = pausedGamePath == g.originalPath,
    )

    // ═══════════════════════════════════════════════════════
    //  Overlay system — dimmed panels over home screen
    // ═══════════════════════════════════════════════════════

    /**
     * Adds a dim overlay on top of the home screen containing [panel].
     * [panel] fills the available area with margins so the dock stays visible.
     * [dismissOnBgClick] controls whether tapping the dim background dismisses.
     */
    private fun showOverlay(panel: View, dismissOnBgClick: Boolean = true) {
        // Remove any existing overlay
        activeOverlay?.let { rootContainer.removeView(it); activeOverlay = null }

        val wrapper = FrameLayout(this).apply {
            // Semi-transparent black dims the home screen underneath
            setBackgroundColor(Color.argb(200, 0, 0, 0))

            // Start below final position so it slides up while fading in
            alpha = 0f
            translationY = resources.displayMetrics.heightPixels * 0.08f
            animate().alpha(1f).translationY(0f).setDuration(250).start()

            // Panel fills available space with margins so the dock peeks through
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            lp.setMargins(dp(8), dp(20), dp(8), dp(74))
            addView(panel, lp)

            // Prevent clicks on the panel from reaching the dim bg
            panel.isClickable = true

            // Tap on dim background to dismiss overlay
            if (dismissOnBgClick) {
                setOnClickListener { dismissOverlay() }
            }
        }
        rootContainer.addView(wrapper,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
        persistentDock.bringToFront()
        activeOverlay = wrapper
    }

    /**
     * Fades out the active overlay, removes it, then runs [onDismissed].
     * Default callback refreshes the home screen.
     */
    private fun dismissOverlay(onDismissed: () -> Unit = { showHome() }) {
        activeOverlay?.let { overlay ->
            overlay.animate().alpha(0f).translationY(resources.displayMetrics.heightPixels * 0.08f).setDuration(200).withEndAction {
                rootContainer.removeView(overlay)
                activeOverlay = null
                onDismissed()
            }.start()
        }
    }

    /** Density-independent pixels helper. */
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showSplash() {
        val splash = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 3, 4))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            val titleText = TextView(this@MainActivity).apply {
                text = "RUNESTONE"
                setTextColor(Color.rgb(207, 174, 126)) // ACCENT color
                textSize = 32f
                typeface = Typeface.create("serif", Typeface.BOLD)
                letterSpacing = 0.3f
                gravity = Gravity.CENTER
            }
            addView(titleText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

            // Subtitle
            val sub = TextView(this@MainActivity).apply {
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

        // Fade in quickly, hold, then fade out and show home
        splash.animate().alpha(1f).setDuration(400).withEndAction {
            splash.postDelayed({
                splash.animate().alpha(0f).setDuration(400).withEndAction {
                    rootContainer.removeView(splash)
                    showHome()
                }.start()
            }, 600)
        }.start()
    }

    // ═══════════════════════════════════════════════════════
    //  Screen navigation
    // ═══════════════════════════════════════════════════════

    private fun showHome() {
        Log.i(TAG, "showHome")
        manageFilesVisible = false
        activeImportProgressView = null

        // Remove any displayed overlay
        activeOverlay?.let {
            rootContainer.removeView(it)
            activeOverlay = null
        }

        // Remove old home content
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
            SortMode.RECENT -> filtered // TODO: Track play history for sorting
            SortMode.DATE_ADDED -> filtered.sortedByDescending { java.io.File(it.originalPath).parentFile?.lastModified() ?: 0L }
        }
        val cards = filtered.map { toCardInfo(it) }.map { card ->
            // First try to find matching cover from available games by title
            val availableCoverUrl = availableGames.firstOrNull {
                it.title.equals(card.displayName, ignoreCase = true) ||
                it.title.contains(card.displayName, ignoreCase = true) ||
                card.displayName.contains(it.title, ignoreCase = true)
            }?.coverUrl
            
            // If not found in available games, check metadata cache
            val coverUrl = availableCoverUrl ?: gameMetadataCache[card.displayName]?.coverUrl
            
            card.copy(coverUrl = coverUrl)
        }
        
        // Fetch metadata for games that don't have cover URLs yet
        cards.filter { it.coverUrl == null }.forEach { card ->
            if (!gameMetadataCache.containsKey(card.displayName)) {
                metadataService.fetchMetadataAsync(card.displayName) { metadata ->
                    metadata?.let {
                        gameMetadataCache[card.displayName] = it
                        // Refresh the home screen to show the new metadata
                        runOnUiThread { showHome() }
                    }
                }
            }
        }
        val pausedGame = cards.find { it.isPaused }

        val homeView = HomeScreen(this).create(
            games = cards,
            onPlay = { playGame(it) },
            onManage = { showManageFiles(it) },
            onAddGame = { startFolderImport() },
            onBrowse = { showAvailableGames() },
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
            uiMode = settings.uiMode,
            onResume = if (pausedGame != null) {{ playGame(pausedGame.storageName) }} else null,
            onStop = if (pausedGame != null) {{ storageName ->
                val game = games.find { it.storageName == storageName }
                if (game != null) {
                    Log.i(TAG, "STOP game: $storageName path=${game.originalPath}")
                    pausedGamePath = null
                    getSharedPreferences("runestone", MODE_PRIVATE).edit()
                        .remove("paused_game").apply()
                    getSharedPreferences("runestone", MODE_PRIVATE).edit()
                        .putString("kill_game", storageName).apply()
                    refreshGames()
                    // Small delay to let game activity finish before showing home
                    rootContainer.postDelayed({
                        showHome()
                    }, 100)
                }
            }} else null,
        )
        // Add at index 0 so overlays (added later) sit on top
        rootContainer.addView(homeView, 0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        homeContentView = homeView
    }

    private fun showManageFiles(storageName: String? = null) {
        Log.i(TAG, "showManageFiles: focused=$storageName")
        manageFilesVisible = true
        val allGames = games.map { ManageFilesScreen.GameInfo(it.storageName, it.displayName, it.engineType, it.fileCount) }
        val mgGames = if (storageName != null) {
            allGames.filter { it.storageName == storageName }
        } else allGames
        showOverlay(
            ManageFilesScreen(this).create(
                games = mgGames,
                storageByGame = storageCache,
                isStorageRefreshing = false,
                importMessage = importMessage,
                onImport = { sName -> startFolderImport(sName) },
                onDelete = { sName -> confirmRemoveGameData(sName) },
                onViewSaves = { sName -> viewSaves(sName) },
                onChangeEngine = { sName -> showEnginePicker(sName) },
                onPerGameSettings = { sName -> showPerGameSettings(sName) },
                onBack = { dismissOverlay() },
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
                    showOverlay(
                        ManageFilesScreen(this).create(
                            games = mgGames,
                            storageByGame = storageCache,
                            isStorageRefreshing = false,
                            importMessage = importMessage,
                            onImport = { sName -> startFolderImport(sName) },
                            onDelete = { sName -> confirmRemoveGameData(sName) },
                            onViewSaves = { sName -> viewSaves(sName) },
                            onChangeEngine = { sName -> showEnginePicker(sName) },
                            onPerGameSettings = { sName -> showPerGameSettings(sName) },
                            onBack = { dismissOverlay() },
                        ),
                    )
                }
            }
        }.start()
    }

    private fun showSettings() {
        manageFilesVisible = false
        showOverlay(
            SettingsScreen(this).create(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    settings = newSettings
                    settingsStore.save(newSettings)
                },
                onBack = { dismissOverlay() },
            ),
        )
    }

    private fun showPerGameSettings(storageName: String) {
        manageFilesVisible = false
        val game = games.find { it.storageName == storageName } ?: return
        val configService = com.runestone.app.data.GameConfigService(this, workspaceManager)
        val config = configService.loadPerGame(storageName)
        
        showOverlay(
            PerGameSettingsScreen(this).create(
                gameTitle = game.displayName,
                config = config,
                onConfigChanged = { newConfig ->
                    configService.savePerGame(storageName, newConfig)
                },
                onBack = { dismissOverlay() },
            ),
        )
    }

    private fun showAvailableGames() {
        manageFilesVisible = false
        isLoadingGames = true
        gamesErrorMessage = null
        val installedTitles = games.map { it.displayName }.toSet()
        renderAvailableGamesScreen(installedGameTitles = installedTitles)

        sourcesManager.fetchGamesFromSources { games, error ->
            runOnUiThread {
                availableGames = games
                isLoadingGames = false
                gamesErrorMessage = error
                val installedTitles = this.games.map { it.displayName }.toSet()
                renderAvailableGamesScreen(installedGameTitles = installedTitles)
            }
        }
    }

    private fun renderAvailableGamesScreen(installedGameTitles: Set<String> = emptySet()) {
        showOverlay(
            AvailableGamesScreen(this).create(
                games = availableGames,
                isLoading = isLoadingGames,
                errorMessage = gamesErrorMessage,
                downloadStates = downloadProgressMap,
                installedGameTitles = installedGameTitles,
                onRefresh = { showAvailableGames() },
                onManageSources = { showSources() },
                onProviderSettings = { showProviderSettings() },
                onDownload = { handleDownload(it) },
                onPauseDownload = { handlePauseDownload(it) },
                onBack = { dismissOverlay() },
            ),
        )
    }

    private fun showSources() {
        manageFilesVisible = false
        showOverlay(
            SourcesScreen(this).create(
                sources = sourcesManager.getSources(),
                onAddSource = { url ->
                    runCatching { sourcesManager.addSource(url) }
                        .onFailure { Toast.makeText(this, it.message ?: "Invalid source URL", Toast.LENGTH_SHORT).show() }
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

    private fun showProviderSettings() {
        manageFilesVisible = false
        showOverlay(
            ProviderSettingsScreen(this).create(
                onBack = { dismissOverlay() },
                onClearAll = {
                    sourcesManager.clearSources()
                    showProviderSettings()
                },
            ),
        )
    }

    private fun showImportProgress(message: String) {
        Log.i(TAG, "showImportProgress: $message")
        importMessage = message
        val progressView = ImportProgressScreen(this).create(title = message)
        activeImportProgressView = progressView
        // No bg-click dismiss — an active import must not be dismissed
        showOverlay(progressView.root, dismissOnBgClick = false)
    }

    // ═══════════════════════════════════════════════════════
    //  Game operations
    // ═══════════════════════════════════════════════════════

    private fun playGame(storageName: String) {
        val game = games.find { it.storageName == storageName } ?: return

        if (pausedGamePath != null && pausedGamePath == game.originalPath) {
            Log.i(TAG, "RESUME: $storageName")
            pausedGamePath = null
            getSharedPreferences("runestone", MODE_PRIVATE).edit().remove("paused_game").apply()
            // Just finish this activity to bring GameActivity back to front
            finish()
            return
        }

        Log.i(TAG, "playGame: $storageName path=${game.originalPath}")
        pausedGamePath = game.originalPath
        getSharedPreferences("runestone", MODE_PRIVATE).edit()
            .putString("paused_game", game.originalPath).apply()

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
        }
        startActivity(intent)
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

    // ═══════════════════════════════════════════════════════
    //  Activity lifecycle
    // ═══════════════════════════════════════════════════════

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
                        // Fade out import overlay, show refreshed home
                        dismissOverlay()
                    }
                    is SafImportResult.Failure -> {
                        Log.e(TAG, "Import FAILED: ${result.reason}")
                        val pv = activeImportProgressView
                        if (pv != null) { pv.phaseView.text = "[FAIL] Import failed"; pv.fileView.text = result.reason; pv.countView.text = "" }
                        importMessage = "Import failed: ${result.reason}"
                        android.os.Handler(mainLooper).postDelayed({
                            refreshGames(); activeImportProgressView = null
                            dismissOverlay { showManageFiles() }
                        }, 3000)
                    }
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume importActive=${activeImportProgressView != null} initial=$initialLaunch overlay=${activeOverlay != null}")
        if (activeImportProgressView != null) return
        if (initialLaunch) {
            initialLaunch = false
            return
        }
        if (activeOverlay != null) return
        pausedGamePath = getSharedPreferences("runestone", MODE_PRIVATE)
            .getString("paused_game", null)
        refreshGames()
        showHome()
    }

    override fun onBackPressed() {
        if (activeImportProgressView != null) {
            Toast.makeText(this, "Operation still running.", Toast.LENGTH_SHORT).show()
        } else if (activeOverlay != null) {
            dismissOverlay()
        } else if (activeEngineFilter != null) {
            activeEngineFilter = null
            showHome()
        } else {
            super.onBackPressed()
        }
    }
}
