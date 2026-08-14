package com.runestone.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.runestone.app.data.ControllerShortcut
import com.runestone.app.data.DisplayCutoutMode
import com.runestone.app.data.EngineType
import com.runestone.app.data.GameConfigService
import com.runestone.app.data.LayoutMode
import com.runestone.app.data.RunnerSettings
import com.runestone.app.engine.EngineRegistry
import com.runestone.app.engine.NativeGameLauncher
import com.runestone.app.engine.WebglConfigBuilder
import com.runestone.app.input.ControlProfileStore
import com.runestone.app.input.InputDispatcher
import com.runestone.app.runtime.WebViewGameSession
import com.runestone.app.workspace.WorkspaceManager
import java.io.File

class GameActivity : Activity() {

    private var webSession: WebViewGameSession? = null
    private var nativeLauncher: NativeGameLauncher? = null
    private var inputDispatcher: InputDispatcher? = null
    private var engineType: EngineType = EngineType.UNKNOWN
    private var gamePath: String = ""
    private var storageName: String? = null
    private var settings = RunnerSettings()
    private var immersiveDecorConfigured = false
    private var lastImmersiveApplyAt = 0L
    private var lastAppliedCutoutMode: DisplayCutoutMode? = null

    companion object {
        private const val TAG = "Runestone"
        private const val EXTRA_GAME_PATH = "game_path"
        private const val EXTRA_STORAGE_NAME = "storage_name"
        private const val EXTRA_ENGINE_TYPE = "engine_type"
        private const val EXTRA_LAYOUT_MODE = "layout_mode"
        private const val EXTRA_TOUCH_OPACITY = "touch_opacity"
        private const val EXTRA_TOUCH_SCALE = "touch_scale"
        private const val EXTRA_HAPTICS = "haptics"
        private const val EXTRA_HAPTIC_INTENSITY = "haptic_intensity"
        private const val EXTRA_SHOW_EXTRA_BTNS = "show_extra_btns"
        private const val EXTRA_AUDIO_EXT = "audio_ext"
        private const val EXTRA_SMOOTH_SCALING = "smooth_scaling"
        private const val EXTRA_INTEGER_SCALING = "integer_scaling"
        private const val EXTRA_TEXT_SCALE = "text_scale"
        private const val EXTRA_HIDE_GAMEPAD = "hide_gamepad"
        private const val EXTRA_DIAGONAL = "diagonal_movement"
        private const val EXTRA_KEEP_SCREEN_ON = "keep_screen_on"
        private const val EXTRA_DISPLAY_CUTOUT_MODE = "display_cutout_mode"
        private const val EXTRA_USE_HTTP_SERVER = "use_http_server"
        private const val EXTRA_WEBGL = "webgl"
        private const val EXTRA_USE_WEBGL2 = "use_webgl2"
        private const val EXTRA_FORCE_CANVAS = "force_canvas"
        private const val EXTRA_ENGINE_FAMILY = "engine_family"
        private const val EXTRA_DESKTOP_MODE = "desktop_mode"
        private const val EXTRA_ALLOW_EXTERNAL = "allow_external"
        private const val EXTRA_DIALOG_LOGS = "dialog_logs"
        private const val EXTRA_USE_RUBY18 = "use_ruby18"
        private const val EXTRA_VSYNC = "vsync"
        private const val EXTRA_FRAME_SKIP = "frame_skip"
        private const val EXTRA_SHADERS = "shaders"
        private const val EXTRA_CONTROLLER_HOME_SHORTCUT = "controller_home_shortcut"
        private const val EXTRA_CONTROLLER_KEYBOARD_SHORTCUT = "controller_keyboard_shortcut"
        private const val EXTRA_CONTROLLER_RUNTIME_MENU_SHORTCUT = "controller_runtime_menu_shortcut"
        private const val EXTRA_CONTROLLER_RESUME_SHORTCUT = "controller_resume_shortcut"

        private fun engineTypeToFamily(type: EngineType): WebglConfigBuilder.EngineFamily = when (type) {
            EngineType.MV -> WebglConfigBuilder.EngineFamily.MV
            EngineType.MZ -> WebglConfigBuilder.EngineFamily.MZ
            else -> WebglConfigBuilder.EngineFamily.HTML
        }

        private fun parseEngineTypeOrUnknown(name: String?): EngineType =
            if (name == null) EngineType.UNKNOWN
            else runCatching { EngineType.valueOf(name) }.getOrDefault(EngineType.UNKNOWN)

        fun start(activity: Activity, gamePath: String, engineType: String? = null, settings: RunnerSettings = RunnerSettings(), storageName: String? = null) {
            val intent = Intent(activity, GameActivity::class.java).apply {
                putExtra(EXTRA_GAME_PATH, gamePath)
                if (storageName != null) putExtra(EXTRA_STORAGE_NAME, storageName)
                if (engineType != null) putExtra(EXTRA_ENGINE_TYPE, engineType)
                putExtra(EXTRA_LAYOUT_MODE, settings.layoutMode.name)
                putExtra(EXTRA_TOUCH_OPACITY, settings.touchOpacity)
                putExtra(EXTRA_TOUCH_SCALE, settings.touchScale)
                putExtra(EXTRA_HAPTICS, settings.hapticsEnabled)
                putExtra(EXTRA_HAPTIC_INTENSITY, settings.hapticIntensity)
                putExtra(EXTRA_SHOW_EXTRA_BTNS, settings.showExtraButtons)
                putExtra(EXTRA_AUDIO_EXT, settings.forceAudioExt)
                putExtra(EXTRA_SMOOTH_SCALING, settings.smoothScaling)
                putExtra(EXTRA_INTEGER_SCALING, settings.integerScaling)
                putExtra(EXTRA_TEXT_SCALE, settings.textScale)
                putExtra(EXTRA_HIDE_GAMEPAD, settings.hideVirtualGamepad)
                putExtra(EXTRA_DIAGONAL, settings.diagonalMovement)
                putExtra(EXTRA_KEEP_SCREEN_ON, settings.keepScreenOn)
                putExtra(EXTRA_DISPLAY_CUTOUT_MODE, settings.displayCutoutMode.name)
                putExtra(EXTRA_USE_HTTP_SERVER, settings.useHttpServer)
                putExtra(EXTRA_WEBGL, settings.webgl)
                putExtra(EXTRA_USE_WEBGL2, settings.useWebgl2)
                putExtra(EXTRA_FORCE_CANVAS, settings.forceCanvas)
                putExtra(EXTRA_ENGINE_FAMILY, engineTypeToFamily(parseEngineTypeOrUnknown(engineType)).name)
                putExtra(EXTRA_DESKTOP_MODE, settings.desktopMode)
                putExtra(EXTRA_ALLOW_EXTERNAL, settings.allowExternalModules)
                putExtra(EXTRA_DIALOG_LOGS, settings.dialogLogs)
                putExtra(EXTRA_USE_RUBY18, settings.useRuby18)
                putExtra(EXTRA_VSYNC, settings.vsync)
                putExtra(EXTRA_FRAME_SKIP, settings.frameSkip)
                putExtra(EXTRA_SHADERS, settings.shaders)
                putExtra(EXTRA_CONTROLLER_HOME_SHORTCUT, settings.controllerHomeShortcut.name)
                putExtra(EXTRA_CONTROLLER_KEYBOARD_SHORTCUT, settings.controllerKeyboardShortcut.name)
                putExtra(EXTRA_CONTROLLER_RUNTIME_MENU_SHORTCUT, settings.controllerRuntimeMenuShortcut.name)
                putExtra(EXTRA_CONTROLLER_RESUME_SHORTCUT, settings.controllerResumeShortcut.name)
            }
            activity.startActivity(intent)
        }
    }

