/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * Thin Activity wrapper for ONScripter (NScripter) visual novels.
 * Uses the JNI bridge from matthewn4444/onscripter-engine-android.
 * Bundled native: libonscripter.so (2.3MB) + libsdl.so (592KB)
 */

package com.runestone.app.engine.onscripter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.onscripter.ONScripterView
import com.onscripter.exception.NativeONSException
import java.io.File

class OnscripterActivity : Activity() {

    private var onscripterView: ONScripterView? = null
    private var gamePath: String? = null

    companion object {
        private const val TAG = "OnscripterActivity"
        private const val EXTRA_GAME_PATH = "game_path"
        private const val EXTRA_SAVE_PATH = "save_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gamePath = intent.getStringExtra(EXTRA_GAME_PATH)
        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)

        if (gamePath == null) {
            Toast.makeText(this, "No game path provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val gameDir = File(gamePath!!)
        if (!gameDir.exists() || !gameDir.isDirectory) {
            Toast.makeText(this, "Game directory not found: $gamePath", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Create save directory if needed
        val saveDir = if (savePath != null) File(savePath) else File(gameDir, "saves")
        saveDir.mkdirs()

        Log.i(TAG, "Launching ONScripter game: ${gameDir.name}")

        try {
            val builder = ONScripterView.Builder(this, Uri.fromFile(gameDir))

            // Set custom save path if provided
            builder.setScreenshotPath(saveDir.absolutePath)

            // Try to find a font in the game directory
            val fontFile = findFontFile(gameDir)
            if (fontFile != null) {
                builder.setFontPath(fontFile.absolutePath)
            }

            builder.useHQAudio()
            builder.useRenderOutline()
            builder.readParentAssets()

            val view = builder.create()
            onscripterView = view

            view.setONScripterEventListener(object : ONScripterView.ONScripterEventListener {
                override fun autoStateChanged(selected: Boolean) {
                    Log.d(TAG, "Auto mode: $selected")
                }
                override fun skipStateChanged(selected: Boolean) {
                    Log.d(TAG, "Skip mode: $selected")
                }
                override fun singlePageStateChanged(selected: Boolean) {
                    Log.d(TAG, "Single page: $selected")
                }
                override fun videoRequested(videoUri: Uri, clickToSkip: Boolean, shouldLoop: Boolean) {
                    Log.d(TAG, "Video requested: $videoUri")
                }
                override fun onNativeError(e: NativeONSException, line: String, backtrace: String) {
                    Log.e(TAG, "Native error: ${e.message} at line $line\n$backtrace")
                }
                override fun onReady() {
                    Log.i(TAG, "ONScripter game ready")
                }
                override fun onUserMessage(messageId: ONScripterView.UserMessage) {
                    Log.w(TAG, "User message: $messageId")
                }
                override fun onGameFinished() {
                    Log.i(TAG, "Game finished")
                    finish()
                }
            })

            setContentView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ONScripterView", e)
            Toast.makeText(this, "Failed to start ONScripter: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        onscripterView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        onscripterView?.onResume()
    }

    override fun onDestroy() {
        onscripterView?.exitApp()
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Let ONScripter handle back button
        val view = onscripterView
        if (view != null) {
            // ONScripter games typically use back for menu/queries
            // If the game doesn't intercept, we'll finish
            view.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Find a font file in the game directory to pass to ONScripter.
     * Common font names in NScripter games.
     */
    private fun findFontFile(gameDir: File): File? {
        val fontNames = listOf(
            "default.ttf", "Default.ttf",
            "font.ttf", "Font.ttf",
            "msmincho.ttf",
            "msgothic.ttf",
        )
        for (name in fontNames) {
            val font = File(gameDir, name)
            if (font.exists()) return font
        }
        // Also check common subdirectories
        val subDirs = listOf("font", "Font", "fonts", "Fonts", "data", "Data")
        for (sub in subDirs) {
            val dir = File(gameDir, sub)
            if (dir.isDirectory) {
                val ttfFiles = dir.listFiles { f -> f.name.endsWith(".ttf") }
                if (ttfFiles != null && ttfFiles.isNotEmpty()) {
                    return ttfFiles.first()
                }
            }
        }
        return null
    }
}
