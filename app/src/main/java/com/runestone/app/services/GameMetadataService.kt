/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Runestone Contributors
 *
 * Game metadata fetching service - retrieves game information from online sources
 */

package com.runestone.app.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches and caches game metadata from online sources.
 * Supports multiple backends: IGDB, RAWG, and custom APIs.
 */
class GameMetadataService(private val context: Context) {

    companion object {
        private const val TAG = "GameMetadataService"
        private const val CACHE_DIR = "metadata_cache"
        private const val COVERS_DIR = "game_covers"
        private const val CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val TIMEOUT_MS = 15000
        private const val USER_AGENT = "Runestone/1.0 (Android Game Launcher)"
    }

    data class GameMetadata(
        val title: String,
        val description: String?,
        val coverUrl: String?,
        val localCoverPath: String?,
        val screenshots: List<String>,
        val releaseDate: String?,
        val developer: String?,
        val publisher: String?,
        val genres: List<String>,
        val rating: Float?,
        val source: String
    )

    private val cache = ConcurrentHashMap<String, Pair<Long, GameMetadata>>()
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
    }
    private val coversDir: File by lazy {
        File(context.filesDir, COVERS_DIR).apply { mkdirs() }
    }

    private fun getApiKey(): String {
        val prefs = context.getSharedPreferences("runestone-settings-v1", android.content.Context.MODE_PRIVATE)
        return prefs.getString("rawgApiKey", "") ?: ""
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Fetch metadata for a game by title.
     * Returns cached result if available and not expired.
     */
    suspend fun fetchMetadata(
        gameTitle: String,
        engineType: String? = null,
        forceFresh: Boolean = false,
    ): GameMetadata? {
        val cacheKey = "v2-${safeCacheKey(gameTitle)}-${safeCacheKey(engineType ?: "any")}"

        if (!forceFresh) {
            // Check memory cache
            cache[cacheKey]?.let { (timestamp, metadata) ->
                if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
                    Log.d(TAG, "Cache hit for $gameTitle")
                    return metadata
                }
            }

            // Check disk cache
            loadFromDiskCache(cacheKey)?.let { metadata ->
                cache[cacheKey] = Pair(System.currentTimeMillis(), metadata)
                return metadata
            }
        }

        if (!forceFresh && isAmbiguousRawgQuery(gameTitle)) {
            Log.d(TAG, "Skipping ambiguous metadata query: $gameTitle")
            return null
        }

        // Fetch from API
        Log.d(TAG, "Fetching metadata for $gameTitle")
        val metadata = fetchFromAPIs(gameTitle, engineType)

        if (metadata != null) {
            cache[cacheKey] = Pair(System.currentTimeMillis(), metadata)
            saveToDiskCache(cacheKey, metadata)
        }

        return metadata
    }

    /**
     * Fetch metadata asynchronously with callback.
     */
    fun fetchMetadataAsync(
        gameTitle: String,
        engineType: String? = null,
        onResult: (GameMetadata?) -> Unit
    ) {
        scope.launch {
            val metadata = fetchMetadata(gameTitle, engineType)
            withContext(Dispatchers.Main) {
                onResult(metadata)
            }
        }
    }

    private suspend fun fetchFromAPIs(gameTitle: String, engineType: String?): GameMetadata? {
        // Try RAWG API first (free, no auth required for basic queries)
        fetchFromRAWG(gameTitle)?.let { return it }

        // Fallback to IGDB (requires API key)
        fetchFromIGDB(gameTitle)?.let { return it }

        return null
    }

    private fun fetchFromRAWG(gameTitle: String): GameMetadata? {
        val apiKey = getApiKey()
        return try {
            val encodedTitle = java.net.URLEncoder.encode(gameTitle, "UTF-8")
            val urlStr = if (apiKey.isNotEmpty()) {
                "https://api.rawg.io/api/games?key=$apiKey&search=$encodedTitle&page_size=5&search_precise=true"
            } else {
                "https://api.rawg.io/api/games?search=$encodedTitle&page_size=5&search_precise=true"
            }
            val url = URL(urlStr)

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val results = json.optJSONArray("results")

                if (results != null && results.length() > 0) {
                    val game = bestRawgMatch(gameTitle, results)
                    if (game == null) {
                        Log.d(TAG, "RAWG returned no close match for $gameTitle")
                        return null
                    }

                    val rawgName = game.optString("name").takeIf { it.isNotEmpty() } ?: gameTitle
                    val rawgId = game.optInt("id", 0)

                    val screenshots = mutableListOf<String>()
                    val screenshotsArray = game.optJSONArray("short_screenshots")
                    if (screenshotsArray != null) {
                        for (i in 0 until minOf(screenshotsArray.length(), 5)) {
                            screenshotsArray.getJSONObject(i).optString("image")?.let {
                                screenshots.add(it)
                            }
                        }
                    }

                    val genres = mutableListOf<String>()
                    val genresArray = game.optJSONArray("genres")
                    if (genresArray != null) {
                        for (i in 0 until genresArray.length()) {
                            genresArray.getJSONObject(i).optString("name")?.let {
                                genres.add(it)
                            }
                        }
                    }

                    val remoteCoverUrl = game.optString("background_image").takeIf { it.isNotEmpty() }
                    var localCoverPath: String? = null

                    // Download and cache cover image locally
                    if (remoteCoverUrl != null) {
                            val coverKey = safeCacheKey(gameTitle)
                        val coverFile = File(coversDir, "${coverKey}_rawg.jpg")
                        try {
                            val imgConn = URL(remoteCoverUrl).openConnection() as HttpURLConnection
                            imgConn.connectTimeout = TIMEOUT_MS
                            imgConn.readTimeout = TIMEOUT_MS
                            if (imgConn.responseCode == 200) {
                                coverFile.outputStream().use { out ->
                                    imgConn.inputStream.copyTo(out)
                                }
                                localCoverPath = coverFile.absolutePath
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to download cover for $gameTitle: ${e.message}")
                        }
                    }

                    val details = if (rawgId > 0) fetchRawgDetails(rawgId, apiKey) else null

                    GameMetadata(
                        title = rawgName,
                        description = details?.optString("description_raw")?.takeIf { it.isNotEmpty() },
                        coverUrl = remoteCoverUrl,
                        localCoverPath = localCoverPath,
                        screenshots = screenshots,
                        releaseDate = game.optString("released").takeIf { it.isNotEmpty() },
                        developer = details?.namesFromArray("developers"),
                        publisher = details?.namesFromArray("publishers"),
                        genres = genres,
                        rating = game.optDouble("rating", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
                        source = "RAWG"
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "RAWG API failed for $gameTitle: ${e.message}")
            null
        }
    }

    private fun fetchRawgDetails(rawgId: Int, apiKey: String): JSONObject? {
        return try {
            val urlStr = if (apiKey.isNotEmpty()) {
                "https://api.rawg.io/api/games/$rawgId?key=$apiKey"
            } else {
                "https://api.rawg.io/api/games/$rawgId"
            }
            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }

            if (connection.responseCode == 200) {
                JSONObject(connection.inputStream.bufferedReader().readText())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "RAWG details failed for $rawgId: ${e.message}")
            null
        }
    }

    private fun JSONObject.namesFromArray(name: String): String? {
        val array = optJSONArray(name) ?: return null
        val names = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotEmpty() }
        }
        return names.joinToString(", ").takeIf { it.isNotEmpty() }
    }

    private fun fetchFromIGDB(gameTitle: String): GameMetadata? {
        // IGDB requires authentication - would need API key configuration
        // Placeholder for future implementation
        return null
    }

    private fun loadFromDiskCache(cacheKey: String): GameMetadata? {
        return try {
            val cacheFile = File(cacheDir, "$cacheKey.json")
            if (!cacheFile.exists()) return null

            val timestamp = cacheFile.lastModified()
            if (System.currentTimeMillis() - timestamp > CACHE_DURATION_MS) {
                cacheFile.delete()
                return null
            }

            val json = JSONObject(cacheFile.readText())
            GameMetadata(
                title = json.getString("title"),
                description = json.optString("description").takeIf { it.isNotBlank() },
                coverUrl = json.optString("coverUrl").takeIf { it.isNotBlank() },
                localCoverPath = json.optString("localCoverPath").takeIf { it.isNotBlank() },
                screenshots = json.optJSONArray("screenshots")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                releaseDate = json.optString("releaseDate").takeIf { it.isNotBlank() },
                developer = json.optString("developer").takeIf { it.isNotBlank() },
                publisher = json.optString("publisher").takeIf { it.isNotBlank() },
                genres = json.optJSONArray("genres")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                rating = json.optDouble("rating", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
                source = json.optString("source", "unknown")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cache for $cacheKey: ${e.message}")
            null
        }
    }

    private fun saveToDiskCache(cacheKey: String, metadata: GameMetadata) {
        try {
            val json = JSONObject().apply {
                put("title", metadata.title)
                metadata.description?.let { put("description", it) }
                metadata.coverUrl?.let { put("coverUrl", it) }
                metadata.localCoverPath?.let { put("localCoverPath", it) }
                put("screenshots", org.json.JSONArray(metadata.screenshots))
                metadata.releaseDate?.let { put("releaseDate", it) }
                metadata.developer?.let { put("developer", it) }
                metadata.publisher?.let { put("publisher", it) }
                put("genres", org.json.JSONArray(metadata.genres))
                metadata.rating?.let { put("rating", it.toDouble()) }
                put("source", metadata.source)
            }

            val cacheFile = File(cacheDir, "$cacheKey.json")
            cacheFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cache for $cacheKey: ${e.message}")
        }
    }

    /**
     * Clear all cached metadata.
     */
    fun clearCache() {
        cache.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Metadata cache cleared")
    }

    /**
     * Cleanup resources.
     */
    fun destroy() {
        scope.cancel()
    }

    /**
     * Fetch metadata from RAWG and apply it to the game's per-game config.
     * Saves the metadata section and optionally the cover image.
     */
    fun fetchAndApplyMetadata(
        gameTitle: String,
        storageName: String,
        configService: com.runestone.app.data.GameConfigService,
        forceFresh: Boolean = false,
        onResult: (com.runestone.app.data.MetadataSection?) -> Unit,
    ) {
        // Check existing metadata first
        if (!forceFresh) {
            val existing = configService.loadPerGame(storageName).metadata
            if (
                existing.gameTitle.isNotEmpty() &&
                (existing.localCoverPath.isNotEmpty() || existing.coverUrl.isNotEmpty()) &&
                (existing.developer.isNotEmpty() || existing.releaseYear.isNotEmpty())
            ) {
                onResult(existing)
                return
            }
        }

        scope.launch {
            val metadata = fetchMetadata(gameTitle, forceFresh = forceFresh)
            val section = if (metadata != null) {
                com.runestone.app.data.MetadataSection(
                    gameTitle = gameTitle,
                    description = metadata.description ?: "",
                    developer = metadata.developer ?: "",
                    publisher = metadata.publisher ?: "",
                    genres = metadata.genres.joinToString(", "),
                    releaseYear = metadata.releaseDate?.take(4) ?: "",
                    coverUrl = metadata.coverUrl ?: "",
                    localCoverPath = metadata.localCoverPath ?: "",
                    metadataSource = metadata.source,
                )
            } else null

            if (section != null) {
                val config = configService.loadPerGame(storageName)
                configService.savePerGame(storageName, config.copy(metadata = section))
            }

            withContext(Dispatchers.Main) {
                onResult(section)
            }
        }
    }

    private fun safeCacheKey(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "unknown" }

    private fun isAmbiguousRawgQuery(title: String): Boolean {
        val normalized = normalizeTitle(title)
        return normalized.length < 4 || normalized in setOf("off", "ib", "ao", "it")
    }

    private fun bestRawgMatch(query: String, results: org.json.JSONArray): JSONObject? {
        var best: JSONObject? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val candidate = results.optJSONObject(i) ?: continue
            val name = candidate.optString("name")
            val score = titleMatchScore(query, name)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return if (bestScore >= 75) best else null
    }

    private fun titleMatchScore(query: String, candidate: String): Int {
        val q = normalizeTitle(query)
        val c = normalizeTitle(candidate)
        if (q.isBlank() || c.isBlank()) return 0
        if (q == c) return 100
        if (q.length >= 6 && (c.contains(q) || q.contains(c))) return 88
        val qTokens = q.split(" ").filter { it.length > 1 }.toSet()
        val cTokens = c.split(" ").filter { it.length > 1 }.toSet()
        if (qTokens.isEmpty() || cTokens.isEmpty()) return 0
        val overlap = qTokens.intersect(cTokens).size
        return ((overlap.toFloat() / qTokens.size.toFloat()) * 100).toInt()
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace("&", " and ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\b(the|game|demo|edition|version|v\\d+)\\b"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
