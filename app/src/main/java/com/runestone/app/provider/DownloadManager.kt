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
    private val pendingResumes = ConcurrentHashMap<String, Pair<String, String>>()
    private var callback: DownloadCallback? = null

    fun setCallback(cb: DownloadCallback) {
        callback = cb
    }

    fun getDownloadDir(): File {
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        ensureNoMedia(dir)
        return dir
    }

    private fun ensureNoMedia(dir: File) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            if (dir.isDirectory) {
                val marker = File(dir, ".nomedia")
                if (!marker.exists()) marker.writeText("")
            }
        }
    }

    fun getState(gameId: String): DownloadState {
        val name = prefs.getString("state_$gameId", DownloadState.IDLE.name)
        val state = try { DownloadState.valueOf(name!!) } catch (_: Exception) { DownloadState.IDLE }
        return if (state == DownloadState.DOWNLOADING && !activeDownloads.containsKey(gameId)) {
            DownloadState.PAUSED
        } else {
            state
        }
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
        File(getDownloadDir(), fileName).takeIf { it.exists() }?.delete()

        val thread = Thread {
            val resolvedUrl = try {
                val resolved = HosterResolver.resolve(url)
                validateDownloadUrl(resolved)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid download URL for $gameId: ${e.message}", e)
                setState(gameId, DownloadState.FAILED)
                callback?.onError(gameId, e.message ?: "Invalid download URL")
                activeDownloads.remove(gameId)
                cancelFlags.remove(gameId)
                pendingResumes.remove(gameId)?.let { (pendingUrl, pendingFileName) ->
                    resumeDownload(gameId, pendingUrl, pendingFileName)
                }
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
        if (activeDownloads.containsKey(gameId)) {
            pendingResumes[gameId] = Pair(url, fileName)
            setState(gameId, DownloadState.DOWNLOADING)
            return
        }
        cancelFlags[gameId] = false
        setState(gameId, DownloadState.DOWNLOADING)

        val thread = Thread {
            val resolvedUrl = try {
                val resolved = HosterResolver.resolve(url)
                validateDownloadUrl(resolved)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid download URL for $gameId: ${e.message}", e)
                setState(gameId, DownloadState.FAILED)
                callback?.onError(gameId, e.message ?: "Invalid download URL")
                activeDownloads.remove(gameId)
                cancelFlags.remove(gameId)
                pendingResumes.remove(gameId)
                return@Thread
            }
            download(gameId, resolvedUrl, fileName)
        }
        activeDownloads[gameId] = thread
        thread.start()
    }

    fun cancelDownload(gameId: String) {
        cancelFlags[gameId] = true
        pendingResumes.remove(gameId)
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0")

            if (existingBytes > 0) {
                conn.setRequestProperty("Range", "bytes=$existingBytes-")
                Log.i(TAG, "Resuming $gameId from $existingBytes bytes")
            }

            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode != 200 && responseCode != 206) {
                throw RuntimeException("HTTP $responseCode")
            }
            val contentType = conn.contentType.orEmpty().lowercase()
            if (contentType.contains("text/html")) {
                throw RuntimeException("Host returned a web page instead of a download")
            }

            val isResume = existingBytes > 0 && responseCode == 206
            val totalBytes = if (isResume) {
                val contentRange = conn.getHeaderField("Content-Range")
                contentRange?.substringAfter("/")?.toLongOrNull()
                    ?: (existingBytes + conn.contentLength.toLong())
            } else {
                conn.contentLength.toLong()
            }

            prefs.edit().putLong("total_$gameId", totalBytes).apply()

            input = conn.inputStream
            output = FileOutputStream(outputFile, isResume)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = if (isResume) existingBytes else 0L
            var bytesRead: Int
            var lastProgressTime = System.currentTimeMillis()
            var lastProgressBytes = bytesDownloaded
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
                output.close()
                output = null
                validateDownloadedFile(outputFile, gameId)
                setState(gameId, DownloadState.COMPLETED)
                prefs.edit().putLong("bytes_$gameId", bytesDownloaded).apply()
                Log.i(TAG, "Download complete: $gameId -> ${outputFile.absolutePath}")
                callback?.onComplete(gameId, outputFile.absolutePath)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $gameId: ${e.message}", e)
            if (cancelFlags[gameId] != true) {
                outputFile.delete()
            }
            setState(gameId, DownloadState.FAILED)
            callback?.onError(gameId, e.message ?: "Download failed")
        } finally {
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
            conn?.disconnect()
            activeDownloads.remove(gameId)
            cancelFlags.remove(gameId)
            pendingResumes.remove(gameId)?.let { (url, fileName) ->
                resumeDownload(gameId, url, fileName)
            }
        }
    }

    private fun setState(gameId: String, state: DownloadState) {
        prefs.edit().putString("state_$gameId", state.name).apply()
    }

    private fun validateDownloadUrl(rawUrl: String): String {
        val url = URL(rawUrl.trim())
        require(url.protocol.equals("https", ignoreCase = true)) { "Only direct HTTPS downloads are supported" }
        require(url.host.isNotBlank()) { "Download URL must include a host" }
        require(url.userInfo == null) { "Download URLs with embedded credentials are not supported" }
        return url.toString()
    }

    private fun validateDownloadedFile(file: File, gameId: String) {
        require(file.length() >= 1024L * 16L) { "Downloaded file is too small to be a game archive" }
        val header = ByteArray(4)
        file.inputStream().use { input ->
            val count = input.read(header)
            require(count == 4) { "Downloaded file is empty" }
        }
        val isZip = header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        require(isZip) { "Downloaded file is not a ZIP archive" }
        Log.i(TAG, "Validated archive for $gameId size=${file.length()}")
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
