/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Font management — scaling, fallback fonts, bold/italic toggles
 * for WebView games.
 */

package com.runestone.app.media

import android.webkit.WebView

object FontManager {

    /**
     * Scale all text in the game by a factor (0.5 - 2.0).
     * Injects CSS that scales font-size on the game canvas.
     */
    fun setFontScale(webView: WebView, scale: Float) {
        val clamped = scale.coerceIn(0.5f, 2.0f)
        val js = """
            (function(){
                var id = 'runestone-font-scale';
                var old = document.getElementById(id);
                if (old) old.remove();
                if (${clamped} === 1.0) return;
                var style = document.createElement('style');
                style.id = id;
                style.textContent = 'body, #gameCanvas, canvas { font-size: ${clamped * 100}% !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /** Force bold text rendering on the game canvas. */
    fun setBold(webView: WebView, enabled: Boolean) {
        val js = """
            (function(){
                var id = 'runestone-font-bold';
                var old = document.getElementById(id);
                if (old) old.remove();
                if (!$enabled) return;
                var style = document.createElement('style');
                style.id = id;
                style.textContent = 'body, #gameCanvas, canvas { font-weight: bold !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /** Inject a custom fallback font. */
    fun setFallbackFont(webView: WebView, fontPath: String) {
        val js = """
            (function(){
                var id = 'runestone-fallback-font';
                var old = document.getElementById(id);
                if (old) old.remove();
                var style = document.createElement('style');
                style.id = id;
                var fontFace = "@font-face { font-family: 'RunestoneFallback'; src: url('file://$fontPath'); }";
                style.textContent = fontFace + ' body, canvas { font-family: "RunestoneFallback", sans-serif !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /** Add text outline (stroke) effect. Size 0-4px. */
    fun setTextOutline(webView: WebView, outlinePx: Int) {
        if (outlinePx <= 0) {
            webView.evaluateJavascript("""
                (function(){ var el = document.getElementById('runestone-text-outline'); if(el) el.remove(); })();
            """.trimIndent(), null)
            return
        }
        val px = outlinePx.coerceIn(1, 4)
        val js = """
            (function(){
                var id = 'runestone-text-outline';
                var old = document.getElementById(id);
                if (old) old.remove();
                var style = document.createElement('style');
                style.id = id;
                style.textContent = 'canvas, body { -webkit-text-stroke: ${px}px black; text-shadow: 0 0 ${px}px rgba(0,0,0,0.8); }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /** Set line spacing multiplier for game dialogs. */
    fun setLineSpacing(webView: WebView, spacing: Float) {
        if (spacing <= 0f) return
        val js = """
            (function(){
                var id = 'runestone-line-spacing';
                var old = document.getElementById(id);
                if (old) old.remove();
                if (${spacing} === 1.0) return;
                var style = document.createElement('style');
                style.id = id;
                style.textContent = 'body, .window, .message_window, #gameCanvas, .dialog { line-height: ${spacing} !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
