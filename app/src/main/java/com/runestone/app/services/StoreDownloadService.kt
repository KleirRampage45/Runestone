/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.runestone.app.provider.DownloadManager

class StoreDownloadService : Service() {
    private lateinit var downloadManager: DownloadManager
    private val activeTitles = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        downloadManager = DownloadManager(this)
        downloadManager.setCallback(object : DownloadManager.DownloadCallback {
            override fun onProgress(gameId: String, progress: DownloadManager.DownloadProgress) {
                notifyProgress(gameId, activeTitles[gameId] ?: gameId, progress)
                broadcast(ACTION_PROGRESS, gameId, progress)
            }

            override fun onComplete(gameId: String, filePath: String) {
                val title = activeTitles[gameId] ?: gameId
                notifySimple(gameId, "Download complete", "$title is ready to install", ongoing = false)
                broadcast(ACTION_COMPLETE, gameId, DownloadManager.DownloadProgress(0, 0, 0f, DownloadManager.DownloadState.COMPLETED), filePath)
                activeTitles.remove(gameId)
                stopIfIdle()
            }

            override fun onError(gameId: String, message: String) {
                val title = activeTitles[gameId] ?: gameId
                notifySimple(gameId, "Download failed", "$title: $message", ongoing = false)
                broadcast(ACTION_ERROR, gameId, DownloadManager.DownloadProgress(0, 0, 0f, DownloadManager.DownloadState.FAILED, message))
                activeTitles.remove(gameId)
                stopIfIdle()
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE) ?: gameId
        activeTitles[gameId] = title
        startForeground(NOTIFICATION_BASE_ID, baseNotification("Preparing download", title))

        when (action) {
            ACTION_START, ACTION_RESUME -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "$gameId.zip"
                if (action == ACTION_RESUME || downloadManager.getState(gameId) == DownloadManager.DownloadState.PAUSED) {
                    downloadManager.resumeDownload(gameId, url, fileName)
                } else {
                    downloadManager.setFileName(gameId, fileName)
                    downloadManager.startDownload(gameId, url, fileName)
                }
            }
            ACTION_PAUSE -> {
                downloadManager.pauseDownload(gameId)
                val progress = DownloadManager.DownloadProgress(
                    bytesDownloaded = downloadManager.getDownloadedBytes(gameId),
                    totalBytes = downloadManager.getTotalBytes(gameId),
                    speed = 0f,
                    state = DownloadManager.DownloadState.PAUSED,
                )
                notifySimple(gameId, "Download paused", title, ongoing = false)
                broadcast(ACTION_PROGRESS, gameId, progress)
                activeTitles.remove(gameId)
                stopIfIdle()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notifyProgress(gameId: String, title: String, progress: DownloadManager.DownloadProgress) {
        val percent = if (progress.totalBytes > 0) {
            ((progress.bytesDownloaded * 100L) / progress.totalBytes).coerceIn(0L, 100L).toInt()
        } else 0
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText("$percent%")
            .setProgress(100, percent, progress.totalBytes <= 0)
            .setOngoing(true)
            .build()
        notificationManager().notify(notificationId(gameId), notification)
    }

    private fun notifySimple(gameId: String, title: String, text: String, ongoing: Boolean) {
        notificationManager().notify(
            notificationId(gameId),
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .build(),
        )
    }

    private fun baseNotification(title: String, text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun broadcast(action: String, gameId: String, progress: DownloadManager.DownloadProgress, filePath: String? = null) {
        sendBroadcast(Intent(action).apply {
            setPackage(packageName)
            putExtra(EXTRA_GAME_ID, gameId)
            putExtra(EXTRA_BYTES, progress.bytesDownloaded)
            putExtra(EXTRA_TOTAL, progress.totalBytes)
            putExtra(EXTRA_SPEED, progress.speed)
            putExtra(EXTRA_STATE, progress.state.name)
            putExtra(EXTRA_ERROR, progress.error)
            putExtra(EXTRA_FILE_PATH, filePath)
        })
    }

    private fun stopIfIdle() {
        if (activeTitles.isEmpty()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun notificationId(gameId: String): Int =
        NOTIFICATION_BASE_ID + kotlin.math.abs(gameId.hashCode() % 1000)

    companion object {
        const val ACTION_START = "com.runestone.app.download.START"
        const val ACTION_RESUME = "com.runestone.app.download.RESUME"
        const val ACTION_PAUSE = "com.runestone.app.download.PAUSE"
        const val ACTION_PROGRESS = "com.runestone.app.download.PROGRESS"
        const val ACTION_COMPLETE = "com.runestone.app.download.COMPLETE"
        const val ACTION_ERROR = "com.runestone.app.download.ERROR"

        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_BYTES = "bytes"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_STATE = "state"
        const val EXTRA_ERROR = "error"
        const val EXTRA_FILE_PATH = "file_path"

        private const val CHANNEL_ID = "runestone_downloads"
        private const val NOTIFICATION_BASE_ID = 4200
    }
}
