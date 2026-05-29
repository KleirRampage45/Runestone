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

import org.json.JSONArray
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

data class DownloadOption(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val url: String,
    val fileSize: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("url", url)
        put("fileSize", fileSize ?: -1)
    }

    companion object {
        fun fromJson(obj: JSONObject): DownloadOption = DownloadOption(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            host = obj.optString("host", ""),
            url = obj.optString("url", ""),
            fileSize = obj.optLong("fileSize", -1).let { if (it < 0) null else it },
        )
    }
}

data class AvailableGame(
    val id: String,
    val title: String,
    val engine: String?,
    val fileSize: Long?,
    val downloadOptions: List<DownloadOption>,
    val sourceName: String,
    val coverUrl: String?,
) {
    val downloadUrl: String? get() = downloadOptions.firstOrNull()?.url
    val pageUrl: String? get() = null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("engine", engine ?: "")
        put("fileSize", fileSize ?: -1)
        put("sourceName", sourceName)
        put("coverUrl", coverUrl ?: "")
        val optsArr = JSONArray()
        downloadOptions.forEach { optsArr.put(it.toJson()) }
        put("downloadOptions", optsArr)
    }

    companion object {
        fun fromJson(obj: JSONObject): AvailableGame {
            val optsArr = obj.optJSONArray("downloadOptions")
            val options = if (optsArr != null) {
                (0 until optsArr.length()).map { DownloadOption.fromJson(optsArr.getJSONObject(it)) }
            } else {
                val legacyUrl = obj.optString("downloadUrl", "").ifEmpty { null }
                if (legacyUrl != null) {
                    listOf(DownloadOption(name = "Download", host = "Direct", url = legacyUrl))
                } else emptyList()
            }
            return AvailableGame(
                id = obj.optString("id", ""),
                title = obj.optString("title", "Unknown"),
                engine = obj.optString("engine", "").ifEmpty { null },
                fileSize = obj.optLong("fileSize", -1).let { if (it < 0) null else it },
                downloadOptions = options,
                sourceName = obj.optString("sourceName", ""),
                coverUrl = obj.optString("coverUrl", "").ifEmpty { null },
            )
        }

        fun fromCatalogueJson(obj: JSONObject): AvailableGame {
            val optsArr = obj.optJSONArray("downloadOptions")
            val options = if (optsArr != null) {
                (0 until optsArr.length()).map { DownloadOption.fromJson(optsArr.getJSONObject(it)) }
            } else {
                val legacyUrl = obj.optString("downloadUrl", "").ifEmpty { null }
                if (legacyUrl != null) {
                    listOf(DownloadOption(name = "Download", host = "Direct", url = legacyUrl))
                } else emptyList()
            }
            return AvailableGame(
                id = obj.optString("id", ""),
                title = obj.optString("title", "Unknown"),
                engine = obj.optString("engine", "").ifEmpty { null },
                fileSize = obj.optLong("fileSize", -1).let { if (it < 0) null else it },
                downloadOptions = options,
                sourceName = obj.optString("sourceName", "Catalogue"),
                coverUrl = obj.optString("coverUrl", "").ifEmpty { null },
            )
        }
    }
}
