/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.provider

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "DL"
        private const val PREFS = "runestone_downloads"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
        private const val BUFFER_SIZE = 8192
    }

    enum class DownloadState { IDLE, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: Float,
        val state: DownloadState,
        val error: String? = null,
    )

    interface DownloadCallback {
        fun onProgress(gameId: String, progress: DownloadProgress)
        fun onComplete(gameId: String, filePath: String)
        fun onError(gameId: String, message: String)
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val activeDownloads = ConcurrentHashMap<String, Thread>()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()
    private var callback: DownloadCallback? = null

    fun setCallback(cb: DownloadCallback) {
        callback = cb
    }

    fun getDownloadDir(): File {
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getState(gameId: String): DownloadState {
        val name = prefs.getString("state_$gameId", DownloadState.IDLE.name)
        return try { DownloadState.valueOf(name!!) } catch (_: Exception) { DownloadState.IDLE }
    }

    fun getDownloadedBytes(gameId: String): Long {
        return prefs.getLong("bytes_$gameId", 0)
    }

    fun getTotalBytes(gameId: String): Long {
        return prefs.getLong("total_$gameId", 0)
    }

    fun startDownload(gameId: String, url: String, fileName: String) {
        if (activeDownloads.containsKey(gameId)) {
            Log.w(TAG, "Download already active for $gameId")
            return
        }

        cancelFlags[gameId] = false
        setState(gameId, DownloadState.DOWNLOADING)

        val thread = Thread {
            val resolvedUrl = try {
                HosterResolver.resolve(url)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve URL for $gameId: ${e.message}", e)
                setState(gameId, DownloadState.FAILED)
                callback?.onError(gameId, "URL resolution failed: ${e.message}")
                return@Thread
            }
            download(gameId, resolvedUrl, fileName)
        }
        activeDownloads[gameId] = thread
        thread.start()
    }

    fun pauseDownload(gameId: String) {
        cancelFlags[gameId] = true
        setState(gameId, DownloadState.PAUSED)
    }

    fun resumeDownload(gameId: String, url: String, fileName: String) {
        if (activeDownloads.containsKey(gameId)) return
        cancelFlags[gameId] = false
        setState(gameId, DownloadState.DOWNLOADING)

        val thread = Thread {
            val resolvedUrl = try {
                HosterResolver.resolve(url)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve URL for $gameId: ${e.message}", e)
                setState(gameId, DownloadState.FAILED)
                callback?.onError(gameId, "URL resolution failed: ${e.message}")
                return@Thread
            }
            download(gameId, resolvedUrl, fileName)
        }
        activeDownloads[gameId] = thread
        thread.start()
    }

    fun cancelDownload(gameId: String) {
        cancelFlags[gameId] = true
        activeDownloads.remove(gameId)?.interrupt()
        setState(gameId, DownloadState.IDLE)
        prefs.edit().remove("bytes_$gameId").remove("total_$gameId").apply()
        val file = File(getDownloadDir(), getFileName(gameId))
        if (file.exists()) file.delete()
    }

    fun isActive(gameId: String): Boolean {
        return activeDownloads.containsKey(gameId)
    }

    private fun download(gameId: String, urlStr: String, fileName: String) {
        val outputFile = File(getDownloadDir(), fileName)
        val existingBytes = if (outputFile.exists()) outputFile.length() else 0L

        var conn: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("Accept", "*/*")

            if (existingBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$existingBytes-")
                Log.i(TAG, "Resuming $gameId from $existingBytes bytes")
            }

            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode != 200 && responseCode != 206) {
                throw RuntimeException("HTTP $responseCode")
            }

            val totalBytes = if (responseCode == 206) {
                val contentRange = conn.getHeaderField("Content-Range")
                contentRange?.substringAfter("/")?.toLongOrNull()
                    ?: (existingBytes + conn.contentLength.toLong())
            } else {
                conn.contentLength.toLong()
            }

            prefs.edit().putLong("total_$gameId", totalBytes).apply()

            input = conn.inputStream
            output = FileOutputStream(outputFile, existingBytes > 0 && responseCode == 206)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = existingBytes
            var bytesRead: Int
            var lastProgressTime = System.currentTimeMillis()
            var lastProgressBytes = existingBytes
            var speed = 0f

            while (true) {
                if (cancelFlags[gameId] == true) {
                    Log.i(TAG, "Download cancelled/paused for $gameId")
                    break
                }

                bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                output.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead

                val now = System.currentTimeMillis()
                val elapsed = now - lastProgressTime
                if (elapsed >= 500) {
                    speed = ((bytesDownloaded - lastProgressBytes).toFloat() / elapsed) * 1000f
                    lastProgressBytes = bytesDownloaded
                    lastProgressTime = now

                    prefs.edit().putLong("bytes_$gameId", bytesDownloaded).apply()

                    callback?.onProgress(gameId, DownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        speed = speed,
                        state = DownloadState.DOWNLOADING,
                    ))
                }
            }

            if (cancelFlags[gameId] != true) {
                output.flush()
                setState(gameId, DownloadState.COMPLETED)
                prefs.edit().putLong("bytes_$gameId", bytesDownloaded).apply()
                Log.i(TAG, "Download complete: $gameId -> ${outputFile.absolutePath}")
                callback?.onComplete(gameId, outputFile.absolutePath)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $gameId: ${e.message}", e)
            setState(gameId, DownloadState.FAILED)
            callback?.onError(gameId, e.message ?: "Download failed")
        } finally {
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
            conn?.disconnect()
            activeDownloads.remove(gameId)
            cancelFlags.remove(gameId)
        }
    }

    private fun setState(gameId: String, state: DownloadState) {
        prefs.edit().putString("state_$gameId", state.name).apply()
    }

    private fun getFileName(gameId: String): String {
        return prefs.getString("filename_$gameId", "$gameId.zip") ?: "$gameId.zip"
    }

    fun setFileName(gameId: String, fileName: String) {
        prefs.edit().putString("filename_$gameId", fileName).apply()
    }

    fun getOutputFile(gameId: String): File {
        return File(getDownloadDir(), getFileName(gameId))
    }

    fun cleanup(gameId: String) {
        prefs.edit()
            .remove("state_$gameId")
            .remove("bytes_$gameId")
            .remove("total_$gameId")
            .remove("filename_$gameId")
            .apply()
    }
}
