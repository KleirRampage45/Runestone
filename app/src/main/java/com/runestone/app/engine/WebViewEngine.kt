/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 */

package com.runestone.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

/**
 * Runs RPG Maker MV/MZ games in a WebView using the device's system
 * Chromium WebView. No additional runtime needed — the game is pure
 * HTML5/JavaScript/Pixi.js.
 *
 * Architecture:
 * - Loads www/index.html from the game directory
 * - Injects virtual gamepad / touch input shims
 * - Handles localStorage persistence via JavaScriptInterface
 * - Fakes greenworks.js to bypass Steam checks
 * - Provides bootstrapper for WebGL/WebAudio detection
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewEngine(context: Context) : WebView(context) {

    init {
        configure()
    }

    private fun configure() {
        setBackgroundColor(android.graphics.Color.BLACK)

        val webSettings = settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowContentAccess = true
        webSettings.allowFileAccess = true
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadsImagesAutomatically = true
        webSettings.supportMultipleWindows = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
    }

    /**
     * Load a game from its www directory.
     */
    fun loadGame(wwwDir: File) {
        val indexHtml = File(wwwDir, "index.html")
        if (!indexHtml.exists()) return

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                // TODO: Intercept greenworks.js requests to return fake implementation
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                return super.onConsoleMessage(message)
            }
        }

        // TODO: Add JavaScriptInterface for:
        // - localStorage persistence
        // - Virtual gamepad
        // - Fake greenworks.js
        // - Bootstrapper (WebGL/WebAudio detection)

        loadUrl("file://${indexHtml.absolutePath}")
    }

    /**
     * Handle touch events for the virtual gamepad overlay.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO: Route to virtual gamepad overlay
        return super.onTouchEvent(event)
    }
}
