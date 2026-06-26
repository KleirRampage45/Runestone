/*
 * Runestone - Multi-engine RPG Maker game launcher for Android
 * Copyright (C) 2026 Gerson (KleirRampage45)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.runestone.app

import android.Manifest
import androidx.activity.ComponentActivity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import com.runestone.app.ui.GameListViewModel
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.runestone.app.data.ControllerShortcut
import com.runestone.app.data.DisplayCutoutMode
import com.runestone.app.data.RunnerSettings
import com.runestone.app.session.GameSessionManager
import com.runestone.app.importer.ImportManager
import com.runestone.app.store.StoreCoordinator
import com.runestone.app.navigation.OverlayNavigationController
import com.runestone.app.ui.HomeCardLayout
import com.runestone.app.ui.HomeScreen
import com.runestone.app.ui.SettingsStore
import com.runestone.app.ui.Theme
import com.runestone.app.services.GameMetadataService
import com.runestone.app.provider.DownloadManager
import com.runestone.app.provider.ExtractionManager
import com.runestone.app.provider.SourcesManager
import com.runestone.app.util.AppScope
import com.runestone.app.workspace.SaveManager
import com.runestone.app.workspace.WorkspaceManager
import com.runestone.app.workspace.WorkspaceStorageReporter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var saveManager: SaveManager
    private lateinit var storageReporter: WorkspaceStorageReporter
    private lateinit var sourcesManager: SourcesManager
    private lateinit var downloadManager: DownloadManager
    private lateinit var extractionManager: ExtractionManager
    private lateinit var metadataService: GameMetadataService
    private lateinit var sessionManager: GameSessionManager
    private lateinit var storeCoordinator: StoreCoordinator
    private lateinit var importManager: ImportManager
    private lateinit var navController: OverlayNavigationController
    private var settings = RunnerSettings()
    private var games: List<WorkspaceManager.GameInfo> = emptyList()
    var gameMetadataCache: MutableMap<String, GameMetadataService.GameMetadata> = mutableMapOf()

    lateinit var rootContainer: FrameLayout
    var activeOverlay: View? = null
    var homeContentView: View? = null
    var persistentDock: View? = null

    companion object {
        private const val TAG = "Runestone"
        private const val NOTIFICATION_CHANNEL = "runestone_downloads"
        private const val EXTRA_ADB_COMMAND = "runestone_adb_command"
        private const val ADB_OPEN_FIRST_GAME = "first_game"
        private const val ADB_OPEN_HOME = "home"
        private const val ADB_OPEN_MANAGE = "manage"
        private const val ADB_OPEN_SETTINGS = "settings"
        private const val ADB_OPEN_STORE = "store"
        private const val ADB_OPEN_GAME_PREFIX = "game:"
    }

    lateinit var gameListViewModel: com.runestone.app.ui.GameListViewModel

    private var pausedGamePath: String? = null
    private var initialLaunch = true
    private var homeCardLayout = HomeCardLayout.GRID_2
    private val pressedControllerKeys = mutableSetOf<Int>()
    private var triggerResumeComboDown = false
    var controllerNavigationEnabled = false
    private var immersiveDecorConfigured = false
    private var lastImmersiveApplyAt = 0L
    private var lastAppliedCutoutMode: DisplayCutoutMode? = null
    val gameSizeCache = mutableMapOf<String, Long>()
    val gameSizeInFlight = mutableSetOf<String>()
    val metadataWarmupInFlight = mutableSetOf<String>()

    private val navCallbacks = object : OverlayNavigationController.Callbacks {
        override fun playGame(storageName: String) = this@MainActivity.playGame(storageName)
        override fun performDeleteGame(storageName: String, gameTitle: String, keepSaves: Boolean) =
            this@MainActivity.performDeleteGame(storageName, gameTitle, keepSaves)
        override fun refreshGames() = this@MainActivity.refreshGames()
        override fun applyImmersiveMode(force: Boolean) = this@MainActivity.applyImmersiveMode(force)
        override fun onSettingsChanged(newSettings: RunnerSettings) {
            val cutoutChanged = settings.displayCutoutMode != newSettings.displayCutoutMode
            settings = newSettings
            navController.settings = newSettings
            settingsStore.save(newSettings)
            applyImmersiveMode(force = cutoutChanged)
            if (cutoutChanged) {
                ViewCompat.requestApplyInsets(rootContainer)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
        applyImmersiveMode()
        pausedGamePath = null
        settingsStore = SettingsStore(this)
        workspaceManager = WorkspaceManager(this)
        saveManager = SaveManager(workspaceManager)
        storageReporter = WorkspaceStorageReporter(workspaceManager)
        sourcesManager = SourcesManager(this)
        downloadManager = DownloadManager(this)
        extractionManager = ExtractionManager(this)
        metadataService = GameMetadataService(this)
        sessionManager = GameSessionManager(this)
        sessionManager.clearResumeState("fresh_on_create")
        storeCoordinator = StoreCoordinator(this, workspaceManager, downloadManager, extractionManager, sourcesManager, metadataService, storeCallbacks)
        importManager = ImportManager(this, workspaceManager, saveManager, importCallbacks)
        gameListViewModel = ViewModelProvider(this, GameListViewModel.Factory(application as Application, workspaceManager, sessionManager, metadataService)).get(GameListViewModel::class.java)
        settings = settingsStore.load()
        navController.settings = settings
        applyImmersiveMode()
        Theme.active = Theme.byName(settings.colorPalette)
        homeCardLayout = runCatching {
            HomeCardLayout.valueOf(
                getSharedPreferences("runestone-settings-v1", MODE_PRIVATE)
                    .getString("homeCardLayout", HomeCardLayout.GRID_2.name).orEmpty(),
            )
        }.getOrDefault(HomeCardLayout.GRID_2)
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        storeCoordinator.registerDownloadReceiver()
        storeCoordinator.setupDownloadCallbacks()

        // Check onboarding
        val onboardingComplete = getSharedPreferences("runestone-settings-v1", MODE_PRIVATE)
            .getBoolean("onboarding_complete", false)

        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(255, 3, 3, 4))
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            if (settings.displayCutoutMode == DisplayCutoutMode.SAFE_AREA) {
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                val left = maxOf(bars.left, cutout.left)
                val top = maxOf(bars.top, cutout.top)
                val right = maxOf(bars.right, cutout.right)
                val bottom = maxOf(bars.bottom, cutout.bottom)
                if (v.paddingLeft != left || v.paddingTop != top || v.paddingRight != right || v.paddingBottom != bottom) {
                    v.setPadding(left, top, right, bottom)
                }
            } else {
                if (v.paddingLeft != 0 || v.paddingTop != 0 || v.paddingRight != 0 || v.paddingBottom != 0) {
                    v.setPadding(0, 0, 0, 0)
                }
            }
            insets
        }
        setContentView(rootContainer)

        if (!onboardingComplete) {
            showOnboarding()
            return
        }

        navController = OverlayNavigationController(
            activity = this,
            callbacks = navCallbacks,
            settingsStore = settingsStore,
            workspaceManager = workspaceManager,
            metadataService = metadataService,
            downloadManager = downloadManager,
            sourcesManager = sourcesManager,
            saveManager = saveManager,
            storageReporter = storageReporter,
            installStateStore = com.runestone.app.workspace.InstallStateStore(workspaceManager),
            storeCoordinator = storeCoordinator,
            importManager = importManager,
            sessionManager = sessionManager,
        )

        navController.rootContainer = rootContainer
        navController.homeCardLayout = homeCardLayout
        navController.gameMetadataCache = gameMetadataCache
        navController.games = games
        navController.settings = settings
        sessionManager.warmCache()

        navController.showSplash()

        rootContainer.post { refreshGames() }

        persistentDock = HomeScreen(this).createDockBar(
            onHome = { navController.dismissOverlay() },
            onAdd = { importManager.startFolderImport() },
            onBrowse = { storeCoordinator.showAvailableGames() },
            onManage = { navController.showManageFiles() },
            onSettings = { navController.showSettings(settings) },
        )
        rootContainer.addView(persistentDock, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(58), android.view.Gravity.BOTTOM).apply {
            setMargins(dp(10), 0, dp(10), dp(8))
        })
        navController.persistentDock = persistentDock
        handleAdbCommand(intent)
    }

    private fun showOnboarding() {
        val screen = com.runestone.app.ui.OnboardingScreen(this)
        val onboardingView = screen.create { result ->
            // Save settings
            getSharedPreferences("runestone-settings-v1", MODE_PRIVATE).edit()
                .putBoolean("onboarding_complete", true)
                .putString("locale", result.locale)
                .putString("rawgApiKey", result.rawgApiKey)
                .apply()

            // Disable unselected optional engines
            val allOptional = setOf("godot", "renpy")
            for (engine in allOptional) {
                com.runestone.app.engine.EngineRegistry.setOptionalEnabled(this, engine, engine in result.selectedEngines)
            }

            // Save RAWG key to settings
            if (result.rawgApiKey.isNotEmpty()) {
                settings = settings.copy(rawgApiKey = result.rawgApiKey)
                settingsStore.save(settings)
            }

            // Trigger RTP install if requested
            if (result.installRtp) {
                Toast.makeText(this@MainActivity, "RTP will be downloaded when you launch a game that needs it.", Toast.LENGTH_LONG).show()
            }

            // Recreate activity to start normal flow
            recreate()
        }
        rootContainer.addView(onboardingView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9104)
        }
    }

    private var gamesCollected = false

    private fun refreshGames() {
        gameListViewModel.refreshGames()
        if (gamesCollected) return
        gamesCollected = true
        AppScope.main.launch {
            gameListViewModel.uiState.collectLatest { state ->
                games = gameListViewModel.games.value
                navController.games = games
                navController.controllerNavigationEnabled = controllerNavigationEnabled
                Log.i(TAG, "refreshGames: found ${state.cards.size} games")
                if (!state.isLoading) navController.dismissSplash()
            }
        }
    }

    private fun showHome() {
        navController.showHome(
            uiMode = settings.uiMode,
            showGameName = settings.showGameName,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Game download progress"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val storeCallbacks = object : StoreCoordinator.Callbacks {
        override fun refreshGames() = this@MainActivity.refreshGames()
        override fun refreshStoreUI() {
            navController.renderAvailableGamesScreen()
        }
        override fun pushDetailOverlayUpdate(gameId: String) {
            val overlay = navController.detailOverlay ?: return
            val game = storeCoordinator.availableGames.firstOrNull { it.id == gameId } ?: return
            overlay.update(
                game = game,
                progress = storeCoordinator.downloadProgressMap[gameId],
                installProgress = storeCoordinator.installProgressMap[gameId],
                installedGameTitles = installedStoreKeys(),
            )
        }
        override fun installedStoreKeys(): Set<String> = this@MainActivity.installedStoreKeys()
        override fun getAvailableGames(): List<com.runestone.app.provider.AvailableGame> = storeCoordinator.availableGames
        override fun getGames(): List<WorkspaceManager.GameInfo> = games
        override fun getDetailOverlay(): Any? = navController.detailOverlay
    }

    private val importCallbacks = object : ImportManager.Callbacks {
        override fun showOverlay(panel: View, dismissOnBgClick: Boolean) = navController.showOverlay(panel, dismissOnBgClick)
        override fun dismissOverlay(onDismissed: () -> Unit) = navController.dismissOverlay(onDismissed)
        override fun showHome() = this@MainActivity.showHome()
        override fun showManageFiles(storageName: String?) = navController.showManageFiles(storageName)
        override fun refreshGames() = this@MainActivity.refreshGames()
        override fun showRtpDownloadDialog(storageName: String, missing: List<com.runestone.app.rtp.RtpPack>) =
            navController.showRtpDownloadDialog(storageName, missing)
        override fun showImportProgress(message: String) = navController.showImportProgress(message)
        override fun getGames(): List<WorkspaceManager.GameInfo> = games
        override fun getSettingsDefaultGameFolder(): String = settings.defaultGameFolder
    }

    private fun installedStoreKeys(): Set<String> {
        return games.flatMap { game ->
            listOf(game.displayName, game.storageName)
        }.toSet()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    fun applyImmersiveMode(force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val cutoutChanged = lastAppliedCutoutMode != settings.displayCutoutMode
        if (!force && !cutoutChanged && now - lastImmersiveApplyAt < 350L) return
        lastImmersiveApplyAt = now

        if (!immersiveDecorConfigured) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            immersiveDecorConfigured = true
        }
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT >= 28 && cutoutChanged) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (settings.displayCutoutMode == DisplayCutoutMode.EDGE_TO_EDGE) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
        }
        lastAppliedCutoutMode = settings.displayCutoutMode
    }

    private fun playGame(storageName: String) {
        val game = games.find { it.storageName == storageName } ?: return

        val isMinimized = getSharedPreferences("runestone", MODE_PRIVATE)
            .getBoolean("game_minimized", false)
        if (isMinimized && pausedGamePath != null && pausedGamePath == game.originalPath) {
            Log.i(TAG, "RESUME: $storageName")
            pausedGamePath = null
            getSharedPreferences("runestone", MODE_PRIVATE).edit()
                .remove("paused_game")
                .remove("game_minimized")
                .apply()
            finish()
            return
        }

        Log.i(TAG, "playGame: $storageName path=${game.originalPath}")
        pausedGamePath = game.originalPath
        sessionManager.start(storageName, game.originalPath)

        val effectiveSettings = com.runestone.app.data.GameConfigService(this, workspaceManager)
            .resolveRunnerSettings(storageName)
        GameActivity.start(this, game.originalPath, game.engineType.name, effectiveSettings, storageName)
    }

    private fun performDeleteGame(storageName: String, gameTitle: String, keepSaves: Boolean) {
        Log.i(TAG, "performDeleteGame: storageName=$storageName keepSaves=$keepSaves")
        try {
            workspaceManager.removeGame(storageName, keepSaves = keepSaves)
            Log.i(TAG, "performDeleteGame: removeGame returned for $storageName")
        } catch (e: Exception) {
            Log.e(TAG, "performDeleteGame: removeGame threw", e)
        }
        refreshGames()
        showHome()
        val msg = if (keepSaves) "$gameTitle reinstalled. Saves kept." else "$gameTitle deleted."
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        Log.i(TAG, "onResume importActive=${importManager.activeImportProgressView != null} initial=$initialLaunch overlay=${navController.activeOverlay != null}")
        if (importManager.activeImportProgressView != null) return
        if (initialLaunch) {
            initialLaunch = false
            return
        }
        if (navController.activeOverlay != null) return
        val runestonePrefs = getSharedPreferences("runestone", MODE_PRIVATE)
        if (runestonePrefs.getBoolean("game_minimized", false)) {
            val minimizedPath = runestonePrefs.getString("paused_game", null)
            val activePath = runestonePrefs.getString("active_game_path", null)
            if (minimizedPath != null && minimizedPath == activePath) {
                pausedGamePath = minimizedPath
                refreshGames()
                showHome()
                return
            }
            sessionManager.clearResumeState("invalid_minimized_state")
            pausedGamePath = null
            refreshGames()
            showHome()
            return
        }
        if (!runestonePrefs.contains("active_game_storage")) {
            return
        }
        sessionManager.finalize("hub_resumed")
        pausedGamePath = null
        refreshGames()
        showHome()
    }

    override fun onBackPressed() {
        if (importManager.activeImportProgressView != null) {
            Toast.makeText(this, "Operation still running.", Toast.LENGTH_SHORT).show()
        } else if (navController.activeOverlay != null) {
            navController.dismissOverlay()
        } else if (navController.activeEngineFilter != null) {
            navController.activeEngineFilter = null
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        storeCoordinator.unregisterDownloadReceiver()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAdbCommand(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
            if (controllerNavigationEnabled) {
                rootContainer.post { navController.enableControllerNavigation(rootContainer) }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.isControllerShortcut()) {
            ensureControllerNavigation()
            if (handleControllerCombo(event)) return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.isControllerShortcut()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> {
                    val focused = currentFocus
                    if (focused != null && focused != rootContainer && focused.dispatchKeyEvent(event)) return true
                    if (performFocusedClick()) return true
                }
                KeyEvent.KEYCODE_BUTTON_B -> {
                    onBackPressed()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_START -> {
                    importManager.startFolderImport()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    navController.showManageFiles()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    storeCoordinator.showAvailableGames()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    navController.cycleEngineFilter()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    navController.cycleCardLayout()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    navController.cycleSortMode()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_MODE -> {
                    navController.showSettings(settings)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && controllerNavigationEnabled) {
            disableControllerNavigation(rootContainer)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isControllerMotionShortcut() && handleTriggerResumeCombo(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i(TAG, "onActivityResult: requestCode=$requestCode resultCode=$resultCode")
        if (requestCode == ImportManager.REQUEST_IMPORT_FOLDER) {
            importManager.handleImportFolderResult(resultCode, data)
        } else if (requestCode == ImportManager.REQUEST_COVER_IMAGE) {
            importManager.handleCoverImageResult(resultCode, data)
        } else if (requestCode == ImportManager.REQUEST_PATCH_ZIP) {
            importManager.handlePatchZipResult(resultCode, data)
        } else if (requestCode == ImportManager.REQUEST_SAVE_EXPORT_ZIP) {
            importManager.handleSaveExportResult(resultCode, data)
        } else if (requestCode == ImportManager.REQUEST_SAVE_IMPORT_ZIP) {
            importManager.handleSaveImportResult(resultCode, data)
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Controller navigation
    // ═══════════════════════════════════════════════════════

    private fun ensureControllerNavigation() {
        if (!controllerNavigationEnabled) {
            controllerNavigationEnabled = true
        }
        navController.enableControllerNavigation(navController.activeOverlay ?: rootContainer)
    }

    private fun disableControllerNavigation(root: View) {
        fun visit(view: View) {
            if (view.isClickable && view !is android.widget.EditText) {
                view.isFocusable = false
                view.isFocusableInTouchMode = false
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
        controllerNavigationEnabled = false
    }

    private fun performFocusedClick(): Boolean {
        val target = currentFocus?.takeIf { it.isClickable && it.visibility == View.VISIBLE }
            ?: firstClickable(rootContainer)
        return if (target != null) {
            target.performClick()
            true
        } else false
    }

    private fun firstClickable(view: View): View? {
        if (view.isClickable && view.visibility == View.VISIBLE) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                firstClickable(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun KeyEvent.isControllerShortcut(): Boolean {
        if (keyCode in controllerShortcutKeys) return true
        val controllerSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
        return source and controllerSources != 0
    }

    private val controllerShortcutKeys: Set<Int>
        get() = setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
        )

    private fun handleControllerCombo(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            pressedControllerKeys.remove(event.keyCode)
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        pressedControllerKeys.add(event.keyCode)
        if (event.repeatCount > 0) return false

        if (shortcutPressed(settings.controllerResumeShortcut)) {
            val paused = games.firstOrNull { it.originalPath == pausedGamePath }
            if (paused != null) {
                playGame(paused.storageName)
                return true
            }
        }
        return false
    }

    private fun shortcutPressed(shortcut: ControllerShortcut): Boolean = when (shortcut) {
        ControllerShortcut.OFF -> false
        ControllerShortcut.L2_R2 ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2)
        ControllerShortcut.L1_R1 ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L1) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R1)
        ControllerShortcut.START_SELECT ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_SELECT)
        ControllerShortcut.L2_START ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_L2) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START)
        ControllerShortcut.R2_START ->
            pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_R2) &&
                pressedControllerKeys.contains(KeyEvent.KEYCODE_BUTTON_START)
    }

    private fun MotionEvent.isControllerMotionShortcut(): Boolean {
        val controllerSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
        return source and controllerSources != 0
    }

    private fun handleTriggerResumeCombo(event: MotionEvent): Boolean {
        if (settings.controllerResumeShortcut != ControllerShortcut.L2_R2) {
            triggerResumeComboDown = false
            return false
        }
        val left = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE),
        )
        val right = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS),
        )
        val bothPressed = left > 0.55f && right > 0.55f
        if (!bothPressed) {
            triggerResumeComboDown = false
            return false
        }
        if (triggerResumeComboDown) return true
        triggerResumeComboDown = true
        val paused = games.firstOrNull { it.originalPath == pausedGamePath } ?: return true
        playGame(paused.storageName)
        return true
    }

    private fun handleAdbCommand(intent: Intent?) {
        val command = intent?.getStringExtra(EXTRA_ADB_COMMAND)
            ?: intent?.getStringExtra("runestone_open")
            ?: return
        rootContainer.postDelayed({
            when (command) {
                ADB_OPEN_FIRST_GAME -> {
                    refreshGames()
                    games.firstOrNull()?.let { playGame(it.storageName) }
                        ?: Toast.makeText(this, "No installed games to launch", Toast.LENGTH_SHORT).show()
                }
                ADB_OPEN_HOME -> showHome()
                ADB_OPEN_MANAGE -> navController.showManageFiles()
                ADB_OPEN_SETTINGS -> navController.showSettings(settings)
                ADB_OPEN_STORE -> storeCoordinator.showAvailableGames()
                else -> {
                    refreshGames()
                    val storageName = command.removePrefix(ADB_OPEN_GAME_PREFIX).takeIf { it != command }
                        ?: command.takeIf { candidate -> games.any { it.storageName == candidate } }
                    if (storageName != null) {
                        playGame(storageName)
                    }
                }
            }
        }, 650)
    }
}
