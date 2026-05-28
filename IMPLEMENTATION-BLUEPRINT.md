# Runestone — Full Implementation Blueprint

> **Goal:** Replace all stubs. Build a complete JoiPlay competitor from scratch.
> **Target version:** 1.0.0
> **Current version:** 0.2.0 (stub-heavy alpha)

---

## Table of Contents

1. [Plugin Architecture](#1-plugin-architecture)
2. [Native Runtime Integration](#2-native-runtime-integration)
3. [Per-Game Configuration System](#3-per-game-configuration-system)
4. [Input & Controls System](#4-input--controls-system)
5. [In-Game Menu Overlay](#5-in-game-menu-overlay)
6. [Cheat System](#6-cheat-system)
7. [Map Optimization](#7-map-optimization)
8. [Game Library & UI](#8-game-library--ui)
9. [Save File Management](#9-save-file-management)
10. [Settings Database](#10-settings-database)
11. [Plugin System for Third Parties](#11-plugin-system-for-third-parties)
12. [Missing Engine Support](#12-missing-engine-support)
13. [Game Import Pipeline](#13-game-import-pipeline)
14. [Configuration File Format](#14-configuration-file-format)
15. [Expanded Settings](#15-expanded-settings)
16. [Complete Settings Reference](#16-complete-settings-reference)
17. [Implementation Priority Matrix](#17-implementation-priority-matrix)

---

## 1. Plugin Architecture

### 1.1 Current State (DELETE)
```
EngineRegistry.initDefaults() registers engines inline at app startup.
All engine code is in the same APK.
Native builds are commented out.
EasyRpgEngine.kt — STUB (throws "coming soon" toast)
RenpyEngine.kt — STUB (throws "coming soon" toast)
```

### 1.2 Target: Plugin APK Architecture

**Core App:** `com.runestone.app` (~5 MB)
- Library management
- Game import
- Input overlay
- In-game menu
- Settings
- Plugin discovery

**Plugin APKs** (separate installable packages):

| Plugin Package | Activity | Purpose |
|---|---|---|
| `com.runestone.plugin.mkxpz` | `MkxpzActivity` | XP/VX/VX Ace native runtime |
| `com.runestone.plugin.easyrpg` | `EasyRpgActivity` | RM2000/2003 native runtime |
| `com.runestone.plugin.renpy` | `RenpyActivity` | Ren'Py runtime |
| `com.runestone.plugin.ruffle` | `RuffleActivity` | Flash games |
| `com.runestone.plugin.godot3` | `Godot3Activity` | Godot 3.x games |
| `com.runestone.plugin.godot4` | `Godot4Activity` | Godot 4.x games |

### 1.3 Plugin Discovery Mechanism

```kotlin
// PluginDiscoveryService.kt
data class PluginInfo(
    val packageName: String,
    val activityClass: String,
    val engineId: String,         // "mkxp-z", "easyrpg", "renpy", etc.
    val engineName: String,       // "RPG Maker XP/VX/VX Ace"
    val version: String,
    val supportedTypes: List<String>,  // ["rpgmxp", "rpgmvx", "rpgmvxace"]
    val isInstalled: Boolean,
    val iconRes: Int?
)

class PluginDiscoveryService(context: Context) {
    fun discoverPlugins(): List<PluginInfo> {
        // Query PackageManager for apps with intent filter:
        // <action android:name="com.runestone.plugin.RUNESTONE_PLUGIN" />
        // Each plugin declares this in its manifest:
        // <meta-data android:name="runestone.engine_id" android:value="mkxp-z"/>
        // <meta-data android:name="runestone.engine_name" android:value="..."/>
        // <meta-data android:name="runestone.supported_types" android:value="rpgmxp,rpgmvx,rpgmvxace"/>
    }
    
    fun isPluginAvailable(engineId: String): Boolean
    fun launchPlugin(context: Context, plugin: PluginInfo, gamePath: String, config: GameConfig)
    fun openPluginMarket(engineId: String) // opens download page if not installed
}
```

### 1.4 Manifest Template for Plugins

```xml
<!-- AndroidManifest.xml for com.runestone.plugin.mkxpz -->
<manifest package="com.runestone.plugin.mkxpz">
    <application>
        <activity android:name=".MkxpzActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="com.runestone.plugin.RUNESTONE_PLUGIN" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data android:name="runestone.engine_id" android:value="mkxp-z" />
            <meta-data android:name="runestone.engine_name" android:value="RPG Maker XP/VX/VX Ace" />
            <meta-data android:name="runestone.supported_types" android:value="rpgmxp,rpgmvx,rpgmvxace" />
            <meta-data android:name="runestone.version" android:value="1.0.0" />
        </activity>
    </application>
</manifest>
```

### 1.5 EngineInterface for Plugin Communications

```kotlin
// PluginAPI.kt — shared library all plugins implement
interface PluginAPI {
    fun canRun(gameFolder: File): Boolean
    fun launch(intent: Intent, gameFolder: File, config: GameConfig)
    fun getSaves(gameFolder: File): List<SaveFileInfo>
    fun getMetadata(gameFolder: File): GameMetadata?
    fun getSettingsScreen(context: Context): View?  // Optional per-plugin settings
}
```

---

## 2. Native Runtime Integration

### 2.1 mkxp-z Native Build (URGENT — currently broken)

**Current state:**
```
build.gradle.kts:
  // Native build is optional
  // externalNativeBuild { ... }  ← COMMENTED OUT

GameActivity.launchRgssGame():
  -> Intent to com.hatkid.mkxpz.MainActivity  ← NOT IN SAME APK
```

**Requirements:**
1. Fork mkxp-z into `native/mkxp-z-android/` submodule
2. Apply Runestone-specific patches:
   - Custom key mapping system (read from `runestone_input.json`)
   - Game config pass-through via Intent extras
   - FPS counter overlay toggle
   - Speed-up/turbo toggle (frame skip injection)
   - Proper Android surface lifecycle
3. Uncomment ndkBuild in build.gradle.kts
4. Build for arm64-v8a

**mkxp-z config passthrough:**
```kotlin
val intent = Intent().apply {
    setClassName(context.packageName, "com.hatkid.mkxpz.MainActivity")
    putExtra("game_path", gameFolder.absolutePath)
    putExtra("mkxp_debug", config.debug)
    // Runestone extras:
    putExtra("runestone_font_scale", gameSettings.fontScale)
    putExtra("runestone_audio_buffer", gameSettings.audioBufferSize)
    putExtra("runestone_show_fps", gameSettings.showFps)
    putExtra("runestone_video_skip", gameSettings.skipVideo)
    putExtra("runestone_speed_multiplier", gameSettings.speedMultiplier)
    putExtra("runestone_screen_filter", gameSettings.screenFilter.name)
    putExtra("runestone_key_mapping", gameSettings.keyMappingFilePath)
}
```

### 2.2 EasyRPG Native Build (REPLACE STUB)

**Current state:** `EasyRpgEngine.launch()` shows toast "coming soon" and finishes.

**Requirements:**
1. Fork EasyRPG Player Android port into `native/easyrpg-android/`
2. Build .so for arm64-v8a
3. Register as engine plugin `org.easyrpg.player.GameActivity`
4. Pass game path and config via Intent extras

**Detection:** Already correct — checks for `RPG_RT.exe` + `.lmt`/`.ldb` files.

### 2.3 Ren'Py Runtime (REPLACE STUB)

**Current state:** `RenpyEngine.launch()` checks for plugin but throws RuntimeException.

**Two approaches:**
- **Option A (Plugin APK):** Separate APK `com.runestone.plugin.renpy` that wraps Ren'Py's Android backend (SDL2 + Python). Users install it separately.
- **Option B (Embedded):** Bundle Ren'Py's Android runtime (~40 MB extracted). Not recommended due to APK size.

**Recommended: Plugin APK with in-app download prompt.**
```kotlin
class RenpyEngine : GameEngine {
    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        if (!isPluginInstalled(context)) {
            promptDownload(context, "Ren'Py Plugin", 
                "https://runestone.app/plugins/renpy")
            return  // don't crash — prompt instead
        }
        // Launch plugin activity
    }
    
    private fun promptDownload(context: Context, name: String, url: String) {
        AlertDialog.Builder(context)
            .setTitle("$name Required")
            .setMessage("This game needs the $name to run. Download it now?")
            .setPositiveButton("Download") { 
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
```

---

## 3. Per-Game Configuration System

### 3.1 Current State (DELETE)
```kotlin
RunnerSettings — global only, per-game override not possible
SettingsStore — saves to SharedPreferences as a single blob
```

### 3.2 Target: Per-Game JSON Settings

Each game directory gets a `runestone.json` config file:

```json
{
  "version": 1,
  "game": {
    "title": "My Game",
    "engine": "mkxp-z",
    "engineOverride": null
  },
  "input": {
    "layoutMode": "portrait_console",
    "buttonOpacity": 0.72,
    "buttonScale": 1.0,
    "showExtraButtons": false,
    "buttonLayout": {
      "dpad": { "x": 0.22, "y": 0.50, "size": 1.0 },
      "btnA": { "x": 0.78, "y": 0.70, "size": 1.0 },
      "btnB": { "x": 0.85, "y": 0.50, "size": 1.0 },
      "btnX": { "x": 0.78, "y": 0.30, "size": 1.0 },
      "btnY": { "x": 0.71, "y": 0.50, "size": 1.0 },
      "select": { "x": 0.20, "y": 0.90, "size": 1.0 },
      "start": { "x": 0.50, "y": 0.90, "size": 1.0 },
      "menu": { "x": 0.80, "y": 0.90, "size": 1.0 }
    },
    "controllerMapping": {
      "btnA": "key_z",
      "btnB": "key_x",
      "btnX": "key_q",
      "btnY": "key_w",
      "select": "key_escape",
      "start": "key_enter"
    }
  },
  "video": {
    "screenFilter": "none",
    "integerScaling": false,
    "smoothScaling": false,
    "showFps": true,
    "frameSkip": 0,
    "vsync": true
  },
  "audio": {
    "forceAudioExt": ".ogg",
    "audioBufferSize": 2048,
    "muteMusic": false,
    "muteSfx": false
  },
  "performance": {
    "speedMultiplier": 1.0,
    "optimizeMaps": false,
    "forceMiniz": false,
    "threadedRendering": true
  },
  "cheats": {
    "enabled": false,
    "scripts": []
  },
  "fonts": {
    "fallbackFont": null,
    "fontScale": 1.0,
    "boldText": false
  }
}
```

### 3.3 Implementation

```kotlin
// PerGameConfig.kt
data class PerGameConfig(
    val version: Int = 1,
    val game: GameSection = GameSection(),
    val input: InputSection = InputSection(),
    val video: VideoSection = VideoSection(),
    val audio: AudioSection = AudioSection(),
    val performance: PerformanceSection = PerformanceSection(),
    val cheats: CheatSection = CheatSection(),
    val fonts: FontSection = FontSection()
) {
    companion object {
        fun load(gameDir: File): PerGameConfig {
            val configFile = File(gameDir.parentFile ?: gameDir, "runestone.json")
            if (!configFile.exists()) return PerGameConfig()
            return Json.decodeFromString(configFile.readText())
        }
        fun save(gameDir: File, config: PerGameConfig) {
            val configFile = File(gameDir.parentFile ?: gameDir, "runestone.json")
            configFile.writeText(Json.encodeToString(config))
        }
    }
}

// GameConfigService.kt — manages resolution: global -> per-game -> defaults
class GameConfigService(private val context: Context) {
    private val globalSettings = SettingsStore(context)
    
    fun resolve(storageName: String): PerGameConfig {
        val gameConfig = loadPerGame(storageName)
        // Merge: game config overrides global defaults
        return mergeConfigs(PerGameConfig(), gameConfig)
    }
}
```

---

## 4. Input & Controls System

### 4.1 Current State
```
TouchOverlayView.kt — 732 lines
✅ D-Pad (4 dirs)
✅ A/B + optional X/Y
✅ Select/Start/Settings
✅ Multi-touch
✅ Haptic feedback
❌ No layout editing (code exists but broken)
❌ No physical controller
❌ No per-game layouts
❌ No L1/R1/L2/R2
❌ No presets
```

### 4.2 Target: Full Input Subsystem

#### 4.2.1 Button Definitions

```kotlin
enum class GameButton(val defaultKey: Int) {
    DPAD_UP(KeyEvent.KEYCODE_DPAD_UP),
    DPAD_DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
    DPAD_LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
    DPAD_RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
    BTN_A(KeyEvent.KEYCODE_Z),
    BTN_B(KeyEvent.KEYCODE_X),
    BTN_X(KeyEvent.KEYCODE_Q),
    BTN_Y(KeyEvent.KEYCODE_W),
    BTN_L1(KeyEvent.KEYCODE_A),
    BTN_R1(KeyEvent.KEYCODE_S),
    BTN_L2(KeyEvent.KEYCODE_D),
    BTN_R2(KeyEvent.KEYCODE_F),
    SELECT(KeyEvent.KEYCODE_ESCAPE),
    START(KeyEvent.KEYCODE_ENTER),
    MENU(KeyEvent.KEYCODE_M),
    SCREENSHOT(KeyEvent.KEYCODE_P),
    SPEED_TOGGLE(KeyEvent.KEYCODE_TAB),
    CHEAT_MENU(KeyEvent.KEYCODE_F12),
}
```

#### 4.2.2 Physical Controller Mapping

```kotlin
// ControllerMapper.kt
class ControllerMapper {
    // Built-in presets:
    // - Xbox 360/One
    // - PS4/PS5
    // - Switch Pro
    // - Retroid Pocket
    // - Anbernic RG
    // - AYN Odin
    // - Generic HID
    
    data class ControllerPreset(
        val name: String,
        val mappings: Map<Int, GameButton>,  // keyCode -> GameButton
        val axisMappings: Map<Int, AxisConfig>  // axis -> direction
    )
    
    data class AxisConfig(
        val positiveButton: GameButton,
        val negativeButton: GameButton,
        val deadZone: Float = 0.3f
    )
    
    // Built-in presets
    val presets = mapOf(
        "xbox" to ControllerPreset(...),
        "ps4" to ControllerPreset(...),
        "generic" to ControllerPreset(...),
    )
    
    fun detectController(device: InputDevice): String?  // auto-detect preset name
    fun mapControllerToKeyboard(event: KeyEvent): GameButton?
    fun mapAxisToKeyboard(event: MotionEvent): List<GameButton>
}
```

#### 4.2.3 Layout Editor (Replace Broken Stubs)

The `TouchOverlayView.kt` already has editor mode stubs. Complete them:

```kotlin
// Editor features needed:
class TouchOverlayView ... {
    // 1. Long-press a control to enter edit mode (DONE — but broken)
    // 2. Drag control to new position (DONE — but no position clamping)
    // 3. Pinch to resize (NOT IMPLEMENTED)
    // 4. Save to per-game config (NOT IMPLEMENTED — uses defaultLayout map)
    // 5. Button to reset to default (DONE — revertRect)
    // 6. Presets selector (NOT IMPLEMENTED — presetRect exists but no content)
    
    // Fix: replace defaultLayout capture with per-game JSON persistence
    fun saveLayout() {
        val config = PerGameConfig.load(gameDir)
        config.input.buttonLayout = captureCurrentLayout()
        PerGameConfig.save(gameDir, config)
    }
    
    fun loadLayout(): Map<Control, ControlPlacement> {
        val config = PerGameConfig.load(gameDir)
        return config.input.buttonLayout?.toPlacementMap() ?: getDefaultLayout()
    }
    
    // Add new buttons: L1, R1, L2, R2 (conditional)
    fun setExtraButtons(showL1R1: Boolean) {
        // Position L1 at top-left, R1 at top-right of screen
        // L2/R2 alongside L1/R1
    }
}
```

#### 4.2.4 Controller Mapping UI

```kotlin
// ControllerMappingScreen.kt
class ControllerMappingScreen(context: Context) : View(context) {
    // Shows a list of GameButton -> physical button mapping
    // "Press the button you want to map..."
    // Wait for any KeyEvent, capture it, bind to selected GameButton
    // Supports:
    //   - Per-game mapping (saves to runestone.json)
    //   - Global default mapping
    //   - Preset selector (Auto-detect, Xbox, PS4, Custom)
    //   - Axis dead zone slider
    //   - Invert axis toggle
}
```

---

## 5. In-Game Menu Overlay

### 5.1 Current State
```
GameActivity.openSettings() -> Toast with layout + haptics info
That's it.
```

### 5.2 Target: Slide-Out Panel Overlay

```
┌──────────────────────┐
│  RUNESTONE  (X)      │  ← Draggable from left edge or tap settings gear
├──────────────────────┤
│ 📁 Close Game        │  → Return to library
│ 🔄 Rotate Screen     │  → Toggle portrait/landscape
│ ⌨️ Keyboard           │  → Show/hide soft keyboard
│ ⏩ Fast Forward       │  → Speed: 1x 2x 3x 4x
│ 🎮 Control Panel     │  → Open layout editor in-game
│ 🧪 Cheat Menu        │  → Open cheat menu overlay
│ 📷 Screenshot        │  → Capture and save screenshot
│ ⚙️ Settings           │  → Quick settings: opacity, scale, haptics
├──────────────────────┤
│ Opacity: [━━━━●━━━]  │
│ Scale:  [━━●━━━━━]   │
│ Speed:  2x  [●]      │
│ FPS: ON              │
└──────────────────────┘
```

### 5.3 Implementation

```kotlin
// InGameMenu.kt
class InGameMenu(context: Context) : FrameLayout(context) {

    // Slide from left edge gesture detector
    private val edgeSwipeThreshold = 20f  // dp
    
    // Menu items
    sealed class MenuAction {
        object CloseGame : MenuAction()
        object RotateScreen : MenuAction()
        object ShowKeyboard : MenuAction()
        data class SetSpeed(val multiplier: Float) : MenuAction()
        object OpenControlPanel : MenuAction()
        object OpenCheats : MenuAction()
        object Screenshot : MenuAction()
        object QuickSettings : MenuAction()
    }
    
    // Floating speed indicator (always visible when > 1x)
    private var speedMultiplier = 1.0f
    private val speedIndicator = TextView(context).apply {
        text = "2x"
        setTextColor(Color.GREEN)
        textSize = 14f
        setBackgroundColor(Color.argb(120, 0, 0, 0))
        setPadding(4, 2, 4, 2)
    }
    
    // Fast Forward implementation:
    // For WebView: evaluateJavascript to overload requestAnimationFrame
    // For mkxp-z: pass frameSkip setting to native runtime
    fun setSpeed(multiplier: Float) {
        speedMultiplier = multiplier
        if (multiplier > 1f) {
            val js = """
                (function() {
                    var _origRAF = window.requestAnimationFrame;
                    var _speed = $multiplier;
                    var _lastTime = 0;
                    window.requestAnimationFrame = function(callback) {
                        _origRAF(function(timestamp) {
                            if (timestamp - _lastTime > 16 / _speed) {
                                _lastTime = timestamp;
                                callback(timestamp);
                            } else {
                                window.requestAnimationFrame(callback);
                            }
                        });
                    };
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }
}
```

---

## 6. Cheat System

### 6.1 Current State
**Nothing.**

### 6.2 Target: Cheat Engine

#### 6.2.1 Cheat Methods By Engine

| Engine | Method |
|--------|--------|
| RPG Maker MV/MZ | JavaScript injection into game context |
| RPG Maker XP/VX/VX Ace | Ruby script injection via mkxp-z |
| Ren'Py | Python injection |
| HTML/WebView | JS injection |

#### 6.2.2 Built-in Cheats

```kotlin
// CheatEngine.kt
sealed class Cheat {
    data class GetItems(val items: List<ItemDef>) : Cheat()
    data class LevelUp(val amount: Int = 99) : Cheat()
    data class HealParty : Cheat()
    data class SetGold(val amount: Int) : Cheat()
    data class SetStat(val stat: String, val value: Int) : Cheat()
    data class WalkThroughWalls : Cheat()
    data class ToggleEncounter : Cheat()
    data class CustomScript(val script: String, val language: ScriptLang) : Cheat()
}

enum class ScriptLang { JAVASCRIPT, RUBY, PYTHON }

// MV/MZ cheats via JS injection
fun injectMvCheat(webView: WebView, cheat: Cheat) {
    val js = when (cheat) {
        is Cheat.SetGold -> """
            if (window.$gameParty) {
                $gameParty._gold = ${cheat.amount};
                $gameParty.gainGold(0);
            }
        """.trimIndent()
        is Cheat.HealParty -> """
            if (window.$gameParty) {
                $gameParty.members().forEach(function(a) {
                    a.recoverAll();
                });
            }
        """.trimIndent()
        is Cheat.ToggleEncounter -> """
            if (window.$gameSystem) {
                $gameSystem._encounterEnabled = !$gameSystem._encounterEnabled;
            }
        """.trimIndent()
        // ... etc
    }
    webView.evaluateJavascript("(function(){try{$js}catch(e){}})();", null)
}

// XP/VX/VX Ace cheats via mkxp-z Ruby injection
fun injectRgssCheat(mkxpzActivity: Activity, cheat: Cheat) {
    // mkxp-z exposes a "runestone_exec_ruby" command pipe
    val ruby = when (cheat) {
        is Cheat.SetGold -> """
            $game_party.gold = ${cheat.amount}
        """.trimIndent()
        // ...
    }
    // Send via Intent or named pipe to mkxp-z process
}
```

#### 6.2.3 Cheat Menu UI

```kotlin
// CheatMenuView.kt
class CheatMenuView(context: Context) : LinearLayout(context) {
    // Floating window overlay (semi-transparent)
    // Tabs: RPG Maker / Pokemon Essentials / Custom
    // 
    // RPG Maker tab:
    // [Gold: ______] [SET]
    // [Level Up All] [HP/MP Full]
    // [Walk Through Walls: OFF]
    // [No Encounters: OFF]
    //
    // Pokemon Essentials tab:
    // [Get All Items]
    // [Get All Pokemon]
    // [Shiny Encounter: OFF]
    // [Catch Rate: 100%]
    //
    // Custom Scripts tab:
    // [Script: ________________] [RUN]
    // [Saved Scripts:]
    //   - script1.js
    //   - my_cheat.rb
}
```

---

## 7. Map Optimization

### 7.1 Current State
**Nothing.**

### 7.2 What JoiPlay's "Optimize Maps" Does

Recreates maps and tilesets to reduce tileset height. This fixes tile rendering issues on mobile devices, especially for Pokémon Essentials games.

### 7.3 Implementation

```kotlin
// MapOptimizer.kt
class MapOptimizer {
    
    /**
     * Optimizes all maps in a game directory for mobile rendering.
     * Strategy:
     * 1. Parse .rxdata/.rvdata2 map files (RGSS)
     * 2. For each map, check tileset height
     * 3. If tileset height exceeds mobile-compatible limit, recreate it
     * 4. Reduce tileset height by splitting into multiple tilesets
     * 5. Update map data with new tileset references
     * 
     * For MV/MZ (JS-based maps):
     * 1. Parse map.json files in data/ directory
     * 2. Apply similar tile reduction
     * 3. Patch the tileSize or scale down large tilesets
     */
    
    data class OptimizationResult(
        val mapsProcessed: Int,
        val tilesetsReduced: Int,
        val errors: List<String>
    )
    
    fun optimize(gameDir: File, engineType: EngineType): OptimizationResult {
        return when (engineType) {
            EngineType.RGSS_XP, EngineType.RGSS_VX, EngineType.RGSS_VX_ACE -> 
                optimizeRgssMaps(gameDir)
            EngineType.MV, EngineType.MZ ->
                optimizeWebMaps(gameDir)
            else -> OptimizationResult(0, 0, emptyList())
        }
    }
    
    private fun optimizeRgssMaps(gameDir: File): OptimizationResult {
        // Using Ruby/Marshal deserialization compatible library
        // 1. Find all Map*.rxdata/rvdata2 files
        // 2. Load map data (Marshal.load equivalent)
        // 3. Check tileset_id and associated tileset
        // 4. If tileset height > 512px, create reduced version
        // 5. Save back
        TODO("Implement RGSS Marshal parser or use scripts")
    }
    
    private fun optimizeWebMaps(gameDir: File): OptimizationResult {
        // For MV/MZ — patch tileset definitions in JSON
        val dataDir = File(gameDir, "www/data")
        val mapFiles = dataDir.listFiles { f -> f.name.startsWith("Map") && f.name.endsWith(".json") }
        // Reduce large tilesets
        TODO("Implement tileset height reduction for MV/MZ")
    }
}
```

---

## 8. Game Library & UI

### 8.1 Current State
```
HomeScreen.kt — 517 lines
✅ Hero cards with engine color
✅ Engine label
✅ Game name overlay
✅ File count
✅ Import button
✅ Manage Files button
✅ Settings button
❌ No search
❌ No game art (solid color backgrounds)
❌ No sorting
❌ No collections
❌ No recent games
❌ No context menus
```

### 8.2 Target Library UI

```kotlin
// LibraryScreen.kt — complete rewrite or extension
class LibraryScreen(context: Context) : FrameLayout(context) {
    
    // View Modes:
    enum class ViewMode { GRID, LIST, COMPACT }
    
    // Sort:
    enum class SortBy { NAME, RECENT, ENGINE, FILE_COUNT }
    
    // Filter:
    data class LibraryFilter(
        val searchQuery: String = "",
        val engineFilter: Set<EngineType> = emptySet(),
        val showMissing: Boolean = true,
        val collections: Set<String> = emptySet()
    )
    
    // Game art extraction:
    // 1. Check for Game.exe icon (Windows PE parser) for RGSS games
    // 2. Check for www/icon/icon.png for MV/MZ
    // 3. Check for renpy/game/icon.png for Ren'Py
    // 4. Check for game.cfg icon reference
    // 5. Fall back to engine color
    fun extractGameArt(gameDir: File): Bitmap? { ... }
    
    // Context menu (long-press):
    // - Play
    // - Settings
    // - Manage Files
    // - Edit Config
    // - Optimize Maps
    // - Export Save
    // - Import Save
    // - Remove Game
    // - View Info
    
    // Collections:
    // - "Favorites"
    // - "Completed"
    // - "Playing"
    // - Custom collections
    // Managed via JSON file at {filesDir}/collections.json
}
```

---

## 9. Save File Management

### 9.1 Current State
```
SaveManager.kt — 108 lines
✅ syncFromActive() — backup before reimport
✅ restoreToActive() — restore after reimport
✅ listSaves() — list saves in dialog
❌ No manual import/export
❌ No cross-platform PC save compatibility
❌ No save file viewer
```

### 9.2 Target Save System

```kotlin
// SaveManager.kt — expanded
class SaveManager(private val workspaceManager: WorkspaceManager) {
    
    data class SaveFileInfo(
        val name: String,
        val file: File,
        val size: Long,
        val lastModified: Long,
        val slot: Int?,
        val screenshot: Bitmap?,  // RGSS saves sometimes have embedded screenshots
        val metadata: Map<String, String>?  // play time, location, level
    )
    
    // Cross-platform save import
    // RPG Maker: Save*.rvdata2/rxdata files from PC
    // Ren'Py: .save files from PC
    fun importFromFile(storageName: String, saveFile: Uri): Boolean {
        // Copy save to correct location
        // For MV/MZ: www/save/
        // For RGSS: game root
        // For Ren'Py: game/saves/
    }
    
    // Export save to share
    fun exportToFile(storageName: String, saveName: String, targetUri: Uri): Boolean
    
    // Save info dialog with metadata
    fun showSaveInfoDialog(context: Context, storageName: String) {
        // Shows list of saves with:
        // - Slot number
        // - File size
        // - Last modified
        // - Play time (if in metadata)
        // - Location (if in metadata)
        // Actions: Export, Delete, Restore
    }
    
    // One-click PC save compatibility
    // RPG Maker games use same file format on PC and Android
    // User just copies Save*.rvdata2 files from PC to phone
    fun detectPcSaves(importFolder: Uri): List<SaveFileInfo> {
        // Scan folder for compatible save files
    }
}
```

---

## 10. Settings Database

### 10.1 Current State
```
SettingsStore.kt — saves entire RunnerSettings as a single SharedPreferences blob
SettingsScreen.kt — 429 lines
```

### 10.2 Target: Layered Settings

```kotlin
// Settings architecture:
//
//  ┌──────────────────────────────────────────┐
//  │          1. Defaults (hardcoded)         │
//  ├──────────────────────────────────────────┤
//  │          2. Global (SharedPrefs)          │
//  ├──────────────────────────────────────────┤
//  │        3. Per-Game (runestone.json)       │
//  ├──────────────────────────────────────────┤
//  │    4. Runtime/Temporary (in-memory)       │
//  └──────────────────────────────────────────┘
//
// Resolution: runtime > per-game > global > defaults

class SettingsManager(context: Context) {
    private val global = SettingsStore(context)
    
    fun getEffective(storageName: String? = null): PerGameConfig {
        val defaults = PerGameConfig()
        val globalConfig = global.load()
        val gameConfig = if (storageName != null) {
            PerGameConfig.load(workspaceManager.gameDir(storageName))
        } else null
        
        return mergeConfigs(defaults, globalConfig, gameConfig)
    }
}

// Settings Categories (SettingsScreen.kt rewrite):
class SettingsScreenV2(context: Context) : LinearLayout(context) {
    // Tabs: General | Input | Video | Audio | Performance | About
    
    // GENERAL tab:
    // - Theme: Dark, Light, System, Material You
    // - Wallpaper: pick image from gallery
    // - Language: (future i18n)
    // - Default Engine: Auto / mkxp-z / MV / MZ / Ren'Py
    
    // INPUT tab:
    // - Default Layout: Portrait Console / Landscape / Gamepad
    // - Button Opacity: slider
    // - Button Scale: slider
    // - Show X/Y Buttons: toggle
    // - Show L1/R1 Buttons: toggle
    // - Haptic Feedback: toggle
    // - Haptic Intensity: slider
    // - Controller Mapping: opens mapping screen
    
    // VIDEO tab:
    // - Screen Filter: None / CRT / Scanlines / Gameboy / Sepia / Night
    // - Integer Scaling: toggle
    // - Smooth Scaling: toggle
    // - Show FPS: toggle
    // - VSync: toggle
    
    // AUDIO tab:
    // - Force Audio Extension: .ogg / .m4a / None
    // - Audio Buffer Size: 1024 / 2048 / 4096 / 8192
    // - Mute Music: toggle
    // - Mute SFX: toggle
    
    // PERFORMANCE tab:
    // - Default Speed: 1x / 2x / 3x / 4x
    // - Optimize Maps on Import: toggle
    // - Force Miniz (RGSS): toggle
    // - Threaded Rendering: toggle
    // - Frame Skip: 0 / 1 / 2 / 3
    
    // ABOUT tab:
    // - Version info
    // - Installed plugins
    // - Licenses
    // - GitHub link
}
```

---

## 11. Plugin System for Third Parties

### 11.1 Plugin Template

Following JoiPlay's model (but open and documented):

```kotlin
// PluginTemplate.kt — published in documentation
abstract class RunestonePlugin {
    abstract val engineId: String
    abstract val displayName: String
    abstract val supportedTypes: List<String>
    abstract val version: String
    
    abstract fun canRun(gameFolder: File): Boolean
    abstract fun launch(context: Context, gameFolder: File, config: PerGameConfig)
    abstract fun getMetadata(gameFolder: File): GameMetadata?
    abstract fun getSaves(gameFolder: File): List<SaveFileInfo>
    
    // Optional
    open fun getSettingsView(context: Context): View? = null
    open fun onImport(context: Context, gameFolder: File): Boolean = true
}
```

### 11.2 Plugin Publishing

- GitHub template repository for new plugins
- Gradle plugin for easy build setup
- Documentation for the Intent-based communication protocol

---

## 12. Missing Engine Support

### 12.1 Godot Support

**Priority:** Medium (added by JoiPlay Nov 2024)

```kotlin
// GodotPlugin.kt (separate APK)
class GodotEngine : GameEngine {
    override val id = "godot"
    override val name = "Godot Engine"
    override val priority = 25
    
    // Detection: project.godot or .pck files
    override fun canRun(gameFolder: File): Boolean {
        return gameFolder.listFiles()?.any { 
            it.name == "project.godot" || it.name.endsWith(".pck")
        } ?: false
    }
    
    // Launch: Pass to Godot Android native activity
    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        val intent = Intent().apply {
            setClassName(context.packageName, "org.godotengine.android.GodotActivity")
            putExtra("godot_arg", "-path")
            putExtra("godot_arg_value", gameFolder.absolutePath)
            putExtra("runestone_config", config.toJson())
        }
        context.startActivity(intent)
    }
}
```

### 12.2 Flash / Ruffle Support

**Priority:** Low

```kotlin
// RufflePlugin.kt (separate APK, wraps Ruffle Android WebView)
class RuffleEngine : GameEngine {
    override val id = "ruffle"
    override val name = "Flash (Ruffle)"
    
    override fun canRun(gameFolder: File): Boolean {
        return gameFolder.listFiles()?.any { 
            it.name.endsWith(".swf")
        } ?: false
    }
    
    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        // Launch Ruffle WebView pointing to the SWF file
        val intent = Intent().apply {
            setClassName(context.packageName, "com.runestone.plugin.ruffle.RuffleActivity")
            putExtra("swf_path", findMainSwf(gameFolder).absolutePath)
        }
        context.startActivity(intent)
    }
}
```

### 12.3 NScripter / Twine / VN Maker

**Priority:** Low
**Approach:** WebView-based for all three (they output HTML/JS).

---

## 13. Game Import Pipeline

### 13.1 Current State
```
SafGameImporter.kt — 194 lines
✅ SAF folder import
✅ File copy to incoming/ -> original/
✅ Engine detection
✅ Manifest generation
✅ Active workspace rebuild
✅ Save backup before reimport
```

### 13.2 Target Import Pipeline

```kotlin
// Enhanced SafGameImporter.kt

class EnhancedGameImporter(...) {
    
    fun importTree(treeUri: Uri, requestedStorageName: String? = null): ImportResult {
        // 1. Parse game folder name
        // 2. Allocate workspace
        // 3. Copy files (with real-time progress per file)
        // 4. Detect engine type
        // 5. Auto-detect game title:
        //    - RGSS: parse Game.ini [Game] Title=
        //    - MV/MZ: parse www/data/System.json gameTitle
        //    - Ren'Py: parse game/options.rpy config.name
        //    - EasyRPG: parse RPG_RT.ldb
        // 6. Extract game art:
        //    - RGSS: extract icon from Game.exe (PE parser) or use default
        //    - MV/MZ: www/icon/icon.png
        //    - Ren'Py: game/icon.png
        // 7. Check for RTP dependency (RGSS games):
        //    - Scan for common RTP resources
        //    - If missing, prompt download
        // 8. Create runestone.json with defaults
        // 9. Optionally run map optimization
        // 10. Build active workspace
    }
    
    // Single .exe import (many games distributed as .exe)
    // Uses RGA-like extraction or unzip
    fun importExecutable(exeFile: Uri): ImportResult {
        // 1. Copy .exe to incoming/
        // 2. Try to extract as ZIP archive
        //    - Many RPG Maker games are self-extracting ZIPs
        //    - If not a ZIP, try other extraction methods
        // 3. If extraction fails, show error
    }
    
    // RGA/JGP archive import
    fun importArchive(archiveFile: Uri): ImportResult {
        // .rga = renamed .zip with game.cfg inside
        // .jgp = renamed .zip with JoiPlay-specific metadata
        // 1. Parse game.cfg for metadata
        // 2. Extract to workspace
    }
}

// Game metadata result
data class GameMetadata(
    val title: String,
    val engine: EngineType,
    val engineVersion: String?,
    val author: String?,
    val version: String?,
    val icon: Bitmap?,
    val requiresRtp: Boolean,
    val rtpVersion: String?,
    val fileCount: Int,
    val totalSize: Long
)
```

---

## 14. Configuration File Format

### 14.1 Game Info File (game.cfg / runestone.cfg)

Support the RGA standard format for compatibility:

```ini
# game.cfg — RGA standard
title=My Game
id=my-game-id
execFile=Game.exe
icon=folder/icon.png
version=1.0
type=rpgmxp
```

Runestone also supports its own richer JSON format:

```json
{
  "runestoneConfig": true,
  "version": 2,
  "game": { ... },
  "input": { ... },
  "video": { ... },
  "audio": { ... },
  "performance": { ... },
  "cheats": { ... },
  "fonts": { ... }
}
```

### 14.2 JGP File Import

JoiPlay's JGP files are renamed ZIPs that contain:
- Game files
- `game.cfg` configuration
- Icon images
- Controller mapping presets

Runestone should support importing these for compatibility.

---

## 15. Expanded Settings

### 15.1 Screen Filters

```kotlin
enum class ScreenFilter(val shaderDescription: String) {
    NONE("No filter"),
    CRT("CRT scanlines overlay"),
    SCANLINES("Horizontal scanlines only"),
    GAMEBOY("Green-tinted, 4-color palette"),
    SEPIA("Warm brown tint"),
    NIGHT("Blue light reduction"),
    SHARPEN("Increase contrast and sharpness"),
    PIXELATED("Nearest-neighbor crisp pixels");
    
    // For WebView: CSS filter + canvas overlay
    // For mkxp-z: OpenGL shader pass
    fun applyToWebView(webView: WebView) {
        val css = when (this) {
            CRT -> """
                #runestone-filter {
                    pointer-events: none;
                    position: fixed; top: 0; left: 0;
                    width: 100%; height: 100%;
                    background: repeating-linear-gradient(
                        0deg, rgba(0,0,0,0.08) 0px, 
                        rgba(0,0,0,0.08) 1px, 
                        transparent 1px, transparent 3px
                    );
                    z-index: 99998;
                }
            """.trimIndent()
            // ...
        }
        webView.evaluateJavascript("""
            (function(){
                var el = document.getElementById('runestone-filter');
                if (!el) { el = document.createElement('div'); el.id = 'runestone-filter'; document.body.appendChild(el); }
                var style = document.createElement('style');
                style.textContent = `$css`;
                document.head.appendChild(style);
            })();
        """.trimIndent(), null)
    }
}
```

### 15.2 Performance Options

```kotlin
data class PerformanceOptions(
    val speedMultiplier: Float = 1.0f,     // 0.5x - 4.0x
    val frameSkip: Int = 0,                 // 0 = none, 1 = skip every other, 2 = skip 2 of 3
    val threadedRendering: Boolean = true,
    val forceMiniz: Boolean = false,         // RGSS: use miniz instead of zlib
    val optimizeMapsOnImport: Boolean = false,
    val textureCacheSize: Int = 64,         // MB
    val audioBufferSize: Int = 2048,        // samples
    val backgroundLoad: Boolean = true,     // load resources async
    val reduceShadowQuality: Boolean = false,
    val reduceParticleEffects: Boolean = false,
)
```

### 15.3 Font Options

```kotlin
data class FontOptions(
    val fontScale: Float = 1.0f,            // 0.5x - 2.0x
    val boldText: Boolean = false,
    val italicText: Boolean = false,
    val fallbackFontPath: String? = null,    // path to TTF/OTF file
    val useGameFonts: Boolean = true,       // try to use embedded fonts
    val textOutlineSize: Int = 0,           // 0 = none, 1-4 px
    val lineSpacing: Float = 1.0f,          // 0.8 - 2.0
)
```

---

## 16. Complete Settings Reference

### 16.1 All Settings — Master Table

#### Global Settings (applied to all games unless overridden)

| ID | Type | Default | Description |
|----|------|---------|-------------|
| `theme` | enum | `dark` | App theme: `dark`, `light`, `system`, `material_you` |
| `wallpaper` | string | null | Path to custom wallpaper image |
| `language` | enum | `en` | UI language |
| `defaultEngine` | string | `auto` | Default engine for detection: `auto`, `mkxp-z`, `mv`, `mz`, `renpy`, `easyrpg` |
| `showFpsGlobal` | bool | `true` | Show FPS overlay in all games |
| `defaultSpeed` | float | `1.0` | Default game speed multiplier |
| `optimizeOnImport` | bool | `false` | Run map optimization on import |
| `autoBackupSaves` | bool | `true` | Backup saves before reimport |
| `enableDeveloperMode` | bool | `false` | Show debug options |

#### Per-Game Settings (override global)

| ID | Type | Default | Description |
|----|------|---------|-------------|
| **Input** | | | |
| `input.layoutMode` | enum | `portrait_console` | `landscape`, `portrait_console`, `gamepad` |
| `input.buttonOpacity` | float | `0.72` | 0.0 - 1.0 |
| `input.buttonScale` | float | `1.0` | 0.5 - 2.0 |
| `input.showExtraButtons` | bool | `false` | Show X/Y buttons |
| `input.showL1R1` | bool | `false` | Show L1/R1 shoulder buttons |
| `input.showL2R2` | bool | `false` | Show L2/R2 trigger buttons |
| `input.hapticsEnabled` | bool | `true` | Vibrate on touch |
| `input.hapticIntensity` | float | `0.55` | 0.0 - 1.0 |
| `input.controllerPreset` | string | `auto` | Controller mapping preset |
| `input.invertAxisX` | bool | `false` | Invert left stick X axis |
| `input.invertAxisY` | bool | `false` | Invert left stick Y axis |
| `input.deadZone` | float | `0.3` | Controller analog dead zone |
| **Video** | | | |
| `video.screenFilter` | enum | `none` | `none`, `crt`, `scanlines`, `gameboy`, `sepia`, `night`, `sharpen` |
| `video.integerScaling` | bool | `false` | Scale by integer multiples only |
| `video.smoothScaling` | bool | `false` | Bilinear/trilinear filtering |
| `video.showFps` | bool | `true` | FPS counter overlay |
| `video.vsync` | bool | `true` | Enable vertical sync |
| `video.resolutionScale` | float | `1.0` | Internal resolution scale (0.5 - 2.0) |
| `video.brightness` | float | `1.0` | 0.0 - 2.0 |
| `video.contrast` | float | `1.0` | 0.0 - 2.0 |
| **Audio** | | | |
| `audio.forceAudioExt` | string | `.ogg` | Force audio extension: `.ogg`, `.m4a`, `none` |
| `audio.audioBufferSize` | int | `2048` | Audio buffer in samples |
| `audio.muteMusic` | bool | `false` | Mute BGM |
| `audio.muteSfx` | bool | `false` | Mute SE/ME |
| `audio.muteVideo` | bool | `false` | Mute video playback |
| `audio.volume` | float | `1.0` | Master volume 0.0 - 1.0 |
| `audio.volumeMusic` | float | `1.0` | Music volume 0.0 - 1.0 |
| `audio.volumeSfx` | float | `1.0` | SFX volume 0.0 - 1.0 |
| **Performance** | | | |
| `performance.speedMultiplier` | float | `1.0` | 0.5 - 4.0 (game speed) |
| `performance.frameSkip` | int | `0` | 0-3 (frame skip level) |
| `performance.threadedRendering` | bool | `true` | Multi-threaded render |
| `performance.forceMiniz` | bool | `false` | Force miniz for RGSS decompression |
| `performance.optimizeMaps` | bool | `false` | Optimize map tilesets |
| `performance.textureCacheSize` | int | `64` | Texture cache in MB |
| `performance.reduceShadows` | bool | `false` | Reduce shadow rendering |
| `performance.reduceParticles` | bool | `false` | Reduce particle effects |
| `performance.backgroundLoading` | bool | `true` | Async resource loading |
| **Fonts** | | | |
| `fonts.fontScale` | float | `1.0` | 0.5 - 2.0 |
| `fonts.boldText` | bool | `false` | Force bold rendering |
| `fonts.italicText` | bool | `false` | Force italic rendering |
| `fonts.fallbackFont` | string | null | Path to TTF/OTF fallback |
| `fonts.useGameFonts` | bool | `true` | Use game's embedded fonts |
| `fonts.textOutline` | int | `0` | 0-4 px text outline |
| `fonts.lineSpacing` | float | `1.0` | 0.8 - 2.0 |
| **Cheats** | | | |
| `cheats.enabled` | bool | `false` | Enable cheat menu |
| `cheats.postLoadScripts` | list | `[]` | Scripts to inject after game loads |
| **Engine** | | | |
| `engine.override` | string | null | Override auto-detected engine |
| `engine.debugMode` | bool | `false` | Enable engine debug logging |
| `engine.customArgs` | map | `{}` | Extra arguments for engine |

---

## 17. Implementation Priority Matrix

### Phase 0 — Fix Critical Broken Things (Now)

| Task | Est. Time | Files to touch |
|------|-----------|----------------|
| Uncomment mkxp-z ndkBuild in build.gradle.kts | 1h | `app/build.gradle.kts` |
| Build mkxp-z .so for arm64-v8a | 4h | `native/mkxp-z-android/` |
| Make EasyRpgEngine show download prompt instead of toast | 1h | `EasyRpgEngine.kt` |
| Make RenpyEngine show download prompt instead of toast | 1h | `RenpyEngine.kt` |

### Phase 1 — Per-Game Config & Input (Week 1)

| Task | Est. Time | Files to create/modify |
|------|-----------|-----------------------|
| `PerGameConfig.kt` data class + JSON serialization | 2h | NEW |
| `GameConfigService.kt` (global + per-game merging) | 2h | NEW |
| Complete `TouchOverlayView` layout editor (save/load to JSON) | 4h | `TouchOverlayView.kt` |
| Add L1/R1/L2/R2 buttons to `TouchOverlayView` | 2h | `TouchOverlayView.kt` |
| `ControllerMapper.kt` (physical controller support) | 6h | NEW + `GameActivity.kt` |
| `ControllerMappingScreen.kt` | 4h | NEW |
| Per-game settings screen (reuse SettingsScreen UI) | 3h | `SettingsScreen.kt` |

### Phase 2 — In-Game Features (Week 2)

| Task | Est. Time | Files to create/modify |
|------|-----------|-----------------------|
| `InGameMenu.kt` (slide-out overlay panel) | 6h | NEW |
| Speed-up/fast-forward for WebView engines | 3h | `InGameMenu.kt`, `WebViewEngine.kt` |
| Speed-up for mkxp-z (frame skip) | 4h | `MkxpZEngine.kt`, mkxp-z patches |
| Screenshot capture | 2h | `InGameMenu.kt` |
| Rotate screen | 1h | `InGameMenu.kt`, `GameActivity.kt` |

### Phase 3 — Cheats & Optimizations (Week 3)

| Task | Est. Time | Files to create/modify |
|------|-----------|-----------------------|
| `CheatEngine.kt` (MV/MZ JavaScript cheats) | 4h | NEW |
| `CheatEngine.kt` (RGSS Ruby cheats) | 4h | NEW |
| `CheatMenuView.kt` (floating cheat overlay) | 3h | NEW |
| `MapOptimizer.kt` (tileset height reduction) | 8h | NEW |

### Phase 4 — Plugin System & More Engines (Week 4)

| Task | Est. Time | Files to create/modify |
|------|-----------|-----------------------|
| `PluginDiscoveryService.kt` | 3h | NEW |
| Plugin API interface + documentation | 2h | `PluginAPI.kt` |
| Extract mkxp-z into separate plugin module | 4h | `plugin-mkxpz/` |
| EasyRPG plugin module | 6h | `plugin-easyrpg/` |
| Ren'Py plugin module | 8h | `plugin-renpy/` |
| Import wizard for .rga/.jgp files | 3h | `SafGameImporter.kt` |

### Phase 5 — Library & UI Polish (Week 5)

| Task | Est. Time | Files to create/modify |
|------|-----------|-----------------------|
| Search bar in library | 2h | `HomeScreen.kt` |
| Sort options | 1h | `HomeScreen.kt` |
| Game art extraction | 4h | `GameArtExtractor.kt` NEW |
| Context menus (long-press) | 2h | `HomeScreen.kt` |
| Theme engine (wallpaper, colors) | 3h | `ThemeManager.kt` NEW |
| Collections system | 3h | `CollectionManager.kt` NEW |
| First-time onboarding/welcome | 3h | `OnboardingScreen.kt` NEW |
| Godot plugin (basic) | 4h | `plugin-godot/` |
| Ruffle plugin (basic) | 3h | `plugin-ruffle/` |

### Phase 6 — Expanded Settings (Week 6)

| Task | Est. Time | Files to create/modify |
|------|-----------|-----------------------|
| Screen filters (shader overlays) | 4h | `ScreenFilter.kt` NEW |
| Audio management (volume sliders, mute) | 2h | `AudioManager.kt` NEW |
| Font system (scale, fallback, outline) | 3h | `FontManager.kt` NEW |
| Performance settings UI | 2h | `SettingsScreen.kt` |
| Save file import/export UI | 3h | `SaveManager.kt`, `ManageFilesScreen.kt` |

---

## TOTAL EFFORT ESTIMATE: ~6 weeks (120-140 hours)

---

## Key Architectural Decisions

1. **Plugin APKs not built-in:** Keeps core app small and allows independent updates
2. **JSON over XML for config:** Easier to read, edit, and debug
3. **JavaScript injection for WebView cheats:** Most flexible, no native build needed
4. **Ruby injection for mkxp-z:** Works within existing mkxp-z architecture
5. **Layered settings (default -> global -> per-game -> runtime):** Clean override system
6. **Per-game JSON config in game directory:** Survives app reinstalls
7. **Compatibility with JoiPlay game format (RGA/JGP):** Users can migrate easily
8. **SAF import for modern Android:** Required for Android 10+ scoped storage

---

*This document replaces all previous stubs. Delete EasyRpgEngine.kt and RenpyEngine.kt stubs and replace with proper implementations per the specifications above.*
