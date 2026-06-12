/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.runestone.app.cache

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Caches store metadata (game listings from the remote catalog) to a local file.
 * Reduces network fetches and provides offline access to previously fetched data.
 */
class StoreMetadataCache(context: Context) {
    private val cacheFile = File(context.filesDir, "store_metadata_cache.json")
    private val maxAge = 24 * 60 * 60 * 1000L // 24 hours

    fun load(): List<CachedMetadata>? {
        if (!cacheFile.exists()) return null
        if (System.currentTimeMillis() - cacheFile.lastModified() > maxAge) {
            cacheFile.delete()
            return null
        }
        return try {
            val json = JSONObject(cacheFile.readText())
            if (json.optInt("version", 0) != 1) return null
            val arr = json.getJSONArray("games")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CachedMetadata(
                    storageName = obj.getString("storageName"),
                    title = obj.optString("title"),
                    coverUrl = obj.optString("coverUrl"),
                    description = obj.optString("description"),
                    engineType = obj.optString("engineType"),
                )
            }
        } catch (e: Exception) { null }
    }

    fun save(games: List<CachedMetadata>) {
        try {
            val arr = JSONArray()
            games.forEach { game ->
                arr.put(JSONObject().apply {
                    put("storageName", game.storageName)
                    put("title", game.title)
                    put("coverUrl", game.coverUrl)
                    put("description", game.description)
                    put("engineType", game.engineType)
                })
            }
            cacheFile.writeText(JSONObject().apply {
                put("version", 1)
                put("games", arr)
            }.toString())
        } catch (e: Exception) { /* cache write failure is non-critical */ }
    }

    data class CachedMetadata(
        val storageName: String,
        val title: String,
        val coverUrl: String,
        val description: String,
        val engineType: String,
    )
}
