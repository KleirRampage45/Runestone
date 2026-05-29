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
        private const val KEY_API_URL = "api_url"
        private const val DEFAULT_API_URL = ""
    }

    fun getApiUrl(): String = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL

    fun setApiUrl(url: String) {
        prefs.edit().putString(KEY_API_URL, url.trim()).apply()
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
        val trimmed = url.trim()
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
        prefs.edit().remove(KEY_SOURCES).apply()
    }

    fun updateSourceStatus(id: String, status: SourceStatus) {
        val sources = getSources().toMutableList()
        val idx = sources.indexOfFirst { it.id == id }
        if (idx >= 0) {
            sources[idx] = sources[idx].copy(status = status)
            saveSources(sources)
        }
    }

    fun isStaticCatalogueUrl(url: String): Boolean =
        url.endsWith(".json") || url.contains("raw.githubusercontent.com")

    fun fetchGamesFromSources(onResult: (List<AvailableGame>, String?) -> Unit) {
        val apiUrl = getApiUrl()
        if (apiUrl.isEmpty()) {
            onResult(emptyList(), "Set up a game catalogue to browse games")
            return
        }

        if (isStaticCatalogueUrl(apiUrl)) {
            fetchGamesFromCatalogue(apiUrl, onResult)
            return
        }

        val sources = getSources()
        if (sources.isEmpty()) {
            onResult(emptyList(), "No sources configured")
            return
        }

        Thread {
            try {
                val allGames = mutableListOf<AvailableGame>()
                var lastError: String? = null

                for (source in sources) {
                    try {
                        val games = fetchFromSource(apiUrl, source)
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
                } else {
                    onResult(allGames.distinctBy { it.id }, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
                onResult(emptyList(), e.message ?: "Network error")
            }
        }.start()
    }

    fun fetchGamesFromCatalogue(url: String, onResult: (List<AvailableGame>, String?) -> Unit) {
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/json")

                try {
                    if (conn.responseCode != 200) {
                        throw RuntimeException("HTTP ${conn.responseCode}")
                    }

                    val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JSONObject(body)
                    val gamesArr = json.optJSONArray("games") ?: JSONArray()

                    val games = (0 until gamesArr.length()).map { i ->
                        val obj = gamesArr.getJSONObject(i)
                        AvailableGame(
                            id = obj.optString("id", "$i"),
                            title = obj.optString("title", "Unknown"),
                            engine = obj.optString("engine", "").ifEmpty { null },
                            fileSize = obj.optLong("fileSize", -1).let { if (it < 0) null else it },
                            downloadUrl = obj.optString("downloadUrl", "").ifEmpty { null },
                            pageUrl = obj.optString("pageUrl", "").ifEmpty { null },
                            sourceName = obj.optString("sourceName", "Catalogue"),
                            coverUrl = obj.optString("coverUrl", "").ifEmpty { null },
                        )
                    }

                    onResult(games, null)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Catalogue fetch failed", e)
                onResult(emptyList(), e.message ?: "Failed to load catalogue")
            }
        }.start()
    }

    private fun fetchFromSource(apiUrl: String, source: ProviderSource): List<AvailableGame> {
        val url = URL("${apiUrl.trimEnd('/')}/games?source=${source.url}")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Accept", "application/json")

        try {
            if (conn.responseCode != 200) {
                throw RuntimeException("HTTP ${conn.responseCode}")
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val gamesArr = json.optJSONArray("games") ?: return emptyList()

            return (0 until gamesArr.length()).map { i ->
                val obj = gamesArr.getJSONObject(i)
                AvailableGame(
                    id = obj.optString("id", "$i"),
                    title = obj.optString("title", "Unknown"),
                    engine = obj.optString("engine", "").ifEmpty { null },
                    fileSize = obj.optLong("fileSize", -1).let { if (it < 0) null else it },
                    downloadUrl = obj.optString("downloadUrl", "").ifEmpty { null },
                    pageUrl = obj.optString("pageUrl", "").ifEmpty { null },
                    sourceName = source.name,
                    coverUrl = obj.optString("coverUrl", "").ifEmpty { null },
                )
            }
        } finally {
            conn.disconnect()
        }
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
