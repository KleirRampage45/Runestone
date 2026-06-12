/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.rtp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * High-level RTP install coordinator. Drives [RtpDownloader] ->
 * [RtpExtractor] -> [RtpManager.markInstalled] with status reporting
 * on the main thread for the UI to consume.
 */
class RtpInstaller(private val context: Context) {

    companion object {
        private const val TAG = "RtpInstaller"
    }

    sealed class Status {
        object Idle : Status()
        data class Downloading(val pack: RtpPack, val bytes: Long, val total: Long) : Status()
        data class Extracting(val pack: RtpPack, val bytes: Long) : Status()
        data class Installed(val pack: RtpPack, val totalBytes: Long, val fileCount: Int) : Status()
        data class Error(val pack: RtpPack, val message: String) : Status()
    }

    interface Listener {
        fun onStatus(status: Status) {}
    }

    private val manager = RtpManager(context)
    private val downloader = RtpDownloader(context)
    private val extractor = RtpExtractor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun manager(): RtpManager = manager
    fun isInstalled(pack: RtpPack): Boolean = manager.isInstalled(pack)
    fun isDownloading(pack: RtpPack): Boolean = downloader.isDownloading(pack)
    fun cancel(pack: RtpPack) = downloader.cancel(pack)
    fun uninstall(pack: RtpPack) = manager.markUninstalled(pack)

    /**
     * Begins download + extract for [pack]. Status updates are delivered
     * to [listener] on the main thread.
     */
    fun install(pack: RtpPack, listener: Listener) {
        if (manager.isInstalled(pack)) {
            post(listener) { onStatus(Status.Installed(pack, 0, 0)) }
            return
        }
        if (downloader.isDownloading(pack)) {
            // already running; just resubscribe
            return
        }

        downloader.start(pack, object : RtpDownloader.Callback {
            override fun onProgress(pack: RtpPack, progress: RtpDownloader.Progress) {
                when (progress.state) {
                    RtpDownloader.State.DOWNLOADING -> post(listener) {
                        onStatus(Status.Downloading(pack, progress.bytesDownloaded, progress.totalBytes))
                    }
                    RtpDownloader.State.COMPLETED -> {
                        // handled in onComplete
                    }
                    RtpDownloader.State.CANCELLED -> post(listener) {
                        onStatus(Status.Error(pack, "Cancelled"))
                    }
                    RtpDownloader.State.FAILED -> post(listener) {
                        onStatus(Status.Error(pack, progress.error ?: "Download failed"))
                    }
                    else -> Unit
                }
            }

            override fun onComplete(pack: RtpPack, stagingFile: File) {
                val targetDir = manager.packDir(pack)
                if (targetDir.exists()) targetDir.deleteRecursively()
                targetDir.mkdirs()

                post(listener) { onStatus(Status.Extracting(pack, stagingFile.length())) }
                Log.i(TAG, "Extracting ${pack.id} from ${stagingFile.absolutePath} -> ${targetDir.absolutePath}")

                Thread {
                    val result = extractor.extract(stagingFile, targetDir)
                    stagingFile.delete()

                    when (result) {
                        is RtpExtractor.Result.Success -> {
                            manager.markInstalled(pack)
                            post(listener) {
                                onStatus(Status.Installed(pack, result.extractedBytes, result.fileCount))
                            }
                        }
                        is RtpExtractor.Result.Failure -> {
                            targetDir.deleteRecursively()
                            post(listener) {
                                onStatus(Status.Error(pack, result.message))
                            }
                        }
                    }
                }.start()
            }

            override fun onError(pack: RtpPack, message: String) {
                post(listener) { onStatus(Status.Error(pack, message)) }
            }
        })
    }

    private inline fun post(listener: Listener, crossinline block: Listener.() -> Unit) {
        mainHandler.post {
            listener.block()
        }
    }
}
