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
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object HosterResolver {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
    private const val TIMEOUT = 30_000

    fun resolve(url: String): String {
        return when {
            url.contains("mediafire.com") -> resolveMediafire(url)
            url.contains("pixeldrain.com") -> resolvePixeldrain(url)
            url.contains("rootz.so") -> resolveRootz(url)
            url.contains("fuckingfast.co") || url.contains("fuckingfast.net") -> resolveFuckingFast(url)
            else -> url
        }
    }

    private fun resolveMediafire(url: String): String {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            val html = conn.inputStream.bufferedReader().readText()

            // Pattern 1: mediafire.com/file/{id}?...&dkey=...
            val p1 = Pattern.compile("""["'](https?:)?(//)?(www\.)?mediafire\.com/(file|view|download)/[^"']+\?dkey=[^"']+["']""")
            val m1 = p1.matcher(html)
            if (m1.find()) {
                var match = m1.group().trim('"', '\'')
                if (match.startsWith("//")) match = "https:$match"
                return match
            }

            // Pattern 2: download{N}.mediafire.com/{path}
            val p2 = Pattern.compile("""["']https?://download\d+\.mediafire\.com/[^"']+["']""")
            val m2 = p2.matcher(html)
            if (m2.find()) {
                return m2.group().trim('"', '\'')
            }

            throw RuntimeException("Could not extract Mediafire download URL")
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to resolve Mediafire URL: ${e.message}", e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun resolvePixeldrain(url: String): String {
        val id = url.substringAfter("/u/").substringBefore("?").trimEnd('/')
        if (id.isEmpty()) throw RuntimeException("Invalid Pixeldrain URL: could not extract file ID")
        return "https://pixeldrain.com/api/file/$id?download"
    }

    private fun resolveRootz(url: String): String {
        var conn: HttpURLConnection? = null
        try {
            val id = url.substringAfter("/d/").substringBefore("?").trimEnd('/')
            if (id.isEmpty()) throw RuntimeException("Invalid Rootz URL: could not extract file ID")
            val apiUrl = "https://www.rootz.so/api/files/download-by-short/$id"
            conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            if (json.optBoolean("success") && json.has("data")) {
                val downloadUrl = json.getJSONObject("data").optString("url", "")
                if (downloadUrl.isNotEmpty()) return downloadUrl
            }
            throw RuntimeException("Failed to resolve Rootz URL: no download URL in response")
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to resolve Rootz URL: ${e.message}", e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun resolveFuckingFast(url: String): String {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            val html = conn.inputStream.bufferedReader().readText()

            val p = Pattern.compile("""window\.open\("(https://fuckingfast\.co/dl/[^"]*)"\)""")
            val m = p.matcher(html)
            if (m.find()) {
                return m.group(1) ?: throw RuntimeException("Could not extract FuckingFast download URL")
            }
            throw RuntimeException("Could not extract FuckingFast download URL")
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Failed to resolve FuckingFast URL: ${e.message}", e)
        } finally {
            conn?.disconnect()
        }
    }
}
