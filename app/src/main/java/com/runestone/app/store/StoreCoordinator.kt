package com.runestone.app.store

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.runestone.app.provider.AvailableGame
import com.runestone.app.provider.DownloadManager
import com.runestone.app.provider.ExtractionManager
import com.runestone.app.provider.SourcesManager
import com.runestone.app.services.GameMetadataService
import com.runestone.app.services.StoreDownloadService
import com.runestone.app.workspace.WorkspaceManager
import com.runestone.app.util.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class StoreCoordinator(
    private val activity: Activity,
    private val workspaceManager: WorkspaceManager,
    private val downloadManager: DownloadManager,
    private val extractionManager: ExtractionManager,
    private val sourcesManager: SourcesManager,
    private val metadataService: GameMetadataService,
    private val callbacks: Callbacks,
) {
    companion object {
        private const val TAG = "StoreCoordinator"
        private const val NOTIFICATION_CHANNEL = "runestone_downloads"
        private const val NOTIFICATION_ID_DOWNLOAD = 2001
    }

    interface Callbacks {
        fun refreshGames()
        fun refreshStoreUI()
        fun pushDetailOverlayUpdate(gameId: String)
        fun installedStoreKeys(): Set<String>
        fun getAvailableGames(): List<AvailableGame>
        fun getGames(): List<WorkspaceManager.GameInfo>
        fun getDetailOverlay(): Any?
    }

    data class InstallProgress(
        val filesExtracted: Int,
        val totalFiles: Int,
        val currentFile: String,
    )

    var downloadProgressMap = mutableMapOf<String, DownloadManager.DownloadProgress>()
    var installProgressMap = mutableMapOf<String, InstallProgress>()
    var availableGames: List<AvailableGame> = emptyList()
    var storeMetadataInFlight = mutableSetOf<String>()
    var storeMetadataLoading = false
    var storeMetadataRenderScheduled = false
    var availableGamesScrollY = 0
    var storeGridColumns = 2
    var isLoadingGames = false
    var gamesErrorMessage: String? = null

    private val lastStoreProgressRenderAt = mutableMapOf<String, Long>()
    private val lastStoreProgressPercent = mutableMapOf<String, Int>()
    private var downloadReceiverRegistered = false

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
            callbacks.pushDetailOverlayUpdate(gameId)
        }
    }

    fun registerDownloadReceiver() {
        if (downloadReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(StoreDownloadService.ACTION_PROGRESS)
            addAction(StoreDownloadService.ACTION_COMPLETE)
            addAction(StoreDownloadService.ACTION_ERROR)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(downloadReceiver, filter)
        }
        downloadReceiverRegistered = true
    }

    fun unregisterDownloadReceiver() {
        if (downloadReceiverRegistered) {
            activity.unregisterReceiver(downloadReceiver)
            downloadReceiverRegistered = false
        }
    }

    fun setupDownloadCallbacks() {
        downloadManager.setCallback(object : DownloadManager.DownloadCallback {
            override fun onProgress(gameId: String, progress: DownloadManager.DownloadProgress) {
                activity.runOnUiThread {
                    downloadProgressMap[gameId] = progress
                    showDownloadNotification(gameId, progress)
                    renderAvailableGamesProgress(
                        key = "download:$gameId",
                        percent = progressPercent(progress.bytesDownloaded, progress.totalBytes),
                    )
                }
            }

            override fun onComplete(gameId: String, filePath: String) {
                activity.runOnUiThread {
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
                activity.runOnUiThread {
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

    fun showAvailableGames() {
        availableGamesScrollY = 0
        isLoadingGames = true
        gamesErrorMessage = null
        callbacks.refreshStoreUI()

        sourcesManager.fetchGamesFromSources { games, error ->
            activity.runOnUiThread {
                availableGames = games
                hydrateStoreDownloadStates()
                isLoadingGames = false
                gamesErrorMessage = error
                callbacks.refreshStoreUI()
                enrichStoreMetadata()
            }
        }
    }

    fun handleDownload(game: AvailableGame) {
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
        activity.startForegroundService(Intent(activity, StoreDownloadService::class.java).apply {
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
        callbacks.pushDetailOverlayUpdate(game.id)
    }

    fun handlePauseDownload(gameId: String) {
        activity.startService(Intent(activity, StoreDownloadService::class.java).apply {
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
        callbacks.refreshStoreUI()
    }

    private fun startExtraction(gameId: String, zipPath: String) {
        val game = availableGames.find { it.id == gameId } ?: return
        val outputDir = workspaceManager.allocateGameDir(game.title)
        installProgressMap[gameId] = InstallProgress(0, 0, "Preparing archive")
        renderAvailableGamesProgress("install:$gameId", 0, force = true)
        callbacks.pushDetailOverlayUpdate(gameId)

        extractionManager.extract(zipPath, outputDir, object : ExtractionManager.ExtractionCallback {
            override fun onProgress(progress: ExtractionManager.ExtractionProgress) {
                Log.d(TAG, "Extracting: ${progress.currentFile} (${progress.filesExtracted}/${progress.totalFiles})")
                activity.runOnUiThread {
                    installProgressMap[gameId] = InstallProgress(
                        filesExtracted = progress.filesExtracted,
                        totalFiles = progress.totalFiles,
                        currentFile = progress.currentFile,
                    )
                    renderAvailableGamesProgress(
                        key = "install:$gameId",
                        percent = progressPercent(progress.filesExtracted.toLong(), progress.totalFiles.toLong()),
                    )
                    callbacks.pushDetailOverlayUpdate(gameId)
                    val notification = Notification.Builder(activity, NOTIFICATION_CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("Extracting ${game.title}")
                        .setContentText("${progress.filesExtracted}/${progress.totalFiles} files")
                        .setOngoing(true)
                        .build()
                    val nm = activity.getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID_DOWNLOAD, notification)
                }
            }

            override fun onComplete(result: ExtractionManager.ExtractionResult) {
                activity.runOnUiThread {
                    try {
                        val gameDir = finalizeDownloadedGame(result, game)
                        val zipFile = File(zipPath)
                        if (zipFile.delete()) {
                            Log.i(TAG, "Deleted ZIP: $zipPath")
                        }
                        downloadManager.cleanup(gameId)
                        downloadProgressMap.remove(gameId)
                        installProgressMap.remove(gameId)
                        clearStoreProgress(gameId)
                        workspaceManager.invalidateGameScanCache()
                        callbacks.refreshGames()
                        callbacks.refreshStoreUI()
                    } catch (e: Exception) {
                        Log.e(TAG, "Installation failed", e)
                        discardFailedInstall(gameId, zipPath, result.outputDir, e.message ?: "Installation failed")
                    }
                }
            }

            override fun onError(message: String) {
                activity.runOnUiThread {
                    Log.e(TAG, "Extraction failed: $message")
                    discardFailedInstall(gameId, zipPath, outputDir, "Extraction failed: $message")
                }
            }
        })
    }

    private fun finalizeDownloadedGame(result: ExtractionManager.ExtractionResult, sourceGame: AvailableGame): File {
        val engine = com.runestone.app.engine.EngineRegistry.detect(result.gameRoot)
        val detectedType = engine?.let { com.runestone.app.data.EngineType.fromEngineId(it.id) } ?: com.runestone.app.data.EngineType.UNKNOWN
        val declaredType = sourceGame.engine?.let { com.runestone.app.data.EngineType.fromEngineId(it) } ?: com.runestone.app.data.EngineType.UNKNOWN
        val engineType = when {
            detectedType != com.runestone.app.data.EngineType.UNKNOWN -> detectedType
            declaredType != com.runestone.app.data.EngineType.UNKNOWN -> declaredType
            else -> com.runestone.app.data.EngineType.UNKNOWN
        }
        require(engineType != com.runestone.app.data.EngineType.UNKNOWN) { "Could not detect a supported game engine" }

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
    }

    private fun showDownloadNotification(gameId: String, progress: DownloadManager.DownloadProgress) {
        val percent = if (progress.totalBytes > 0) {
            (progress.bytesDownloaded * 100 / progress.totalBytes).toInt()
        } else 0
        val game = availableGames.find { it.id == gameId }
        val title = game?.title ?: gameId
        val notification = Notification.Builder(activity, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText("$percent%")
            .setOngoing(true)
            .build()
        val nm = activity.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_DOWNLOAD + gameId.hashCode() % 100, notification)
    }

    private fun showInstallNotification(gameId: String) {
        val game = availableGames.find { it.id == gameId }
        val title = game?.title ?: gameId
        val notification = Notification.Builder(activity, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText("$title — extracting...")
            .setAutoCancel(true)
            .build()
        val nm = activity.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_DOWNLOAD + gameId.hashCode() % 100, notification)
    }

    private fun showErrorNotification(gameId: String, error: String) {
        val game = availableGames.find { it.id == gameId }
        val title = game?.title ?: gameId
        val notification = Notification.Builder(activity, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("$title: $error")
            .setAutoCancel(true)
            .build()
        val nm = activity.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_DOWNLOAD + gameId.hashCode() % 100, notification)
    }

    private fun renderAvailableGamesProgress(key: String, percent: Int, force: Boolean = false) {
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
        callbacks.refreshStoreUI()
    }

    private fun clearStoreProgress(gameId: String) {
        listOf("download:$gameId", "install:$gameId").forEach { key ->
            lastStoreProgressRenderAt.remove(key)
            lastStoreProgressPercent.remove(key)
        }
    }

    private fun progressPercent(done: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((done * 100L) / total).coerceIn(0L, 100L).toInt()
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

    fun enrichStoreMetadata() {
        val targets = availableGames
            .filter { it.coverUrl == null && it.title.isNotBlank() && it.id !in storeMetadataInFlight }
            .take(6)
        if (targets.isEmpty()) {
            storeMetadataLoading = false
            return
        }
        storeMetadataLoading = true
        callbacks.refreshStoreUI()
        targets.forEach { game ->
            storeMetadataInFlight.add(game.id)
            metadataService.fetchMetadataAsync(game.rawgQuery ?: game.title, game.engine) { metadata ->
                activity.runOnUiThread {
                    storeMetadataInFlight.remove(game.id)
                    if (storeMetadataInFlight.isEmpty()) {
                        storeMetadataLoading = false
                        scheduleStoreMetadataRender()
                    }
                }
                if (metadata == null) return@fetchMetadataAsync
                val cover = metadata.localCoverPath?.let { "local:$it" } ?: metadata.coverUrl
                if (cover.isNullOrBlank()) return@fetchMetadataAsync
                activity.runOnUiThread {
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
        (activity as? android.os.Handler)?.let { handler ->
            handler.postDelayed({
                storeMetadataRenderScheduled = false
                callbacks.refreshStoreUI()
            }, 250L)
        }
    }
}
