# Known WebView Compatibility Issues

This document records games and code paths that are known to misbehave on the Android WebView runtime used by `WebViewEngine`. Future debugging should start here before re-discovering the same root causes.

All issues were observed on a real Android 14 device with system WebView version reported by `adb shell dumpsys package com.google.android.webview` (capture in each new investigation). The same issues may or may not reproduce on other devices.

---

## 1. MV games on WebGL — Fear & Hunger works, others may not

**Symptom:** Game renders correctly under WebGL on most MV exports. Some MZ exports black-screen even when `useWebgl2 = true` and `forceCanvas = false`.

**Root cause:** Unconfirmed. The PIXI deprecation warning "PIXI.WebGLRenderer class has moved to PIXI.Renderer" appears in PIXI v5.0.0 but is non-fatal. The page stays alive (FPS overlay ticks) but the canvas stays black.

**Workaround:** Per-game `forceCanvas = true` override in `runestone.json`. See `docs/webgl-perf-testing.md` for how to write the override. Canvas renderer is slower but works for most games.

---

## 2. Effekseer-based MZ games — Look Outside, Haven

**Symptom:** MZ games that ship `js/libs/effekseer.min.js` and `js/libs/effekseer.wasm` (e.g. Look Outside, Haven) hang on first launch. The loading spinner shows but the title screen never appears. `RunestoneBridge.boot(...)` is never called from the game, indicating `Scene_Boot` never runs.

**Diagnostic signal:** `effekseer.initRuntime(...)` is called from the game's `main.js` but the `onLoad` callback never fires. No errors are logged. The WASM file is delivered successfully via the `shouldInterceptRequest` interceptor.

**Root cause (likely):** Effekseer's WASM runtime requires `WebAssembly.Memory` with shared array buffer support, which requires `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp` HTTP response headers. Android WebView does not expose these headers for `file://` requests and cannot be configured to set them on the main document without serving the page over `http://` (a separate feature, see `RunnerSettings.useHttpServer` — not yet implemented for MV/MZ).

**Workaround:**
- **Haven:** works in canvas mode. Set `forceCanvas = true` in the per-game `runestone.json` under `performance`. Slow but functional.
- **Look Outside:** does not work in canvas mode either. The canvas renderer initialises but no frames are drawn even after several minutes. No known workaround at this time. Skip on this WebView build.

**Future fix (not done):** Implement an `http://` server mode for MV/MZ games so the page can be served with the required COOP/COEP headers. This is a larger change and out of scope for the WebGL optimisation work.

---

## 3. WebView tile memory exhaustion on hi-DPI phones — FIXED

**Symptom:** `[ERROR:cc/tiles/tile_manager.cc:1012] WARNING: tile memory limits exceeded, some content may not draw` in logcat. Some pages render to a black canvas while the FPS overlay (rendered outside the page) keeps ticking.

**Root cause:** `WebSettings.setOffscreenPreRaster(true)` pre-rasterises the entire viewport at the device's native pixel density. On phones with DPR 2-3, this is enough to exhaust the WebView's GPU tile memory pool for any page that also allocates a WebGL canvas.

**Fix:** `WebViewEngine.configure()` now sets `setOffscreenPreRaster(false)`. Pages render lazily as they scroll, which uses far less memory. This change is safe for all WebView engines; the upstream Android team recommends `false` for non-trivial pages.

**Commit:** see `fix(webgl): disable setOffscreenPreRaster to stop WebView tile memory exhaustion` on the `perf/webgl-optimization` branch.

---

## 4. WebView fetch() against `file://` .wasm — FIXED

**Symptom:** Games that call `fetch("js/libs/something.wasm")` from their main.js hang silently. The page reaches `initRuntime(...)` and never returns. No console error is logged.

**Root cause:** Modern Chromium restricts `fetch()` against `file://` URLs. Older Android WebView versions also block `XMLHttpRequest` for `.wasm` files in some configurations.

**Fix:** `WebViewEngine.WebViewClient.shouldInterceptRequest` now also intercepts any URL ending in `.wasm` (with optional `?...` or `#...`) and serves the file from disk with `application/wasm` MIME. This works around the fetch policy because the WebView's own resource pipeline delivers the response.

**Commit:** see `fix(webgl): serve .wasm assets via shouldInterceptRequest; mirror page console` on the `perf/webgl-optimization` branch.

---

## 5. Page-side console not visible in logcat — FIXED

**Symptom:** When a game's page throws an unhandled error or logs via `console.log/error/warn`, the message is not visible in `adb logcat` even with `chromium:V` filter. Debugging requires chrome://inspect over USB, which is slow and not always possible.

**Root cause:** The pre-existing `WebChromeClient.onConsoleMessage` swallowed the "Scripts may close only the windows that were opened by it" warning and forwarded everything else to `super.onConsoleMessage(msg)`, which goes to the `chromium` tag at the default level (often suppressed on production WebView builds).

**Fix:** The handler now mirrors every page-side console message to the `Runestone` logcat tag with format `page-console[LEVEL] LINE: MESSAGE`. The pre-existing filter for the close-window warning is preserved.

**Commit:** see `fix(webgl): serve .wasm assets via shouldInterceptRequest; mirror page console` on the `perf/webgl-optimization` branch.

---

## 6. WebViewEngine.classify `?webgl2=1` hint to pre-5.2 PIXI — FIXED

**Symptom:** Some MZ games built against PIXI v5.0/5.1 (which do not ship `PIXI.WebGL2Renderer`) read the `webgl2=1` query param via `Utils.isOptionValid('webgl2')` and try a WebGL2 path the bundled PIXI does not support, producing a black screen.

**Root cause:** `WebglConfigBuilder.queryParams(WebglVersion.WEBGL2)` was emitting `&webgl2=1`. The Kotlin-side decision is what the JS bootstrap should honour; the URL hint was leaking the WebGL2 intent to games that can't act on it.

**Fix:** `?webgl=1&renderer=webgl2` (no `&webgl2=1`). The JS bootstrap still attempts the WebGL2 upgrade when it makes sense.

**Commit:** see `fix(webgl): stop emitting webgl2=1 in the game URL hint` on the `perf/webgl-optimization` branch.

---

## 7. WebViewMzEngine only matches `www/` layout — FIXED

**Symptom:** Some MZ games (e.g. Look Outside) ship `index.html` at the root with `js/rmmz_*.js` at the root, no `www/` directory. The detector returned false and the game fell through to the generic HTML engine, getting the wrong renderer hint and the wrong save path.

**Root cause:** `WebViewMzEngine.canRun` only checked the standard `www/` layout.

**Fix:** Detection now also matches `js/rmmz_*.js` at the root. `launch()` picks the right entry point (`www/index.html` or `index.html`). `getSaves()` checks both save/ directory layouts.

**Commit:** see `fix(detector): recognise MZ games with index.html at the root` on the `perf/webgl-optimization` branch.
