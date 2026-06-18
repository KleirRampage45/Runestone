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
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.BufferedReader
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
    private val externalHostCache = mutableMapOf<String, Boolean>()
    private var localServer: LocalServer? = null
    private var serverIp: String? = null

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLocalServer()
    }

    private fun stopLocalServer() {
        localServer?.stop()
        localServer = null
        serverIp = null
    }

    /**
     * Pick a non-loopback IPv4 address to use as the server's bind
     * address / page origin. The Android WebView treats 127.0.0.1 /
     * localhost as a null origin and refuses to enable cross-origin
     * isolation (COOP+COEP) on responses from there. A real LAN IP
     * gives the WebView a proper origin and crossOriginIsolated
     * becomes true.
     *
     * Returns null if no suitable IP is found; callers should fall
     * back to file:// loading in that case.
     */
    private fun pickServerIp(): String? {
        val interfaces = try {
            java.net.NetworkInterface.getNetworkInterfaces()
        } catch (_: Exception) {
            return null
        } ?: return null
        val candidates = mutableListOf<String>()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (!ni.isUp || ni.isLoopback || ni.isPointToPoint) continue
            val addrs = ni.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    candidates.add(addr.hostAddress ?: continue)
                }
            }
        }
        // Prefer Wi-Fi-ish names but accept any.
        return candidates.firstOrNull()
    }

    data class WebViewGameConfig(
        val fixLocalStorage: Boolean = true,
        val addGamepad: Boolean = true,
        val fakeGreenworks: Boolean = true,
        val manuallyStart: Boolean = false,
        val forceAudioExt: String = "",
        val showFps: Boolean = true,
        val backButtonQuits: Boolean = false,
        val title: String = "Runestone",
        val smoothScaling: Boolean = true,
        val integerScaling: Boolean = false,
        val textScale: Float = 1.0f,
        val useHttpServer: Boolean = false,
        val webgl: Boolean = true,
        val useWebgl2: Boolean = true,
        val forceCanvas: Boolean = false,
        val engineFamily: WebglConfigBuilder.EngineFamily = WebglConfigBuilder.EngineFamily.HTML,
        val desktopMode: Boolean = false,
        val allowExternalModules: Boolean = false,
        val allowedExternalHosts: List<String> = emptyList(),
        val dialogLogs: Boolean = false,
        // When true, the WebView's request for js/libs/effekseer.min.js
        // is intercepted and served from our bundled
        // effekseer_asmjs.min.js. Required for any MZ game whose
        // main.js calls effekseer.initRuntime() on Android WebView.
        val useAsmjsEffekseer: Boolean = true,
    )

    init {
        configure()
    }

    private fun configure() {
        setBackgroundColor(android.graphics.Color.BLACK)

        // Force hardware layer for GPU compositing
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val webSettings = settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowContentAccess = true
        webSettings.allowFileAccess = true
        webSettings.databaseEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadsImagesAutomatically = true
        webSettings.setSupportMultipleWindows(false)
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        webSettings.textZoom = (config.textScale * 100).toInt().coerceIn(50, 200)
        webSettings.setSupportZoom(false)
        // OffscreenPreRaster pre-rasterises the entire viewport at the
        // WebView's native resolution. On hi-DPI phones with games that
        // allocate a WebGL canvas at full viewport size (e.g. RPG Maker
        // MZ with Effekseer particles), this is what exhausts the
        // WebView's tile memory pool and produces a black canvas with
        // Chromium's "tile memory limits exceeded" warning. Disabling
        // it is the difference between a black screen and a working
        // game on devices we've tested.
        webSettings.setOffscreenPreRaster(false)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isNestedScrollingEnabled = false

        // API 31+: renderer priority
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, true)
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(0, 0)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(0, 0, oldl, oldt)
        if (l != 0 || t != 0) {
            super.scrollTo(0, 0)
        }
    }

    /**
     * Load a game from its game directory.
     * Expects a www/ subdirectory containing index.html
     */
    fun loadGame(gamePath: String, cfg: WebViewGameConfig? = null) {
        if (cfg != null) config = cfg

        val gameDirFile = File(gamePath)
        gameDir = gameDirFile

        // Apply WebGL setting
        val hasWebGL = config.webgl

        // Apply desktop mode user-agent
        if (config.desktopMode) {
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }

        // Re-apply text zoom (config may have changed)
        settings.textZoom = (config.textScale * 100).toInt().coerceIn(50, 200)

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

        // Local HTTP server. When enabled, the game is served from
        // http://<device-ip>:PORT/ with COOP/COEP headers, which unlocks
        // SharedArrayBuffer / shared-memory WebAssembly on the system
        // WebView. This is required for Effekseer-based MZ games
        // (look-outside, haven) to boot, and is harmless for games
        // that don't need it.
        //
        // We bind to 0.0.0.0 and load via the device's Wi-Fi IP, not
        // 127.0.0.1, because the Android WebView treats 127.0.0.1 as
        // a null origin and refuses to enable cross-origin isolation
        // (crossOriginIsolated stays false). Using the device's LAN
        // IP gives the WebView a real origin.
        if (config.useHttpServer) {
            stopLocalServer()
            val server = LocalServer(wwwDir).also { it.start() }
            localServer = server
            serverIp = pickServerIp()
        } else {
            stopLocalServer()
            serverIp = null
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url.toString()

                // Block external modules if not allowed (cached per host)
                if (!config.allowExternalModules) {
                    val host = request.url.host ?: ""
                    if (host.isNotEmpty()) {
                        val isExternal = externalHostCache.getOrPut(host) {
                            host != "localhost" && host != "127.0.0.1" && !isPrivateIp(host) && host !in config.allowedExternalHosts
                        }
                        if (isExternal) {
                            return WebResourceResponse("text/plain", "utf-8",
                                ByteArrayInputStream("".toByteArray()))
                        }
                    }
                }

                // Intercept greenworks.js requests
                if (config.fakeGreenworks && url.contains("greenworks")) {
                    val js = readAssetFile("fake_greenworks.js")
                    if (js != null) {
                        return WebResourceResponse("application/javascript", "utf-8",
                            java.io.ByteArrayInputStream(js.toByteArray()))
                    }
                }

                // Intercept effekseer.min.js requests — swap in the
                // asm.js runtime so initRuntime() takes the immediate
                // fallback path (no WASM, no SharedArrayBuffer).
                //
                // Background: Android system WebView does not enable
                // cross-origin isolation, so SharedArrayBuffer is
                // permanently unavailable. The WASM Effekseer runtime
                // needs shared-memory WebAssembly and silently hangs
                // forever in initRuntime() on Android WebView. The
                // asm.js runtime is a 2.5 MB plain-JS port of the same
                // API; its initRuntime() short-circuits to onload()
                // when effekseer_native is undefined, which it always
                // is in the asm.js build.
                //
                // Trade-off: particle effects don't render. The MZ
                // runtime's Graphics.effekseer calls into the loaded
                // module but gets a no-op. Scenes, maps, battles,
                // menus, saves — everything else works.
                if (config.useAsmjsEffekseer && url.endsWith("/effekseer.min.js", ignoreCase = true)) {
                    val asmjs = readAssetFile("effekseer_asmjs.min.js")
                    if (asmjs != null) {
                        android.util.Log.d(
                            "Runestone",
                            "effekseer intercept: url=$url -> serving asm.js runtime",
                        )
                        return WebResourceResponse(
                            "application/javascript",
                            "utf-8",
                            200,
                            "OK",
                            mapOf("Content-Type" to "application/javascript"),
                            java.io.ByteArrayInputStream(asmjs.toByteArray()),
                        )
                    }
                }

                // Intercept .m4a audio requests — serve .ogg instead if available
                if (config.forceAudioExt.isNotEmpty() && url.contains(".m4a")) {
                    val oggUrl = url.replace(Regex("\\.m4a(\\?.*)?$"), config.forceAudioExt)
                    val oggFile = resolveGameFile(oggUrl)
                    if (oggFile != null && oggFile.exists()) {
                        return createAudioResponse(oggFile, "audio/ogg")
                    }
                }

                // Intercept .wasm asset requests — serve from the game
                // directory with the correct MIME type, an explicit 200
                // status, and the headers required for the page to be
                // cross-origin-isolated (Effekseer's WASM runtime needs
                // SharedArrayBuffer, which requires both COOP/COEP on
                // the main document and CORP on every subresource).
                //
                // The 3-arg WebResourceResponse constructor is unreliable
                // for .wasm on some Android WebView versions: the response
                // is returned to the XHR but the WASM fails to instantiate
                // because the response is missing CORS / CORP headers.
                // The 6-arg constructor with explicit status + headers
                // is the supported path.
                if (url.endsWith(".wasm", ignoreCase = true) ||
                    url.contains(".wasm?", ignoreCase = true) ||
                    url.contains(".wasm#", ignoreCase = true)
                ) {
                    // When the local HTTP server is serving the game,
                    // its response already includes COOP/COEP/CORP and
                    // is what enables cross-origin-isolation. Don't
                    // override it with our simpler response or the
                    // page loses its isolation.
                    val fromLocalServer = url.startsWith("http://127.0.0.1:") ||
                        url.startsWith("http://localhost:") ||
                        (serverIp != null && url.startsWith("http://$serverIp:"))
                    if (!fromLocalServer) {
                        val wasmFile = resolveGameFile(url)
                        if (wasmFile != null && wasmFile.exists()) {
                            val headers = mapOf(
                                "Content-Type" to "application/wasm",
                                "Content-Length" to wasmFile.length().toString(),
                                "Cross-Origin-Resource-Policy" to "cross-origin",
                                "Access-Control-Allow-Origin" to "*",
                                "Cache-Control" to "no-store",
                            )
                            android.util.Log.d(
                                "Runestone",
                                "wasm intercept: url=$url size=${wasmFile.length()} " +
                                    "headers=$headers",
                            )
                            return WebResourceResponse(
                                "application/wasm",
                                "utf-8",
                                200,
                                "OK",
                                headers,
                                FileInputStream(wasmFile),
                            )
                        }
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // Inject dialog logging if enabled
                if (config.dialogLogs) {
                    view.evaluateJavascript(DIALOG_LOG_JS, null)
                }

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
                        FORCE_AUDIO_EXT_JS.replace("$1", "\"${config.forceAudioExt}\""),
                        null
                    )
                }
                if (config.showFps) {
                    view.evaluateJavascript(FPS_OVERLAY_JS, null)
                }
                // Apply scaling mode
                val scalingJs = if (config.integerScaling) SCALING_INTEGER_JS else if (config.smoothScaling) "" else SCALING_NEAREST_JS
                if (scalingJs.isNotEmpty()) {
                    view.evaluateJavascript(scalingJs, null)
                }
                // Renderer-pick + PIXI options: this is the one injection that
                // runs only when webgl is enabled. It probes the actual context,
                // forces WebGL2 on MZ when available, tunes mobile-friendly PIXI
                // options, and reports back via RunestoneBridge.bootDetailed(...).
                if (config.webgl) {
                    val targetRenderer = WebglConfigBuilder
                        .pick(config.engineFamily, config.useWebgl2, config.forceCanvas)
                        .name.lowercase()
                    val bootstrapJs = readAssetFile("webgl-bootstrap.js")
                    if (bootstrapJs != null) {
                        val tpl = bootstrapJs.replace("__TARGET_RENDERER__", targetRenderer)
                        view.evaluateJavascript(tpl, null)
                    }
                }
                // Fix PIXI tile bleeding — force NEAREST scale mode.
                // Kept as the only post-load PIXI patch; the previous
                // PIXI_RENDER_OPTS_JS plus devicePixelRatio override have
                // been removed because they were observed to black-screen
                // some MZ games (look-outside, haven) on hi-DPI phones.
                view.evaluateJavascript(PIXI_TILE_FIX_JS, null)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val log = msg.message()
                if (log.contains("Scripts may close only the windows that were opened by it.")) {
                    // Game tried to close via window.close() — ignore
                    return true
                }
                // Mirror all page-side console output to Runestone-tagged
                // logcat so we can debug game issues without attaching
                // chrome://inspect. Format: "page-console(level): <message>"
                // plus the source URL and line number, when available.
                val level = when (msg.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> "E"
                    ConsoleMessage.MessageLevel.WARNING -> "W"
                    else -> "I"
                }
                android.util.Log.println(
                    android.util.Log.INFO,
                    "Runestone",
                    "page-console[$level] ${msg.lineNumber()}: $log",
                )
                return super.onConsoleMessage(msg)
            }
        }

        // Load the game — compose the renderer-hint query string via the
        // shared, unit-tested builder. The string may be empty (when webgl
        // is disabled) or carry `?webgl=1&renderer=...` discriminator flags.
        val query = WebglConfigBuilder.buildQuery(
            engineFamily = config.engineFamily,
            useWebgl2 = config.useWebgl2,
            forceCanvas = config.forceCanvas,
            webglEnabled = config.webgl,
        )
        val url = if (config.useHttpServer && localServer != null && serverIp != null) {
            "http://$serverIp:${localServer!!.port}/index.html$query"
        } else {
            "file://${indexHtml.absolutePath}$query"
        }
        loadUrl(url)
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
            evaluateJavascript("(function(){if(typeof TouchInput!=='undefined'&&TouchInput._onCancel)TouchInput._onCancel();})();", null)
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
        private var saveTimer: java.util.Timer? = null
        private var pendingSave = false

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

        private fun scheduleSave() {
            if (pendingSave) return
            pendingSave = true
            android.util.Log.d("Runestone", "LocalStorage: scheduling delayed save")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                pendingSave = false
                save()
            }, 1000) // Batch writes: save 1s after last change
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
            scheduleSave()
        }

        @JavascriptInterface
        fun removeItem(key: String) {
            props.remove(key)
            scheduleSave()
        }

        @JavascriptInterface
        fun clear() {
            props.clear()
            scheduleSave()
        }

        @JavascriptInterface
        fun key(index: Int): String? = props.keys.toList().getOrNull(index) as? String

        @get:JavascriptInterface
        val length: Int get() = props.size
    }

    /**
     * Bootstrapper interface - called from injected JS to signal readiness.
     *
     * Accepts both the legacy two-arg form (webgl, webaudio) and the richer
     * form used by `webgl-bootstrap.js` (webgl, webaudio, renderer, webglVersion).
     * Older games that only post the two-arg shape keep working without changes.
     */
    inner class Bootstrapper {
        @JavascriptInterface
        fun boot(webgl: Boolean, webaudio: Boolean) {
            android.util.Log.d("Runestone", "Game booted: WebGL=$webgl, WebAudio=$webaudio")
        }

        @JavascriptInterface
        fun bootDetailed(
            webgl: Boolean,
            webaudio: Boolean,
            renderer: String?,
            webglVersion: Int,
        ) {
            android.util.Log.d(
                "Runestone",
                "Game booted: WebGL=$webgl WebAudio=$webaudio renderer=$renderer webglVersion=$webglVersion",
            )
        }
    }

    /**
     * Resolve a URL path to a file in the game directory.
     * Handles file:// URLs, our own http://127.0.0.1:PORT/ URLs (when
     * useHttpServer is on), and relative paths with query strings.
     */
    private fun resolveGameFile(url: String): File? {
        val gameDir = gameDir ?: return null
        // Strip file:// prefix
        var path = url
        if (path.startsWith("file://")) {
            path = path.removePrefix("file://")
        } else if (path.startsWith("http://127.0.0.1:") || path.startsWith("http://localhost:")
            || (serverIp != null && path.startsWith("http://$serverIp:"))
        ) {
            // The local server is up; strip the origin so we're left
            // with the same path we would have used under file://.
            val schemeEnd = path.indexOf("://") + 3
            val pathStart = path.indexOf('/', schemeEnd)
            path = if (pathStart >= 0) path.substring(pathStart) else "/"
        }
        // Strip query string
        val queryIdx = path.indexOf('?')
        if (queryIdx > 0) path = path.substring(0, queryIdx)
        // Strip fragment
        val fragIdx = path.indexOf('#')
        if (fragIdx > 0) path = path.substring(0, fragIdx)

        // If it's an absolute path, return as-is (shouldn't happen for game assets)
        if (path.startsWith("/")) return File(path)

        // Relative path — resolve from game dir
        val wwwDir = findWwwDir(gameDir)
        return File(wwwDir, path)
    }

    /**
     * Create a WebResourceResponse from an audio file with proper MIME type.
     * Throws if the file cannot be opened.
     */
    private fun createAudioResponse(file: File, mimeType: String): WebResourceResponse {
        return WebResourceResponse(
            mimeType,
            "utf-8",
            FileInputStream(file)
        )
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
            }
        })();
        """

        // JS to inject gamepad support
        private const val GAMEPAD_INJECT_JS = """
        (function() {
            // Hack: patch AudioContext to bypass nw.js audio restrictions
            var origAudioContext = window.AudioContext || window.webkitAudioContext;
            // Rest of gamepad setup...
        })();
        """

        // JS to inject the FPS overlay
        private const val FPS_OVERLAY_JS = """
        (function() {
            // Create FPS display overlay
            var fpsDiv = document.createElement('div');
            fpsDiv.id = 'runestone-fps';
            fpsDiv.style.cssText = 'position:fixed;bottom:4px;right:4px;color:#0f0;font-family:monospace;font-size:12px;z-index:99999;background:rgba(0,0,0,0.5);padding:2px 6px;border-radius:3px;pointer-events:none;';
            document.body.appendChild(fpsDiv);
            var frames = 0, lastTime = performance.now();
            function loop() {
                frames++;
                var now = performance.now();
                if (now - lastTime >= 1000) {
                    fpsDiv.textContent = frames + ' FPS';
                    frames = 0;
                    lastTime = now;
                }
                requestAnimationFrame(loop);
            }
            loop();
        })();
        """

        // JS to force audio extension
        private const val FORCE_AUDIO_EXT_JS = """
        (function() {
            // Intercept WebAudio requests to redirect .m4a to the forced extension
            if (typeof WebAudio !== 'undefined' && WebAudio._load) {
                var orig = WebAudio._load;
                WebAudio._load = function(url) {
                    url = url.replace(/\\.m4a(\\?.*)?$/i, $1);
                    return orig.call(this, url);
                };
            }
        })();
        """

        // JS to force dialogue logging
        private const val DIALOG_LOG_JS = """
        (function() {
            // Log all ${'$'}gameMessage messages to console
            var origAdd = ${'$'}gameMessage.add;
            ${'$'}gameMessage.add = function(text) {
                console.log('[Game Dialog]', text);
                return origAdd.call(this, text);
            };
        })();
        """

        // JS for integer scaling
        private const val SCALING_INTEGER_JS = """
        (function() {
            var canvas = document.querySelector('canvas');
            if (canvas) {
                canvas.style.imageRendering = 'pixelated';
            }
        })();
        """

        // JS for nearest-neighbor scaling
        private const val SCALING_NEAREST_JS = """
        (function() {
            var canvas = document.querySelector('canvas');
            if (canvas) {
                canvas.style.imageRendering = 'crisp-edges';
            }
        })();
        """

        // JS to fix PIXI tile bleeding
        private const val PIXI_TILE_FIX_JS = """
        (function() {
            // Inject CSS to force pixel-art rendering
            var s = document.createElement('style');
            s.textContent = 'canvas { image-rendering: pixelated; image-rendering: crisp-edges; }';
            document.head.appendChild(s);
            // Patch PIXI to use NEAREST scaling
            if (typeof PIXI !== 'undefined' && PIXI.settings) {
                PIXI.settings.SCALE_MODE = 0;
            }
            if (typeof PIXI !== 'undefined' && PIXI.BaseTexture && PIXI.BaseTexture.defaultOptions) {
                PIXI.BaseTexture.defaultOptions.scaleMode = 0;
            }
        })();
        """

        // JS to apply mobile-friendly PIXI renderer options. Runs BEFORE the
        // tile-bleeding fix so that __runestonePixiOpts is in place by the
        // time the webgl-bootstrap (if injected) reads it.
        //
        // Conservative defaults: only touch the things that are universal
        // wins on mobile. We do NOT force roundPixels, antialias, or
        // resolution globally — those interact with PIXI v5 shaders in ways
        // that have produced black screens on real games. The game is
        // allowed to set them itself; we just nudge the bits that are
        // never wrong.
        //
        // - PRECISION_FRAGMENT = 'mediump'   → cheaper fragment math on mobile GPUs
        // - scaleMode = 0 (NEAREST)           → duplicated in PIXI_TILE_FIX_JS;
        //                                       kept here in case that injection is skipped
        // - resolution cap via opts hint     → only consumed by the bootstrap
        private const val PIXI_RENDER_OPTS_JS = """
        (function() {
            try {
                if (typeof PIXI === 'undefined') return;
                if (PIXI.settings) {
                    if ('PRECISION_FRAGMENT' in PIXI.settings) {
                        PIXI.settings.PRECISION_FRAGMENT = 'mediump';
                    }
                }
                if (PIXI.BaseTexture && PIXI.BaseTexture.defaultOptions) {
                    if ('scaleMode' in PIXI.BaseTexture.defaultOptions) {
                        PIXI.BaseTexture.defaultOptions.scaleMode = 0;
                    }
                }
                // Stash a resolution hint for the bootstrap to read. The
                // bootstrap is the only place that actually forwards
                // resolution to the renderer constructor, and only when the
                // game has not already set one.
                var dpr = window.devicePixelRatio || 1;
                window.__runestonePixiOpts = {
                    resolution: Math.max(1, Math.min(2, dpr)),
                };
            } catch (e) {
                // Best-effort: never break the game over a tuning patch.
            }
        })();
        """

        /** Check if an IP is in a private/local network range */
        private fun isPrivateIp(host: String): Boolean {
            // Try IPv4 private ranges
            val parts = host.split('.')
            if (parts.size == 4) {
                val first = parts[0].toIntOrNull() ?: return false
                return when (first) {
                    10 -> true          // 10.x.x.x
                    127 -> true         // 127.x.x.x (loopback)
                    172 -> {            // 172.16-31.x.x
                        val second = parts[1].toIntOrNull() ?: return false
                        second in 16..31
                    }
                    192 -> parts[1] == "168"  // 192.168.x.x
                    169 -> parts[1] == "254"  // 169.254.x.x (link-local)
                    else -> false
                }
            }
            // Not an IPv4 string — probably a domain name, so it's external
            return false
        }
    }
}
