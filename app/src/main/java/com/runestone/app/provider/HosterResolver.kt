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

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Resolves hoster-specific URLs to direct download links that Android can download.
 *
 * Some hosters (Mediafire, MEGA) return mobile HTML pages on Android instead of files.
 * This resolver knows which hosters work and how to get direct URLs from the ones that do.
 */
object HosterResolver {

    private const val TAG = "HosterResolver"

    data class HostStatus(
        val supported: Boolean,
        val label: String,
    )

    /**
     * Returns whether this URL can be downloaded on Android.
     */
    fun isSupported(url: String): HostStatus {
        val lower = url.lowercase()
        return when {
            lower.contains("mediafire.com") ->
                HostStatus(false, "Mediafire")
            lower.contains("mega.nz") || lower.contains("mega.co.nz") ->
                HostStatus(false, "MEGA")
            lower.startsWith("magnet:") ->
                HostStatus(false, "Torrent")
            lower.contains("1fichier.com") ->
                HostStatus(false, "1Fichier")
            lower.contains("pixeldrain.com") ->
                HostStatus(true, "Pixeldrain")
            lower.contains("gofile.io") ->
                HostStatus(true, "Gofile")
            lower.contains("datanodes.to") ->
                HostStatus(true, "Datanodes")
            lower.contains("fuckingfast.co") ->
                HostStatus(true, "FuckingFast")
            lower.contains("rootz.so") ->
                HostStatus(true, "Rootz")
            lower.contains("vikingfile.com") || lower.contains("vik1ngfile.site") ->
                HostStatus(true, "VikingFile")
            lower.startsWith("itch://") ->
                HostStatus(true, "itch.io")
            // Direct HTTPS links — assume supported
            lower.startsWith("https://") ->
                HostStatus(true, "Direct")
            lower.startsWith("http://") ->
                HostStatus(false, "HTTP (not HTTPS)")
            else ->
                HostStatus(false, "Unknown host")
        }
    }

    /**
     * Resolves a download URL to a direct download link.
     * Throws RuntimeException if the hoster is not supported on Android.
     */
    fun resolve(rawUrl: String): String {
        val status = isSupported(rawUrl)
        if (!status.supported) {
            throw RuntimeException("${status.label} is not supported on Android")
        }

        val lower = rawUrl.lowercase()
        return when {
            lower.startsWith("itch://") -> resolveItch(rawUrl)
            lower.contains("pixeldrain.com") -> resolvePixeldrain(rawUrl)
            lower.contains("gofile.io") -> resolveGofile(rawUrl)
            // Direct HTTPS or other supported hosters — pass through
            else -> rawUrl
        }
    }

    private fun resolvePixeldrain(url: String): String {
        val uri = URI(url)
        val parts = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        val fileId = when {
            parts.size >= 2 && parts[0].equals("u", ignoreCase = true) -> parts[1]
            parts.size >= 2 && parts[0].equals("file", ignoreCase = true) -> parts[1]
            parts.size >= 3 && parts[0].equals("api", ignoreCase = true) && parts[1].equals("file", ignoreCase = true) -> parts[2]
            else -> ""
        }
        require(fileId.isNotBlank()) { "Invalid Pixeldrain URL" }
        return "https://pixeldrain.com/api/file/$fileId?download"
    }

