/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Screen filter system — applies visual effects to WebView games.
 * CRT, scanlines, GameBoy, sepia, night mode, sharpen.
 */

package com.runestone.app.media

import android.webkit.WebView

enum class ScreenFilter(val id: String, val label: String) {
    NONE("none", "No Filter"),
    CRT("crt", "CRT Monitor"),
    SCANLINES("scanlines", "Scanlines"),
    GAMEBOY("gameboy", "Game Boy"),
    SEPIA("sepia", "Sepia"),
    NIGHT("night", "Night Mode"),
    SHARPEN("sharpen", "Sharpen"),
    PIXELATED("pixelated", "Pixel Perfect");

    companion object {
        fun fromId(id: String): ScreenFilter = entries.find { it.id == id } ?: NONE
    }
}

object ScreenFilterEngine {

    private const val FILTER_DIV_ID = "runestone-screen-filter"

    /** Apply a screen filter to a running WebView game. */
    fun apply(webView: WebView, filter: ScreenFilter) {
        // Remove any existing filter first
        remove(webView)
        if (filter == ScreenFilter.NONE) return

        val css = when (filter) {
            ScreenFilter.CRT -> """
                #$FILTER_DIV_ID {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    background: repeating-linear-gradient(
                        0deg,
                        rgba(0,0,0,0.12) 0px,
                        rgba(0,0,0,0.12) 1px,
                        transparent 1px,
                        transparent 3px
                    );
                    z-index: 99998;
                }
            """.trimIndent()

            ScreenFilter.SCANLINES -> """
                #$FILTER_DIV_ID {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    background: repeating-linear-gradient(
                        0deg,
                        rgba(0,0,0,0.06) 0px,
                        rgba(0,0,0,0.06) 2px,
                        transparent 2px,
                        transparent 4px
                    );
                    z-index: 99998;
                }
            """.trimIndent()

            ScreenFilter.GAMEBOY -> """
                #$FILTER_DIV_ID {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    background-color: rgba(140, 180, 80, 0.30);
                    mix-blend-mode: multiply;
                    z-index: 99998;
                }
            """.trimIndent()

            ScreenFilter.SEPIA -> """
                #$FILTER_DIV_ID {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    background-color: rgba(180, 140, 80, 0.25);
                    mix-blend-mode: multiply;
                    z-index: 99998;
                }
            """.trimIndent()

            ScreenFilter.NIGHT -> """
                #$FILTER_DIV_ID {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    background-color: rgba(20, 10, 60, 0.30);
                    mix-blend-mode: multiply;
                    z-index: 99998;
                }
            """.trimIndent()

            ScreenFilter.SHARPEN -> """
                #$FILTER_DIV_ID {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    filter: contrast(1.15) brightness(1.05);
                    z-index: 99998;
                }
            """.trimIndent()

            ScreenFilter.PIXELATED -> """
                #$FILTER_DIV_ID {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    image-rendering: pixelated;
                    z-index: 99998;
                }
            """.trimIndent()

            ScreenFilter.NONE -> ""
        }

        if (css.isEmpty()) return

        val js = """
            (function(){
                var el = document.getElementById('$FILTER_DIV_ID');
                if (!el) {
                    el = document.createElement('div');
                    el.id = '$FILTER_DIV_ID';
                    document.body.appendChild(el);
                }
                var style = document.createElement('style');
                style.textContent = `$css`;
                document.head.appendChild(style);
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    /** Remove any active filter from the WebView. */
    fun remove(webView: WebView) {
        val js = """
            (function(){
                var el = document.getElementById('$FILTER_DIV_ID');
                if (el) el.remove();
                var styles = document.head.querySelectorAll('style');
                styles.forEach(function(s) {
                    if (s.textContent.indexOf('$FILTER_DIV_ID') >= 0) s.remove();
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
