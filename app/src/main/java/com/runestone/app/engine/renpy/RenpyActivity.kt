/*
 * Runestone - Multi-engine RPG Maker & visual novel launcher for Android
 * Copyright (C) 2026 Runestone Contributors
 *
 * RenpyActivity - Bootstrap activity for Ren'Py visual novels
 * Based on python-for-android's PythonActivity pattern
 */

package com.runestone.app.engine.renpy

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import android.graphics.Color
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Activity wrapper for Ren'Py games.
 * 
 * Bootstraps the Ren'Py runtime by:
 * 1. Extracting engine files (renpy/common/) from assets on first launch
 * 2. Setting environment variables for Ren'Py paths
 * 3. Loading librenpython.so and starting the Python interpreter
 * 
 * The librenpython.so (55MB) contains Python 3.11 + SDL2 + Pygame + Ren'Py core.
 * Engine script files (renpy/common/) must be bundled in assets/renpy-engine/.
 */
class RenpyActivity : Activity() {
    
    companion object {
        private const val TAG = "RenpyActivity"
        
        const val EXTRA_GAME_PATH = "game_path"
        const val EXTRA_SAVE_PATH = "save_path"
        const val EXTRA_ENGINE_VERSION = "engine_version"
        
        private const val RENPY_ENGINE_ASSETS = "renpy-engine"
        private const val RENPY_ENGINE_DIR = "renpy-engine"
        
        // Ren'Py 8.x uses Python 3.11
        private const val PYTHON_VERSION = "3.11"
        
        init {
            // Load SDL2 first (required by renpython)
            try {
                System.loadLibrary("SDL2")
                Log.i(TAG, "Loaded libSDL2.so")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "SDL2 not found as separate lib, may be bundled in renpython")
            }
            
            // Load the main Ren'Py library
            try {
                System.loadLibrary("renpython")
                Log.i(TAG, "Loaded librenpython.so")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load librenpython.so", e)
            }
        }
    }
    
    private var gamePath: String? = null
    private var savePath: String? = null
    private var engineVersion: String = "8.3.4"
    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on during game
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Parse intent extras
        gamePath = intent.getStringExtra(EXTRA_GAME_PATH)
        savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
        engineVersion = intent.getStringExtra(EXTRA_ENGINE_VERSION) ?: "8.3.4"
        
        if (gamePath == null) {
            Log.e(TAG, "No game path provided")
            showErrorAndFinish("No game path provided")
            return
        }
        
        val gameDir = File(gamePath!!)
        if (!gameDir.exists() || !gameDir.isDirectory) {
            Log.e(TAG, "Game directory not found: $gamePath")
            showErrorAndFinish("Game directory not found")
            return
        }
        
        // Show loading UI while extracting engine files
        showLoadingUI()
        
        // Extract engine files and start Ren'Py
        activityScope.launch {
            try {
                // Extract Ren'Py engine files from assets
                val engineDir = extractEngineFiles()
                
                // Set up save directory
                val saveDir = if (savePath != null) File(savePath!!) else File(gameDir, "saves")
                saveDir.mkdirs()
                
                // Set environment variables for Ren'Py
                setupEnvironment(gameDir, engineDir, saveDir)
                
                // Start Ren'Py
                Log.i(TAG, "Starting Ren'Py game: ${gameDir.name}")
                startRenpy(gameDir.absolutePath, engineDir.absolutePath)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Ren'Py", e)
                showErrorAndFinish("Failed to start Ren'Py: ${e.message}")
            }
        }
    }
    
    private fun showLoadingUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
        }
        
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        
        val textView = TextView(this).apply {
            text = "Loading Ren'Py engine..."
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        
        layout.addView(progressBar)
        layout.addView(textView)
        setContentView(layout)
    }
    
    private suspend fun extractEngineFiles(): File = withContext(Dispatchers.IO) {
        val engineDir = File(filesDir, RENPY_ENGINE_DIR)
        val versionFile = File(engineDir, "version.txt")
        
        // Check if already extracted (version match)
        if (engineDir.exists() && versionFile.exists() && versionFile.readText() == engineVersion) {
            Log.i(TAG, "Engine files already extracted")
            return@withContext engineDir
        }
        
        Log.i(TAG, "Extracting Ren'Py engine files...")
        engineDir.mkdirs()
        
        // Check if engine files exist in assets
        val assetManager = assets
        try {
            val engineFiles = assetManager.list(RENPY_ENGINE_ASSETS)
            if (engineFiles == null || engineFiles.isEmpty()) {
                throw IOException("Ren'Py engine files not found in assets/$RENPY_ENGINE_ASSETS")
            }
            
            // Recursively extract all files
            extractAssetDirectory(assetManager, RENPY_ENGINE_ASSETS, engineDir)
            
            // Write version file
            versionFile.writeText(engineVersion)
            
            Log.i(TAG, "Engine files extracted to ${engineDir.absolutePath}")
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract engine files", e)
            throw e
        }
        
        engineDir
    }
    
    private fun extractAssetDirectory(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        val entries = assetManager.list(assetPath) ?: return
        
        if (entries.isEmpty()) {
            // This is a file, extract it
            val fileName = assetPath.substringAfterLast('/')
            val targetFile = File(targetDir, fileName)
            
            try {
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.v(TAG, "Extracted: $assetPath")
            } catch (e: IOException) {
                Log.w(TAG, "Failed to extract $assetPath", e)
            }
        } else {
            // This is a directory, create it and recurse
            val subDir = File(targetDir, assetPath.substringAfterLast('/'))
            if (!subDir.exists()) {
                subDir.mkdirs()
            }
            
            for (entry in entries) {
                val childPath = if (assetPath.isEmpty()) entry else "$assetPath/$entry"
                extractAssetDirectory(assetManager, childPath, subDir)
            }
        }
    }
    
    private fun setupEnvironment(gameDir: File, engineDir: File, saveDir: File) {
        val dataDir = filesDir.absolutePath
        val publicDir = getExternalFilesDir(null)?.absolutePath ?: dataDir
        
        // Set environment variables that Ren'Py expects
        // RENPY_RUNTIME_DIR - where renpy/common/ lives
        System.setProperty("RENPY_RUNTIME_DIR", "$engineDir/renpy")
        
        // RENPY_GAME_DIR - where the game's script.rpyc and assets live
        System.setProperty("RENPY_GAME_DIR", gameDir.absolutePath)
        
        // ANDROID_PUBLIC - writable save directory
        System.setProperty("ANDROID_PUBLIC", saveDir.absolutePath)
        
        // ANDROID_PRIVATE - app's private data dir
        System.setProperty("ANDROID_PRIVATE", dataDir)
        
        // Python paths
        System.setProperty("python.home", "$engineDir/renpy")
        System.setProperty("python.path", "$engineDir/renpy")
        
        Log.i(TAG, "Environment configured:")
        Log.i(TAG, "  RENPY_RUNTIME_DIR = $engineDir/renpy")
        Log.i(TAG, "  RENPY_GAME_DIR = ${gameDir.absolutePath}")
        Log.i(TAG, "  ANDROID_PUBLIC = ${saveDir.absolutePath}")
    }
    
    private fun startRenpy(gamePath: String, enginePath: String) {
        try {
            // Call native method to start Ren'Py
            // This is defined in librenpython.so
            nativeStartRenpy(gamePath, enginePath)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native start method not found", e)
            showErrorAndFinish("Ren'Py native library not compatible")
        }
    }
    
    private fun showErrorAndFinish(message: String) {
        runOnUiThread {
            android.app.AlertDialog.Builder(this)
                .setTitle("Ren'Py Error")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
    
    /**
     * Native method to start the Ren'Py interpreter.
     * This should be implemented in librenpython.so or a JNI wrapper.
     */
    external fun nativeStartRenpy(gamePath: String, enginePath: String)
}
