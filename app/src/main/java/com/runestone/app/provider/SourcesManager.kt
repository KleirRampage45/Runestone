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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SourcesManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("runestone_providers", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SourcesManager"
        private const val KEY_SOURCES = "sources_json"
        private const val BUNDLED_CATALOGUE = "runestone-catalogue.json"
        private const val MAX_CATALOGUE_CHARS = 4 * 1024 * 1024
        private const val MAX_GAMES_PER_SOURCE = 10_000
        private const val MAX_DOWNLOAD_OPTIONS_PER_GAME = 20
    }

    fun getSources(): List<ProviderSource> {
        val json = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { ProviderSource.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sources", e)
            emptyList()
        }
    }

    private fun saveSources(sources: List<ProviderSource>) {
        val arr = JSONArray()
        sources.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SOURCES, arr.toString()).apply()
    }

    fun addSource(url: String): ProviderSource {
        val trimmed = validateHttpsUrl(url)
        getSources().firstOrNull { it.url == trimmed }?.let { return it }
        val name = extractNameFromUrl(trimmed)
        val source = ProviderSource(name = name, url = trimmed)
        val sources = getSources().toMutableList()
        sources.add(source)
        saveSources(sources)
        return source
    }

    fun removeSource(id: String) {
        saveSources(getSources().filter { it.id != id })
    }

    fun clearSources() {
        prefs.edit().remove(KEY_SOURCES).remove("api_url").apply()
    }

    fun updateSourceStatus(id: String, status: SourceStatus) {
        val sources = getSources().toMutableList()
        val idx = sources.indexOfFirst { it.id == id }
        if (idx >= 0) {
            sources[idx] = sources[idx].copy(status = status)
            saveSources(sources)
        }
    }

    fun fetchGamesFromSources(onResult: (List<AvailableGame>, String?) -> Unit) {
        val sources = getSources()

        Thread {
            try {
                val allGames = loadBundledCatalogue().toMutableList()
                var lastError: String? = null

                for (source in sources) {
                    try {
                        val games = fetchGamesFromCatalogue(source)
                        allGames.addAll(games)
                        updateSourceStatus(source.id, SourceStatus.ACTIVE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch from ${source.name}: ${e.message}")
                        updateSourceStatus(source.id, SourceStatus.FAILED)
                        lastError = e.message
                    }
                }

                if (allGames.isEmpty() && lastError != null) {
                    onResult(emptyList(), lastError)
                } else if (allGames.isEmpty()) {
                    onResult(emptyList(), "Add a source URL to browse available games")
                } else {
                    onResult(allGames.distinctBy { it.id }, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
                onResult(emptyList(), e.message ?: "Network error")
            }
        }.start()
    }

    private fun loadBundledCatalogue(): List<AvailableGame> {
        return try {
            val body = context.assets.open(BUNDLED_CATALOGUE)
                .bufferedReader()
                .use { it.readText() }
            val source = ProviderSource(
                id = "runestone-bundled",
                name = "Runestone Picks",
                url = "https://kleirrampage45.github.io/runestone-catalogue/games.json",
                status = SourceStatus.ACTIVE,
            )
            parseCatalogueFormat(source, JSONObject(body))
        } catch (e: Exception) {
            Log.w(TAG, "Bundled catalogue unavailable", e)
            emptyList()
        }
    }

    private fun fetchGamesFromCatalogue(source: ProviderSource): List<AvailableGame> {
        val validatedUrl = validateHttpsUrl(source.url)
        val url = URL(validatedUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Runestone/1.0")

        try {
            if (conn.responseCode != 200) throw RuntimeException("HTTP ${conn.responseCode}")

            val contentType = conn.contentType ?: ""
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw RuntimeException("Source returned HTML instead of JSON")
            }

            val body = readLimitedBody(conn)
            return parseCatalogueFormat(source, JSONObject(body))
        } finally {
            conn.disconnect()
        }
    }

    private fun parseCatalogueFormat(source: ProviderSource, json: JSONObject): List<AvailableGame> {
        val gamesArr = json.optJSONArray("games") ?: return emptyList()
        require(gamesArr.length() <= MAX_GAMES_PER_SOURCE) {
            "Catalogue exceeds $MAX_GAMES_PER_SOURCE games"
        }
        return (0 until gamesArr.length()).map { i ->
            val obj = gamesArr.getJSONObject(i)
            val optsArr = obj.optJSONArray("downloadOptions")
            val options = if (optsArr != null) {
                require(optsArr.length() <= MAX_DOWNLOAD_OPTIONS_PER_GAME) {
                    "Game exceeds $MAX_DOWNLOAD_OPTIONS_PER_GAME download options"
                }
                (0 until optsArr.length()).map { optionIndex ->
                    val optObj = optsArr.getJSONObject(optionIndex)
                    DownloadOption(
                        name = optObj.optString("name", "Download"),
                        host = optObj.optString("host", source.name),
                        url = validateDownloadUrl(optObj.optString("url", "")),
                        fileSize = optObj.optLong("fileSize", -1).let { if (it < 0) null else it },
                    )
                }
            } else {
                val legacyUrl = obj.optString("downloadUrl", "").ifEmpty { null }
                if (legacyUrl != null) {
                    listOf(DownloadOption(name = "Download", host = "Direct", url = validateDownloadUrl(legacyUrl)))
                } else emptyList()
            }
            val remoteId = obj.optString("id", "").ifBlank { i.toString() }
            AvailableGame(
                id = "${source.id}:$remoteId",
                title = obj.optString("title", "Unknown"),
                engine = obj.optString("engine", "").ifEmpty { null },
                fileSize = obj.optLong("fileSize", -1).let { if (it < 0) null else it },
                downloadOptions = options,
                sourceName = source.name,
                coverUrl = obj.optString("coverUrl", "").ifEmpty { null }?.let(::validateHttpsUrl),
                pageUrl = obj.optString("pageUrl", "").ifEmpty { null }?.let(::validateHttpsUrl),
                description = obj.optString("description", "").ifEmpty { null },
                tags = obj.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList(),
                language = obj.optString("language", "").ifEmpty { null },
                license = obj.optString("license", "").ifEmpty { null },
                rawgQuery = obj.optString("rawgQuery", "").ifEmpty { null },
            )
        }
    }

    private fun readLimitedBody(conn: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        return reader.use {
            val body = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(body.length + count <= MAX_CATALOGUE_CHARS) {
                    "Catalogue exceeds ${MAX_CATALOGUE_CHARS / 1024 / 1024} MB"
                }
                body.append(buffer, 0, count)
            }
            body.toString()
        }
    }

    private fun validateHttpsUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val upgraded = if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://" + trimmed.substringAfter("://")
        } else {
            trimmed
        }
        val url = URL(upgraded)
        require(url.protocol.equals("https", ignoreCase = true)) { "Only HTTPS URLs are supported" }
        require(url.host.isNotBlank()) { "URL must include a host" }
        require(url.userInfo == null) { "URLs with embedded credentials are not supported" }
        return url.toString()
    }

    private fun validateDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("itch://", ignoreCase = true)) {
            return trimmed
        }
        return validateHttpsUrl(trimmed)
    }

    private fun extractNameFromUrl(url: String): String {
        return try {
            val host = URL(url).host
            host.removePrefix("www.").take(24)
        } catch (_: Exception) {
            url.take(24)
        }
    }
}
