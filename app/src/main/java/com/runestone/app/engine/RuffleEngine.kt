/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Ruffle Flash player engine — MIT/Apache2 licensed.
 * Runs .swf files via embedded Ruffle WebView.
 */

package com.runestone.app.engine

import android.content.Context
import android.util.Log
import java.io.File

class RuffleEngine : GameEngine {

    override val id = "ruffle"
    override val name = "Flash (Ruffle)"
    override val version = "1.0.0"
    override val priority = 30

    companion object {
        private const val TAG = "RuffleEngine"
        private const val RUFFLE_CDN = "https://unpkg.com/@ruffle-rs/ruffle"
    }

    override fun canRun(gameFolder: File): Boolean {
        if (!gameFolder.isDirectory) return false
        return gameFolder.listFiles()?.any { it.name.endsWith(".swf") } ?: false
    }

    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        Log.i(TAG, "Launching Flash game: ${gameFolder.name}")
        // Find main .swf file
        val swfFile = gameFolder.listFiles()?.find { it.name.endsWith(".swf") }
            ?: gameFolder.listFiles()?.find { it.name.equals("game.swf", ignoreCase = true) }
            ?: gameFolder.listFiles()?.firstOrNull { it.name.endsWith(".swf") }

        if (swfFile == null) {
            Log.e(TAG, "No .swf file found in ${gameFolder.absolutePath}")
            return
        }

        // Ruffle runs in WebView with the ruffle.js loader
        // The WebView loads a small HTML page that embeds Ruffle
        val ruffleHtml = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <script src="$RUFFLE_CDN"></script>
            <style>body{margin:0;background:#000;display:flex;justify-content:center;align-items:center;height:100vh;overflow:hidden}</style>
            </head><body>
            <div id="ruffle-container"></div>
            <script>
                const ruffle = window.RufflePlayer.newest();
                const player = ruffle.createPlayer();
                player.id = "ruffle-player";
                document.getElementById("ruffle-container").appendChild(player);
                player.load({ url: "file://${swfFile.absolutePath}" });
                player.style.width = "100vw";
                player.style.height = "100vh";
            </script>
            </body></html>
        """.trimIndent()

        // Write HTML to cache and load via WebView
        val htmlFile = File(context.cacheDir, "ruffle_${gameFolder.name}.html")
        htmlFile.writeText(ruffleHtml)

        // Launch via existing WebView engine
        val intent = android.content.Intent(context, Class.forName("com.runestone.app.GameActivity")).apply {
            putExtra("game_path", htmlFile.parentFile?.absolutePath ?: gameFolder.absolutePath)
            putExtra("engine_type", "tyrano") // reuse WebView launch
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
