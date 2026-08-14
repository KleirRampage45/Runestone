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

import com.runestone.app.engine.WebglConfigBuilder.EngineFamily
import com.runestone.app.engine.WebglConfigBuilder.WebglVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class WebglConfigBuilderTest {

    // ── pick() decision table ──────────────────────────────────────

    @Test
    fun `MV is always WEBGL1 regardless of useWebgl2`() {
        assertEquals(WebglVersion.WEBGL1, WebglConfigBuilder.pick(EngineFamily.MV, useWebgl2 = true, forceCanvas = false))
        assertEquals(WebglVersion.WEBGL1, WebglConfigBuilder.pick(EngineFamily.MV, useWebgl2 = false, forceCanvas = false))
    }

    @Test
    fun `MZ with useWebgl2 picks WEBGL2`() {
        assertEquals(WebglVersion.WEBGL2, WebglConfigBuilder.pick(EngineFamily.MZ, useWebgl2 = true, forceCanvas = false))
    }

    @Test
    fun `MZ with useWebgl2 false picks WEBGL1`() {
        assertEquals(WebglVersion.WEBGL1, WebglConfigBuilder.pick(EngineFamily.MZ, useWebgl2 = false, forceCanvas = false))
    }

    @Test
    fun `HTML defaults to WEBGL1 and respects useWebgl2`() {
        assertEquals(WebglVersion.WEBGL1, WebglConfigBuilder.pick(EngineFamily.HTML, useWebgl2 = false, forceCanvas = false))
        // HTML has no PIXI; the JS bootstrap probes the WebView directly. The
        // query string still advertises the intent when useWebgl2 is on, but
        // the pick() decision is conservative WEBGL1.
        assertEquals(WebglVersion.WEBGL1, WebglConfigBuilder.pick(EngineFamily.HTML, useWebgl2 = true, forceCanvas = false))
    }

    @Test
    fun `forceCanvas wins for every engine family`() {
        assertEquals(WebglVersion.CANVAS, WebglConfigBuilder.pick(EngineFamily.MV, useWebgl2 = true, forceCanvas = true))
        assertEquals(WebglVersion.CANVAS, WebglConfigBuilder.pick(EngineFamily.MZ, useWebgl2 = true, forceCanvas = true))
        assertEquals(WebglVersion.CANVAS, WebglConfigBuilder.pick(EngineFamily.HTML, useWebgl2 = true, forceCanvas = true))
        assertEquals(WebglVersion.CANVAS, WebglConfigBuilder.pick(EngineFamily.MV, useWebgl2 = false, forceCanvas = true))
    }

    // ── queryParams() string format ────────────────────────────────

    @Test
    fun `webglEnabled false returns empty query string`() {
        assertEquals("", WebglConfigBuilder.queryParams(WebglVersion.WEBGL1, webglEnabled = false))
        assertEquals("", WebglConfigBuilder.queryParams(WebglVersion.WEBGL2, webglEnabled = false))
        assertEquals("", WebglConfigBuilder.queryParams(WebglVersion.CANVAS, webglEnabled = false))
    }

    @Test
    fun `WEBGL1 query string`() {
        assertEquals("?webgl=1&renderer=webgl", WebglConfigBuilder.queryParams(WebglVersion.WEBGL1, webglEnabled = true))
    }

    @Test
    fun `WEBGL2 query string has the discriminator but does NOT advertise webgl2=1 to the game`() {
        // We intentionally do not emit &webgl2=1. Some MZ games built
        // against pre-5.2 PIXI honour that flag and try a WebGL2 path
        // their bundled PIXI does not support, producing a black screen.
        // The Kotlin-side decision tells the JS bootstrap what we want;
        // the URL hint stays minimal.
        assertEquals("?webgl=1&renderer=webgl2", WebglConfigBuilder.queryParams(WebglVersion.WEBGL2, webglEnabled = true))
    }

    @Test
    fun `CANVAS query string explicitly disables webgl`() {
        assertEquals("?webgl=0&renderer=canvas", WebglConfigBuilder.queryParams(WebglVersion.CANVAS, webglEnabled = true))
    }

    // ── buildQuery() combined entry point ─────────────────────────

    @Test
    fun `buildQuery MV default keeps legacy behavior`() {
        // MV + useWebgl2(true or false) + forceCanvas(false) → ?webgl=1&renderer=webgl
        // This matches the prior shipped behavior on the restore/store-work branch.
        assertEquals(
            "?webgl=1&renderer=webgl",
            WebglConfigBuilder.buildQuery(EngineFamily.MV, useWebgl2 = true, forceCanvas = false, webglEnabled = true),
        )
        assertEquals(
            "?webgl=1&renderer=webgl",
            WebglConfigBuilder.buildQuery(EngineFamily.MV, useWebgl2 = false, forceCanvas = false, webglEnabled = true),
        )
    }

    @Test
    fun `buildQuery MZ useWebgl2 on produces webgl2 hint without leaking webgl2=1 to the URL`() {
        assertEquals(
            "?webgl=1&renderer=webgl2",
            WebglConfigBuilder.buildQuery(EngineFamily.MZ, useWebgl2 = true, forceCanvas = false, webglEnabled = true),
        )
    }

    @Test
    fun `buildQuery forceCanvas produces canvas hint`() {
        assertEquals(
            "?webgl=0&renderer=canvas",
            WebglConfigBuilder.buildQuery(EngineFamily.MZ, useWebgl2 = true, forceCanvas = true, webglEnabled = true),
        )
    }

    @Test
    fun `buildQuery webglEnabled false produces empty string for any engine`() {
        assertEquals(
            "",
            WebglConfigBuilder.buildQuery(EngineFamily.MV, useWebgl2 = true, forceCanvas = false, webglEnabled = false),
        )
        assertEquals(
            "",
            WebglConfigBuilder.buildQuery(EngineFamily.MZ, useWebgl2 = true, forceCanvas = false, webglEnabled = false),
        )
    }
}