    private fun resolveItch(url: String): String {
        val uri = URI(url)
        val projectUrl = uri.getQueryParameter("url")
            ?: throw RuntimeException("itch.io entry is missing project URL")
        val uploadId = uri.getQueryParameter("upload_id")
            ?: throw RuntimeException("itch.io entry is missing upload ID")

        val pageConn = (URL(projectUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Runestone/1.0")
        }
        val cookies = mutableListOf<String>()
        val pageHtml = try {
            pageConn.headerFields["Set-Cookie"]?.mapTo(cookies) { it.substringBefore(";") }
            pageConn.inputStream.bufferedReader().readText()
        } finally {
            pageConn.disconnect()
        }

        val csrf = extractCsrf(pageHtml)
        val downloadEndpoint = extractGenerateDownloadUrl(pageHtml)
            ?: "${projectUrl.trimEnd('/')}/download_url"
        val downloadPageJson = postItch(downloadEndpoint, projectUrl, csrf, cookies)
        val downloadPageUrl = JSONObject(downloadPageJson).optString("url", "")
        if (downloadPageUrl.isBlank()) {
            throw RuntimeException("itch.io did not return a download page")
        }

        val downloadPageConn = (URL(downloadPageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Runestone/1.0")
            setRequestProperty("Cookie", cookies.joinToString("; "))
        }
        val downloadPageHtml = try {
            downloadPageConn.headerFields["Set-Cookie"]?.mapTo(cookies) { it.substringBefore(";") }
            downloadPageConn.inputStream.bufferedReader().readText()
        } finally {
            downloadPageConn.disconnect()
        }

        val downloadCsrf = extractCsrf(downloadPageHtml)
        val fileEndpoint = "${projectUrl.trimEnd('/')}/file/$uploadId?source=game_download"
        val fileJson = postItch(fileEndpoint, downloadPageUrl, downloadCsrf, cookies)
        val fileUrl = JSONObject(fileJson).optString("url", "")
        if (fileUrl.isBlank()) {
            throw RuntimeException("itch.io did not return a file URL")
        }
        return fileUrl
    }

    private fun extractCsrf(html: String): String {
        val match = Regex("""<meta\s+name=["']csrf_token["']\s+value=["']([^"']+)["']""").find(html)
        return match?.groupValues?.get(1)
            ?: throw RuntimeException("itch.io CSRF token not found")
    }

    private fun extractGenerateDownloadUrl(html: String): String? {
        val match = Regex(""""generate_download_url"\s*:\s*"([^"]+)"""").find(html)
            ?: return null
        return match.groupValues[1]
            .replace("\\/", "/")
            .replace("\\u0026", "&")
    }

    private fun postItch(url: String, referer: String, csrf: String, cookies: List<String>): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Runestone/1.0")
            setRequestProperty("Referer", referer)
            setRequestProperty("X-CSRF-Token", csrf)
            setRequestProperty("Cookie", cookies.joinToString("; "))
        }
        return try {
            conn.outputStream.use { output ->
                val body = "csrf_token=${URLEncoder.encode(csrf, Charsets.UTF_8.name())}"
                output.write(body.toByteArray())
            }
            if (conn.responseCode != 200) {
                throw RuntimeException("itch.io returned HTTP ${conn.responseCode}")
            }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun URI.getQueryParameter(name: String): String? {
        return rawQuery
            ?.split("&")
            ?.firstOrNull { it.substringBefore("=") == name }
            ?.substringAfter("=", "")
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun resolveGofile(url: String): String {
        // Gofile: need to call their API to get the download server
        val fileId = url.substringAfter("/d/").substringBefore("/").substringBefore("?")
        require(fileId.isNotBlank()) { "Invalid Gofile URL: $url" }

        try {
            // Step 1: Get a server
            val serverUrl = URL("https://api.gofile.io/servers")
            val serverConn = serverUrl.openConnection() as HttpURLConnection
            serverConn.connectTimeout = 10_000
            serverConn.readTimeout = 10_000
            serverConn.setRequestProperty("User-Agent", "Runestone/1.0")

            val serverResponse = serverConn.inputStream.bufferedReader().readText()
            serverConn.disconnect()

            val serverJson = JSONObject(serverResponse)
            if (serverJson.optString("status") != "ok") {
                throw RuntimeException("Gofile: server request failed")
            }
            val servers = serverJson.getJSONObject("data")
                .getJSONArray("servers")
            if (servers.length() == 0) {
                throw RuntimeException("Gofile: no servers available")
            }
            val serverName = servers.getJSONObject(0).getString("name")

            // Step 2: Get content info
            val apiUrl = URL("https://$serverName.gofile.io/contents/$fileId")
            val contentConn = apiUrl.openConnection() as HttpURLConnection
            contentConn.connectTimeout = 10_000
            contentConn.readTimeout = 10_000
            contentConn.setRequestProperty("User-Agent", "Runestone/1.0")

            val contentResponse = contentConn.inputStream.bufferedReader().readText()
            contentConn.disconnect()

            val contentJson = JSONObject(contentResponse)
            if (contentJson.optString("status") != "ok") {
                throw RuntimeException("Gofile: content not found or private")
            }

            // Find the first file child and return its direct link
            val data = contentJson.getJSONObject("data")
            val children = data.optJSONObject("children")
            if (children != null) {
                val keys = children.keys()
                if (keys.hasNext()) {
                    val child = children.getJSONObject(keys.next())
                    val directLink = child.optString("link", "")
                    if (directLink.isNotBlank()) return directLink
                }
            }

            // Fallback: try the direct download URL
            return "https://$serverName.gofile.io/download/$fileId/"
        } catch (e: Exception) {
            Log.e(TAG, "Gofile resolution failed for $url: ${e.message}", e)
            throw RuntimeException("Gofile: ${e.message}")
        }
    }
}
