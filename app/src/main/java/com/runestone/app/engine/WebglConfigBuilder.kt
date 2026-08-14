/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app.engine

/**
 * Pure-Kotlin decision + query-string composer for the WebView-based
 * WebGL/WebGL2/canvas renderer selection.
 *
 * Kept free of Android types so it can be unit-tested on the JVM
 * (`./gradlew :app:testDebugUnitTest`).
 */
object WebglConfigBuilder {

    enum class WebglVersion {
        /** No WebGL at all. Game renders to a 2D canvas. */
        CANVAS,
        /** WebGL 1.0 context. Universal PIXI compatibility (v4 + v5). */
        WEBGL1,
        /** WebGL 2.0 context. PIXI v5.2+ only; PIXI v4 ignores this. */
        WEBGL2,
    }

    enum class EngineFamily {
        /** RPG Maker MV. Bundles PIXI v4. WebGL1 only. */
        MV,
        /** RPG Maker MZ. Bundles PIXI v5. WebGL2 supported in 5.2+. */
        MZ,
        /** Other HTML5 engines (Tyrano, Construct, Twine, Ruffle, etc.). */
        HTML,
    }

    /**
     * Pick the target WebGL version for a given engine and settings.
     *
     * Rules:
     * - [forceCanvas] always wins → CANVAS.
     * - MV is always WEBGL1 (PIXI v4 has no WebGL2 path; forcing it breaks MV games).
     * - MZ + [useWebgl2] → WEBGL2 (the JS-side probe may still downgrade to WEBGL1
     *   if the WebView lacks WebGL2 support — the query string advertises intent,
     *   not a guarantee).
     * - Otherwise → WEBGL1.
     */
    fun pick(
        engineFamily: EngineFamily,
        useWebgl2: Boolean,
        forceCanvas: Boolean,
    ): WebglVersion = when {
        forceCanvas -> WebglVersion.CANVAS
        engineFamily == EngineFamily.MV -> WebglVersion.WEBGL1
        engineFamily == EngineFamily.MZ && useWebgl2 -> WebglVersion.WEBGL2
        else -> WebglVersion.WEBGL1
    }

    /**
     * Compose the query string appended to the game's `index.html` URL.
     *
     * - Returns `""` when [webglEnabled] is false (the game will not see a hint
     *   and may fall back to canvas on its own).
     * - Otherwise emits `?webgl=1` plus, for WebGL2, a `&renderer=webgl2`
     *   discriminator. We deliberately do NOT emit `&webgl2=1` — some MZ
     *   games built against pre-5.2 PIXI read that flag via
     *   `Utils.isOptionValid('webgl2')` and try a WebGL2 path the bundled
     *   PIXI does not actually support, producing a black screen. The
     *   Kotlin-side decision is what we want the JS bootstrap to honour;
     *   the URL hint is intentionally minimal.
     */
    fun queryParams(
        version: WebglVersion,
        webglEnabled: Boolean,
    ): String = if (!webglEnabled) {
        ""
    } else when (version) {
        WebglVersion.CANVAS -> "?webgl=0&renderer=canvas"
        WebglVersion.WEBGL1 -> "?webgl=1&renderer=webgl"
        WebglVersion.WEBGL2 -> "?webgl=1&renderer=webgl2"
    }

    /**
     * Combine [pick] + [queryParams] in one call. The single entry point used
     * by `WebViewEngine` and exercised by the unit tests.
     */
    fun buildQuery(
        engineFamily: EngineFamily,
        useWebgl2: Boolean,
        forceCanvas: Boolean,
        webglEnabled: Boolean,
    ): String = queryParams(
        version = pick(engineFamily, useWebgl2, forceCanvas),
        webglEnabled = webglEnabled,
    )
}