    private val sessionCallbacks = object : WebViewGameSession.Callbacks {
        override fun onGoHomePaused() = goHomePaused()
        override fun onOpenSettings() = showRuntimeActions()
        override fun onToggleKeyboard() = toggleKeyboard()
        override fun onPersistInputSettings(layoutMode: LayoutMode, hideGamepad: Boolean) = persistRuntimeInputSettings()
        override fun onPersistControlProfile(buttons: List<com.runestone.app.input.ControlButtonProfile>) = persistRuntimeControlProfile(buttons)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveMode()

        gamePath = intent.getStringExtra(EXTRA_GAME_PATH) ?: run {
            Toast.makeText(this, "No game path provided", Toast.LENGTH_SHORT).show(); finish(); return
        }
        storageName = intent.getStringExtra(EXTRA_STORAGE_NAME)
        val gameDir = File(gamePath)
        if (!gameDir.exists() || !gameDir.isDirectory) {
            Toast.makeText(this, "Game directory not found", Toast.LENGTH_SHORT).show(); finish(); return
        }

        val typeStr = intent.getStringExtra(EXTRA_ENGINE_TYPE)
        engineType = if (typeStr != null) {
            runCatching { EngineType.valueOf(typeStr) }.getOrDefault(detectEngine(gameDir))
        } else detectEngine(gameDir)

        migrateOverlayPrefs()
        settings = loadSettingsFromExtras()
        if (settings.layoutMode == LayoutMode.GAMEPAD) {
            settings = settings.copy(layoutMode = LayoutMode.LANDSCAPE, hideVirtualGamepad = true)
        }
        if (settings.keepScreenOn) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ControlProfileStore(this).ensureDefaults(engineType, storageName, settings)
        applyImmersiveMode()

        requestedOrientation = when {
            engineType == EngineType.RENPY -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            settings.layoutMode == LayoutMode.LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        nativeLauncher = NativeGameLauncher(this, settings, engineType)

        when (engineType) {
            EngineType.MV, EngineType.MZ, EngineType.TYRANO, EngineType.CONSTRUCT,
            EngineType.HTML, EngineType.TWINE, EngineType.VNMAKER, EngineType.RUFFLE,
            EngineType.UNKNOWN -> {
                if (engineType == EngineType.UNKNOWN) Toast.makeText(this, "Unknown engine, trying WebView", Toast.LENGTH_SHORT).show()
                launchWebView(gameDir)
            }
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> nativeLauncher!!.launchRgssGame(gameDir)
            EngineType.RGSS_2000, EngineType.RGSS_2003, EngineType.EASYRPG -> nativeLauncher!!.launchEasyRpgGame(gameDir)
            EngineType.RENPY -> nativeLauncher!!.launchRenpyGame(gameDir)
            EngineType.GODOT, EngineType.GODOT3, EngineType.GODOT4 -> nativeLauncher!!.launchGodotGame(gameDir)
            EngineType.NSCRIPTER -> nativeLauncher!!.launchNScripterGame(gameDir)
            EngineType.RM95, EngineType.DANTE98, EngineType.WOLF, EngineType.KIRIKIRI,
            EngineType.UNITY, EngineType.UNREAL, EngineType.GAMEMAKER, EngineType.AGS ->
                nativeLauncher!!.showLegacyDialog(engineType)
            EngineType.ELECTRON -> nativeLauncher!!.showElectronDialog()
        }
    }

    private fun launchWebView(gameDir: File) {
        val session = WebViewGameSession(this, gameDir, settings, engineType, sessionCallbacks)
        webSession = session
        inputDispatcher = InputDispatcher(session.webViewEngine)
        session.launch()
    }

    private fun loadSettingsFromExtras(): RunnerSettings {
        val d = RunnerSettings()
        return RunnerSettings(
            layoutMode = runCatching { LayoutMode.valueOf(intent.getStringExtra(EXTRA_LAYOUT_MODE) ?: LayoutMode.PORTRAIT_CONSOLE.name) }.getOrDefault(LayoutMode.PORTRAIT_CONSOLE),
            touchOpacity = intent.getFloatExtra(EXTRA_TOUCH_OPACITY, d.touchOpacity),
            touchScale = intent.getFloatExtra(EXTRA_TOUCH_SCALE, d.touchScale),
            hapticsEnabled = intent.getBooleanExtra(EXTRA_HAPTICS, d.hapticsEnabled),
            hapticIntensity = intent.getFloatExtra(EXTRA_HAPTIC_INTENSITY, d.hapticIntensity),
            showExtraButtons = intent.getBooleanExtra(EXTRA_SHOW_EXTRA_BTNS, d.showExtraButtons),
            forceAudioExt = intent.getStringExtra(EXTRA_AUDIO_EXT) ?: d.forceAudioExt,
            smoothScaling = intent.getBooleanExtra(EXTRA_SMOOTH_SCALING, d.smoothScaling),
            integerScaling = intent.getBooleanExtra(EXTRA_INTEGER_SCALING, d.integerScaling),
            textScale = intent.getFloatExtra(EXTRA_TEXT_SCALE, d.textScale),
            hideVirtualGamepad = intent.getBooleanExtra(EXTRA_HIDE_GAMEPAD, d.hideVirtualGamepad),
            diagonalMovement = intent.getBooleanExtra(EXTRA_DIAGONAL, d.diagonalMovement),
            keepScreenOn = intent.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, d.keepScreenOn),
            displayCutoutMode = runCatching { DisplayCutoutMode.valueOf(intent.getStringExtra(EXTRA_DISPLAY_CUTOUT_MODE) ?: d.displayCutoutMode.name) }.getOrDefault(d.displayCutoutMode),
            useHttpServer = intent.getBooleanExtra(EXTRA_USE_HTTP_SERVER, d.useHttpServer),
            webgl = intent.getBooleanExtra(EXTRA_WEBGL, d.webgl),
            useWebgl2 = intent.getBooleanExtra(EXTRA_USE_WEBGL2, d.useWebgl2),
            forceCanvas = intent.getBooleanExtra(EXTRA_FORCE_CANVAS, d.forceCanvas),
            desktopMode = intent.getBooleanExtra(EXTRA_DESKTOP_MODE, d.desktopMode),
            allowExternalModules = intent.getBooleanExtra(EXTRA_ALLOW_EXTERNAL, d.allowExternalModules),
            dialogLogs = intent.getBooleanExtra(EXTRA_DIALOG_LOGS, d.dialogLogs),
            useRuby18 = intent.getBooleanExtra(EXTRA_USE_RUBY18, d.useRuby18),
            vsync = intent.getBooleanExtra(EXTRA_VSYNC, d.vsync),
            frameSkip = intent.getBooleanExtra(EXTRA_FRAME_SKIP, d.frameSkip),
            shaders = intent.getBooleanExtra(EXTRA_SHADERS, d.shaders),
            controllerHomeShortcut = shortcut(EXTRA_CONTROLLER_HOME_SHORTCUT, d.controllerHomeShortcut),
            controllerKeyboardShortcut = shortcut(EXTRA_CONTROLLER_KEYBOARD_SHORTCUT, d.controllerKeyboardShortcut),
            controllerRuntimeMenuShortcut = shortcut(EXTRA_CONTROLLER_RUNTIME_MENU_SHORTCUT, d.controllerRuntimeMenuShortcut),
            controllerResumeShortcut = shortcut(EXTRA_CONTROLLER_RESUME_SHORTCUT, d.controllerResumeShortcut),
        )
    }

