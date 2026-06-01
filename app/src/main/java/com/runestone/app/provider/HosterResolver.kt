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
import java.net.URL
import java.util.regex.Pattern

object HosterResolver {

    private const val TAG = "HosterResolver"

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
    private const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 Chrome/144.0 Mobile Safari/537.36"
    private const val TIMEOUT = 30_000
    private val OLD_MEDIAFIRE_URL = Regex(
        """(?i)^https?://(?:www\.)?mediafire\.com/download/([^/?#]+)/([^/?#]+)(?:[?#].*)?$"""
    )

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
        val convertedUrl = convertOldMediafireUrl(url)
        val urlsToTry = listOf(url, convertedUrl).distinct()
        
        // Try multiple strategies to extract download URL
        for (candidateUrl in urlsToTry) {
            for (userAgent in listOf(USER_AGENT, MOBILE_USER_AGENT)) {
                extractMediafireDownloadUrl(candidateUrl, userAgent)?.let { 
                    Log.d(TAG, "Mediafire resolved via HTML extraction: $it")
                    return it 
                }
            }
        }

        // Fallback: try redirect following
        for (candidateUrl in urlsToTry) {
            resolveRedirect(candidateUrl)?.let { redirectedUrl ->
                if (redirectedUrl != candidateUrl) {
                    Log.d(TAG, "Mediafire resolved via redirect: $redirectedUrl")
                    return redirectedUrl
                }
            }
        }

        // All strategies failed - throw specific exception
        Log.w(TAG, "Mediafire resolution failed for: $url")
        throw MediafireResolutionException(
            "Mediafire download links are unreliable on Android. " +
            "If a Pixeldrain mirror is available, please use that instead. " +
            "Original URL: $url"
        )
    }

    private fun extractMediafireDownloadUrl(url: String, userAgent: String): String? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            val html = conn.inputStream.bufferedReader().readText()

            // Pattern 1: mediafire.com/file/{id}?...&dkey=...
            val p1 = Pattern.compile("""["']((?:https?:)?//(?:www\.)?mediafire\.com/(?:file|view|download)/[^"'/?]+\?dkey=[^"']+)["']""")
            val m1 = p1.matcher(html)
            if (m1.find()) {
                return normalizeMediafireUrl(m1.group(1) ?: return null)
            }

            // Pattern 2: download{N}.mediafire.com/{path}
            val p2 = Pattern.compile("""["']((?:https?:)?//download\d+\.mediafire\.com/[^"']+)["']""")
            val m2 = p2.matcher(html)
            if (m2.find()) {
                return normalizeMediafireUrl(m2.group(1) ?: return null)
            }

            // Pattern 3: download button href on desktop and mobile pages.
            val p3 = Pattern.compile("""(?is)<[^>]*\bid=["']downloadButton["'][^>]*>""")
            val m3 = p3.matcher(html)
            if (m3.find()) {
                val href = Pattern.compile("""(?is)\bhref=["']([^"']+)["']""").matcher(m3.group())
                if (href.find()) return normalizeMediafireUrl(href.group(1) ?: return null)
            }

            // Pattern 4: JavaScript download URL variables used by some page variants.
            val p4 = Pattern.compile("""(?i)\b(?:var|let|const)?\s*(?:downloadUrl|download_url|downloadLink|download_link)\s*=\s*["']([^"']+)["']""")
            val m4 = p4.matcher(html)
            if (m4.find()) {
                return normalizeMediafireUrl(m4.group(1) ?: return null)
            }

            return null
        } catch (_: Exception) {
            return null
        } finally {
            conn?.disconnect()
        }
    }

    private fun convertOldMediafireUrl(url: String): String {
        val match = OLD_MEDIAFIRE_URL.matchEntire(url) ?: return url
        val (id, fileName) = match.destructured
        return "https://www.mediafire.com/file/$id/$fileName/file"
    }

    private fun normalizeMediafireUrl(url: String): String {
        val decodedUrl = url.replace("&amp;", "&")
        return if (decodedUrl.startsWith("//")) "https:$decodedUrl" else decodedUrl
    }

    private fun resolveRedirect(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.connect()
            conn.responseCode
            conn.url.toString()
        } catch (_: Exception) {
            null
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

/**
 * Specific exception for Mediafire resolution failures.
 * Allows DownloadManager to show user-friendly error messages.
 */
class MediafireResolutionException(message: String) : RuntimeException(message)
