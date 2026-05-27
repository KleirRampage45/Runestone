# Agent Instructions — Runestone

## CodeGraph

Use CodeGraph for structural code questions. Always run `codegraph sync` before relying on results after editing files.

```bash
codegraph sync  # Run after editing files
codegraph status  # Check index status
```

## Project Overview

Runestone is an **open-source multi-engine game launcher for Android** — the definitive alternative to JoiPlay. It supports:

### Current Engines
- **RPG Maker MV / MZ** — via Android WebView (✅ fully implemented)
- **RPG Maker XP / VX / VX Ace** — via mkxp-z native runtime (🚧 in progress)

### Planned Engines
- **RPG Maker 2000 / 2003** — via EasyRPG (Phase 1)
- **Ren'Py** — Visual novels (Phase 2)
- **TyranoBuilder** — HTML/JS visual novels (Phase 2)
- **Construct 2/3** — HTML5 games (Phase 3)
- **Flash** — via Ruffle WASM (Phase 3)

See `DESIGN.md` for full architecture and roadmap.

## Key Architecture

### Plugin-Based Engine System

Each engine implements the `GameEngine` interface:

```kotlin
interface GameEngine {
    val id: String              // "mkxp-z", "webview-mv"
    val name: String            // "RPG Maker XP/VX/VX Ace"
    fun canRun(gameFolder: File): Boolean
    fun launch(context: Context, gameFolder: File, config: GameConfig)
    fun getSaves(gameFolder: File): List<SaveFile>
}
```

Engines register in `EngineRegistry` and are auto-detected by `EngineDetector`.

### Project Structure

```
Runestone/
├── app/src/main/java/com/runestone/app/
│   ├── MainActivity.kt        → Home screen, game library, import flow
│   ├── GameActivity.kt        → Routes to correct engine based on detection
│   ├── engine/
│   │   ├── EngineDetector.kt  → Detects engine type from game files
│   │   ├── EngineRegistry.kt  → Plugin registration system
│   │   ├── WebViewEngine.kt   → MV/MZ runtime via Chromium WebView
│   │   └── MkxpZEngine.kt     → mkxp-z integration (WIP)
│   ├── importer/
│   │   └── SafGameImporter.kt → Imports games via SAF
│   ├── workspace/
│   │   └── WorkspaceManager.kt → Manages installed games + files
│   └── data/
│       ├── EngineType.kt      → Enum of supported engines
│       └── RunnerSettings.kt  → User preferences
├── native/
│   └── mkxp-z-android/        → mkxp-z submodule (native C++ runtime)
├── app/src/main/assets/
│   ├── fake_greenworks.js     → Fakes Steam API for MV/MZ games
│   ├── bootstrap.js           → WebGL/WebAudio detection
│   └── gamepad.html           → Virtual gamepad overlay
├── DESIGN.md                  → Architecture & design decisions
├── CONTRIBUTING.md            → Contribution guidelines
└── tests/games/               → Test games (gitignored)
```

## Build

```bash
./gradlew assembleDebug          # Build APK
./gradlew installDebug           # Install on connected device via ADB
```

## Project Rules

- **No copyrighted game files in git.** Test games go in `/tests/games/` (gitignored).
- **GPLv2+ license.** All source files must include the GPL header.
- **Keep patterns consistent with Grimmobile** (blacksouls-android).
- **Engine-agnostic.** EngineDetector must handle any RPG Maker game.
- **Use CodeGraph** for structural queries, not grep.

## Engine Detection Logic

EngineDetector inspects files in this order:
1. `Game.rmmzproject` → MZ
2. `Game.rvproj2` or `Game.rgss3a` → VX Ace
3. `Game.rvproj` or `Scripts.rvdata` → VX
4. `Game.rxproj` or `Scripts.rxdata` → XP
5. `www/index.html` + `package.json` → MV or MZ
6. `Data/` with `.rvdata2` / `.rvdata` / `.rxdata` → respective RGSS engine

## Test Data

Fear & Hunger (Spanish, RPG Maker MV) is at `/tests/games/fear-hunger/` for testing.
Not committed to git. Use it to verify the WebView game player.

## Branches

- `master` — stable/working builds
- `development` — active work, branch y trabajo aquí por defecto
