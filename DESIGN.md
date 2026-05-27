# Runestone — Design Document

**Version:** 1.0  
**Last Updated:** 2026-05-26  
**Status:** Active Development

---

## Executive Summary

Runestone is an open-source (GPLv2+) multi-engine RPG Maker and visual novel launcher for Android. It aims to be the definitive open-source alternative to JoiPlay, providing:

- **Full engine coverage** for all major RPG Maker versions (2000/2003/XP/VX/VX Ace/MV/MZ)
- **Visual novel support** (Ren'Py, TyranoBuilder)
- **HTML5 game support** (Construct 2/3, generic HTML games)
- **Mod-friendly architecture** with AI companion integration
- **Community-driven development** with transparent plugin system

---

## Architecture Overview

### Core Philosophy

1. **Plugin-based engine system** — Each game engine is a separate module/plugin
2. **Lightweight core** — Base app is just the launcher + UI
3. **Native performance** — Use native runtimes where possible (mkxp-z, EasyRPG)
4. **WebView for HTML engines** — MV/MZ, TyranoBuilder, Construct use optimized WebView
5. **Extensible** — Community can add new engine plugins

### High-Level Architecture

```
┌─────────────────────────────────────────────────┐
│                 Runestone App                    │
│  (Kotlin + Jetpack Compose UI)                  │
├─────────────────────────────────────────────────┤
│              Engine Plugin System                │
├──────────────┬──────────────┬──────────────────┤
│  WebView     │  Native      │  Future          │
│  Engines     │  Engines     │  Engines         │
├──────────────┼──────────────┼──────────────────┤
│ • MV (done)  │ • mkxp-z     │ • Ren'Py         │
│ • MZ (done)  │   (XP/VX/    │ • TyranoBuilder  │
│ • Construct  │    VX Ace)   │ • Flash (Ruffle) │
│ • Tyrano     │ • EasyRPG    │                  │
│              │   (2000/03)  │                  │
└──────────────┴──────────────┴──────────────────┘
```

---

## Engine Plugin System

### Plugin Interface

Each engine plugin implements:

```kotlin
interface GameEngine {
    val id: String              // "mkxp-z", "easyrpg", "webview-mv"
    val name: String            // "RPG Maker XP/VX/VX Ace"
    val version: String         // "1.0.0"
    
    // Detection
    fun canRun(gameFolder: File): Boolean
    fun detectEngine(gameFolder: File): EngineMetadata?
    
    // Lifecycle
    fun launch(context: Context, gameFolder: File, config: GameConfig)
    fun getSaves(gameFolder: File): List<SaveFile>
    
    // Optional features
    fun supportsPatching(): Boolean = false
    fun applyPatch(gameFolder: File, patchFile: File): Boolean = false
}

data class EngineMetadata(
    val engine: String,
    val version: String?,
    val title: String,
    val icon: Bitmap?
)
```

### Plugin Registration

```kotlin
object EngineRegistry {
    private val engines = mutableMapOf<String, GameEngine>()
    
    fun register(engine: GameEngine) {
        engines[engine.id] = engine
    }
    
    fun detect(gameFolder: File): GameEngine? {
        return engines.values.firstOrNull { it.canRun(gameFolder) }
    }
    
    fun get(id: String): GameEngine? = engines[id]
}
```

### Plugin Types

#### 1. WebView Engines (HTML5-based)

- **RPG Maker MV/MZ** — NW.js games, run in WebView
- **TyranoBuilder** — HTML/JS visual novels
- **Construct 2/3** — HTML5 game exports
- **Generic HTML** — Any HTML5 game

Implementation:
```kotlin
class WebViewEngine(
    override val id: String,
    override val name: String
) : GameEngine {
    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        val intent = Intent(context, WebViewGameActivity::class.java).apply {
            putExtra("game_path", gameFolder.absolutePath)
            putExtra("entry_point", findEntryPoint(gameFolder)) // index.html
        }
        context.startActivity(intent)
    }
}
```

#### 2. Native Engines (C/C++ with JNI)

- **mkxp-z** — RPG Maker XP/VX/VX Ace (RGSS interpreter)
- **EasyRPG** — RPG Maker 2000/2003

Implementation:
```kotlin
class MkxpZEngine : GameEngine {
    override val id = "mkxp-z"
    override val name = "RPG Maker XP/VX/VX Ace"
    
    override fun launch(context: Context, gameFolder: File, config: GameConfig) {
        val intent = Intent(context, MkxpZActivity::class.java).apply {
            putExtra("game_path", gameFolder.absolutePath)
            // mkxp-z handles the rest natively
        }
        context.startActivity(intent)
    }
}
```

#### 3. Hybrid Engines (Future)

- **Ren'Py** — Python + SDL2, complex integration
- **Flash (Ruffle)** — WASM in WebView

---

## Engine Detection

### Detection Strategy

```kotlin
object EngineDetector {
    fun detect(gameFolder: File): GameEngine? {
        // Try each engine in priority order
        return EngineRegistry.engines.values
            .sortedBy { it.priority }
            .firstOrNull { it.canRun(gameFolder) }
    }
}
```

### Detection Signatures

| Engine | Detection Files |
|--------|----------------|
| RPG Maker MV | `www/data/System.json` or `package.json` with "nw" |
| RPG Maker MZ | `www/data/System.json` + `js/rmmz_*.js` |
| RPG Maker XP | `Game.exe` + `Data/RGSS102E.dll` or `.rxproj` |
| RPG Maker VX | `Game.exe` + `Data/RGSS202E.dll` or `.rvproj` |
| RPG Maker VX Ace | `Game.exe` + `Data/RGSS301.dll` or `.rvproj2` |
| RPG Maker 2000 | `RPG_RT.exe` + `RPG_RT.lmt` |
| RPG Maker 2003 | `RPG_RT.exe` + `RPG_RT.lmt` + newer format |
| Ren'Py | `renpy/` folder or `.rpy` files |
| TyranoBuilder | `data/` + `tyrano/` folders |
| Construct 2/3 | `index.html` + `c2runtime.js` or `c3runtime.js` |
| Pokemon Essentials | `PBS/` folder + RPG Maker XP structure |

---

## Native Engine Integration

### mkxp-z (RPG Maker XP/VX/VX Ace)

**Source:** `KleirRampage45/mkxp-z-android` (submodule)

**Integration Steps:**
1. Add submodule: `git submodule add https://github.com/KleirRampage45/mkxp-z-android native/mkxp-z`
2. Configure NDK build in `app/build.gradle`:
   ```gradle
   android {
       externalNativeBuild {
           cmake {
               path "native/mkxp-z/CMakeLists.txt"
           }
       }
   }
   ```
3. Register activity in `AndroidManifest.xml`:
   ```xml
   <activity android:name="com.hatkid.mkxpz.MainActivity"
             android:configChanges="orientation|screenSize"
             android:screenOrientation="sensorLandscape" />
   ```
4. Implement `MkxpZEngine.kt` to launch the activity

**Configuration:**
```kotlin
data class MkxpZConfig(
    val gamePath: String,
    val rgssVersion: Int,  // 1, 2, or 3
    val enableDebug: Boolean = false,
    val customFont: String? = null
)
```

### EasyRPG (RPG Maker 2000/2003)

**Source:** `EasyRPG/Player` (official Android port)

**Integration Steps:**
1. Add submodule: `git submodule add https://github.com/EasyRPG/Player native/easyrpg`
2. Configure NDK build (similar to mkxp-z)
3. Register activity
4. Implement `EasyRpgEngine.kt`

**Key Differences from mkxp-z:**
- Different file structure detection
- Different save file format
- Simpler configuration

### Ren'Py (Visual Novels) — Future

**Source:** `renpy/renpy` (official)

**Challenges:**
- Python runtime embedding
- Large binary size (~100MB+)
- Complex dependency chain (SDL2, Python, Ren'Py libs)

**Approach Options:**
1. **Separate APK plugin** (like JoiPlay) — Users install Ren'Py plugin separately
2. **Dynamic download** — Download Ren'Py runtime on first use
3. **Embedded** — Bundle everything (bloated but simple)

**Recommendation:** Option 1 (separate APK) for now.

---

## WebView Engine Optimization

### RPG Maker MV/MZ (Already Implemented)

Current implementation uses basic WebView. Optimizations needed:

1. **Hardware acceleration** — Enable WebGL
2. **Audio backend** — Use ExoPlayer for better audio
3. **Touch handling** — Custom touch overlay for game controls
4. **Save management** — Intercept localStorage for save files
5. **Plugin support** — Allow injecting custom JS mods

### TyranoBuilder / Construct

Similar to MV/MZ but with engine-specific tweaks:

```kotlin
class TyranoWebViewActivity : WebViewGameActivity() {
    override fun configureWebView(webView: WebView) {
        super.configureWebView(webView)
        webView.settings.apply {
            // Tyrano-specific optimizations
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
    }
}
```

---

## Save File Management

### Save Location Strategy

```kotlin
object SaveManager {
    fun getSaveDir(gameFolder: File, engine: GameEngine): File {
        // Default: alongside game folder in saves/ subdirectory
        val saveDir = File(gameFolder, "saves")
        if (!saveDir.exists()) saveDir.mkdirs()
        return saveDir
    }
    
    fun backupSaves(gameFolder: File, engine: GameEngine) {
        // Cloud sync hook
        // Future: Google Drive / Dropbox integration
    }
}
```

### Engine-Specific Save Formats

| Engine | Save Location | Format |
|--------|--------------|--------|
| MV/MZ | `www/save/` | JSON files |
| mkxp-z | `Save*.rxdata` | Ruby Marshal |
| EasyRPG | `Save*.lsd` | Custom binary |
| Ren'Py | `game/saves/` | Python pickle |

---

## Game Library

### Data Model

```kotlin
@Entity(tableName = "games")
data class Game(
    @PrimaryKey val id: String,
    val title: String,
    val engine: String,
    val path: String,
    val icon: String?,  // path to icon file
    val lastPlayed: Long?,
    val playTime: Long,  // in seconds
    val metadata: String?  // JSON with engine-specific data
)
```

### Library Features

- **Auto-detection** — Scan folders for games
- **Metadata fetching** — Pull game info from online databases (future)
- **Sorting/filtering** — By engine, last played, play time
- **Search** — By title
- **Custom icons** — User can set custom cover art

---

## Touch Overlay System

### Virtual Controls

```kotlin
class TouchOverlay(context: Context) : View(context) {
    // Configurable button layout
    var layout: OverlayLayout = OverlayLayout.DEFAULT
    
    // Button actions
    val buttons = listOf(
        OverlayButton("dpad", ButtonType.DPAD),
        OverlayButton("a", ButtonType.ACTION),
        OverlayButton("b", ButtonType.CANCEL),
        OverlayButton("start", ButtonType.MENU)
    )
    
    // Touch handling
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Map touch to virtual button press
        // Send key event to game engine
    }
}
```

### Customization

- **Button size** — Adjustable
- **Button position** — Draggable
- **Opacity** — Configurable
- **Profiles** — Save different layouts per game

---

## AI Companion Integration (Killer Feature)

### Architecture

```kotlin
interface AICompanion {
    fun onGameEvent(event: GameEvent)
    fun provideHint(game: Game, context: GameContext): String?
    fun translate(text: String, from: String, to: String): String
}

class HermesCompanion : AICompanion {
    override fun onGameEvent(event: GameEvent) {
        // Log events for context
        // Trigger proactive hints
    }
    
    override fun provideHint(game: Game, context: GameContext): String? {
        // Query Hermes API with game state
        // Return contextual hint
    }
}
```

### Use Cases

1. **Contextual hints** — "You're stuck? Try talking to the NPC in the tavern"
2. **Auto-translation** — Real-time JP→EN translation for untranslated games
3. **Playthrough tracking** — Log choices, achievements, playtime
4. **Fear & Hunger mod** — Your existing AI companion integration

---

## Mod/Patch System

### Patch Types

1. **Translation patches** — Replace text files
2. **Mod patches** — Add/replace game assets
3. **Bug fix patches** — Binary patches for specific issues

### Patch Format

```kotlin
data class Patch(
    val id: String,
    val name: String,
    val version: String,
    val targetGame: String,  // game ID
    val targetEngine: String,
    val files: List<PatchFile>
)

data class PatchFile(
    val source: String,  // path in patch
    val destination: String,  // path in game
    val action: PatchAction  // REPLACE, APPEND, DELETE
)
```

---

## Build System

### Module Structure

```
Runestone/
├── app/                          # Main app module
│   ├── src/main/
│   │   ├── java/                 # Kotlin source
│   │   └── res/                  # Resources
│   └── build.gradle
├── core/                         # Core library (engine interface, utils)
├── engine-webview/               # WebView engine implementation
├── engine-mkxpz/                 # mkxp-z wrapper (loads native .so)
├── engine-easyrpg/               # EasyRPG wrapper
├── native/
│   ├── mkxp-z/                   # mkxp-z submodule
│   └── easyrpg/                  # EasyRPG submodule
└── ui/                           # Shared UI components
```

### Build Variants

```gradle
android {
    buildTypes {
        debug {
            applicationIdSuffix ".debug"
            debuggable true
        }
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt')
        }
    }
    
    flavorDimensions "engine"
    productFlavors {
        full {
            // All engines included
        }
        lite {
            // WebView engines only (smaller APK)
        }
    }
}
```

---

## Distribution

### Primary Channels

1. **GitHub Releases** — APK downloads
2. **F-Droid** — Open source app store
3. **itch.io** — Game-focused audience

### Why NOT Google Play?

- **Policy restrictions** — "Game interpreters" get banned (see: JoiPlay)
- **APK-only is fine** — Target audience knows how to sideload
- **More control** — No Google review delays/rejections

---

## Roadmap

### Phase 1 — Foundation (Current)
- [x] Basic launcher UI
- [x] WebView engine (MV/MZ)
- [ ] mkxp-z integration (XP/VX/VX Ace)
- [ ] EasyRPG integration (2000/2003)
- [ ] Save file management
- [ ] Game library with detection

**Goal:** Cover all RPG Maker versions

### Phase 2 — Visual Novels
- [ ] Ren'Py engine plugin (separate APK)
- [ ] TyranoBuilder support (WebView)
- [ ] Better VN-specific UI (text speed, skip, backlog)

**Goal:** Cover visual novels

### Phase 3 — Full Coverage
- [ ] Construct 2/3 support (WebView)
- [ ] Flash support (Ruffle WASM)
- [ ] Pokemon Essentials auto-detection
- [ ] Touch overlay customization

**Goal:** Feature parity with JoiPlay

### Phase 4 — Killer Features
- [ ] AI companion framework
- [ ] Auto-translation layer
- [ ] Mod/patch loader
- [ ] Cloud save sync
- [ ] Multiplayer support (where applicable)

**Goal:** Surpass JoiPlay

---

## Technical Debt Prevention

1. **Write tests** — Unit tests for engine detection, integration tests for launches
2. **Document everything** — This design doc, code comments, README
3. **Modular architecture** — Keep engines isolated
4. **Use Kotlin coroutines** — For async operations
5. **Proper error handling** — User-friendly error messages
6. **Performance monitoring** — Track launch times, crashes

---

## Contributing

See `CONTRIBUTING.md` for guidelines.

### Development Setup

```bash
# Clone with submodules
git clone --recursive https://github.com/KleirRampage45/Runestone.git
cd Runestone

# Build
./gradlew assembleDebug

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Testing Games

Test with games from each engine:
- **MV/MZ:** Any RPG Maker MV/MZ export
- **XP/VX/VX Ace:** Black Souls series, Fear & Hunger (mkxp-z)
- **2000/2003:** Yume Nikki, Ib (EasyRPG)
- **Ren'Py:** DDLC, Monster Prom (future)

---

## References

- **JoiPlay:** https://joiplay.net/ (competitor reference)
- **mkxp-z:** https://github.com/KleirRampage45/mkxp-z-android
- **EasyRPG:** https://github.com/EasyRPG/Player
- **Ren'Py:** https://github.com/renpy/renpy
- **RPG Maker:** https://www.rpgmakerweb.com/

---

## License

GPLv2+ — See `LICENSE` file.

---

**Questions?** Open an issue or join the discussion on GitHub.
