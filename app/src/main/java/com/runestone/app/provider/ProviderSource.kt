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

import org.json.JSONObject
import java.util.UUID

data class ProviderSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val status: SourceStatus = SourceStatus.PENDING,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", url)
        put("status", status.name)
        put("addedAt", addedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject): ProviderSource = ProviderSource(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            url = obj.optString("url", ""),
            status = try { SourceStatus.valueOf(obj.optString("status", "PENDING")) } catch (_: Exception) { SourceStatus.PENDING },
            addedAt = obj.optLong("addedAt", System.currentTimeMillis()),
        )
    }
}

enum class SourceStatus { PENDING, ACTIVE, FAILED }

data class AvailableGame(
    val id: String,
    val title: String,
    val engine: String?,
    val fileSize: Long?,
    val downloadUrl: String?,
    val sourceName: String,
    val coverUrl: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("engine", engine ?: "")
        put("fileSize", fileSize ?: -1)
        put("downloadUrl", downloadUrl ?: "")
        put("sourceName", sourceName)
        put("coverUrl", coverUrl ?: "")
    }

    companion object {
        fun fromJson(obj: JSONObject): AvailableGame = AvailableGame(
            id = obj.optString("id", ""),
            title = obj.optString("title", "Unknown"),
            engine = obj.optString("engine", "").ifEmpty { null },
            fileSize = obj.optLong("fileSize", -1).let { if (it < 0) null else it },
            downloadUrl = obj.optString("downloadUrl", "").ifEmpty { null },
            sourceName = obj.optString("sourceName", ""),
            coverUrl = obj.optString("coverUrl", "").ifEmpty { null },
        )
    }
}
