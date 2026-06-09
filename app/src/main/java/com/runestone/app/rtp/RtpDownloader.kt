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
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads an RTP ZIP from the configured mirror into the app's
 * private files dir, with progress reporting and cancellation.
 *
 * Designed to be safe to call from the UI thread via a [Thread]; the
 * callback is invoked on the calling thread. For UI updates, post from
 * there to the main looper.
 */
class RtpDownloader(private val context: Context) {

    companion object {
        private const val TAG = "RtpDownloader"
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 32 * 1024
        private const val PROGRESS_INTERVAL_MS = 250L
    }

    enum class State { IDLE, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val state: State,
        val error: String? = null,
    ) {
        val fraction: Float
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    interface Callback {
        fun onProgress(pack: RtpPack, progress: Progress) {}
        fun onComplete(pack: RtpPack, stagingFile: File) {}
        fun onError(pack: RtpPack, message: String) {}
    }

    private val activeThreads = ConcurrentHashMap<String, Thread>()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()

    fun isDownloading(pack: RtpPack): Boolean = activeThreads.containsKey(pack.id)

    fun cancel(pack: RtpPack) {
        cancelFlags[pack.id] = true
    }

    fun start(pack: RtpPack, callback: Callback) {
        if (isDownloading(pack)) {
            callback.onError(pack, "Already downloading ${pack.id}")
            return
        }
        cancelFlags[pack.id] = false

        val stagingFile = File(context.filesDir, "rtp/${pack.id}.zip.part")
        stagingFile.parentFile?.mkdirs()
        if (stagingFile.exists()) stagingFile.delete()

        val thread = Thread({
            try {
                downloadInternal(pack, stagingFile, callback)
            } catch (t: Throwable) {
                Log.e(TAG, "Download thread crashed for ${pack.id}", t)
                callback.onError(pack, t.message ?: "Unknown error")
            } finally {
                activeThreads.remove(pack.id)
            }
        }, "rtp-dl-${pack.id}").also { it.isDaemon = true }

        activeThreads[pack.id] = thread
        thread.start()
    }

    private fun downloadInternal(pack: RtpPack, stagingFile: File, callback: Callback) {
        val url = URL(pack.sourceUrl)
        Log.i(TAG, "Starting ${pack.id} download from ${pack.sourceUrl}")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        val totalBytes = runCatching { conn.contentLengthLong }.getOrDefault(-1L)
        if (totalBytes > 0 && totalBytes < pack.approxBytes / 2) {
            conn.disconnect()
            callback.onError(pack, "Download truncated: got $totalBytes, expected ~${pack.approxBytes}")
            return
        }

        callback.onProgress(pack, Progress(0, totalBytes, State.DOWNLOADING))

        var downloaded = 0L
        var lastReport = 0L

        try {
            conn.inputStream.use { input: InputStream ->
                FileOutputStream(stagingFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancelFlags[pack.id] == true) {
                            output.close()
                            stagingFile.delete()
                            conn.disconnect()
                            callback.onProgress(pack, Progress(downloaded, totalBytes, State.CANCELLED))
                            return
                        }

                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            callback.onProgress(pack, Progress(downloaded, totalBytes, State.DOWNLOADING))
                        }
                    }
                    output.flush()
                }
            }
        } catch (e: Exception) {
            stagingFile.delete()
            conn.disconnect()
            callback.onError(pack, "Network error: ${e.message ?: e.javaClass.simpleName}")
            return
        }

        conn.disconnect()
        Log.i(TAG, "Downloaded ${pack.id}: $downloaded bytes")
        callback.onProgress(pack, Progress(downloaded, downloaded, State.COMPLETED))
        callback.onComplete(pack, stagingFile)
    }
}
