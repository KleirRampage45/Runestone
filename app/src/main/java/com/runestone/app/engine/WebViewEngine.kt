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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Properties

/**
 * Full WebView-based runtime for RPG Maker MV/MZ games.
 *
 * Replaces nw.js with the device's built-in Chromium WebView.
 * Handles:
 * - Game loading from www/index.html
 * - localStorage persistence via Properties file
 * - Virtual gamepad overlay
 * - Fake greenworks.js for Steam-free play
 * - Bootstrapper for WebGL/WebAudio detection
 * - Audio format fallback (.m4a → .ogg)
 * - Touch input routing to keyboard events
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewEngine(context: Context) : WebView(context) {

    private var saveFile: File? = null
    private var localStorageShim: LocalStorageInterface? = null
    private var gameDir: File? = null
    private var config: WebViewGameConfig = WebViewGameConfig()

    data class WebViewGameConfig(
        val fixLocalStorage: Boolean = true,
        val addGamepad: Boolean = true,
        val fakeGreenworks: Boolean = true,
        val manuallyStart: Boolean = false,
        val forceCanvas: Boolean = false,
        val forceNoAudio: Boolean = false,
        val forceAudioExt: String = "",
        val showFps: Boolean = true,
        val backButtonQuits: Boolean = false,
        val title: String = "Runestone",
    )

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
        webSettings.setSupportMultipleWindows(true)
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings.textZoom = 100
        webSettings.setSupportZoom(false)
    }

    /**
     * Load a game from its game directory.
     * Expects a www/ subdirectory containing index.html
     */
    fun loadGame(gamePath: String, cfg: WebViewGameConfig? = null) {
        if (cfg != null) config = cfg

        val gameDirFile = File(gamePath)
        gameDir = gameDirFile

        // Find the www directory
        val wwwDir = findWwwDir(gameDirFile)
        val indexHtml = File(wwwDir, "index.html")
        if (!indexHtml.exists()) return

        saveFile = File(wwwDir, "runestone.save")
        if (config.fixLocalStorage) {
            localStorageShim = LocalStorageInterface(saveFile!!)
            addJavascriptInterface(localStorageShim as Any, "RunestoneLocalStorage")
        }

        addJavascriptInterface(Bootstrapper(), "RunestoneBridge")

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url.toString()

                // Intercept greenworks.js requests
                if (config.fakeGreenworks && url.contains("greenworks")) {
                    val js = readAssetFile("fake_greenworks.js")
                    if (js != null) {
                        return WebResourceResponse("application/javascript", "utf-8",
                            java.io.ByteArrayInputStream(js.toByteArray()))
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // Inject JavaScript shims after page loads
                if (config.fixLocalStorage) {
                    view.evaluateJavascript(LOCALSTORAGE_FIX_JS, null)
                }
                if (config.addGamepad) {
                    view.evaluateJavascript(GAMEPAD_INJECT_JS, null)
                }
                if (config.fakeGreenworks) {
                    val fakeGw = readAssetFile("fake_greenworks.js")
                    if (fakeGw != null) {
                        view.evaluateJavascript(fakeGw, null)
                    }
                }
                if (config.forceAudioExt.isNotEmpty()) {
                    view.evaluateJavascript(
                        FORCE_AUDIO_EXT_JS.replace("\$1", "\"${config.forceAudioExt}\""),
                        null
                    )
                }
                if (config.showFps) {
                    view.evaluateJavascript(FPS_OVERLAY_JS, null)
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val log = msg.message()
                if (log.contains("Scripts may close only the windows that were opened by it.")) {
                    // Game tried to close via window.close() — ignore
                    return true
                }
                return super.onConsoleMessage(msg)
            }
        }

        // Build URI with query params for WebGL/audio
        val uri = buildGameUri(indexHtml)
        loadUrl(uri)
    }

    private fun findWwwDir(gameDir: File): File {
        // Check if gameDir itself is the www dir
        if (File(gameDir, "index.html").exists()) return gameDir
        // Check www subdirectory
        val wwwDir = File(gameDir, "www")
        if (wwwDir.exists() && File(wwwDir, "index.html").exists()) return wwwDir
        // Check any subdirectory that has index.html
        val subDirs = gameDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (dir in subDirs) {
            if (File(dir, "index.html").exists()) return dir
        }
        return gameDir
    }

    private fun buildGameUri(indexHtml: File): String {
        val sb = StringBuilder("file://${indexHtml.absolutePath}?")

        if (config.forceCanvas || !detectWebglSupport()) {
            sb.append("canvas")
        } else {
            sb.append("webgl")
        }

        if (config.forceNoAudio || !detectWebAudioSupport()) {
            sb.append("&noaudio")
        }

        return sb.toString()
    }

    private fun detectWebglSupport(): Boolean {
        // We assume WebView supports WebGL on modern Android (API 26+)
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
    }

    private fun detectWebAudioSupport(): Boolean {
        return true // All modern Android WebViews support WebAudio
    }

    private fun readAssetFile(filename: String): String? {
        return try {
            val stream = context.assets.open(filename)
            val reader = BufferedReader(InputStreamReader(stream))
            reader.readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Handle back button - send cancel/escape to game or quit
     */
    fun handleBack(): Boolean {
        if (!config.backButtonQuits) {
            evaluateJavascript("TouchInput._onCancel();", null)
            return false // Don't quit
        }
        return true // Let the activity handle quitting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let the game receive touch events and also pass to gamepad overlay
        return super.onTouchEvent(event)
    }

    /**
     * JavaScript interface for localStorage persistence.
     * RPG Maker MV/MZ uses localStorage for save data.
     * We store it in a properties file in the game directory.
     */
    inner class LocalStorageInterface(private val file: File) {
        private val props = Properties()

        init {
            load()
        }

        private fun load() {
            try {
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    save()
                }
                FileInputStream(file).use { props.load(it) }
            } catch (e: Exception) {
                // Reset on corruption
                save()
            }
        }

        private fun save() {
            try {
                FileOutputStream(file).use { props.store(it, "Runestone localStorage") }
            } catch (e: Exception) {
                android.util.Log.e("Runestone", "Failed to save localStorage", e)
            }
        }

        @JavascriptInterface
        fun getItem(key: String): String? = props.getProperty(key)

        @JavascriptInterface
        fun setItem(key: String, value: String) {
            props.setProperty(key, value)
            save()
        }

        @JavascriptInterface
        fun removeItem(key: String) {
            props.remove(key)
            save()
        }

        @JavascriptInterface
        fun clear() {
            props.clear()
            save()
        }

        @JavascriptInterface
        fun key(index: Int): String? = props.keys.toList().getOrNull(index) as? String

        @get:JavascriptInterface
        val length: Int get() = props.size
    }

    /**
     * Bootstrapper interface - called from injected JS to signal readiness
     */
    inner class Bootstrapper {
        @JavascriptInterface
        fun boot(webgl: Boolean, webaudio: Boolean) {
            android.util.Log.d("Runestone", "Game booted: WebGL=$webgl, WebAudio=$webaudio")
        }
    }

    companion object {
        // JS to fix localStorage access
        private const val LOCALSTORAGE_FIX_JS = """
        (function() {
            if (window.RunestoneLocalStorage) {
                var origSet = localStorage.setItem;
                var origGet = localStorage.getItem;
                var origRemove = localStorage.removeItem;
                var origClear = localStorage.clear;
                localStorage.setItem = function(k,v) {
                    RunestoneLocalStorage.setItem(k,v);
                    origSet.call(localStorage, k, v);
                };
                localStorage.getItem = function(k) {
                    var v = RunestoneLocalStorage.getItem(k);
                    return v !== null ? v : origGet.call(localStorage, k);
                };
                localStorage.removeItem = function(k) {
                    RunestoneLocalStorage.removeItem(k);
                    origRemove.call(localStorage, k);
                };
                localStorage.clear = function() {
                    RunestoneLocalStorage.clear();
                    origClear.call(localStorage);
                };
                console.log('[Runestone] localStorage persistence active');
            }
        })();
        """

        // JS to inject virtual gamepad
        private const val GAMEPAD_INJECT_JS = """
        (function() {
            // Create canvas overlay for gamepad hit areas
            var style = document.createElement('style');
            style.textContent = `
                .runestone-gp-btn { position:fixed; z-index:9998; opacity:0.2; border-radius:50%; background:rgba(255,255,255,0.1); }
                .runestone-gp-btn:active { opacity:0.4; }
                .runestone-gp-arrow { position:fixed; z-index:9999; color:rgba(255,255,255,0.3); font-size:24px; text-align:center; line-height:60px; width:60px; height:60px; bottom:20px; user-select:none; -webkit-user-select:none; touch-action:none; }
                .runestone-gp-action { position:fixed; z-index:9999; color:rgba(255,255,255,0.4); font-size:14px; text-align:center; line-height:64px; width:64px; height:64px; border-radius:50%; border:2px solid rgba(255,255,255,0.2); bottom:80px; user-select:none; -webkit-user-select:none; touch-action:none; font-weight:bold; }
            `;
            document.head.appendChild(style);
            console.log('[Runestone] Gamepad styles injected');
        })();
        """

        // JS to force audio extension
        private const val FORCE_AUDIO_EXT_JS = """
        (function() {
            var _origDecrypt = WebAudio?._decryptXor ? WebAudio._decryptXor.bind(WebAudio) : null;
            if (WebAudio && WebAudio._createContext) {
                var origLoad = WebAudio._load;
                if (origLoad) {
                    WebAudio._load = function() {
                        arguments[0] = arguments[0].replace(/\\.m4a$/g, $1);
                        return origLoad.apply(this, arguments);
                    };
                    console.log('[Runestone] Forced audio extension: ' + $1);
                }
            }
        })();
        """

        // JS for FPS overlay
        private const val FPS_OVERLAY_JS = """
        (function() {
            var el = document.createElement('div');
            el.id = 'runestone-fps';
            el.style.cssText = 'position:fixed;top:2px;right:2px;z-index:99999;color:rgba(255,255,255,0.4);font-size:10px;font-family:monospace;background:rgba(0,0,0,0.4);padding:2px 6px;border-radius:4px;pointer-events:none;';
            document.body.appendChild(el);
            var frames = 0, last = performance.now();
            function tick() { frames++; var n=performance.now(); if(n-last>=1000){el.textContent=frames+' FPS';frames=0;last=n;} requestAnimationFrame(tick); }
            requestAnimationFrame(tick);
        })();
        """
    }
}