    private fun shortcut(extra: String, default: ControllerShortcut): ControllerShortcut =
        runCatching { ControllerShortcut.valueOf(intent.getStringExtra(extra) ?: default.name) }.getOrDefault(default)

    private fun goHomePaused() {
        getSharedPreferences("runestone", MODE_PRIVATE).edit()
            .putBoolean("game_minimized", true).putString("paused_game", gamePath).apply()
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private fun showRuntimeActions() { webSession?.showRuntimeActions() }

    private fun toggleKeyboard() { webSession?.toggleKeyboard() }

    private fun persistRuntimeInputSettings() {
        val name = storageName ?: return
        runCatching {
            val service = GameConfigService(this, WorkspaceManager(this))
            val current = service.loadPerGame(name)
            service.savePerGame(name, current.copy(input = current.input.copy(
                layoutMode = settings.layoutMode.name.lowercase(), hideVirtualGamepad = settings.hideVirtualGamepad,
            )))
        }.onFailure { Log.w(TAG, "Failed to persist runtime input settings", it) }
    }

    private fun persistRuntimeControlProfile(buttons: List<com.runestone.app.input.ControlButtonProfile>) {
        if (buttons.isEmpty()) return
        runCatching {
            val store = com.runestone.app.input.ControlProfileStore(this)
            val existing = store.loadEffective(engineType, storageName, settings)
            val editedLayout = buttons.first().layout
            val merged = existing.buttons.filterNot { it.layout == editedLayout } + buttons
            val scope = if (storageName != null) com.runestone.app.input.ControlProfileScope.GAME else com.runestone.app.input.ControlProfileScope.ENGINE
            store.save(com.runestone.app.input.ControlProfile(
                id = if (storageName != null) "custom-$storageName" else "custom-${engineType.name.lowercase()}",
                name = "Custom Layout", scope = scope, engineType = engineType, storageName = storageName, buttons = merged,
            ))
            Toast.makeText(this, "Control layout saved", Toast.LENGTH_SHORT).show()
        }.onFailure { Log.w(TAG, "Failed to persist control profile", it) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (webSession?.keyboardVisible == true && InputDispatcher.isControllerEvent(event)) {
            webSession?.keyboardView?.handleControllerKey(event)?.let { if (it) return true }
        }
        val dispatcher = inputDispatcher
        if (dispatcher != null) {
            if (dispatcher.dispatchKeyEvent(event)) return true
            if (InputDispatcher.isControllerEvent(event) && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when {
                    dispatcher.shortcutPressed(settings.controllerHomeShortcut, settings.controllerHomeShortcut) -> { goHomePaused(); return true }
                    dispatcher.shortcutPressed(settings.controllerKeyboardShortcut, settings.controllerKeyboardShortcut) -> { toggleKeyboard(); return true }
                    dispatcher.shortcutPressed(settings.controllerRuntimeMenuShortcut, settings.controllerRuntimeMenuShortcut) -> { showRuntimeActions(); return true }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val dispatcher = inputDispatcher
        if (dispatcher != null && InputDispatcher.isControllerMotionEvent(event)) {
            if (webSession?.keyboardVisible == true) return true
            if (settings.controllerHomeShortcut == ControllerShortcut.L2_R2) {
                if (maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.55f &&
                    maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.55f) {
                    goHomePaused(); return true
                }
            }
            return dispatcher.dispatchGenericMotionEvent(event)
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onBackPressed() {
        if (webSession?.handleBack() != true) {
            getSharedPreferences("runestone", MODE_PRIVATE).edit().remove("paused_game").remove("game_minimized").apply()
            super.onBackPressed()
        }
    }

    override fun onPause() { super.onPause(); inputDispatcher?.releaseControllerAxes(); webSession?.onPause() }

    override fun onResume() {
        super.onResume(); applyImmersiveMode()
        val killPath = getSharedPreferences("runestone", MODE_PRIVATE).getString("kill_game", null)
        if (killPath != null && gamePath != null && (killPath == gamePath || killPath == gamePath.substringAfterLast("/"))) {
            getSharedPreferences("runestone", MODE_PRIVATE).edit().remove("kill_game").apply()
            Log.i(TAG, "kill_game signal received for $killPath — finishing"); finish(); return
        }
        webSession?.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) applyImmersiveMode() }

    override fun onDestroy() { super.onDestroy(); webSession?.onDestroy(); webSession = null }

    private fun migrateOverlayPrefs() {
        val prefs = getSharedPreferences("runestone", MODE_PRIVATE)
        val version = prefs.getInt("overlay_layout_version", 0)
        if (version >= 3) return
        prefs.edit().putInt("overlay_layout_version", 3).apply {
            if (prefs.getBoolean("hide_virtual_gamepad", false)) putBoolean("hide_virtual_gamepad", false)
            prefs.all.keys.filter { it.startsWith("landscape_SIMPLIFIED_") || it.startsWith("portrait_SIMPLIFIED_") }.forEach { remove(it) }
        }.apply()
        runCatching {
            val lp = getSharedPreferences("controller-layout-v2", MODE_PRIVATE).edit()
            getSharedPreferences("controller-layout-v2", MODE_PRIVATE).all.forEach { (k, v) ->
                if (v is Float) when {
                    k.endsWith("_x") || k.endsWith("_y") -> if (v < 0f || v > 1f) lp.remove(k)
                    k.endsWith("_size") -> if (v < 0.02f || v > 0.5f) lp.remove(k)
                }
            }
            lp.apply()
        }
    }

    private fun applyImmersiveMode(force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val cutoutChanged = lastAppliedCutoutMode != settings.displayCutoutMode
        if (!force && !cutoutChanged && now - lastImmersiveApplyAt < 350L) return
        lastImmersiveApplyAt = now
        if (!immersiveDecorConfigured) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            immersiveDecorConfigured = true
        }
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= 28 && cutoutChanged) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (settings.displayCutoutMode == DisplayCutoutMode.EDGE_TO_EDGE)
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
        lastAppliedCutoutMode = settings.displayCutoutMode
    }

    private fun detectEngine(gameDir: java.io.File): EngineType {
        val engine = com.runestone.app.engine.EngineRegistry.detect(gameDir)
        return engine?.let { com.runestone.app.data.EngineType.fromEngineId(it.id) } ?: com.runestone.app.data.EngineType.UNKNOWN
    }
}
