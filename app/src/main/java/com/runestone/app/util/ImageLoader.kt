/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object ImageLoader {

    private const val TAG = "ImageLoader"
    private const val MAX_CACHE_BYTES = 32L * 1024 * 1024

    private val executor: ThreadPoolExecutor = ThreadPoolExecutor(
        2, 2, 30L, TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        ThreadFactory { r ->
            Thread(r, "img-loader-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
        },
    ).also { it.allowCoreThreadTimeOut(true) }

    private val threadCounter = AtomicInteger(0)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES.toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    fun load(
        source: String,
        target: ImageView,
        maxWidthPx: Int = 720,
    ) {
        val key = "${maxWidthPx}|$source"
        val tag = System.identityHashCode(target)
        target.setTag(tag)

        cache.get(key)?.let { cached ->
            target.setImageBitmap(cached)
            return
        }

        if (!inFlight.add(key)) {
            return
        }
        executor.execute {
            try {
                val bitmap = decodeSampled(source, maxWidthPx)
                if (bitmap != null) {
                    cache.put(key, bitmap)
                }
                mainHandler.post {
                    if (target.getTag() == tag && bitmap != null) {
                        target.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode $source: ${e.message}")
            } finally {
                inFlight.remove(key)
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    fun decodeSampled(source: String, maxWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(source).use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidthPx) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return openStream(source).use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }

    private fun openStream(source: String) = when {
        source.startsWith("local:") -> File(source.removePrefix("local:")).inputStream()
        else -> URL(source).openStream()
    }
}
