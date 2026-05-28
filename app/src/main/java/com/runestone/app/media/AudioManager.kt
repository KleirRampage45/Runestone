/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Audio management — mute toggles, volume control for WebView games.
 */

package com.runestone.app.media

import android.webkit.WebView

/**
 * Controls audio in WebView games via JavaScript injection.
 * MV/MZ games use WebAudio API and HTML5 <audio> elements.
 */
object AudioManager {

    /**
     * Set master volume for the game (0.0 - 1.0).
     * Uses WebAudio API gain node injection.
     */
    fun setVolume(webView: WebView, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        val js = """
            (function(){
                // Hook AudioContext
                if (window.AudioContext || window.webkitAudioContext) {
                    var _origAC = window.AudioContext || window.webkitAudioContext;
                    window.__runestoneVolume = $clamped;
                    // Set gain on all existing audio contexts
                    if (window.__runestoneGainNodes) {
                        window.__runestoneGainNodes.forEach(function(g) {
                            g.gain.value = $clamped;
                        });
                    }
                }
                // Set volume on all <audio> and <video> elements
                document.querySelectorAll('audio, video').forEach(function(el) {
                    el.volume = $clamped;
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /** Mute or unmute BGM (background music). MV/MZ games use $gameSystem._bgmVolume. */
    fun muteMusic(webView: WebView, muted: Boolean) {
        val js = """
            (function(){
                if (window.__runestoneOrigBgmVol === undefined) {
                    window.__runestoneOrigBgmVol = window.AudioManager ? AudioManager._bgmVolume : 1.0;
                }
                var vol = $muted ? 0 : window.__runestoneOrigBgmVol;
                setVolume(webView, vol); // uses existing volume system
            })();
        """.trimIndent()
        // For MV/MZ games, use their native audio manager:
        val mvJs = """
            (function(){
                try {
                    if (window.AudioManager) {
                        AudioManager._bgmVolume = $muted ? 0 : (window.__runestoneOrigBgmVol || 90);
                    }
                    if (window.WebAudio) {
                        WebAudio._bgmVolume = $muted ? 0 : (window.__runestoneOrigBgmVol || 90);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
        webView.evaluateJavascript(mvJs, null)
    }

    /** Mute or unmute sound effects. */
    fun muteSfx(webView: WebView, muted: Boolean) {
        val mvJs = """
            (function(){
                try {
                    if (window.AudioManager) {
                        AudioManager._seVolume = $muted ? 0 : (window.__runestoneOrigSeVol || 90);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(mvJs, null)
    }

    /** Force audio files to a specific extension. */
    fun forceAudioExtension(webView: WebView, ext: String) {
        if (ext.isBlank()) return
        val js = """
            (function(){
                // Override AudioManager.load to rewrite file extensions
                var _origLoad = window.AudioManager && AudioManager.loadAudio;
                if (_origLoad && !window.__runestoneAudioHooked) {
                    window.__runestoneAudioHooked = true;
                    AudioManager.loadAudio = function(folder, name) {
                        var fixed = name.replace(/\.(m4a|ogg|mp3|wav)$$/i, '.$ext');
                        return _origLoad.call(this, folder, fixed);
                    };
                }
                // Also hook WebAudio
                var _origCreate = window.WebAudio && WebAudio._createBuffer;
                if (_origCreate && !window.__runestoneWebAudioHooked) {
                    window.__runestoneWebAudioHooked = true;
                    WebAudio._createBuffer = function(url) {
                        var fixed = url.replace(/\.(m4a|ogg|mp3|wav)$$/i, '.$ext');
                        return _origCreate.call(this, fixed);
                    };
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
