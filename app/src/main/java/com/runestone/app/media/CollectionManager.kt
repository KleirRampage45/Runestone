/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Collection manager — organizes games into user-defined collections.
 * Stored as JSON in the app's internal files directory.
 */

package com.runestone.app.media

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CollectionInfo(
    val id: String,
    val name: String,
    val gameIds: List<String>,  // storage names
    val isBuiltIn: Boolean = false,  // "Favorites", "Recent", etc.
)

class CollectionManager(context: Context) {

    private val storeFile = File(context.filesDir, "collections.json")
    private var collections: MutableList<CollectionInfo> = mutableListOf()

    companion object {
        // Built-in collection IDs
        const val COLLECTION_FAVORITES = "__favorites"
        const val COLLECTION_RECENT = "__recent"
        const val COLLECTION_PLAYING = "__playing"
        const val COLLECTION_COMPLETED = "__completed"
    }

    init { load() }

    /** Load from disk, creating built-in collections if missing. */
    private fun load() {
        if (!storeFile.exists()) {
            collections = mutableListOf(
                CollectionInfo(COLLECTION_FAVORITES, "Favorites", emptyList(), true),
                CollectionInfo(COLLECTION_RECENT, "Recently Played", emptyList(), true),
                CollectionInfo(COLLECTION_PLAYING, "Playing", emptyList(), true),
                CollectionInfo(COLLECTION_COMPLETED, "Completed", emptyList(), true),
            )
            save()
            return
        }
        try {
            val json = JSONArray(storeFile.readText())
            collections.clear()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val gameArr = obj.optJSONArray("gameIds") ?: JSONArray()
                val games = (0 until gameArr.length()).map { gameArr.getString(it) }
                collections.add(CollectionInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    gameIds = games,
                    isBuiltIn = obj.optBoolean("isBuiltIn", false),
                ))
            }
        } catch (e: Exception) {
            // Corrupt file — reset
            collections.clear()
            save()
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (col in collections) {
            val obj = JSONObject().apply {
                put("id", col.id)
                put("name", col.name)
                put("gameIds", JSONArray(col.gameIds))
                put("isBuiltIn", col.isBuiltIn)
            }
            arr.put(obj)
        }
        storeFile.writeText(arr.toString(2))
    }

    // ── queries ──────────────────────────────────────────────────

    fun all(): List<CollectionInfo> = collections.toList()

    fun get(id: String): CollectionInfo? = collections.find { it.id == id }

    fun gamesIn(id: String): List<String> = get(id)?.gameIds ?: emptyList()

    fun isInCollection(collectionId: String, storageName: String): Boolean {
        return gamesIn(collectionId).contains(storageName)
    }

    fun collectionsFor(storageName: String): List<String> {
        return collections.filter { storageName in it.gameIds }.map { it.id }
    }

    // ── mutations ────────────────────────────────────────────────

    fun addToCollection(collectionId: String, storageName: String) {
        val col = collections.find { it.id == collectionId } ?: return
        if (storageName in col.gameIds) return
        val idx = collections.indexOf(col)
        collections[idx] = col.copy(gameIds = col.gameIds + storageName)
        save()
    }

    fun removeFromCollection(collectionId: String, storageName: String) {
        val col = collections.find { it.id == collectionId } ?: return
        val idx = collections.indexOf(col)
        collections[idx] = col.copy(gameIds = col.gameIds.filter { it != storageName })
        save()
    }

    fun toggleCollection(collectionId: String, storageName: String) {
        if (isInCollection(collectionId, storageName))
            removeFromCollection(collectionId, storageName)
        else
            addToCollection(collectionId, storageName)
    }

    fun recordPlayed(storageName: String) {
        val col = collections.find { it.id == COLLECTION_RECENT } ?: return
        val idx = collections.indexOf(col)
        // Move to front, keep max 20
        val updated = listOf(storageName) + col.gameIds.filter { it != storageName }
        collections[idx] = col.copy(gameIds = updated.take(20))
        save()
    }

    fun createCollection(name: String): CollectionInfo {
        val id = "custom_${System.currentTimeMillis()}"
        val col = CollectionInfo(id, name, emptyList(), false)
        collections.add(col)
        save()
        return col
    }

    fun deleteCollection(id: String): Boolean {
        val col = collections.find { it.id == id } ?: return false
        if (col.isBuiltIn) return false  // can't delete built-in
        collections.remove(col)
        save()
        return true
    }

    fun renameCollection(id: String, newName: String): Boolean {
        val col = collections.find { it.id == id } ?: return false
        val idx = collections.indexOf(col)
        collections[idx] = col.copy(name = newName)
        save()
        return true
    }
}
