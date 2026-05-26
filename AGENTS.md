# Agent Instructions — Runestone

## CodeGraph

Use CodeGraph for structural code questions. Always run `codegraph sync` before relying on results after editing files.

## Project Overview

Runestone is a multi-engine RPG Maker game launcher for Android. It supports:
- **XP / VX / VX Ace** — via mkxp-z native runtime (TODO: integrate native .so)
- **MV / MZ** — via Android WebView (fully implemented in WebViewEngine.kt)

## Key Architecture

```
app/src/main/java/com/runestone/app/
├── MainActivity.kt        → Home screen, game list, import flow
├── GameActivity.kt        → Routes to correct engine based on detected type
├── engine/
│   ├── EngineDetector.kt  → Detects engine type from game files
│   ├── WebViewEngine.kt   → Full MV/MZ runtime via Chromium WebView
│   └── RgssEngine.kt      → Stub for mkxp-z integration (WIP)
├── importer/
│   └── SafGameImporter.kt → Imports games via SAF (full implementation)
├── workspace/
│   └── WorkspaceManager.kt → Manages installed games + files
└── data/
    ├── GameEntry.kt       → Removed. Use WorkspaceManager.GameInfo instead
    └── RunnerSettings.kt

app/src/main/assets/
├── fake_greenworks.js     → Fakes Steam API for MV/MZ games
├── bootstrap.js           → WebGL/WebAudio detection and bootstrapper
└── gamepad.html           → Virtual gamepad overlay (injected via JS)
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
