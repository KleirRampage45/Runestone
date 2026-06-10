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

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple one-shot HTTP downloader for RTP ZIP files.
 *
 * Kept separate from [com.runestone.app.provider.DownloadManager] because
 * RTP downloads have different semantics: no pause/resume, no retry queue,
 * no game-ID state tracking. Just a single file from archive.org.
 */
class RtpDownloader {

    companion object {
        private const val TAG = "RtpDL"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
        private const val BUFFER_SIZE = 8192
    }

    /** Callback for download progress and completion. */
    interface Callback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long)
        fun onComplete(file: File)
        fun onError(message: String)
    }

    private var cancelFlag = false

    fun cancel() {
        cancelFlag = true
    }

    /**
     * Download a ZIP from [urlStr] to [outputFile] on a background thread.
     * Reports progress and completion via [callback].
     */
    fun download(urlStr: String, outputFile: File, callback: Callback) {
        Thread {
            var conn: HttpURLConnection? = null
            var input: InputStream? = null
            var output: FileOutputStream? = null

            try {
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()

                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT
                conn.readTimeout = READ_TIMEOUT
                conn.setRequestProperty("User-Agent", "Runestone/1.0 (Android RTP installer)")
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    throw RuntimeException("HTTP $responseCode — server returned error")
                }

                val totalBytes = conn.contentLength.toLong()
                input = conn.inputStream
                output = FileOutputStream(outputFile)

                val buffer = ByteArray(BUFFER_SIZE)
                var downloaded = 0L
                var bytesRead: Int
                var lastProgressTime = System.currentTimeMillis()

                while (true) {
                    if (cancelFlag) {
                        Log.i(TAG, "Download cancelled")
                        break
                    }
                    bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime >= 500) {
                        lastProgressTime = now
                        callback.onProgress(downloaded, totalBytes)
                    }
                }

                output.flush()
                output.close()
                output = null

                if (cancelFlag) {
                    outputFile.delete()
                    return@Thread
                }

                val fileSize = outputFile.length()
                if (fileSize < 1024L * 1024L) {
                    throw RuntimeException("Downloaded file is too small ($fileSize bytes) — may be an error page")
                }

                Log.i(TAG, "Download complete: ${outputFile.name} ($fileSize bytes)")
                callback.onComplete(outputFile)

            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                if (outputFile.exists()) outputFile.delete()
                callback.onError(e.message ?: "Download failed")
            } finally {
                try { output?.close() } catch (_: Exception) {}
                try { input?.close() } catch (_: Exception) {}
                conn?.disconnect()
            }
        }.start()
    }
}
