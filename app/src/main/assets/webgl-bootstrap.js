// Runestone: WebGL/WebGL2 renderer bootstrap for RPG Maker MV/MZ (and HTML).
//
// Injected by `WebViewEngine` in `onPageFinished` after the page has loaded.
// Runs *after* PIXI is on the page (since it loads via the game's own
// index.html → rpg_managers.js / rmmz_managers.js).
//
// Responsibilities:
//   1. Probe the WebView for WebGL2 / WebGL1 / no-WebGL support.
//   2. If the target is "webgl2" and WebGL2 is available, force
//      `PIXI.WebGLRenderer` to ask for a WebGL2 context (PIXI v5.2+).
//   3. Apply mobile-friendly PIXI renderer defaults (antialias off, round
//      pixels, high-performance, resolution clamp).
//   4. Post a single `RunestoneBridge.bootDetailed(...)` message so the
//      Kotlin side can log the actual renderer + version that won out.
//
// The Kotlin side injects this template with `__TARGET_RENDERER__` already
// substituted to one of "webgl2" | "webgl" | "canvas".
//
// All steps are wrapped in try/catch and degrade silently on failure.

(function() {
    'use strict';

    var target = '__TARGET_RENDERER__';

    function probe() {
        var c = document.createElement('canvas');
        var hasWebgl2 = false;
        var hasWebgl1 = false;
        var unmasked = null;
        try {
            var gl2 = c.getContext('webgl2');
            if (gl2) {
                hasWebgl2 = true;
                try {
                    var dbg = gl2.getExtension('WEBGL_debug_renderer_info');
                    if (dbg) unmasked = gl2.getParameter(dbg.UNMASKED_RENDERER_WEBGL);
                } catch (e) { /* ignore */ }
            }
            if (!hasWebgl2) {
                var gl1 = c.getContext('webgl') || c.getContext('experimental-webgl');
                if (gl1) {
                    hasWebgl1 = true;
                    if (!unmasked) {
                        try {
                            var dbg1 = gl1.getExtension('WEBGL_debug_renderer_info');
                            if (dbg1) unmasked = gl1.getParameter(dbg1.UNMASKED_RENDERER_WEBGL);
                        } catch (e) { /* ignore */ }
                    }
                }
            }
        } catch (e) {
            // No WebGL at all.
        }
        return { hasWebgl2: hasWebgl2, hasWebgl1: hasWebgl1, unmasked: unmasked };
    }

    function effectiveVersion(target, caps) {
        if (target === 'canvas') return 'canvas';
        if (target === 'webgl2') {
            return caps.hasWebgl2 ? 'webgl2' : (caps.hasWebgl1 ? 'webgl' : 'canvas');
        }
        // target === 'webgl'
        return caps.hasWebgl1 ? 'webgl' : (caps.hasWebgl2 ? 'webgl2' : 'canvas');
    }

    function pickPixiCtor(eff) {
        if (typeof PIXI === 'undefined') return null;
        // Order matters: PIXI v5 ships BOTH WebGLRenderer and WebGL2Renderer
        // on the same page. We must check the v5-only WebGL2Renderer FIRST
        // when the effective target is webgl2, otherwise we'd return the
        // generic WebGLRenderer and report "webgl" even though the runtime
        // would have created a WebGL2 context.
        if (eff === 'webgl2') {
            if (PIXI.WebGL2Renderer) return PIXI.WebGL2Renderer;
            if (PIXI.WebGLRenderer) return PIXI.WebGLRenderer;
        } else if (eff === 'webgl') {
            if (PIXI.WebGLRenderer) return PIXI.WebGLRenderer;
        } else {
            // canvas
            if (PIXI.CanvasRenderer) return PIXI.CanvasRenderer;
        }
        // Last-resort fallbacks.
        if (PIXI.WebGLRenderer) return PIXI.WebGLRenderer;
        if (PIXI.CanvasRenderer) return PIXI.CanvasRenderer;
        return null;
    }

    function effectiveRendererName(pixiCtor) {
        if (!pixiCtor) return 'none';
        try {
            if (pixiCtor === PIXI.WebGL2Renderer) return 'webgl2';
            if (pixiCtor === PIXI.WebGLRenderer) return 'webgl';
            if (pixiCtor === PIXI.CanvasRenderer) return 'canvas';
        } catch (e) { /* ignore */ }
        return 'unknown';
    }

    // Map the chosen constructor to the actual GL context version it
    // creates. A PIXI.WebGLRenderer always creates a WebGL1 context
    // (PIXI v5 will not auto-upgrade to WebGL2 even if the WebView
    // supports it, because autoDetectRenderer has to be called with
    // specific options to ask for v2). A PIXI.WebGL2Renderer creates a
    // WebGL2 context. Canvas is 0.
    function actualVersion(eff, ctor) {
        if (eff === 'canvas' || ctor === PIXI.CanvasRenderer) return 0;
        if (ctor === PIXI.WebGL2Renderer) return 2;
        if (ctor === PIXI.WebGLRenderer) return 1;
        return 0;
    }

    function patchPIXI(eff, opts) {
        // Force autoDetectRenderer to choose the constructor we want.
        if (typeof PIXI === 'undefined') return;
        try {
            if (PIXI.utils) {
                if (eff === 'webgl2' && PIXI.WebGL2Renderer) {
                    // v5.2+: redirect autoDetect to WebGL2Renderer.
                    PIXI.utils._canUseWebGL2 = function() { return true; };
                }
            }
            if (PIXI.settings) {
                if ('SCALE_MODE' in PIXI.settings) {
                    PIXI.settings.SCALE_MODE = 0; // NEAREST
                }
            }
            if (PIXI.BaseTexture && PIXI.BaseTexture.defaultOptions) {
                PIXI.BaseTexture.defaultOptions.scaleMode = 0;
                // Only override resolution if the game hasn't set one.
                // Forcing resolution=2 on a 3x-DPR phone has been observed
                // to black-screen MZ games that compute texture coordinates
                // in absolute pixel space.
                if (
                    opts && typeof opts.resolution === 'number' &&
                    (typeof PIXI.BaseTexture.defaultOptions.resolution !== 'number' ||
                        PIXI.BaseTexture.defaultOptions.resolution <= 0)
                ) {
                    PIXI.BaseTexture.defaultOptions.resolution = opts.resolution;
                }
            }
        } catch (e) { /* ignore */ }
    }

    function postBoot(eff, caps, pixiCtor) {
        try {
            if (typeof window.RunestoneBridge === 'undefined') return;
            // Legacy two-arg form so old logs stay readable.
            if (typeof window.RunestoneBridge.boot === 'function') {
                window.RunestoneBridge.boot(eff !== 'canvas', true);
            }
            // Richer form for the new path. webglVersion is the *actual*
            // context version (2 for WebGL2Renderer, 1 for WebGLRenderer
            // on a WebGL1-only WebView, 0 for canvas).
            if (typeof window.RunestoneBridge.bootDetailed === 'function') {
                window.RunestoneBridge.bootDetailed(
                    eff !== 'canvas',
                    true,
                    effectiveRendererName(pixiCtor),
                    actualVersion(eff, pixiCtor),
                );
            }
        } catch (e) { /* ignore */ }
    }

    try {
        var caps = probe();
        var eff = effectiveVersion(target, caps);
        var opts = (typeof window.__runestonePixiOpts === 'object' && window.__runestonePixiOpts) || null;
        patchPIXI(eff, opts);
        var ctor = pickPixiCtor(eff);
        // Defer the post a tick so the game's own manager script can finish
        // instantiating PIXI first (it overrides the prototype we just set).
        setTimeout(function() {
            postBoot(eff, caps, ctor);
        }, 0);
    } catch (e) {
        // Never break the page over a tuning bootstrap.
    }
})();
