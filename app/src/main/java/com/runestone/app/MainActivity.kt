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

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.runestone.app.data.DisplayCutoutMode
import com.runestone.app.data.EngineType
import com.runestone.app.data.RunnerSettings
import com.runestone.app.data.UIMode
import com.runestone.app.engine.EngineRegistry
import com.runestone.app.ui.SortMode
import com.runestone.app.importer.SafGameImporter
import com.runestone.app.importer.SafImportResult
import com.runestone.app.importer.SafStorageBrowser
import com.runestone.app.ui.AvailableGamesScreen
import com.runestone.app.ui.GameFolderBrowserScreen
import com.runestone.app.ui.GameCardInfo
import com.runestone.app.ui.HomeCardLayout
import com.runestone.app.ui.HomeScreen
import com.runestone.app.ui.ImportProgressScreen
import com.runestone.app.ui.ImportProgressView
import com.runestone.app.ui.ManageFilesScreen
import com.runestone.app.ui.PerGameSettingsScreen
import com.runestone.app.ui.ProviderSettingsScreen
import com.runestone.app.ui.SettingsScreen
import com.runestone.app.ui.SettingsStore
import com.runestone.app.ui.Theme
import com.runestone.app.ui.SourcesScreen
import com.runestone.app.services.GameMetadataService
import com.runestone.app.services.StoreDownloadService
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

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
    private var pendingCoverStorage: String? = null
    private var pendingCoverCallback: ((String) -> Unit)? = null
    private var pendingPatchStorage: String? = null
    private var pendingPatchCallback: ((String) -> Unit)? = null
    private var pendingSaveExportStorage: String? = null
    private var pendingSaveImportStorage: String? = null
    private val importBrowserStack = mutableListOf<SafStorageBrowser.Folder>()
    private var importBrowserShowLocations = false
    private var downloadProgressMap = mutableMapOf<String, DownloadManager.DownloadProgress>()
    private var installProgressMap = mutableMapOf<String, InstallProgress>()
    private val lastStoreProgressRenderAt = mutableMapOf<String, Long>()
    private val lastStoreProgressPercent = mutableMapOf<String, Int>()

    data class InstallProgress(
        val filesExtracted: Int,
        val totalFiles: Int,
        val currentFile: String,
    )

    // Overlay navigation - root container set once, overlays added on top
    private lateinit var rootContainer: FrameLayout
    private var activeOverlay: View? = null
    private var homeContentView: View? = null
    private lateinit var persistentDock: View

    companion object {
        private const val REQUEST_IMPORT_FOLDER = 9001
        private const val REQUEST_COVER_IMAGE = 9002
        private const val REQUEST_PATCH_ZIP = 9003
        private const val REQUEST_SAVE_EXPORT_ZIP = 9004
        private const val REQUEST_SAVE_IMPORT_ZIP = 9005
        private const val TAG = "Runestone"
        private const val NOTIFICATION_CHANNEL = "runestone_downloads"
        private const val NOTIFICATION_ID_DOWNLOAD = 2001
        private const val EXTRA_ADB_COMMAND = "runestone_adb_command"
        private const val ADB_OPEN_FIRST_GAME = "first_game"
        private const val ADB_OPEN_HOME = "home"
        private const val ADB_OPEN_MANAGE = "manage"
        private const val ADB_OPEN_SETTINGS = "settings"
        private const val ADB_OPEN_STORE = "store"
    }

    private var pausedGamePath: String? = null
    private var initialLaunch = true
    private var activeEngineFilter: EngineType? = null
    private var currentSort: SortMode = SortMode.DATE_ADDED
    private var searchQuery: String = ""
    private var homeCardLayout = HomeCardLayout.GRID_2
    private var availableGames: List<AvailableGame> = emptyList()
    private val storeMetadataInFlight = mutableSetOf<String>()
    private var storeMetadataLoading = false
    private var storeMetadataRenderScheduled = false
    private var availableGamesScrollY = 0
    private var isLoadingGames = false
    private var gamesErrorMessage: String? = null
    private var downloadReceiverRegistered = false
    private val pressedControllerKeys = mutableSetOf<Int>()

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val gameId = intent.getStringExtra(StoreDownloadService.EXTRA_GAME_ID) ?: return
            val stateName = intent.getStringExtra(StoreDownloadService.EXTRA_STATE) ?: DownloadManager.DownloadState.IDLE.name
            val state = runCatching { DownloadManager.DownloadState.valueOf(stateName) }.getOrDefault(DownloadManager.DownloadState.IDLE)
            val progress = DownloadManager.DownloadProgress(
                bytesDownloaded = intent.getLongExtra(StoreDownloadService.EXTRA_BYTES, downloadManager.getDownloadedBytes(gameId)),
                totalBytes = intent.getLongExtra(StoreDownloadService.EXTRA_TOTAL, downloadManager.getTotalBytes(gameId)),
                speed = intent.getFloatExtra(StoreDownloadService.EXTRA_SPEED, 0f),
                state = state,
                error = intent.getStringExtra(StoreDownloadService.EXTRA_ERROR),
            )
            downloadProgressMap[gameId] = progress
            when (intent.action) {
                StoreDownloadService.ACTION_COMPLETE -> {
                    val path = intent.getStringExtra(StoreDownloadService.EXTRA_FILE_PATH)
                    if (path != null) startExtraction(gameId, path)
                }
                StoreDownloadService.ACTION_ERROR -> showErrorNotification(gameId, progress.error ?: "Download failed")
            }
            renderAvailableGamesProgress("download:$gameId", progressPercent(progress.bytesDownloaded, progress.totalBytes), force = state != DownloadManager.DownloadState.DOWNLOADING)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        applyImmersiveMode()
        // Check for paused game from SharedPreferences — load for display,
        // then clear immediately since the game activity is dead after fresh onCreate
        pausedGamePath = getSharedPreferences("runestone", MODE_PRIVATE)
            .getString("paused_game", null)
        if (getSharedPreferences("runestone", MODE_PRIVATE).getBoolean("game_minimized", false)) {
            Log.i(TAG, "onCreate preserving minimized game path=$pausedGamePath")
        } else {
            finalizeActivePlaySession(reason = "fresh_on_create")
            getSharedPreferences("runestone", MODE_PRIVATE).edit()
                .remove("paused_game").apply()
            pausedGamePath = null // game process died with the app
        }
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
        applyImmersiveMode()
        Theme.active = Theme.byName(settings.colorPalette)
        homeCardLayout = runCatching {
            HomeCardLayout.valueOf(
                getSharedPreferences("runestone-settings-v1", MODE_PRIVATE)
                    .getString("homeCardLayout", HomeCardLayout.GRID_2.name).orEmpty(),
            )
        }.getOrDefault(HomeCardLayout.GRID_2)
        refreshGames()
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        registerDownloadReceiver()
        setupDownloadCallbacks()

        // Create permanent root frame - setContentView ONCE
        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.rgb(3, 3, 4))
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            if (settings.displayCutoutMode == DisplayCutoutMode.SAFE_AREA) {
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                v.setPadding(
                    maxOf(bars.left, cutout.left),
                    maxOf(bars.top, cutout.top),
                    maxOf(bars.right, cutout.right),
                    maxOf(0, cutout.bottom),
                )
            } else {
                v.setPadding(0, 0, 0, 0)
            }
            applyImmersiveMode()
            insets
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
        handleAdbCommand(intent)
    }

    private fun registerDownloadReceiver() {
        if (downloadReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(StoreDownloadService.ACTION_PROGRESS)
            addAction(StoreDownloadService.ACTION_COMPLETE)
            addAction(StoreDownloadService.ACTION_ERROR)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
        downloadReceiverRegistered = true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9104)
        }
    }

    private fun refreshGames() {
        games = workspaceManager.scanInstalledGames()
        Log.i(TAG, "refreshGames: found ${games.size} games")
    }

    private fun startPlaySession(storageName: String, gamePath: String) {
        val now = System.currentTimeMillis()
        getSharedPreferences("runestone", MODE_PRIVATE).edit()
            .putString("active_game_storage", storageName)
            .putString("active_game_path", gamePath)
            .putLong("active_game_started_at", now)
            .putLong("active_game_last_seen_at", now)
            .putString("paused_game", gamePath)
            .apply()

        getSharedPreferences("play_stats", MODE_PRIVATE).edit()
            .putLong("session_start_${storageName}", now)
            .apply()
    }

    private fun finalizeActivePlaySession(reason: String) {
        val runestonePrefs = getSharedPreferences("runestone", MODE_PRIVATE)
        val storageName = runestonePrefs.getString("active_game_storage", null) ?: return
        val startedAt = runestonePrefs.getLong("active_game_started_at", 0L)
        if (startedAt <= 0L) return

        val now = System.currentTimeMillis()
        val elapsedSeconds = ((now - startedAt) / 1000L).coerceAtLeast(0L)
        if (elapsedSeconds > 0L) {
            val playStats = getSharedPreferences("play_stats", MODE_PRIVATE)
            val total = playStats.getLong("total_${storageName}", 0L)
            playStats.edit()
                .putLong("total_${storageName}", total + elapsedSeconds)
                .putLong("last_played_${storageName}", now)
                .remove("session_start_${storageName}")
                .apply()
            Log.i(TAG, "Play session finalized: $storageName +${elapsedSeconds}s ($reason)")
        }

        runestonePrefs.edit()
            .remove("active_game_storage")
            .remove("active_game_path")
            .remove("active_game_started_at")
            .remove("active_game_last_seen_at")
            .remove("paused_game")
            .apply()
        pausedGamePath = null
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
                    renderAvailableGamesProgress(
                        key = "download:$gameId",
                        percent = progressPercent(progress.bytesDownloaded, progress.totalBytes),
                    )
                }
            }

            override fun onComplete(gameId: String, filePath: String) {
                runOnUiThread {
                    downloadProgressMap[gameId] = DownloadManager.DownloadProgress(
                        bytesDownloaded = 0, totalBytes = 0, speed = 0f,
                        state = DownloadManager.DownloadState.COMPLETED
                    )
                    renderAvailableGamesProgress("download:$gameId", 100, force = true)
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
                    renderAvailableGamesProgress("download:$gameId", 0, force = true)
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
        installProgressMap[gameId] = InstallProgress(0, 0, "Preparing archive")
        renderAvailableGamesProgress("install:$gameId", 0, force = true)

        extractionManager.extract(zipPath, outputDir, object : ExtractionManager.ExtractionCallback {
            override fun onProgress(progress: ExtractionManager.ExtractionProgress) {
                Log.d(TAG, "Extracting: ${progress.currentFile} (${progress.filesExtracted}/${progress.totalFiles})")
                runOnUiThread {
                    installProgressMap[gameId] = InstallProgress(
                        filesExtracted = progress.filesExtracted,
                        totalFiles = progress.totalFiles,
                        currentFile = progress.currentFile,
                    )
                    renderAvailableGamesProgress(
                        key = "install:$gameId",
                        percent = progressPercent(progress.filesExtracted.toLong(), progress.totalFiles.toLong()),
                    )
                    val notification = Notification.Builder(this@MainActivity, NOTIFICATION_CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("Extracting ${game.title}")
                        .setContentText("${progress.filesExtracted}/${progress.totalFiles} files")
                        .setOngoing(true)
                        .build()
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID_DOWNLOAD, notification)
                }
            }

            override fun onComplete(result: ExtractionManager.ExtractionResult) {
                runOnUiThread {
                    try {
                        val gameDir = finalizeDownloadedGame(result, game)
                        val zipFile = File(zipPath)
                        if (settings.preserveFiles) {
                            Log.i(TAG, "Preserved ZIP: $zipPath")
                        } else if (zipFile.delete()) {
                            Log.i(TAG, "Deleted ZIP: $zipPath")
                        }

                        downloadManager.cleanup(gameId)
                        downloadProgressMap.remove(gameId)
                        installProgressMap.remove(gameId)
                        clearStoreProgress(gameId)
                        refreshGames()
                        dismissOverlay { showHome() }
                        val zipStatus = if (settings.preserveFiles) "ZIP kept" else "ZIP deleted"
                        Toast.makeText(this@MainActivity, "${gameDir.name} installed. $zipStatus.", Toast.LENGTH_SHORT).show()
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

    private fun finalizeDownloadedGame(result: ExtractionManager.ExtractionResult, sourceGame: AvailableGame): File {
        val engine = EngineRegistry.detect(result.gameRoot)
        val detectedType = engine?.let { EngineType.fromEngineId(it.id) } ?: EngineType.UNKNOWN
        val declaredType = sourceGame.engine?.let { EngineType.fromEngineId(it) } ?: EngineType.UNKNOWN
        val engineType = when {
            detectedType != EngineType.UNKNOWN -> detectedType
            declaredType != EngineType.UNKNOWN -> declaredType
            else -> EngineType.UNKNOWN
        }
        require(engineType != EngineType.UNKNOWN) { "Could not detect a supported game engine" }
        Log.i(TAG, "Install engine: $engineType for ${result.gameRoot.name} detected=${engine?.id} declared=${sourceGame.engine}")

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

        val fileCount = originalDir.walkTopDown().count { it.isFile }
        require(fileCount > 0) { "Archive did not contain game files" }

        workspaceManager.ensureWorkspace(gameDir.name)
        workspaceManager.ensureNoMedia(gameDir.name)

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
        installProgressMap.remove(gameId)
        clearStoreProgress(gameId)
        downloadManager.cleanup(gameId)
        downloadProgressMap[gameId] = DownloadManager.DownloadProgress(
            bytesDownloaded = 0, totalBytes = 0, speed = 0f,
            state = DownloadManager.DownloadState.FAILED, error = message,
        )
        renderAvailableGamesProgress("download:$gameId", 0, force = true)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleDownload(game: AvailableGame) {
        val url = game.downloadUrl ?: return
        val fileName = "${sha256(game.id).take(32)}.zip"
        downloadManager.setFileName(game.id, fileName)
        val cachedFile = File(downloadManager.getDownloadDir(), fileName)
        if (isReadableZip(cachedFile)) {
            downloadProgressMap[game.id] = DownloadManager.DownloadProgress(
                bytesDownloaded = cachedFile.length(),
                totalBytes = cachedFile.length(),
                speed = 0f,
                state = DownloadManager.DownloadState.COMPLETED,
            )
            startExtraction(game.id, cachedFile.absolutePath)
            renderAvailableGamesProgress("download:${game.id}", 100, force = true)
            return
        }
        val state = downloadManager.getState(game.id)
        val action = if (state == DownloadManager.DownloadState.PAUSED) {
            StoreDownloadService.ACTION_RESUME
        } else {
            StoreDownloadService.ACTION_START
        }
        startForegroundService(Intent(this, StoreDownloadService::class.java).apply {
            this.action = action
            putExtra(StoreDownloadService.EXTRA_GAME_ID, game.id)
            putExtra(StoreDownloadService.EXTRA_TITLE, game.title)
            putExtra(StoreDownloadService.EXTRA_URL, url)
            putExtra(StoreDownloadService.EXTRA_FILE_NAME, fileName)
        })
        downloadProgressMap[game.id] = DownloadManager.DownloadProgress(
            bytesDownloaded = downloadManager.getDownloadedBytes(game.id),
            totalBytes = downloadManager.getTotalBytes(game.id),
            speed = 0f,
            state = DownloadManager.DownloadState.DOWNLOADING
        )
        renderAvailableGamesProgress("download:${game.id}", 0, force = true)
    }

    private fun progressPercent(done: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((done * 100L) / total).coerceIn(0L, 100L).toInt()
    }

    private fun renderAvailableGamesProgress(key: String, percent: Int, force: Boolean = false) {
        if (activeOverlay == null) return

        val now = System.currentTimeMillis()
        val lastAt = lastStoreProgressRenderAt[key] ?: 0L
        val lastPercent = lastStoreProgressPercent[key]
        val shouldRender = force ||
            lastPercent == null ||
            percent >= 100 ||
            percent != lastPercent ||
            now - lastAt >= 10_000L

        if (!shouldRender) return

        lastStoreProgressRenderAt[key] = now
        lastStoreProgressPercent[key] = percent
        renderAvailableGamesScreen()
    }

    private fun clearStoreProgress(gameId: String) {
        listOf("download:$gameId", "install:$gameId").forEach { key ->
            lastStoreProgressRenderAt.remove(key)
            lastStoreProgressPercent.remove(key)
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun isReadableZip(file: File): Boolean {
        if (!file.isFile || file.length() < 16L * 1024L) return false
        return runCatching {
            ZipFile(file).use { zip -> zip.entries().hasMoreElements() }
        }.getOrDefault(false)
    }

    private fun handlePauseDownload(gameId: String) {
        startService(Intent(this, StoreDownloadService::class.java).apply {
            action = StoreDownloadService.ACTION_PAUSE
            putExtra(StoreDownloadService.EXTRA_GAME_ID, gameId)
            putExtra(StoreDownloadService.EXTRA_TITLE, availableGames.find { it.id == gameId }?.title ?: gameId)
        })
        downloadProgressMap[gameId] = DownloadManager.DownloadProgress(
            bytesDownloaded = downloadManager.getDownloadedBytes(gameId),
            totalBytes = downloadManager.getTotalBytes(gameId),
            speed = 0f,
            state = DownloadManager.DownloadState.PAUSED
        )
        renderAvailableGamesScreen()
    }

    private fun toCardInfo(g: WorkspaceManager.GameInfo): GameCardInfo {
        val perGame = runCatching {
            com.runestone.app.data.GameConfigService(this, workspaceManager).loadPerGame(g.storageName)
        }.getOrNull()
        val metadata = perGame?.metadata?.takeIf {
            it.gameTitle.isBlank() || metadataTitleMatches(g.displayName, it.gameTitle)
        }

        // Priority: custom cover > metadata local cover > nothing (will be filled by pipeline)
        val customCoverPath = perGame?.game?.customCoverPath?.let { path ->
            if (File(path).exists()) return@let "local:$path"
            null
        }
        val metadataCoverPath = metadata?.localCoverPath?.takeIf { it.isNotEmpty() }?.let { path ->
            if (File(path).exists()) return@let "local:$path"
            null
        }
        val coverUrl = customCoverPath ?: metadataCoverPath

        return GameCardInfo(
            storageName = g.storageName,
            displayName = metadata?.gameTitle?.takeIf { it.isNotEmpty() } ?: g.displayName,
            engineType = g.engineType,
            fileCount = g.fileCount,
            fileSize = runCatching {
                val dir = java.io.File(g.originalPath)
                if (dir.isDirectory) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else dir.length()
            }.getOrNull() ?: 0L,
            totalPlayTime = getSharedPreferences("play_stats", MODE_PRIVATE).getLong("total_${g.storageName}", 0L),
            lastPlayedTimestamp = getSharedPreferences("play_stats", MODE_PRIVATE).getLong("last_played_${g.storageName}", 0L),
            isReady = true,
            isPaused = pausedGamePath == g.originalPath,
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

    private fun normalizedTitle(value: String): String =
        value.lowercase()
            .replace("&", " and ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

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
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            homeContentView?.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    10f,
                    10f,
                    android.graphics.Shader.TileMode.CLAMP,
                ),
            )
        }

        val wrapper = FrameLayout(this).apply {
            // Semi-transparent black dims the home screen underneath
            setBackgroundColor(Color.argb(218, 0, 0, 0))

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
        rootContainer.post { enableControllerNavigation(wrapper) }
    }

    /**
     * Fades out the active overlay, removes it, then runs [onDismissed].
     * Default callback refreshes the home screen.
     */
    private fun dismissOverlay(onDismissed: () -> Unit = {}) {
        activeOverlay?.let { overlay ->
            overlay.animate().alpha(0f).translationY(resources.displayMetrics.heightPixels * 0.08f).setDuration(200).withEndAction {
                rootContainer.removeView(overlay)
                activeOverlay = null
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    homeContentView?.setRenderEffect(null)
                }
                onDismissed()
            }.start()
        }
    }

    /** Density-independent pixels helper. */
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (settings.displayCutoutMode == DisplayCutoutMode.EDGE_TO_EDGE) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
    }

    private fun enableControllerNavigation(root: View) {
        val clickables = mutableListOf<View>()
        fun visit(view: View) {
            if (view.isClickable && view.visibility == View.VISIBLE) {
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                clickables += view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
        if (currentFocus == null || currentFocus == rootContainer) {
            clickables.firstOrNull()?.requestFocus()
        }
    }

    private fun performFocusedClick(): Boolean {
        val target = currentFocus?.takeIf { it.isClickable && it.visibility == View.VISIBLE }
            ?: firstClickable(rootContainer)
        return if (target != null) {
            target.performClick()
            true
        } else false
    }

    private fun firstClickable(view: View): View? {
        if (view.isClickable && view.visibility == View.VISIBLE) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                firstClickable(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun cycleSortMode() {
        currentSort = when (currentSort) {
            SortMode.DATE_ADDED -> SortMode.NAME_ASC
            SortMode.NAME_ASC -> SortMode.NAME_DESC
            SortMode.NAME_DESC -> SortMode.RECENT
            SortMode.RECENT -> SortMode.DATE_ADDED
        }
        Toast.makeText(this, "Sort: ${sortLabel(currentSort)}", Toast.LENGTH_SHORT).show()
        showHome()
    }

    private fun cycleEngineFilter() {
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
        Toast.makeText(this, "Filter: ${activeEngineFilter?.label ?: "All games"}", Toast.LENGTH_SHORT).show()
        showHome()
    }

    private fun cycleCardLayout() {
        homeCardLayout = homeCardLayout.next()
        getSharedPreferences("runestone-settings-v1", MODE_PRIVATE)
            .edit()
            .putString("homeCardLayout", homeCardLayout.name)
            .apply()
        Toast.makeText(this, "Layout: ${homeCardLayout.name.lowercase().replace('_', ' ')}", Toast.LENGTH_SHORT).show()
        showHome()
    }

    private fun sortLabel(sort: SortMode): String = when (sort) {
        SortMode.NAME_ASC -> "Name A-Z"
        SortMode.NAME_DESC -> "Name Z-A"
        SortMode.RECENT -> "Recently played"
        SortMode.DATE_ADDED -> "Date added"
    }

    private fun showSplash() {
        val splash = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(3, 3, 4))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            val titleText = TextView(this@MainActivity).apply {
                text = "RUNESTONE"
                setTextColor(Theme.active.accent) // ACCENT color
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
            // Keep custom cover if already set
            if (card.coverUrl != null) return@map card
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
        
        // Fetch missing metadata into per-game config so hero cards are warm on next launch.
        val configService = com.runestone.app.data.GameConfigService(this, workspaceManager)
        cards.filter { it.coverUrl == null }.forEach { card ->
            if (!gameMetadataCache.containsKey(card.displayName)) {
                metadataService.fetchAndApplyMetadata(
                    gameTitle = card.displayName,
                    storageName = card.storageName,
                    configService = configService,
                ) { section ->
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
        val pausedGame = cards.find { it.isPaused }

        val homeView = HomeScreen(this).create(
            games = cards,
            onPlay = { playGame(it) },
            onManage = { showPerGameSettings(it) },
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
            cardLayout = homeCardLayout,
            showGameName = settings.showGameName,
            onLongPress = { game ->
                HomeScreen(this).showInspectOverlay(game, { playGame(it) }, { showPerGameSettings(it) })
            },
            onCardLayoutChanged = { layout ->
                homeCardLayout = layout
                getSharedPreferences("runestone-settings-v1", MODE_PRIVATE)
                    .edit()
                    .putString("homeCardLayout", layout.name)
                    .apply()
                showHome()
            },
            onResume = if (pausedGame != null) {{ playGame(pausedGame.storageName) }} else null,
            onStop = if (pausedGame != null) {{ storageName ->
                val game = games.find { it.storageName == storageName }
                if (game != null) {
                    Log.i(TAG, "STOP game: $storageName path=${game.originalPath}")
                    // Record play session
                    val playStats = getSharedPreferences("play_stats", MODE_PRIVATE)
                    val sessionStart = playStats.getLong("session_start_${storageName}", 0L)
                    if (sessionStart > 0L) {
                        val elapsed = (System.currentTimeMillis() - sessionStart) / 1000
                        val total = playStats.getLong("total_${storageName}", 0L)
                        playStats.edit()
                            .putLong("total_${storageName}", total + elapsed)
                            .putLong("last_played_${storageName}", System.currentTimeMillis())
                            .remove("session_start_${storageName}")
                            .apply()
                    }
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
        rootContainer.post { enableControllerNavigation(rootContainer) }
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

    private fun clearRuntimeCache() {
        val runtimeDir = java.io.File(filesDir, "runtime")
        if (runtimeDir.exists()) {
            runtimeDir.deleteRecursively()
        }
        val cacheDir = java.io.File(filesDir, "cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    private fun showSettings() {
        manageFilesVisible = false
        showOverlay(
            SettingsScreen(this).create(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    settings = newSettings
                    settingsStore.save(newSettings)
                    applyImmersiveMode()
                    ViewCompat.requestApplyInsets(rootContainer)
                },
                onBack = { dismissOverlay() },
                onResetDefaults = {
                    settings = RunnerSettings()
                    settingsStore.save(settings)
                    showSettings()
                },
                onClearRuntimeCache = {
                    clearRuntimeCache()
                },
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
                storageName = storageName,
                onConfigChanged = { newConfig ->
                    configService.savePerGame(storageName, newConfig)
                },
                onBack = { dismissOverlay() },
                onPickCover = { resultCallback ->
                    pendingCoverStorage = storageName
                    pendingCoverCallback = resultCallback
                    val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false)
                    }
                    startActivityForResult(intent, REQUEST_COVER_IMAGE)
                },
                onFetchMetadata = { resultCallback ->
                    val fetched = AtomicBoolean(false)
                    metadataService.fetchAndApplyMetadata(
                        gameTitle = game.displayName,
                        storageName = storageName,
                        configService = configService,
                        forceFresh = true,
                    ) { section ->
                        if (section != null && !fetched.getAndSet(true)) {
                            resultCallback(true)
                            runOnUiThread {
                                gameMetadataCache.remove(game.displayName)
                                dismissOverlay {
                                    refreshGames()
                                    showPerGameSettings(storageName)
                                }
                            }
                        } else if (!fetched.getAndSet(true)) {
                            resultCallback(false)
                        }
                    }
                },
                onInstallPatch = { zipCallback ->
                    pendingPatchStorage = storageName
                    pendingPatchCallback = zipCallback
                    val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false)
                    }
                    startActivityForResult(intent, REQUEST_PATCH_ZIP)
                },
            ),
        )
    }

    private fun showAvailableGames() {
        manageFilesVisible = false
        availableGamesScrollY = 0
        isLoadingGames = true
        gamesErrorMessage = null
        val installedTitles = installedStoreKeys()
        renderAvailableGamesScreen(installedGameTitles = installedTitles)

        sourcesManager.fetchGamesFromSources { games, error ->
            runOnUiThread {
                availableGames = games
                hydrateStoreDownloadStates()
                isLoadingGames = false
                gamesErrorMessage = error
                val installedTitles = installedStoreKeys()
                renderAvailableGamesScreen(installedGameTitles = installedTitles)
                enrichStoreMetadata()
            }
        }
    }

    private fun enrichStoreMetadata() {
        val targets = availableGames
            .filter { it.coverUrl == null && it.title.isNotBlank() && it.id !in storeMetadataInFlight }
            .take(6)
        if (targets.isEmpty()) {
            storeMetadataLoading = false
            return
        }
        storeMetadataLoading = true
        renderAvailableGamesScreen()
        targets.forEach { game ->
                storeMetadataInFlight.add(game.id)
                metadataService.fetchMetadataAsync(game.rawgQuery ?: game.title, game.engine) { metadata ->
                    runOnUiThread {
                        storeMetadataInFlight.remove(game.id)
                        if (storeMetadataInFlight.isEmpty()) {
                            storeMetadataLoading = false
                            scheduleStoreMetadataRender()
                        }
                    }
                    if (metadata == null) return@fetchMetadataAsync
                    val cover = metadata.localCoverPath?.let { "local:$it" } ?: metadata.coverUrl
                    if (cover.isNullOrBlank()) return@fetchMetadataAsync
                    runOnUiThread {
                        availableGames = availableGames.map {
                            if (it.id == game.id) it.copy(
                                coverUrl = cover,
                                description = it.description ?: metadata.description,
                                tags = if (it.tags.isNotEmpty()) it.tags else metadata.genres,
                            ) else it
                        }
                        scheduleStoreMetadataRender()
                    }
                }
        }
    }

    private fun hydrateStoreDownloadStates() {
        availableGames.forEach { game ->
            val state = downloadManager.getState(game.id)
            when (state) {
                DownloadManager.DownloadState.IDLE -> Unit
                DownloadManager.DownloadState.COMPLETED -> {
                    val outputFile = downloadManager.getOutputFile(game.id)
                    if (outputFile.isFile && game.id !in installProgressMap) {
                        downloadProgressMap[game.id] = DownloadManager.DownloadProgress(
                            bytesDownloaded = outputFile.length(),
                            totalBytes = outputFile.length(),
                            speed = 0f,
                            state = state,
                        )
                        startExtraction(game.id, outputFile.absolutePath)
                    }
                }
                else -> downloadProgressMap[game.id] = DownloadManager.DownloadProgress(
                    bytesDownloaded = downloadManager.getDownloadedBytes(game.id),
                    totalBytes = downloadManager.getTotalBytes(game.id),
                    speed = 0f,
                    state = state,
                )
            }
        }
    }

    private fun scheduleStoreMetadataRender() {
        if (storeMetadataRenderScheduled) return
        storeMetadataRenderScheduled = true
        rootContainer.postDelayed({
            storeMetadataRenderScheduled = false
            if (activeOverlay != null) renderAvailableGamesScreen()
        }, 250L)
    }

    private fun renderAvailableGamesScreen(installedGameTitles: Set<String>? = null) {
        val titles = installedGameTitles ?: installedStoreKeys()
        showOverlay(
            AvailableGamesScreen(this).create(
                games = availableGames,
                isLoading = isLoadingGames,
                isMetadataLoading = storeMetadataLoading || storeMetadataInFlight.isNotEmpty(),
                errorMessage = gamesErrorMessage,
                downloadStates = downloadProgressMap,
                installStates = installProgressMap,
                installedGameTitles = titles,
                initialScrollY = availableGamesScrollY,
                onScrollYChanged = { availableGamesScrollY = it },
                onRefresh = { showAvailableGames() },
                onManageSources = { showSources() },
                onProviderSettings = { showProviderSettings() },
                onDownload = { handleDownload(it) },
                onPauseDownload = { handlePauseDownload(it) },
                onBack = { dismissOverlay() },
            ),
        )
    }

    private fun installedStoreKeys(): Set<String> {
        return games.flatMap { game ->
            listOf(game.displayName, game.storageName)
        }.toSet()
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
            getSharedPreferences("runestone", MODE_PRIVATE).edit()
                .remove("paused_game")
                .remove("game_minimized")
                .apply()
            // Just finish this activity to bring GameActivity back to front
            finish()
            return
        }

        Log.i(TAG, "playGame: $storageName path=${game.originalPath}")
        pausedGamePath = game.originalPath
        startPlaySession(storageName, game.originalPath)

        val effectiveSettings = com.runestone.app.data.GameConfigService(this, workspaceManager)
            .resolveRunnerSettings(storageName)
        GameActivity.start(this, game.originalPath, game.engineType.name, effectiveSettings)
    }

    private fun startFolderImport(requestedName: String? = null) {
        Log.i(TAG, "startFolderImport: requestedName=$requestedName")
        importMessage = null
        pendingImportStorage = requestedName
        importBrowserStack.clear()
        importBrowserShowLocations = false
        showGameFolderBrowser()
    }

    private fun requestStorageAccess() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_FOLDER)
    }

    private fun showGameFolderBrowser() {
        val browser = SafStorageBrowser(contentResolver)
        val roots = browser.listRoots()
        if (!importBrowserShowLocations && importBrowserStack.isEmpty() && roots.isNotEmpty()) {
            val preferred = roots.firstOrNull { it.name.equals(settings.defaultGameFolder, ignoreCase = true) }
                ?: roots.first()
            importBrowserStack += browser.describeFolder(preferred.documentUri)
        }
        val current = importBrowserStack.lastOrNull()
        val entries = current?.let { runCatching { browser.listEntries(it.uri) }.getOrDefault(emptyList()) } ?: emptyList()
        showOverlay(
            GameFolderBrowserScreen(this).create(
                roots = roots,
                currentFolder = current,
                entries = entries,
                pathSegments = importBrowserStack.map { it.name },
                canNavigateUp = importBrowserStack.size > 1,
                onBack = {
                    if (current == null) {
                        importBrowserShowLocations = false
                        dismissOverlay()
                    } else if (importBrowserStack.size > 1) {
                        importBrowserStack.removeAt(importBrowserStack.lastIndex)
                        showGameFolderBrowser()
                    } else {
                        importBrowserShowLocations = true
                        importBrowserStack.clear()
                        showGameFolderBrowser()
                    }
                },
                onUp = {
                    if (importBrowserStack.size > 1) {
                        importBrowserStack.removeAt(importBrowserStack.lastIndex)
                        showGameFolderBrowser()
                    } else {
                        importBrowserShowLocations = true
                        importBrowserStack.clear()
                        showGameFolderBrowser()
                    }
                },
                onOpenRoot = { storageRoot ->
                    importBrowserShowLocations = false
                    importBrowserStack.clear()
                    importBrowserStack += browser.describeFolder(storageRoot.documentUri)
                    showGameFolderBrowser()
                },
                onOpenFolder = { folder ->
                    importBrowserStack += folder
                    showGameFolderBrowser()
                },
                onImportFolder = { folder -> importSelectedFolder(folder.uri) },
                onGrantStorage = { requestStorageAccess() },
            ),
        )
    }

    private fun importSelectedFolder(folderUri: Uri) {
        if (pendingImportStorage != null) {
            val backedUp = saveManager.syncFromActive(pendingImportStorage!!)
            Log.i(TAG, "Backed up $backedUp saves for $pendingImportStorage before import")
        }

        showImportProgress("Importing game")
        Log.i(TAG, "importSelectedFolder: progress screen shown, starting thread uri=$folderUri")

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
            val result = importer.importTree(folderUri, pendingImportStorage)
            Log.i(TAG, "import finished: $result")

            runOnUiThread {
                pendingImportStorage = null
                importBrowserStack.clear()
                when (result) {
                    is SafImportResult.Success -> {
                        Log.i(TAG, "Import OK: ${result.storageName} (${result.fileCount} files)")
                        importMessage = null
                        saveManager.restoreToActive(result.storageName)
                        activeImportProgressView = null
                        refreshGames()
                        dismissOverlay { showHome() }
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
        val gameTitle = games.find { it.storageName == storageName }?.displayName ?: storageName
        val message = if (saves.isEmpty()) {
            "No save files were detected yet."
        } else {
            saves.joinToString("\n") { "${it.name} (${formatBytes(it.length())})" }
        }
        AlertDialog.Builder(this)
            .setTitle("Save Files - $gameTitle")
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setPositiveButton("Actions") { _, _ ->
                showSaveActions(storageName, gameTitle)
            }
            .show()
    }

    private fun showSaveActions(storageName: String, gameTitle: String) {
        val actions = arrayOf(
            "Sync protected copy",
            "Backup now",
            "Restore protected saves",
            "Export ZIP",
            "Import ZIP",
            "View backups",
        )
        AlertDialog.Builder(this)
            .setTitle("Save Actions - $gameTitle")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        val count = saveManager.syncFromActive(storageName)
                        Toast.makeText(this, "Synced $count save files into protected storage", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val result = saveManager.backupSaves(storageName, "manual")
                        val message = if (result.count > 0) {
                            "Backed up ${result.count} save files"
                        } else {
                            "No save files detected"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    private fun confirmRestoreSaves(storageName: String, gameTitle: String) {
        AlertDialog.Builder(this)
            .setTitle("Restore saves to $gameTitle?")
            .setMessage("Protected saves will be copied back into the installed game folder and may overwrite matching live save files.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                val count = saveManager.restoreToActive(storageName)
                Toast.makeText(this, "Restored $count save files", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSaveExportPicker(storageName: String) {
        pendingSaveExportStorage = storageName
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        val filename = "${storageName}-saves-$stamp.zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        startActivityForResult(intent, REQUEST_SAVE_EXPORT_ZIP)
    }

    private fun showSaveImportPicker(storageName: String) {
        pendingSaveImportStorage = storageName
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        startActivityForResult(intent, REQUEST_SAVE_IMPORT_ZIP)
    }

    private fun showSaveBackups(storageName: String, gameTitle: String) {
        val backups = saveManager.listSaveBackups(storageName)
        val message = if (backups.isEmpty()) {
            "No save backups have been created yet."
        } else {
            backups.joinToString("\n") {
                "${it.name}: ${it.fileCount} files (${formatBytes(it.bytes)})"
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Save Backups - $gameTitle")
            .setMessage(message)
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

    private fun handleCoverImageResult(resultCode: Int, data: Intent?) {
        val callback = pendingCoverCallback
        pendingCoverCallback = null
        val storageName = pendingCoverStorage
        pendingCoverStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        val coverDir = File(filesDir, "game_covers").apply { mkdirs() }
        val destFile = File(coverDir, "${storageName}.jpg")
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected cover image")
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            callback?.invoke(destFile.absolutePath)
            runOnUiThread { showHome() }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to save cover image", e)
            runOnUiThread {
                android.widget.Toast.makeText(this, "Failed to set cover image", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePatchZipResult(resultCode: Int, data: Intent?) {
        val callback = pendingPatchCallback
        pendingPatchCallback = null
        val storageName = pendingPatchStorage
        pendingPatchStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        val patchDir = File(cacheDir, "patch_zips").apply { mkdirs() }
        val destFile = File(patchDir, "${storageName}_patch_${System.currentTimeMillis()}.zip")
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open ZIP file")
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            callback?.invoke(destFile.absolutePath)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to copy patch ZIP", e)
            callback?.invoke("")
            runOnUiThread {
                android.widget.Toast.makeText(this, "Failed to read patch file", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSaveExportResult(resultCode: Int, data: Intent?) {
        val storageName = pendingSaveExportStorage
        pendingSaveExportStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        try {
            val outputStream = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open export destination")
            val count = outputStream.use { output ->
                saveManager.exportAllSavesZip(storageName, output)
            }
            Toast.makeText(this, "Exported $count save files", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export saves", e)
            Toast.makeText(this, "Failed to export saves", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSaveImportResult(resultCode: Int, data: Intent?) {
        val storageName = pendingSaveImportStorage
        pendingSaveImportStorage = null

        if (resultCode != Activity.RESULT_OK || data?.data == null || storageName == null) return

        val uri = data.data!!
        val importDir = File(cacheDir, "save_import_zips").apply { mkdirs() }
        val destFile = File(importDir, "${storageName}_saves_${System.currentTimeMillis()}.zip")
        try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected save ZIP")
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val count = saveManager.importSavesZip(storageName, destFile)
            Toast.makeText(this, "Imported $count save files", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import saves", e)
            Toast.makeText(this, "Failed to import saves", Toast.LENGTH_SHORT).show()
        } finally {
            destFile.delete()
        }
    }

    private fun formatBytes(bytes: Long): String {
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
    //  Activity lifecycle
    // ═══════════════════════════════════════════════════════

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "onActivityResult: requestCode=$requestCode resultCode=$resultCode data=$data")
        if (requestCode != REQUEST_IMPORT_FOLDER) {
            if (requestCode == REQUEST_COVER_IMAGE) {
                handleCoverImageResult(resultCode, data)
            } else if (requestCode == REQUEST_PATCH_ZIP) {
                handlePatchZipResult(resultCode, data)
            } else if (requestCode == REQUEST_SAVE_EXPORT_ZIP) {
                handleSaveExportResult(resultCode, data)
            } else if (requestCode == REQUEST_SAVE_IMPORT_ZIP) {
                handleSaveImportResult(resultCode, data)
            }
            return
        }
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
        val browser = SafStorageBrowser(contentResolver)
        importBrowserStack.clear()
        runCatching {
            importBrowserStack += browser.describeFolder(browser.rootFromTreeUri(treeUri).documentUri)
        }.onFailure { error ->
            Log.w(TAG, "Could not open authorized storage location", error)
            Toast.makeText(this, "Could not open that storage location", Toast.LENGTH_SHORT).show()
        }
        showGameFolderBrowser()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isControllerShortcut() && handleControllerCombo(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.isControllerShortcut()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> if (performFocusedClick()) return true
                KeyEvent.KEYCODE_BUTTON_B -> {
                    onBackPressed()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_START -> {
                    startFolderImport()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    showManageFiles()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    showAvailableGames()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    cycleEngineFilter()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    cycleCardLayout()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    cycleSortMode()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_MODE -> {
                    showSettings()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAdbCommand(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
            rootContainer.post { enableControllerNavigation(rootContainer) }
        }
    }

    private fun KeyEvent.isControllerShortcut(): Boolean {
        if (keyCode in controllerShortcutKeys) return true
        val controllerSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
        return source and controllerSources != 0
    }

    private val controllerShortcutKeys: Set<Int>
        get() = setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
        )

    private fun handleControllerCombo(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            pressedControllerKeys.remove(event.keyCode)
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        pressedControllerKeys.add(event.keyCode)
        if (event.repeatCount > 0) return false

        if (
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2) &&
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2)
        ) {
            val paused = games.firstOrNull { it.originalPath == pausedGamePath }
            if (paused != null) {
                playGame(paused.storageName)
                return true
            }
        }
        return false
    }

    private fun handleAdbCommand(intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_ADB_COMMAND)
            ?: intent?.getStringExtra("runestone_open")
            ?: return
        rootContainer.postDelayed({
            when (command) {
                ADB_OPEN_FIRST_GAME -> {
                    refreshGames()
                    games.firstOrNull()?.let { playGame(it.storageName) }
                        ?: Toast.makeText(this, "No installed games to launch", Toast.LENGTH_SHORT).show()
                }
                ADB_OPEN_HOME -> showHome()
                ADB_OPEN_MANAGE -> showManageFiles()
                ADB_OPEN_SETTINGS -> showSettings()
                ADB_OPEN_STORE -> showAvailableGames()
            }
        }, 650)
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        Log.i(TAG, "onResume importActive=${activeImportProgressView != null} initial=$initialLaunch overlay=${activeOverlay != null}")
        if (activeImportProgressView != null) return
        if (initialLaunch) {
            initialLaunch = false
            return
        }
        if (activeOverlay != null) return
        val runestonePrefs = getSharedPreferences("runestone", MODE_PRIVATE)
        if (runestonePrefs.getBoolean("game_minimized", false)) {
            pausedGamePath = runestonePrefs.getString("paused_game", null)
            refreshGames()
            showHome()
            return
        }
        if (!runestonePrefs.contains("active_game_storage")) {
            return
        }
        finalizeActivePlaySession(reason = "hub_resumed")
        pausedGamePath = null
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

    override fun onDestroy() {
        if (downloadReceiverRegistered) {
            unregisterReceiver(downloadReceiver)
            downloadReceiverRegistered = false
        }
        super.onDestroy()
    }
}
